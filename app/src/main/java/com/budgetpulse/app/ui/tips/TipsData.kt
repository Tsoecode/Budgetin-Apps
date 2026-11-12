package com.budgetpulse.app.ui.tips

import com.budgetpulse.app.R
import androidx.annotation.DrawableRes


/** The embedded guide: the Bank Jago article behind https://share.google/lwk0l2CEf1GJzNcyI */
const val EMBEDDED_TIPS_URL = "https://www.jago.com/en/blog/saving-tips-for-beginners"
const val TIPS_SHARE_LINK = "https://share.google/lwk0l2CEf1GJzNcyI"

data class Tip(val title: String, val body: String, @DrawableRes val icon: Int)

/** Original wording written for this app (the linked article is shown unchanged in the embedded view). */
val SAVINGS_TIPS = listOf(
    Tip("Pay yourself first", "Move a fixed amount to savings the day your money arrives, before you can spend it. Automate it so it needs no willpower.", R.drawable.ic_savings),
    Tip("Try the 50 / 30 / 20 rule", "A starting point: about half of income for needs, 30% for wants and 20% for savings or debt. Adjust it to your situation.", R.drawable.ic_pie_chart),
    Tip("Build an emergency fund", "Aim for three to six months of essential costs. If that feels huge, begin with one month and grow it.", R.drawable.ic_shield),
    Tip("Track every expense for 30 days", "You cannot change what you cannot see. Log each purchase and look at the Statistics tab at the end of the month.", R.drawable.ic_edit),
    Tip("Use the 24-hour rule", "For a non-essential purchase above a limit you choose, wait one day. Many impulse buys lose their appeal overnight.", R.drawable.ic_timer),
    Tip("Cancel what you do not use", "Review subscriptions and recurring payments every few months. Small automatic charges add up quietly.", R.drawable.ic_cancel),
    Tip("Set category limits", "Give food, shopping or entertainment a monthly cap in Budgets. The progress bar turns red before the month is lost.", R.drawable.ic_flag),
    Tip("Name your goals", "'Laptop in June' beats 'save more'. A clear target and date makes it easier to keep going.", R.drawable.ic_emoji_events),
    Tip("Check exchange rates before you pay abroad", "Compare the rate you are offered with the Market tab, and avoid converting at airports or at poor rates.", R.drawable.ic_currency_exchange)
)
