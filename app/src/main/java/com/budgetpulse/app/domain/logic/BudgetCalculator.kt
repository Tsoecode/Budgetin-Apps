package com.budgetpulse.app.domain.logic

import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

data class Summary(val income: Double, val expense: Double) {
    val net: Double get() = income - expense
    /** Share of income that was kept, between -1.0 and 1.0 (0 when there is no income). */
    val savingsRate: Double
        get() = if (income <= 0.0) 0.0 else ((income - expense) / income).coerceIn(-1.0, 1.0)
}

data class CategoryShare(val category: Category, val amount: Double, val percent: Double)

data class LimitProgress(val limit: Double, val spent: Double) {
    val fraction: Float get() = if (limit <= 0.0) 0f else (spent / limit).toFloat().coerceIn(0f, 1f)
    val remaining: Double get() = limit - spent
    val isOver: Boolean get() = spent > limit
    val percent: Int get() = if (limit <= 0.0) 0 else Math.round(spent / limit * 100).toInt()
}

enum class Period(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

data class Bucket(val label: String, val income: Double, val expense: Double)
data class PeriodRange(val start: LocalDate, val endExclusive: LocalDate, val title: String)
data class PeriodReport(
    val period: Period,
    val range: PeriodRange,
    val buckets: List<Bucket>,
    val summary: Summary,
    val breakdown: List<CategoryShare>
)

/** Pure business logic: no Android classes, so it is fully unit-testable on the JVM. */
object BudgetCalculator {

    fun accountBalance(initial: Double, txs: List<Transaction>): Double =
        initial + txs.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }

    fun summarize(txs: List<Transaction>): Summary = Summary(
        income = txs.filter { it.type == TxType.INCOME }.sumOf { it.amount },
        expense = txs.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    )

    fun expenseBreakdown(txs: List<Transaction>): List<CategoryShare> {
        val expenses = txs.filter { it.type == TxType.EXPENSE }
        val total = expenses.sumOf { it.amount }
        if (total <= 0.0) return emptyList()
        return expenses.groupBy { it.category }
            .map { (cat, list) ->
                val amount = list.sumOf { it.amount }
                CategoryShare(cat, amount, amount / total * 100.0)
            }
            .sortedByDescending { it.amount }
    }

    fun limitProgress(limit: Double, spent: Double) = LimitProgress(limit, spent)

    fun goalProgress(saved: Double, target: Double): Float =
        if (target <= 0.0) 0f else (saved / target).toFloat().coerceIn(0f, 1f)

    /** Progress toward the monthly savings target, based on net income of the month. */
    fun savingsTargetProgress(monthNet: Double, target: Double): Float =
        if (target <= 0.0) 0f else (monthNet / target).toFloat().coerceIn(0f, 1f)

    fun rangeFor(period: Period, anchor: LocalDate, offset: Int): PeriodRange = when (period) {
        Period.DAY -> {
            val d = anchor.plusDays(offset.toLong())
            PeriodRange(d, d.plusDays(1), d.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)))
        }
        Period.WEEK -> {
            val monday = anchor.plusWeeks(offset.toLong()).with(DayOfWeek.MONDAY)
            val end = monday.plusDays(6)
            val f = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
            PeriodRange(monday, monday.plusDays(7), monday.format(f) + " - " + end.format(f) + " " + end.year)
        }
        Period.MONTH -> {
            val first = anchor.plusMonths(offset.toLong()).withDayOfMonth(1)
            PeriodRange(first, first.plusMonths(1), first.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)))
        }
        Period.YEAR -> {
            val first = anchor.plusYears(offset.toLong()).withDayOfYear(1)
            PeriodRange(first, first.plusYears(1), first.year.toString())
        }
    }

    fun report(
        txs: List<Transaction>,
        period: Period,
        anchor: LocalDate,
        offset: Int,
        zone: ZoneId = ZoneId.systemDefault()
    ): PeriodReport {
        val range = rangeFor(period, anchor, offset)
        val inRange = txs.filter {
            val date = java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
            !date.isBefore(range.start) && date.isBefore(range.endExclusive)
        }
        val keyOf: (Transaction) -> Int = {
            val z = java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone)
            when (period) {
                Period.DAY -> z.hour
                Period.WEEK -> z.dayOfWeek.value - 1
                Period.MONTH -> z.dayOfMonth - 1
                Period.YEAR -> z.monthValue - 1
            }
        }
        val labels: List<String> = when (period) {
            Period.DAY -> (0..23).map { "%02d".format(it) }
            Period.WEEK -> DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
            Period.MONTH -> (1..range.start.lengthOfMonth()).map { it.toString() }
            Period.YEAR -> java.time.Month.entries.map { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
        }
        val grouped = inRange.groupBy(keyOf)
        val buckets = labels.mapIndexed { i, label ->
            val list = grouped[i].orEmpty()
            Bucket(label, list.filter { it.type == TxType.INCOME }.sumOf { it.amount },
                list.filter { it.type == TxType.EXPENSE }.sumOf { it.amount })
        }
        return PeriodReport(period, range, buckets, summarize(inRange), expenseBreakdown(inRange))
    }
}
