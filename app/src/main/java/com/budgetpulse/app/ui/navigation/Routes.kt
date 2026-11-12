package com.budgetpulse.app.ui.navigation

import com.budgetpulse.app.R
import androidx.annotation.DrawableRes
import kotlinx.serialization.Serializable

// Type-safe routes (Navigation 2.8+): arguments are plain Kotlin properties.
@Serializable object Welcome
@Serializable object SignIn
@Serializable data class PickCurrency(val name: String, val email: String)

@Serializable object Home
@Serializable object History
@Serializable object Budgets
@Serializable object Stats
@Serializable object Market
@Serializable object Tips
@Serializable object Settings
@Serializable object Notifications

@Serializable data class TransactionForm(val transactionId: Long = -1L, val startAsIncome: Boolean = false)
@Serializable data class TransactionDetail(val transactionId: Long)

data class TopLevelRoute<T : Any>(
    val label: String,
    val route: T,
    @DrawableRes val icon: Int,
    /** Phones show only the first five in the bottom bar; rail/drawer on larger screens show all. */
    val inBottomBar: Boolean = true
)

val topLevelRoutes: List<TopLevelRoute<out Any>> = listOf(
    TopLevelRoute("Home", Home, R.drawable.ic_home),
    TopLevelRoute("History", History, R.drawable.ic_receipt),
    TopLevelRoute("Budgets", Budgets, R.drawable.ic_savings),
    TopLevelRoute("Stats", Stats, R.drawable.ic_bar_chart),
    TopLevelRoute("Market", Market, R.drawable.ic_currency_exchange),
    TopLevelRoute("Tips", Tips, R.drawable.ic_lightbulb, inBottomBar = false),
    TopLevelRoute("Settings", Settings, R.drawable.ic_settings, inBottomBar = false)
)
