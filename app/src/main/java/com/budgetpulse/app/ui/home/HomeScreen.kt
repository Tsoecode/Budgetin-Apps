package com.budgetpulse.app.ui.home

import com.budgetpulse.app.R
import com.budgetpulse.app.ui.common.AppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.AccountWithBalance
import com.budgetpulse.app.domain.model.NewsItem
import com.budgetpulse.app.ui.common.AnimatedProgressBar
import com.budgetpulse.app.ui.common.AppCard
import com.budgetpulse.app.ui.common.BrandMark
import com.budgetpulse.app.ui.common.CategoryBadge
import com.budgetpulse.app.ui.common.CircleIconButton
import com.budgetpulse.app.ui.common.EmptyHint
import com.budgetpulse.app.ui.common.LoadingBox
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.common.LocalBottomInset
import com.budgetpulse.app.ui.common.MoneyText
import com.budgetpulse.app.ui.common.NewsCarousel
import com.budgetpulse.app.ui.common.PillButton
import com.budgetpulse.app.ui.common.ScreenScaffold
import com.budgetpulse.app.ui.common.SectionHeader
import com.budgetpulse.app.ui.common.TransactionRow
import com.budgetpulse.app.ui.common.appViewModel
import com.budgetpulse.app.ui.common.color
import com.budgetpulse.app.ui.common.openUrl
import com.budgetpulse.app.ui.onboarding.Mascot
import com.budgetpulse.app.util.formatMoney

@Composable
fun HomeScreen(
    widthClass: WindowWidthSizeClass,
    onOpenSettings: () -> Unit,
    onAddTransaction: (startAsIncome: Boolean) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenNotifications: () -> Unit
) {
    val container = LocalAppContainer.current
    val vm = appViewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    HomeContent(
        state = state,
        wide = widthClass != WindowWidthSizeClass.Compact,
        onOpenSettings = onOpenSettings,
        onAddTransaction = onAddTransaction,
        onOpenTransaction = onOpenTransaction,
        onOpenBudgets = onOpenBudgets,
        onOpenTips = onOpenTips,
        onOpenNotifications = onOpenNotifications,
        onOpenNews = { openUrl(context, it.link) },
        onAddAccount = vm::addAccount
    )
}

@Composable
fun HomeContent(
    state: HomeUiState,
    wide: Boolean,
    onOpenSettings: () -> Unit,
    onAddTransaction: (Boolean) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenNews: (NewsItem) -> Unit,
    onAddAccount: (String, AccountType, Double) -> Unit,
    autoScrollNews: Boolean = true
) {
    var showAddAccount by remember { mutableStateOf(false) }
    val greeting = if (state.userName.isBlank()) "Welcome back" else "Hi, " + state.userName.substringBefore(' ')
    val bottomInset = LocalBottomInset.current

    val left: LazyListScope.() -> Unit = {
        item { BalanceHeader(state, greeting) }
        item { AccountsRow(state.accounts, state.currency, onAdd = { showAddAccount = true }) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton("Spend", { onAddTransaction(false) }, Modifier.weight(1f).testTag("btn_spend"), icon = R.drawable.ic_remove)
                PillButton("Income", { onAddTransaction(true) }, Modifier.weight(1f).testTag("btn_income"), icon = R.drawable.ic_add)
            }
        }
        item { InsightCard(state) }
        if (state.savingsTarget > 0) item { SavingsTargetCard(state) }
        item { LimitsCard(state, onOpenBudgets) }
        item { GoalsCard(state, onOpenBudgets) }
    }
    val right: LazyListScope.() -> Unit = {
        item { RecentCard(state, onOpenTransaction) }
        item {
            Column {
                SectionHeader("Finance news this week")
                if (state.news.isEmpty()) {
                    if (state.newsLoading) LoadingBox() else EmptyHint("News will appear here once you are online.")
                } else {
                    NewsCarousel(state.news, onOpenNews, autoScroll = autoScrollNews)
                }
            }
        }
        item { TipsTeaser(onOpenTips) }
    }

    ScreenScaffold(
        title = "Budgetin",
        leading = {
            CircleIconButton(R.drawable.ic_settings, "Settings", onOpenSettings, Modifier.testTag("btn_settings"))
        },
        titleContent = { BrandMark(showName = true) },
        actions = {
            // The badge counts unread notifications and disappears once the list has been opened.
            val unread = state.unreadNotifications
            val badge = if (unread <= 0) null else if (unread > 9) "9+" else unread.toString()
            CircleIconButton(
                R.drawable.ic_notifications,
                if (unread <= 0) "Notifications" else "Notifications, $unread unread",
                onOpenNotifications, Modifier.testTag("btn_notifications"), badge = badge
            )
        }
    ) {
        if (state.loading) {
            LoadingBox(Modifier.align(Alignment.Center))
        } else if (wide) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { left() }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { right() }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp).testTag("home_list"),
                contentPadding = PaddingValues(bottom = 24.dp + bottomInset), verticalArrangement = Arrangement.spacedBy(14.dp)
            ) { left(); right() }
        }
    }
    if (showAddAccount) {
        AddAccountDialog(onDismiss = { showAddAccount = false }, onConfirm = { n, t, b -> onAddAccount(n, t, b); showAddAccount = false })
    }
}

@Composable
private fun BalanceHeader(state: HomeUiState, greeting: String) {
    Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(greeting, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Total balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(state.totalBalance, state.currency, Modifier.testTag("total_balance"))
    }
}

@Composable
private fun AccountsRow(accounts: List<AccountWithBalance>, currency: String, onAdd: () -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(accounts, key = { it.account.id }) { a ->
            AppCard(Modifier.width(176.dp)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(a.account.colorArgb)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(a.account.name.take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            a.account.name, style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    MoneyText(a.balance, currency, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item { AddAccountCard(onAdd) }
    }
}

/** Dashed outline tile, like the "Add account" tile of the reference. */
@Composable
private fun AddAccountCard(onAdd: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier.width(128.dp).height(112.dp).clip(shape)
            .clickable(onClickLabel = "Add account", onClick = onAdd)
            .drawBehind {
                drawRoundRect(
                    color = line, cornerRadius = CornerRadius(24.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                )
            }
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(R.drawable.ic_add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text("Add account", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun InsightCard(state: HomeUiState) {
    val text = when {
        !state.hasTransactions -> "Add your first transaction and I'll start spotting trends for you."
        state.month.income > 0 && state.month.expense > state.month.income ->
            "Heads up: you spent " + formatMoney(state.month.expense - state.month.income, state.currency) + " more than you earned this month."
        state.month.income > 0 ->
            "Nice! You kept " + Math.round(state.month.savingsRate * 100) + "% of this month's income."
        else -> "You've spent " + formatMoney(state.month.expense, state.currency) + " this month. Log your income to see your savings rate."
    }
    AppCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Mascot(Modifier.size(54.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Key insight", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SavingsTargetCard(state: HomeUiState) {
    val net = state.month.net
    val fraction = BudgetCalculator.savingsTargetProgress(net, state.savingsTarget)
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Monthly savings target", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            AnimatedProgressBar(
                fraction, MaterialTheme.colorScheme.tertiary,
                "Savings target progress " + Math.round(fraction * 100) + " percent", height = 12.dp
            )
            Text(
                formatMoney(net.coerceAtLeast(0.0), state.currency) + " of " + formatMoney(state.savingsTarget, state.currency),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LimitsCard(state: HomeUiState, onOpenBudgets: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Established limits", actionLabel = "View all", onAction = onOpenBudgets)
            if (state.limits.isEmpty()) EmptyHint("No category limits yet. Set one in Budgets to get a warning before you overspend.")
            state.limits.take(3).forEach { l ->
                val over = l.progress.isOver
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryBadge(l.category, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(l.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            formatMoney(l.progress.spent, state.currency) + " / " + formatMoney(l.progress.limit, state.currency),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    AnimatedProgressBar(
                        l.progress.fraction, if (over) MaterialTheme.colorScheme.error else l.category.color(),
                        l.category.label + " limit " + l.progress.percent + " percent used" + if (over) ", over limit" else "",
                        height = 12.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalsCard(state: HomeUiState, onOpenBudgets: () -> Unit) {
    AppCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionHeader("Savings goals", actionLabel = "View all", onAction = onOpenBudgets)
            if (state.goals.isEmpty()) EmptyHint("Create a goal such as 'Emergency fund' or 'New laptop'.")
            state.goals.take(2).forEach { g ->
                val f = BudgetCalculator.goalProgress(g.saved, g.target)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row {
                        Text(g.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            if (f >= 1f) "Reached" else Math.round(f * 100).toString() + "%",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    AnimatedProgressBar(f, MaterialTheme.colorScheme.tertiary, g.name + " goal " + Math.round(f * 100) + " percent", height = 12.dp)
                    Text(
                        formatMoney(g.saved, state.currency) + " of " + formatMoney(g.target, state.currency),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(state: HomeUiState, onOpen: (Long) -> Unit) {
    AppCard {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SectionHeader("Transactions")
            if (state.recent.isEmpty()) EmptyHint("Nothing here yet. Tap Spend or Income to add your first transaction.")
            state.recent.forEach { tx -> TransactionRow(tx, state.currency, onClick = { onOpen(tx.id) }) }
        }
    }
}

@Composable
private fun TipsTeaser(onOpenTips: () -> Unit) {
    AppCard(onClick = onOpenTips, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(R.drawable.ic_savings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Savings tips for beginners", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("Simple habits plus an embedded guide from Bank Jago.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
        }
    }
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit, onConfirm: (String, AccountType, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Account name") }, singleLine = true)
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Starting balance") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, type, amount.replace(',', '.').toDoubleOrNull() ?: 0.0) }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
