package com.budgetpulse.app.domain.logic

/** Lookback window for the very first email scan (when lastEmailSyncAt == 0). */
enum class InitialSyncRange(val days: Int, val label: String) {
    ONE_WEEK(7, "1 week"),
    ONE_MONTH(30, "1 month");

    val durationMs: Long get() = days.toLong() * 24 * 60 * 60 * 1000

    companion object {
        val DEFAULT = ONE_WEEK
        fun fromDays(days: Int): InitialSyncRange = entries.firstOrNull { it.days == days } ?: DEFAULT
    }
}
