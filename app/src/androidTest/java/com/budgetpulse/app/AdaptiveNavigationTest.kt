package com.budgetpulse.app

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import androidx.test.platform.app.InstrumentationRegistry
import com.budgetpulse.app.data.prefs.ThemeMode
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.ui.common.LocalAppContainer
import com.budgetpulse.app.ui.navigation.BudgetPulseRoot
import com.budgetpulse.app.ui.navigation.Budgets
import com.budgetpulse.app.ui.navigation.History
import com.budgetpulse.app.ui.navigation.Home
import com.budgetpulse.app.ui.navigation.Market
import com.budgetpulse.app.ui.navigation.Notifications
import com.budgetpulse.app.ui.navigation.SignIn
import com.budgetpulse.app.ui.navigation.Stats
import com.budgetpulse.app.ui.theme.BudgetPulseTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Compose UI test #1 - navigation + responsive chrome.
 * Runs the real [BudgetPulseRoot] with a TestNavHostController and three simulated window sizes.
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
class AdaptiveNavigationTest {

    @get:Rule val rule = createComposeRule()
    private lateinit var nav: TestNavHostController

    private val loggedIn = UserPrefs(isLoggedIn = true, userName = "Test User", userEmail = "test@example.com", currency = "USD")
    private val phone = DpSize(400.dp, 800.dp)    // Compact
    private val tablet = DpSize(700.dp, 900.dp)   // Medium
    private val desktop = DpSize(1000.dp, 700.dp) // Expanded

    private fun launch(size: DpSize, prefs: UserPrefs) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as BudgetPulseApp
        rule.setContent {
            nav = TestNavHostController(LocalContext.current).apply { navigatorProvider.addNavigator(ComposeNavigator()) }
            CompositionLocalProvider(LocalAppContainer provides app.container) {
                BudgetPulseTheme(ThemeMode.LIGHT) { BudgetPulseRoot(WindowSizeClass.calculateFromSize(size), prefs, nav) }
            }
        }
        rule.waitForIdle()
    }

    private fun tap(tag: String) {
        rule.onNodeWithTag(tag).performClick()
        rule.waitForIdle()
    }

    @Test
    fun phone_bottomBarNavigatesBetweenTabs() {
        launch(phone, loggedIn)
        rule.onNodeWithTag("bottom_nav").assertIsDisplayed()
        rule.onNodeWithTag("nav_rail").assertDoesNotExist()
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)

        tap("nav_History")
        assertTrue(nav.currentDestination?.hasRoute<History>() == true)
        tap("nav_Budgets")
        assertTrue(nav.currentDestination?.hasRoute<Budgets>() == true)
        tap("nav_Stats")
        assertTrue(nav.currentDestination?.hasRoute<Stats>() == true)
        tap("nav_Market")
        assertTrue(nav.currentDestination?.hasRoute<Market>() == true)
        tap("nav_Home")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
    }

    @Test
    fun screensEnteredFromAnotherScreen_showABackButton_thatReturnsHome() {
        launch(phone, loggedIn)
        rule.onAllNodesWithTag("btn_back").assertCountEquals(0)      // Home is the start screen: no back button
        tap("nav_History")
        rule.onNodeWithTag("btn_back").assertIsDisplayed()
        tap("btn_back")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
        rule.onAllNodesWithTag("btn_back").assertCountEquals(0)
    }

    @Test
    fun settingsOpenedFromHome_hasABackButton() {
        launch(phone, loggedIn)
        tap("btn_settings")
        assertTrue(nav.currentDestination?.hasRoute<com.budgetpulse.app.ui.navigation.Settings>() == true)
        rule.onNodeWithTag("btn_back").assertIsDisplayed()
        tap("btn_back")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
    }

    @Test
    fun homeButton_returnsToHome_afterOpeningAPageFromHome() {
        launch(phone, loggedIn)
        tap("btn_settings")                                   // Home -> Settings (opened from Home)
        assertTrue(nav.currentDestination?.hasRoute<com.budgetpulse.app.ui.navigation.Settings>() == true)
        tap("nav_Home")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
        rule.onAllNodesWithTag("btn_back").assertCountEquals(0)
    }

    @Test
    fun homeButton_alsoWorks_afterSwitchingTabsFromAPageOpenedFromHome() {
        // The exact sequence that used to restore the old page instead of Home:
        launch(phone, loggedIn)
        tap("btn_settings")
        tap("nav_History")
        tap("nav_Budgets")
        tap("nav_Home")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
        rule.onAllNodesWithTag("btn_back").assertCountEquals(0)
        tap("nav_Market")
        tap("nav_Home")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
    }

    @Test
    fun bell_opensNotifications_withABackButton() {
        launch(phone, loggedIn)
        tap("btn_notifications")
        assertTrue(nav.currentDestination?.hasRoute<Notifications>() == true)
        rule.onNodeWithTag("btn_back").assertIsDisplayed()
        tap("btn_back")
        assertTrue(nav.currentDestination?.hasRoute<Home>() == true)
    }

    @Test
    fun phone_bottomBarHasFiveDestinations_tipsAndSettingsLiveElsewhere() {
        launch(phone, loggedIn)
        rule.onNodeWithTag("nav_Home").assertIsDisplayed()
        rule.onNodeWithTag("nav_Market").assertIsDisplayed()
        rule.onAllNodesWithTag("nav_Tips").assertCountEquals(0)
        rule.onAllNodesWithTag("nav_Settings").assertCountEquals(0)
    }

    @Test
    fun tablet_usesNavigationRailWithAllDestinations() {
        launch(tablet, loggedIn)
        rule.onNodeWithTag("nav_rail").assertIsDisplayed()
        rule.onNodeWithTag("bottom_nav").assertDoesNotExist()
        rule.onNodeWithTag("nav_Tips").assertIsDisplayed()
    }

    @Test
    fun expanded_usesPermanentDrawer() {
        launch(desktop, loggedIn)
        rule.onNodeWithTag("nav_drawer").assertIsDisplayed()
        rule.onNodeWithTag("bottom_nav").assertDoesNotExist()
        rule.onNodeWithTag("nav_rail").assertDoesNotExist()
    }

    @Test
    fun loggedOut_startsOnWelcome_withoutNavigation_andMovesToSignIn() {
        launch(phone, UserPrefs(isLoggedIn = false))
        rule.onNodeWithText("Get Started").assertIsDisplayed()
        rule.onNodeWithTag("bottom_nav").assertDoesNotExist()

        rule.onNodeWithText("Get Started").performClick()
        rule.waitForIdle()
        assertTrue(nav.currentDestination?.hasRoute<SignIn>() == true)
        rule.onNodeWithText("Make your financial behavior better!").assertIsDisplayed()
    }
}
