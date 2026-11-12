package com.budgetpulse.app.ui.history

import com.budgetpulse.app.R
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.util.formatMoney
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.ENGLISH)

@Composable
fun HistoryScreen(
    widthClass: WindowWidthSizeClass,
    onOpenTransaction: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { HistoryViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val twoPane = widthClass == WindowWidthSizeClass.Expanded
    var deleteTarget by remember { mutableStateOf<Transaction?>(null) }

    ScreenScaffold(
        title = "History",
        actions = {
            IconButton(
                enabled = state.totalCount > 0,
                onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Budgetin transactions")
                        putExtra(Intent.EXTRA_TEXT, state.csv)
                    }
                    context.startActivity(Intent.createChooser(send, "Export as CSV"))
                }
            ) { AppIcon(R.drawable.ic_share, contentDescription = "Export transactions as CSV") }
        },
        floatingAction = {
            ExtendedFloatingActionButton(
                onClick = onAddTransaction,
                icon = { AppIcon(R.drawable.ic_add, contentDescription = null) },
                text = { Text("Add") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 16.dp + LocalBottomInset.current).testTag("fab_add")
            )
        }
    ) {
        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                ListPane(state, vm, selectedId = state.selected?.id, onClick = { vm.select(it) }, modifier = Modifier.weight(0.45f))
                Box(Modifier.weight(0.55f).fillMaxHeight().padding(end = 16.dp, bottom = 16.dp)) {
                    val sel = state.selected
                    if (sel == null) {
                        EmptyHint("Select a transaction to see its details here.", Modifier.padding(24.dp))
                    } else {
                        TransactionDetailContent(
                            tx = sel, accountName = state.selectedAccountName, currency = state.currency,
                            onEdit = { onEditTransaction(sel.id) }, onDelete = { deleteTarget = sel }
                        )
                    }
                }
            }
        } else {
            ListPane(state, vm, selectedId = null, onClick = onOpenTransaction, modifier = Modifier.fillMaxSize())
        }
    }
    deleteTarget?.let { tx ->
        ConfirmDeleteDialog(onConfirm = { vm.delete(tx.id); deleteTarget = null }, onDismiss = { deleteTarget = null })
    }
}

@Composable
private fun ListPane(state: HistoryUiState, vm: HistoryViewModel, selectedId: Long?, onClick: (Long) -> Unit, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = state.query, onValueChange = vm::setQuery, singleLine = true,
            placeholder = { Text("Search notes or categories") },
            leadingIcon = { AppIcon(R.drawable.ic_search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                    AppIcon(R.drawable.ic_close, contentDescription = "Clear search")
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("search_field")
        )
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.filter == null, onClick = { vm.setFilter(null) }, label = { Text("All") })
            FilterChip(selected = state.filter == TxType.EXPENSE, onClick = { vm.setFilter(TxType.EXPENSE) }, label = { Text("Expenses") })
            FilterChip(selected = state.filter == TxType.INCOME, onClick = { vm.setFilter(TxType.INCOME) }, label = { Text("Income") })
        }
        if (state.loading) {
            LoadingBox()
        } else if (state.groups.isEmpty()) {
            EmptyHint(if (state.totalCount == 0) "No transactions yet. Tap Add to create one, or load demo data in Settings." else "No transactions match your filter.")
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 96.dp + LocalBottomInset.current), modifier = Modifier.testTag("history_list")) {
                state.groups.forEach { group ->
                    item(key = "h" + group.date.toString()) { DayHeader(group.date, group.net, state.currency) }
                    items(group.items, key = { it.id }) { tx ->
                        Surface(
                            color = if (tx.id == selectedId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            TransactionRow(tx, state.currency, onClick = { onClick(tx.id) }, modifier = Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate, net: Double, currency: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            date.format(DAY_FORMAT), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            (if (net >= 0) "+" else "-") + formatMoney(kotlin.math.abs(net), currency),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ConfirmDeleteDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this transaction?") },
        text = { Text("This permanently removes the transaction and updates your balance.") },
        confirmButton = { TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirm_delete")) { Text("Delete") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ------------------------------------------------------------------ detail
@Composable
fun TransactionDetailContent(
    tx: Transaction,
    accountName: String,
    currency: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val amountColor = if (tx.type == TxType.EXPENSE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    AppCard(modifier.fillMaxWidth().testTag("detail_card")) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(tx.category, size = 56.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(tx.note.ifBlank { tx.category.label }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(tx.category.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(tx.signedAmount(currency), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = amountColor)
            HorizontalDivider()
            DetailRow("Type", if (tx.type == TxType.EXPENSE) "Expense" else "Income")
            DetailRow("Account", accountName.ifBlank { "-" })
            DetailRow("Date", tx.formattedDate())
            DetailRow("Source", if (tx.source == TxSource.EMAIL) "Imported from e-mail" else "Added manually")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f).testTag("btn_edit")) {
                    AppIcon(R.drawable.ic_edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Edit")
                }
                Button(
                    onClick = onDelete, modifier = Modifier.weight(1f).testTag("btn_delete"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                ) {
                    AppIcon(R.drawable.ic_delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp)); Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "$label: $value" }) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium)
    }
}
