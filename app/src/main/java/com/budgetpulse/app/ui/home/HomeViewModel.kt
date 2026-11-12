package com.budgetpulse.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.logic.LimitProgress
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.domain.logic.Summary
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.AccountWithBalance
import com.budgetpulse.app.domain.model.BudgetLimit
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class LimitUi(val category: Category, val progress: LimitProgress)

data class HomeUiState(
    val loading: Boolean = true,
    val userName: String = "",
    val currency: String = "USD",
    val totalBalance: Double = 0.0,
    val accounts: List<AccountWithBalance> = emptyList(),
    val recent: List<Transaction> = emptyList(),
    val limits: List<LimitUi> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val month: Summary = Summary(0.0, 0.0),
    val savingsTarget: Double = 0.0,
    val hasTransactions: Boolean = false,
    val news: List<NewsItem> = emptyList(),
    val newsLoading: Boolean = false,
    val unreadNotifications: Int = 0
)

private data class Core(val prefs: UserPrefs, val accounts: List<Account>, val txs: List<Transaction>)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val newsLoading = MutableStateFlow(false)

    private val core = combine(
        container.userPreferences.prefs,
        container.accountRepository.observeAll(),
        container.transactionRepository.observeAll()
    ) { prefs, accounts, txs -> Core(prefs, accounts, txs) }

    private data class Extras(val news: List<NewsItem>, val newsLoading: Boolean, val unread: Int)

    private val extras = combine(
        container.newsRepository.observeNews(), newsLoading, container.notificationRepository.observeUnreadCount()
    ) { news, loading, unread -> Extras(news, loading, unread) }

    val state: StateFlow<HomeUiState> = combine(
        core,
        container.budgetRepository.observeLimits(),
        container.budgetRepository.observeGoals(),
        extras
    ) { c, limits, goals, x -> build(c, limits, goals, x.news, x.newsLoading, x.unread) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    init {
        refreshNews(force = false)
    }

    private fun build(c: Core, limits: List<BudgetLimit>, goals: List<SavingsGoal>, news: List<NewsItem>, loadingNews: Boolean, unread: Int): HomeUiState {
        val balances = c.accounts.map { a ->
            AccountWithBalance(a, BudgetCalculator.accountBalance(a.initialBalance, c.txs.filter { it.accountId == a.id }))
        }
        val month = BudgetCalculator.report(c.txs, Period.MONTH, LocalDate.now(), 0)
        val limitUi = limits.map { l ->
            val spent = month.breakdown.firstOrNull { it.category == l.category }?.amount ?: 0.0
            LimitUi(l.category, BudgetCalculator.limitProgress(l.monthlyLimit, spent))
        }.sortedByDescending { it.progress.fraction }
        return HomeUiState(
            loading = false,
            userName = c.prefs.userName,
            currency = c.prefs.currency,
            totalBalance = balances.sumOf { it.balance },
            accounts = balances,
            recent = c.txs.take(5),
            limits = limitUi,
            goals = goals,
            month = month.summary,
            savingsTarget = c.prefs.monthlySavingsTarget,
            hasTransactions = c.txs.isNotEmpty(),
            news = news,
            newsLoading = loadingNews,
            unreadNotifications = unread
        )
    }

    fun refreshNews(force: Boolean) {
        viewModelScope.launch {
            newsLoading.value = true
            val result = container.newsRepository.refreshIfNeeded(force)
            AppLog.d(AppLog.STATE, "home news refresh success=${result.isSuccess}")
            newsLoading.value = false
        }
    }

    fun addAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch {
            val colors = listOf(0xFF8E54E9, 0xFF2BA58F, 0xFFD6336C, 0xFF4F6BED)
            container.accountRepository.add(
                Account(name = name.trim(), type = type, colorArgb = colors.random(), initialBalance = initialBalance)
            )
        }
    }
}
