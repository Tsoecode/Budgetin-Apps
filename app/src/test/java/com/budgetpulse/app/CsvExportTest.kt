package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.CsvExport
import com.budgetpulse.app.domain.model.Category
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class CsvExportTest {
    private fun tx(note: String, amount: Double, type: TxType = TxType.EXPENSE, day: Int = 2) = Transaction(
        accountId = 1, type = type, amount = amount, category = Category.FOOD, note = note,
        timestamp = LocalDate.of(2026, 11, day).atTime(9, 30).toInstant(ZoneOffset.UTC).toEpochMilli()
    )

    @Test
    fun csv_hasHeader_andRowsSortedByDate() {
        val csv = CsvExport.build(listOf(tx("later", 5.0, day = 4), tx("first", 10.0, day = 2)), mapOf(1L to "Cash"), ZoneOffset.UTC)
        val lines = csv.trim().lines()
        assertEquals("date,type,category,amount,account,note", lines[0])
        assertEquals("2026-11-02 09:30,expense,Food & Restaurants,10.0,Cash,first", lines[1])
        assertTrue(lines[2].endsWith("later"))
    }

    @Test
    fun csv_escapesCommasAndQuotes() {
        assertEquals("\"Lunch, with \"\"Ana\"\"\"", CsvExport.escape("Lunch, with \"Ana\""))
        assertEquals("plain", CsvExport.escape("plain"))
    }
}
