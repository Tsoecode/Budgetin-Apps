package com.budgetpulse.app.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.budgetpulse.app.data.prefs.UserPrefs
import com.budgetpulse.app.ui.budgets.BudgetsScreen
import com.budgetpulse.app.ui.common.LocalBackAction
import com.budgetpulse.app.ui.history.HistoryScreen
import com.budgetpulse.app.ui.history.TransactionDetailScreen
import com.budgetpulse.app.ui.history.TransactionFormScreen
import com.budgetpulse.app.ui.home.HomeScreen
import com.budgetpulse.app.ui.market.MarketScreen
import com.budgetpulse.app.ui.notifications.NotificationsScreen
import com.budgetpulse.app.ui.onboarding.PickCurrencyScreen
import com.budgetpulse.app.ui.onboarding.SignInScreen
import com.budgetpulse.app.ui.onboarding.WelcomeScreen
import com.budgetpulse.app.ui.settings.SettingsScreen
import com.budgetpulse.app.ui.stats.StatsScreen
import com.budgetpulse.app.ui.tips.TipsScreen
import com.budgetpulse.app.util.AppLog

private fun NavDestination?.isAuthScreen(): Boolean =
    this != null && (hasRoute<Welcome>() || hasRoute<SignIn>() || hasRoute<PickCurrency>())

/**
 * App root: owns the NavController, the adaptive navigation chrome and the dynamic
 * auth routing (logged out -> Welcome flow, logged in -> Home).
 */
@Composable
fun BudgetPulseRoot(windowSize: WindowSizeClass, prefs: UserPrefs, navController: NavHostController = rememberNavController()) {
    val startDestination: Any = remember { if (prefs.isLoggedIn) Home else Welcome }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    // Dynamic navigation: react to the authentication state stored in DataStore.
    LaunchedEffect(prefs.isLoggedIn, destination) {
        if (destination == null) return@LaunchedEffect
        if (prefs.isLoggedIn && destination.isAuthScreen()) {
            AppLog.i(AppLog.NAV, "authenticated -> Home")
            navController.navigate(Home) { popUpTo(navController.graph.id) { inclusive = true } }
        } else if (!prefs.isLoggedIn && !destination.isAuthScreen()) {
            AppLog.i(AppLog.NAV, "signed out -> Welcome")
            navController.navigate(Welcome) { popUpTo(navController.graph.id) { inclusive = true } }
        }
    }

    val isTopLevel = destination?.hierarchy?.any { d -> topLevelRoutes.any { d.hasRoute(it.route::class) } } == true

    AdaptiveScaffold(
        widthClass = windowSize.widthSizeClass,
        showNavigation = isTopLevel,
        isSelected = { item -> destination?.hierarchy?.any { it.hasRoute(item.route::class) } == true },
        onSelect = { item ->
            AppLog.d(AppLog.NAV, "tab -> ${item.label}")
            if (item.route == Home) {
                // Pop back to Home explicitly. Using restoreState here would reopen the page we left FROM Home
                // (Navigation files that page under Home's own id when a tab is selected with saveState = true).
                if (!navController.popBackStack<Home>(inclusive = false)) {
                    navController.navigate(Home) { popUpTo(navController.graph.id) { inclusive = true } }
                }
            } else {
                navController.navigate(item.route) {
                    popUpTo<Home> { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        }
    ) {
        val widthClass = windowSize.widthSizeClass
        // Every screen that was entered from another screen gets a round back button in its header.
        val canGoBack = backStackEntry != null && navController.previousBackStackEntry != null
        val backAction: (() -> Unit)? = if (canGoBack) ({ navController.popBackStack() }) else null
        CompositionLocalProvider(LocalBackAction provides backAction) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(220)) }
        ) {
            composable<Welcome> { WelcomeScreen(onGetStarted = { navController.navigate(SignIn) }) }
            composable<SignIn> {
                SignInScreen(
                    onBack = { navController.popBackStack() },
                    // data is passed to the next screen as type-safe arguments
                    onContinue = { name, email -> navController.navigate(PickCurrency(name, email)) }
                )
            }
            composable<PickCurrency> { entry ->
                val args = entry.toRoute<PickCurrency>()
                PickCurrencyScreen(args.name, args.email, onBack = { navController.popBackStack() })
            }

            composable<Home> {
                HomeScreen(
                    widthClass = widthClass,
                    onOpenSettings = { navController.navigate(Settings) { launchSingleTop = true } },
                    onAddTransaction = { income -> navController.navigate(TransactionForm(startAsIncome = income)) },
                    onOpenTransaction = { id -> navController.navigate(TransactionDetail(id)) },
                    onOpenBudgets = { navController.navigate(Budgets) { launchSingleTop = true } },
                    onOpenTips = { navController.navigate(Tips) { launchSingleTop = true } },
                    onOpenNotifications = { navController.navigate(Notifications) { launchSingleTop = true } }
                )
            }
            composable<History> {
                HistoryScreen(
                    widthClass = widthClass,
                    onOpenTransaction = { id -> navController.navigate(TransactionDetail(id)) },
                    onAddTransaction = { navController.navigate(TransactionForm()) },
                    onEditTransaction = { id -> navController.navigate(TransactionForm(transactionId = id)) }
                )
            }
            composable<Budgets> { BudgetsScreen(widthClass) }
            composable<Stats> { StatsScreen(widthClass) }
            composable<Market> { MarketScreen(widthClass) }
            composable<Tips> { TipsScreen(widthClass) }
            composable<Settings> { SettingsScreen(widthClass) }
            composable<Notifications> {
                NotificationsScreen(onOpenTransaction = { id -> navController.navigate(TransactionDetail(id)) })
            }

            composable<TransactionDetail> { entry ->
                val args = entry.toRoute<TransactionDetail>()
                TransactionDetailScreen(
                    transactionId = args.transactionId,
                    onBack = { navController.popBackStack() },
                    onEdit = { id -> navController.navigate(TransactionForm(transactionId = id)) }
                )
            }
            composable<TransactionForm> { entry ->
                val args = entry.toRoute<TransactionForm>()
                TransactionFormScreen(args.transactionId, args.startAsIncome, onClose = { navController.popBackStack() })
            }
        }
        }
    }
}
