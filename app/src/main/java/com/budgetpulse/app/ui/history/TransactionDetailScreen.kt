package com.budgetpulse.app.ui.history

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.ui.common.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val tx: Transaction? = null,
    val accountName: String = "",
    val currency: String = "USD"
)

/** Receives the transaction id as a navigation argument and observes that single row in Room. */
class TransactionDetailViewModel(private val container: AppContainer, private val id: Long) : ViewModel() {
    val state: StateFlow<DetailUiState> = combine(
        container.transactionRepository.observe(id),
        container.accountRepository.observeAll(),
        container.userPreferences.prefs
    ) { tx, accounts, prefs ->
        DetailUiState(false, tx, accounts.firstOrNull { it.id == tx?.accountId }?.name ?: "", prefs.currency)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DetailUiState())

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            container.transactionRepository.delete(id)
            onDone()
        }
    }
}

@Composable
fun TransactionDetailScreen(transactionId: Long, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "detail-$transactionId") { TransactionDetailViewModel(container, transactionId) }
    val state by vm.state.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Transaction", onBack = onBack) {
        val tx = state.tx
        when {
            state.loading -> LoadingBox()
            tx == null -> ErrorBox("This transaction no longer exists.", onRetry = null)
            else -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.TopCenter) {
                TransactionDetailContent(
                    tx = tx, accountName = state.accountName, currency = state.currency,
                    onEdit = { onEdit(tx.id) }, onDelete = { confirm = true },
                    modifier = Modifier.widthIn(max = 560.dp)
                )
            }
        }
    }
    if (confirm) {
        ConfirmDeleteDialog(onConfirm = { confirm = false; vm.delete(onBack) }, onDismiss = { confirm = false })
    }
}
