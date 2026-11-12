package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.RateDisplay
import com.budgetpulse.app.util.formatRate
import com.budgetpulse.app.util.label
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateDisplayTest {

    @Test
    fun changingMyrToUsd_isShownAsOneUsdInMyr() {
        // 1 MYR = 0.2464 USD  ->  must read "1 USD = 4.06 MYR", not a long decimal
        val pair = RateDisplay.fromFactor("MYR", "USD", 0.2464)
        assertEquals("USD", pair.strong)
        assertEquals("MYR", pair.weak)
        assertEquals("1 USD = 4.06 MYR", pair.label())
    }

    @Test
    fun changingUsdToMyr_alreadyReadsNaturally() {
        assertEquals("1 USD = 4.06 MYR", RateDisplay.fromFactor("USD", "MYR", 4.0584).label())
    }

    @Test
    fun rupiahPairsShowOneForeignUnitInRupiah() {
        assertEquals("1 USD = 16,000 IDR", RateDisplay.fromFactor("IDR", "USD", 1.0 / 16000.0).label())
        assertEquals("1 EUR = 17,500 IDR", RateDisplay.fromFactor("IDR", "EUR", 1.0 / 17500.0).label())
    }

    @Test
    fun marketRows_alwaysPutTheStrongerCurrencyFirst_whateverTheBase() {
        val rates = mapOf("USD" to 1.0, "IDR" to 16000.0, "MYR" to 4.0, "EUR" to 0.5)
        // base USD: IDR is weaker -> "1 USD = 16,000 IDR"; EUR is stronger -> "1 EUR = 2.00 USD"
        assertEquals("1 USD = 16,000 IDR", RateDisplay.forRow(rates, "USD", "IDR")!!.label())
        assertEquals("1 EUR = 2.00 USD", RateDisplay.forRow(rates, "USD", "EUR")!!.label())
    }

    @Test
    fun marketRows_valueIsNeverBelowOne() {
        val rates = mapOf("IDR" to 1.0, "USD" to 0.0000625, "JPY" to 0.0094, "MYR" to 0.00025)   // base IDR
        for (code in listOf("USD", "JPY", "MYR")) assertTrue(RateDisplay.forRow(rates, "IDR", code)!!.value >= 1.0)
    }

    @Test
    fun unknownOrBrokenRatesGiveNoRow() {
        assertNull(RateDisplay.forRow(mapOf("USD" to 1.0), "USD", "XXX"))
        assertNull(RateDisplay.forRow(mapOf("USD" to 1.0, "IDR" to 0.0), "USD", "IDR"))
        assertNull(RateDisplay.forRow(mapOf("USD" to 1.0, "IDR" to Double.NaN), "USD", "IDR"))
    }

    @Test
    fun formatRate_usesShortReadableNumbers() {
        assertEquals("16,022", formatRate(16021.6))
        assertEquals("4.06", formatRate(4.0584))
        assertEquals("0.0123", formatRate(0.0123))
    }
}
