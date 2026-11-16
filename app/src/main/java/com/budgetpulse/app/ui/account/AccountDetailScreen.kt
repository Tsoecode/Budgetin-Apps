package com.budgetpulse.app.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.budgetpulse.app.data.repository.AccountDeleteResult
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.common.AppCard
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.common.MoneyText
import com.budgetpulse.app.ui.common.ScreenScaffold
import com.budgetpulse.app.ui.common.TransactionRow
import com.budgetpulse.app.ui.common.appViewModel
import com.budgetpulse.app.util.formatMoney

private enum class MutationFilter(val label: String) { ALL("All"), INCOME("Income"), EXPENSE("Expense") }

@Composable
fun AccountDetailScreen(
    accountId: Long,
    onBack: () -> Unit,
    onOpenTransaction: (Long) -> Unit
) {
    val container = LocalAppContainer.current
    val vm = appViewModel(key = "account-$accountId") { AccountDetailViewModel(container, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val deleteResult by vm.accountDeleteResult.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(MutationFilter.ALL) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var destinationId by remember { mutableStateOf<Long?>(null) }
    var showDeleteError by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf("") }

    LaunchedEffect(deleteResult) {
        when (deleteResult) {
            AccountDeleteResult.Deleted -> {
                vm.clearDeleteResult()
                onBack()
            }
            null -> Unit
            else -> {
                deleteErrorMessage = when (deleteResult) {
                    AccountDeleteResult.LastAccount -> "At least one account must remain."
                    AccountDeleteResult.RequiresDestination -> "Choose another account to receive this account's balance and history."
                    AccountDeleteResult.NotFound -> "This account was already removed."
                    AccountDeleteResult.InvalidDestination -> "The selected destination account is no longer available."
                    else -> "The account could not be deleted. Please try again."
                }
                showDeleteError = true
                vm.clearDeleteResult()
            }
        }
    }

    val accountWithBalance = state.account
    ScreenScaffold(title = accountWithBalance?.account?.name ?: "Account", onBack = onBack) {
        if (state.loading) return@ScreenScaffold
        if (accountWithBalance == null) {
            Text("This account is no longer available.", Modifier.align(Alignment.Center))
            return@ScreenScaffold
        }
        val account = accountWithBalance.account
        val requiresMerge = accountWithBalance.balance != 0.0 || state.transactions.isNotEmpty()
        val filtered = remember(state.transactions, filter) {
            when (filter) {
                MutationFilter.ALL -> state.transactions
                MutationFilter.INCOME -> state.transactions.filter { it.type == TxType.INCOME }
                MutationFilter.EXPENSE -> state.transactions.filter { it.type == TxType.EXPENSE }
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(account.type.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(accountWithBalance.balance, state.currency, style = MaterialTheme.typography.headlineMedium)
                    Text("Current balance", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MutationFilter.entries.forEach { item ->
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text(item.label) }
                    )
                }
            }
            Text("Mutations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(vertical = 8.dp))
            if (filtered.isEmpty()) {
                Text("No ${filter.label.lowercase()} transactions for this account yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(filtered, key = { it.id }) { transaction ->
                        TransactionRow(transaction, state.currency, onClick = { onOpenTransaction(transaction.id) })
                    }
                }
            }
            if (state.accounts.size <= 1) {
                Text(
                    "At least one account must remain.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
            }
            Button(
                onClick = {
                    destinationId = null
                    showDeleteDialog = true
                },
                enabled = state.accounts.size > 1,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)
            ) {
                Text("Delete account")
            }
        }

        if (showDeleteDialog) {
            if (requiresMerge) {
                MergeAccountDialog(
                    source = account,
                    balance = accountWithBalance.balance,
                    transactionsCount = state.transactions.size,
                    currency = state.currency,
                    destinations = state.accounts.filterNot { it.id == account.id },
                    selectedId = destinationId,
                    onSelect = { destinationId = it },
                    onDismiss = { showDeleteDialog = false },
                    onConfirm = {
                        val selected = destinationId ?: return@MergeAccountDialog
                        showDeleteDialog = false
                        vm.deleteAccount(selected)
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text("Delete account?") },
                    text = { Text("This empty account will be removed. No transactions or balance will be lost.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteDialog = false
                            vm.deleteAccount()
                        }) { Text("Delete") }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } }
                )
            }
        }
    }

    if (showDeleteError) {
        AlertDialog(
            onDismissRequest = { showDeleteError = false },
            title = { Text("Account not deleted") },
            text = { Text(deleteErrorMessage) },
            confirmButton = { TextButton(onClick = { showDeleteError = false }) { Text("OK") } }
        )
    }
}

@Composable
private fun MergeAccountDialog(
    source: Account,
    balance: Double,
    transactionsCount: Int,
    currency: String,
    destinations: List<Account>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Merge before deleting") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Move ${formatMoney(balance, currency)} and $transactionsCount transaction(s) from ${source.name} to another account."
                )
                Text("Choose the account that will receive the balance and transaction history:", modifier = Modifier.padding(top = 8.dp))
                destinations.forEach { account ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onSelect(account.id) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedId == account.id, onClick = { onSelect(account.id) })
                        Text(account.name, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = selectedId != null) { Text("Merge & delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
