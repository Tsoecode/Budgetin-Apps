package com.budgetpulse.app.domain.logic

/** How often the mailbox is checked for bank notifications. */
enum class SyncInterval(val minutes: Int, val label: String) {
    TEN_MINUTES(10, "Every 10 minutes"),
    ONE_HOUR(60, "Every hour");

    companion object {
        val DEFAULT = ONE_HOUR
        /** Unknown / old stored values fall back to the light option. */
        fun fromMinutes(minutes: Int): SyncInterval = entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}
