package com.budgetpulse.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertDoesNotExist
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.domain.model.AppNotification
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.domain.model.TxType
import com.budgetpulse.app.ui.notifications.NotificationsContent
import com.budgetpulse.app.ui.notifications.NotificationsUiState
import com.budgetpulse.app.ui.splash.SplashScreen
import com.budgetpulse.app.ui.theme.BudgetPulseTheme
import androidx.compose.ui.test.onNodeWithContentDescription
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Notification list + splash screen (stateless composables, no database needed). */
class NotificationsUiTest {

    @get:Rule val rule = createComposeRule()

    private val now = System.currentTimeMillis()
    private val manual = AppNotification(1, TxSource.MANUAL, TxType.EXPENSE, "Lunch", Category.FOOD, 12.5, now, read = false, transactionId = 10L)
    private val email = AppNotification(2, TxSource.EMAIL, TxType.INCOME, "ACME SDN BHD", Category.SALARY, 2300.0, now - 60_000, read = true, transactionId = 11L)

    private fun show(state: NotificationsUiState, onOpen: (Long) -> Unit = {}, onClear: () -> Unit = {}) {
        rule.setContent { BudgetPulseTheme(ThemeMode.LIGHT) { NotificationsContent(state, onOpen, onClear) } }
    }

    @Test
    fun listShowsEntriesWithWhereTheyCameFrom() {
        show(NotificationsUiState(loading = false, items = listOf(manual, email), newIds = setOf(1L), currency = "USD"))
        rule.onNodeWithText("Expense added").assertIsDisplayed()
        rule.onNodeWithText("Income added").assertIsDisplayed()
        rule.onNodeWithText("Food & Restaurants", substring = true).assertIsDisplayed()   // "... - Added manually - ..."
    }

    @Test
    fun tappingAnEntryOpensItsTransaction() {
        var opened = -1L
        show(NotificationsUiState(loading = false, items = listOf(manual), currency = "USD"), onOpen = { opened = it })
        rule.onNodeWithText("Expense added").performClick()
        assertEquals(10L, opened)
    }

    @Test
    fun emptyStateExplainsWhatShowsUpHere() {
        show(NotificationsUiState(loading = false, items = emptyList()))
        rule.onNodeWithText("No notifications yet").assertIsDisplayed()
        rule.onNodeWithTag("btn_clear_notifications").assertDoesNotExist()
    }

    @Test
    fun clearAllButtonCallsBack() {
        var cleared = 0
        show(NotificationsUiState(loading = false, items = listOf(manual)), onClear = { cleared++ })
        rule.onNodeWithTag("btn_clear_notifications").performClick()
        assertEquals(1, cleared)
    }

    @Test
    fun splashShowsTheLogo() {
        rule.setContent { BudgetPulseTheme(ThemeMode.DARK) { SplashScreen() } }
        rule.onNodeWithTag("splash").assertIsDisplayed()
        rule.onNodeWithContentDescription("Budgetin logo").assertIsDisplayed()
        rule.onNodeWithText("Budgetin").assertIsDisplayed()
    }
}
