package com.budgetpulse.app.ui.budgets

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.home.LimitUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BudgetsUiState(
    val loading: Boolean = true,
    val currency: String = "USD",
    val limits: List<LimitUi> = emptyList(),
    val freeCategories: List<Category> = emptyList(),
    val goals: List<SavingsGoal> = emptyList(),
    val savingsTarget: Double = 0.0,
    val monthNet: Double = 0.0
)

class BudgetsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<BudgetsUiState> = combine(
        container.userPreferences.prefs,
        container.transactionRepository.observeAll(),
        container.budgetRepository.observeLimits(),
        container.budgetRepository.observeGoals()
    ) { prefs, txs, limits, goals ->
        val month = BudgetCalculator.report(txs, Period.MONTH, LocalDate.now(), 0)
        val ui = limits.map { l ->
            val spent = month.breakdown.firstOrNull { it.category == l.category }?.amount ?: 0.0
            LimitUi(l.category, BudgetCalculator.limitProgress(l.monthlyLimit, spent))
        }.sortedBy { it.category.ordinal }
        BudgetsUiState(
            loading = false, currency = prefs.currency, limits = ui,
            freeCategories = Category.forType(TxType.EXPENSE).filter { c -> limits.none { it.category == c } },
            goals = goals, savingsTarget = prefs.monthlySavingsTarget, monthNet = month.summary.net
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetsUiState())

    fun setLimit(category: Category, amount: Double) { viewModelScope.launch { container.budgetRepository.setLimit(category, amount) } }
    fun removeLimit(category: Category) { viewModelScope.launch { container.budgetRepository.removeLimit(category) } }
    fun addGoal(name: String, target: Double) { viewModelScope.launch { container.budgetRepository.addGoal(SavingsGoal(name = name.trim(), target = target, saved = 0.0)) } }
    fun addFunds(goal: SavingsGoal, amount: Double) { viewModelScope.launch { container.budgetRepository.updateGoal(goal.copy(saved = goal.saved + amount)) } }
    fun deleteGoal(id: Long) { viewModelScope.launch { container.budgetRepository.deleteGoal(id) } }
    fun setSavingsTarget(value: Double) { viewModelScope.launch { container.userPreferences.setMonthlySavingsTarget(value) } }
}
