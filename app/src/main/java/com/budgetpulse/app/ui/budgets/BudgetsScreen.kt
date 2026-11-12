package com.budgetpulse.app.ui.budgets

import com.budgetpulse.app.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.ui.common.*
import com.budgetpulse.app.ui.home.LimitUi
import com.budgetpulse.app.util.formatMoney

private sealed interface BudgetDialog {
    data object NewLimit : BudgetDialog
    data class EditLimit(val item: LimitUi) : BudgetDialog
    data object NewGoal : BudgetDialog
    data class Funds(val goal: SavingsGoal) : BudgetDialog
    data object Target : BudgetDialog
}

@Composable
fun BudgetsScreen(widthClass: WindowWidthSizeClass) {
    val container = LocalAppContainer.current
    val vm = appViewModel { BudgetsViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<BudgetDialog?>(null) }
    val wide = widthClass != WindowWidthSizeClass.Compact

    val limitsSection: @Composable () -> Unit = {
        LimitsSection(state, onNew = { dialog = BudgetDialog.NewLimit }, onEdit = { dialog = BudgetDialog.EditLimit(it) })
    }
    val goalsSection: @Composable () -> Unit = {
        GoalsSection(state, onNew = { dialog = BudgetDialog.NewGoal }, onFunds = { dialog = BudgetDialog.Funds(it) }, onDelete = vm::deleteGoal)
    }

    ScreenScaffold(title = "Budgets") {
        if (state.loading) {
            LoadingBox()
        } else if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { TargetCard(state) { dialog = BudgetDialog.Target } }
                    item { limitsSection() }
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { goalsSection() }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 24.dp + LocalBottomInset.current), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TargetCard(state) { dialog = BudgetDialog.Target } }
                item { limitsSection() }
                item { goalsSection() }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        BudgetDialog.NewLimit -> LimitDialog(
            categories = state.freeCategories, initialCategory = state.freeCategories.firstOrNull(), initialAmount = "",
            onSave = { c, a -> vm.setLimit(c, a); dialog = null }, onDelete = null, onDismiss = { dialog = null }
        )
        is BudgetDialog.EditLimit -> LimitDialog(
            categories = listOf(d.item.category), initialCategory = d.item.category, initialAmount = d.item.progress.limit.toLong().toString(),
            onSave = { c, a -> vm.setLimit(c, a); dialog = null },
            onDelete = { vm.removeLimit(d.item.category); dialog = null }, onDismiss = { dialog = null }
        )
        BudgetDialog.NewGoal -> GoalDialog(onSave = { n, t -> vm.addGoal(n, t); dialog = null }, onDismiss = { dialog = null })
        is BudgetDialog.Funds -> MoneyInputDialog(
            title = "Add to " + d.goal.name, label = "Amount (" + state.currency + ")", initial = "", confirm = "Add",
            onConfirm = { vm.addFunds(d.goal, it); dialog = null }, onDismiss = { dialog = null }
        )
        BudgetDialog.Target -> MoneyInputDialog(
            title = "Monthly savings target", label = "Target (" + state.currency + ")",
            initial = if (state.savingsTarget > 0) state.savingsTarget.toLong().toString() else "", confirm = "Save",
            allowZero = true, onConfirm = { vm.setSavingsTarget(it); dialog = null }, onDismiss = { dialog = null }
        )
    }
}

@Composable
private fun TargetCard(state: BudgetsUiState, onEdit: () -> Unit) {
    val f = BudgetCalculator.savingsTargetProgress(state.monthNet, state.savingsTarget)
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly savings target", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onEdit, modifier = Modifier.testTag("btn_target")) { Text(if (state.savingsTarget > 0) "Change" else "Set target") }
            }
            if (state.savingsTarget <= 0) {
                EmptyHint("Set how much you want to keep each month. Progress uses income minus expenses.")
            } else {
                AnimatedProgressBar(f, MaterialTheme.colorScheme.tertiary, "Savings target " + Math.round(f * 100) + " percent reached")
                Text(
                    formatMoney(state.monthNet.coerceAtLeast(0.0), state.currency) + " of " + formatMoney(state.savingsTarget, state.currency) + " this month",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun LimitsSection(state: BudgetsUiState, onNew: () -> Unit, onEdit: (LimitUi) -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Category limits", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FilledTonalButton(onClick = onNew, enabled = state.freeCategories.isNotEmpty(), modifier = Modifier.testTag("btn_new_limit")) {
                    AppIcon(R.drawable.ic_add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Limit")
                }
            }
            if (state.limits.isEmpty()) EmptyHint("No limits yet. A limit warns you when a category eats too much of your month.")
            state.limits.forEach { l ->
                val over = l.progress.isOver
                Column(
                    Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = l.category.label + " limit, tap to edit" },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(l.category, size = 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(l.category.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text(
                                if (over) "Over by " + formatMoney(-l.progress.remaining, state.currency)
                                else formatMoney(l.progress.remaining, state.currency) + " left",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(l.progress.percent.toString() + "%", style = MaterialTheme.typography.labelLarge, color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        IconButton(onClick = { onEdit(l) }) { AppIcon(R.drawable.ic_edit, contentDescription = "Edit " + l.category.label + " limit") }
                    }
                    AnimatedProgressBar(
                        l.progress.fraction, if (over) MaterialTheme.colorScheme.error else l.category.color(),
                        l.category.label + " " + l.progress.percent + " percent of limit used"
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalsSection(state: BudgetsUiState, onNew: () -> Unit, onFunds: (SavingsGoal) -> Unit, onDelete: (Long) -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Savings goals", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                FilledTonalButton(onClick = onNew, modifier = Modifier.testTag("btn_new_goal")) {
                    AppIcon(R.drawable.ic_add, contentDescription = null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Goal")
                }
            }
            if (state.goals.isEmpty()) EmptyHint("Give your savings a purpose: 'Emergency fund', 'New laptop', 'Trip'.")
            state.goals.forEach { g ->
                val f = BudgetCalculator.goalProgress(g.saved, g.target)
                val reached = f >= 1f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        if (reached) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text("Goal reached", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        IconButton(onClick = { onDelete(g.id) }) { AppIcon(R.drawable.ic_delete, contentDescription = "Delete goal " + g.name) }
                    }
                    AnimatedProgressBar(f, MaterialTheme.colorScheme.tertiary, g.name + " " + Math.round(f * 100) + " percent saved")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatMoney(g.saved, state.currency) + " of " + formatMoney(g.target, state.currency),
                            Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!reached) TextButton(onClick = { onFunds(g) }) { Text("Add funds") }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ dialogs
private fun parseMoney(text: String): Double? = text.replace(',', '.').toDoubleOrNull()

@Composable
fun MoneyInputDialog(
    title: String, label: String, initial: String, confirm: String,
    onConfirm: (Double) -> Unit, onDismiss: () -> Unit, allowZero: Boolean = false
) {
    var text by remember { mutableStateOf(initial) }
    val value = parseMoney(text)
    val valid = value != null && (value > 0 || (allowZero && value >= 0))
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            OutlinedTextField(
                text, { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("dialog_amount")
            )
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(value!!) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LimitDialog(
    categories: List<Category>, initialCategory: Category?, initialAmount: String,
    onSave: (Category, Double) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit
) {
    var category by remember { mutableStateOf(initialCategory) }
    var amount by remember { mutableStateOf(initialAmount) }
    val value = parseMoney(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (onDelete == null) "New category limit" else "Edit limit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) }) }
                }
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Monthly limit") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.testTag("dialog_amount")
                )
            }
        },
        confirmButton = {
            TextButton(enabled = category != null && value != null && value > 0, onClick = { onSave(category!!, value!!) }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

@Composable
private fun GoalDialog(onSave: (String, Double) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val value = parseMoney(target)
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("New savings goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text("Goal name") }, singleLine = true)
                OutlinedTextField(
                    target, { target = it }, label = { Text("Target amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank() && value != null && value > 0, onClick = { onSave(name, value!!) }) { Text("Create") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
