package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.BudgetCalculator
import com.budgetpulse.app.domain.logic.Period
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class BudgetCalculatorTest {

    private val utc = ZoneOffset.UTC

    private fun tx(type: TxType, amount: Double, cat: Category, date: LocalDate, hour: Int = 12) = Transaction(
        accountId = 1, type = type, amount = amount, category = cat, note = "",
        timestamp = date.atTime(hour, 0).toInstant(utc).toEpochMilli()
    )

    @Test
    fun accountBalance_addsIncomeAndSubtractsExpenses() {
        val d = LocalDate.of(2026, 11, 2)
        val list = listOf(
            tx(TxType.INCOME, 500.0, Category.SALARY, d),
            tx(TxType.EXPENSE, 200.0, Category.FOOD, d)
        )
        assertEquals(1300.0, BudgetCalculator.accountBalance(1000.0, list), 0.001)
    }

    @Test
    fun summarize_computesNetAndSavingsRate() {
        val d = LocalDate.of(2026, 11, 2)
        val s = BudgetCalculator.summarize(
            listOf(tx(TxType.INCOME, 1000.0, Category.SALARY, d), tx(TxType.EXPENSE, 250.0, Category.BILLS, d))
        )
        assertEquals(750.0, s.net, 0.001)
        assertEquals(0.75, s.savingsRate, 0.001)
    }

    @Test
    fun savingsRate_isZeroWithoutIncome_andClampedWhenOverspending() {
        val d = LocalDate.of(2026, 11, 2)
        assertEquals(0.0, BudgetCalculator.summarize(listOf(tx(TxType.EXPENSE, 50.0, Category.FOOD, d))).savingsRate, 0.0)
        val overspent = BudgetCalculator.summarize(
            listOf(tx(TxType.INCOME, 100.0, Category.SALARY, d), tx(TxType.EXPENSE, 900.0, Category.FOOD, d))
        )
        assertEquals(-1.0, overspent.savingsRate, 0.0)
    }

    @Test
    fun expenseBreakdown_percentagesSumTo100_sortedDescending_ignoresIncome() {
        val d = LocalDate.of(2026, 11, 2)
        val shares = BudgetCalculator.expenseBreakdown(
            listOf(
                tx(TxType.EXPENSE, 300.0, Category.FOOD, d),
                tx(TxType.EXPENSE, 100.0, Category.TRANSPORT, d),
                tx(TxType.EXPENSE, 100.0, Category.FOOD, d),
                tx(TxType.INCOME, 9999.0, Category.SALARY, d)
            )
        )
        assertEquals(2, shares.size)
        assertEquals(Category.FOOD, shares.first().category)
        assertEquals(400.0, shares.first().amount, 0.001)
        assertEquals(80.0, shares.first().percent, 0.001)
        assertEquals(100.0, shares.sumOf { it.percent }, 0.001)
    }

    @Test
    fun limitProgress_flagsOverspendAndClampsFraction() {
        val over = BudgetCalculator.limitProgress(limit = 1000.0, spent = 1250.0)
        assertTrue(over.isOver)
        assertEquals(1f, over.fraction, 0.0001f)
        assertEquals(-250.0, over.remaining, 0.001)
        assertEquals(125, over.percent)

        val under = BudgetCalculator.limitProgress(limit = 1000.0, spent = 250.0)
        assertFalse(under.isOver)
        assertEquals(0.25f, under.fraction, 0.0001f)
    }

    @Test
    fun goalProgress_handlesZeroTargetAndClamps() {
        assertEquals(0f, BudgetCalculator.goalProgress(saved = 50.0, target = 0.0), 0f)
        assertEquals(1f, BudgetCalculator.goalProgress(saved = 300.0, target = 200.0), 0f)
        assertEquals(0.5f, BudgetCalculator.goalProgress(saved = 100.0, target = 200.0), 0.0001f)
    }

    @Test
    fun weeklyReport_groupsByWeekdayAndExcludesOtherWeeks() {
        // Thu 5 Nov 2026 -> week Mon 2 Nov .. Sun 8 Nov
        val anchor = LocalDate.of(2026, 11, 5)
        val list = listOf(
            tx(TxType.EXPENSE, 40.0, Category.FOOD, LocalDate.of(2026, 11, 2)),      // Monday
            tx(TxType.INCOME, 500.0, Category.SALARY, LocalDate.of(2026, 11, 4)),    // Wednesday
            tx(TxType.EXPENSE, 10.0, Category.FOOD, LocalDate.of(2026, 11, 4)),      // Wednesday
            tx(TxType.EXPENSE, 999.0, Category.FOOD, LocalDate.of(2026, 10, 30))     // previous week
        )
        val r = BudgetCalculator.report(list, Period.WEEK, anchor, 0, utc)
        assertEquals(7, r.buckets.size)
        assertEquals("Mon", r.buckets[0].label)
        assertEquals(40.0, r.buckets[0].expense, 0.001)
        assertEquals(500.0, r.buckets[2].income, 0.001)
        assertEquals(10.0, r.buckets[2].expense, 0.001)
        assertEquals(50.0, r.summary.expense, 0.001)
        assertTrue(r.range.title.contains("2 Nov"))
    }

    @Test
    fun monthlyReport_hasOneBucketPerDay_andOffsetMovesBackwards() {
        val anchor = LocalDate.of(2027, 3, 15)
        val feb = BudgetCalculator.report(emptyList(), Period.MONTH, anchor, -1, utc)
        assertEquals(28, feb.buckets.size)
        assertEquals("February 2027", feb.range.title)
    }

    @Test
    fun dailyReport_bucketsByHour() {
        val d = LocalDate.of(2026, 11, 5)
        val r = BudgetCalculator.report(listOf(tx(TxType.EXPENSE, 25.0, Category.FOOD, d, hour = 13)), Period.DAY, d, 0, utc)
        assertEquals(24, r.buckets.size)
        assertEquals(25.0, r.buckets[13].expense, 0.001)
        assertEquals(0.0, r.buckets[12].expense, 0.001)
    }

    @Test
    fun yearlyReport_hasTwelveMonths() {
        val d = LocalDate.of(2026, 11, 5)
        val r = BudgetCalculator.report(listOf(tx(TxType.INCOME, 70.0, Category.BONUS, d)), Period.YEAR, d, 0, utc)
        assertEquals(12, r.buckets.size)
        assertEquals(70.0, r.buckets[10].income, 0.001)
    }

    @Test
    fun savingsTargetProgress_usesMonthlyNet() {
        assertEquals(0.4f, BudgetCalculator.savingsTargetProgress(monthNet = 400.0, target = 1000.0), 0.0001f)
        assertEquals(0f, BudgetCalculator.savingsTargetProgress(monthNet = -50.0, target = 1000.0), 0f)
    }
}
