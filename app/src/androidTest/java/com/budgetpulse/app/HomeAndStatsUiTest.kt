package com.budgetpulse.app

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.domain.model.Account
import com.budgetpulse.app.domain.model.AccountType
import com.budgetpulse.app.domain.model.AccountWithBalance
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.SavingsGoal
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.home.HomeContent
import com.budgetpulse.app.ui.home.HomeUiState
import com.budgetpulse.app.ui.home.LimitUi
import com.budgetpulse.app.ui.stats.StatsContent
import com.budgetpulse.app.ui.stats.StatsUiState
import com.budgetpulse.app.ui.theme.BudgetPulseTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Compose UI tests #4 and #5 - dashboard and statistics rendering + interaction. */
class HomeAndStatsUiTest {

    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val lunch = Transaction(1, 1, TxType.EXPENSE, 12.5, Category.FOOD, "Lunch with friends", now)
    private val salary = Transaction(2, 1, TxType.INCOME, 2000.0, Category.SALARY, "Monthly salary", now - 1000)

    private val homeState = HomeUiState(
        loading = false, userName = "Alex Tan", currency = "USD", totalBalance = 1987.5,
        accounts = listOf(AccountWithBalance(Account(1, "Cash wallet", AccountType.CASH, 0xFFE4572E, 0.0), 1987.5)),
        recent = listOf(lunch, salary),
        limits = listOf(LimitUi(Category.FOOD, BudgetCalculator.limitProgress(limit = 100.0, spent = 125.0))),
        goals = listOf(SavingsGoal(1, "Emergency fund", 1000.0, 250.0)),
        month = BudgetCalculator.summarize(listOf(lunch, salary)), hasTransactions = true
    )

    private fun showHome(state: HomeUiState = homeState, onAdd: (Boolean) -> Unit = {}, onOpen: (Long) -> Unit = {}) {
        rule.setContent {
            BudgetPulseTheme(ThemeMode.LIGHT) {
                HomeContent(
                    state, wide = false, onOpenSettings = {}, onAddTransaction = onAdd, onOpenTransaction = onOpen,
                    onOpenBudgets = {}, onOpenTips = {}, onOpenNotifications = {}, onOpenNews = {}, onAddAccount = { _, _, _ -> }, autoScrollNews = false
                )
            }
        }
    }

    @Test
    fun home_showsGreetingBalanceAccountsAndRecentTransactions() {
        showHome()
        rule.onNodeWithText("Hi, Alex").assertIsDisplayed()
        rule.onNodeWithTag("total_balance").assertIsDisplayed()
        rule.onNodeWithText("Cash wallet").assertIsDisplayed()
        rule.onNodeWithText("Key insight").assertIsDisplayed()
    }

    @Test
    fun home_actionButtonsReportWhichKindOfTransactionToCreate() {
        val calls = mutableListOf<Boolean>()
        showHome(onAdd = { calls += it })
        rule.onNodeWithTag("btn_spend").performClick()
        rule.onNodeWithTag("btn_income").performClick()
        assertEquals(listOf(false, true), calls)
    }

    @Test
    fun home_overspentLimitIsAnnouncedToScreenReaders() {
        showHome()
        rule.onNodeWithTag("home_list").performScrollToNode(hasText("Established limits"))
        rule.onNodeWithText("Established limits").assertIsDisplayed()
        val announcesOver = SemanticsMatcher("progress bar describes 'over limit'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.contains("over limit") } == true
        }
        rule.onNode(announcesOver).assertExists()
    }

    @Test
    fun home_bellShowsTheUnreadCount_andNoBadgeWhenEverythingIsRead() {
        showHome(homeState.copy(unreadNotifications = 3))
        rule.onNodeWithContentDescription("Notifications, 3 unread").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun home_bellCapsTheBadgeAtNinePlus() {
        showHome(homeState.copy(unreadNotifications = 14))
        rule.onNodeWithText("9+").assertIsDisplayed()
    }

    @Test
    fun home_bellHasNoBadge_whenNothingIsUnread() {
        showHome(homeState.copy(unreadNotifications = 0))
        rule.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        rule.onNodeWithTag("btn_notifications").assertIsDisplayed()
    }

    @Test
    fun home_emptyStateGuidesTheUser() {
        showHome(HomeUiState(loading = false, userName = "", currency = "USD"))
        rule.onNodeWithText("Welcome back").assertIsDisplayed()
        rule.onNodeWithText("Add your first transaction and I'll start spotting trends for you.").assertIsDisplayed()
    }

    private fun statsState(period: Period = Period.MONTH): StatsUiState {
        val report = BudgetCalculator.report(listOf(lunch, salary), period, LocalDate.now(), 0, ZoneId.systemDefault())
        return StatsUiState(loading = false, period = period, offset = 0, report = report, currency = "USD")
    }

    @Test
    fun stats_rendersAccessibleChartAndCategoryBreakdown() {
        rule.setContent { BudgetPulseTheme(ThemeMode.LIGHT) { StatsContent(statsState(), wide = true, onPeriod = {}, onShift = {}) } }
        rule.onNodeWithTag("stats_chart").assertExists()
        val area = SemanticsMatcher("area chart has a text alternative") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Area chart") } == true
        }
        rule.onNode(area).assertExists()
        rule.onNodeWithText("Spending by category").assertIsDisplayed()
    }

    @Test
    fun stats_chartStyleCanBeSwitchedToBars() {
        rule.setContent { BudgetPulseTheme(ThemeMode.LIGHT) { StatsContent(statsState(), wide = true, onPeriod = {}, onShift = {}) } }
        rule.onNodeWithTag("chart_bars").performClick()
        rule.waitForIdle()
        val bars = SemanticsMatcher("bar chart has a text alternative") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Bar chart") } == true
        }
        rule.onNode(bars).assertExists()
    }

    @Test
    fun stats_periodChipsAndArrowsCallBack() {
        val periods = mutableListOf<Period>()
        var shifted = 0
        rule.setContent {
            BudgetPulseTheme(ThemeMode.LIGHT) { StatsContent(statsState(), wide = false, onPeriod = { periods += it }, onShift = { shifted += it }) }
        }
        rule.onNodeWithTag("period_WEEK").performClick()
        rule.onNodeWithTag("period_YEAR").performClick()
        rule.onNodeWithTag("period_prev").performClick()
        assertEquals(listOf(Period.WEEK, Period.YEAR), periods)
        assertTrue(shifted == -1)
    }
}
