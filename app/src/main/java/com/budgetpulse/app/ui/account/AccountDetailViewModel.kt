package com.budgetpulse.app.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.data.repository.AccountDeleteResult
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountWithBalance
import com.budgetpulse.app.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountDetailUiState(
    val loading: Boolean = true,
    val currency: String = "USD",
    val account: AccountWithBalance? = null,
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList()
)

class AccountDetailViewModel(
    private val container: AppContainer,
    private val accountId: Long
) : ViewModel() {
    private val deleteResult = MutableStateFlow<AccountDeleteResult?>(null)

    val state = combine(
        container.accountRepository.observeAll(),
        container.transactionRepository.observeForAccount(accountId),
        container.userPreferences.prefs
    ) { accounts, transactions, prefs ->
        val account = accounts.firstOrNull { it.id == accountId }
        AccountDetailUiState(
            loading = false,
            currency = prefs.currency,
            account = account?.let {
                AccountWithBalance(it, BudgetCalculator.accountBalance(it.initialBalance, transactions))
            },
            accounts = accounts,
            transactions = transactions
        )
    }.flowOn(Dispatchers.Default).stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AccountDetailUiState()
    )

    val accountDeleteResult = deleteResult

    fun deleteAccount(destinationId: Long? = null) {
        viewModelScope.launch {
            deleteResult.value = container.accountRepository.deleteOrMerge(accountId, destinationId)
        }
    }

    fun clearDeleteResult() {
        deleteResult.value = null
    }
}
