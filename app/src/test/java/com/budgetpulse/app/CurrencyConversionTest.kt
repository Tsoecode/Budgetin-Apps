package com.budgetpulse.app

import com.budgetpulse.app.domain.logic.CurrencyConversion
import com.budgetpulse.app.domain.logic.EmailAmountConverter
import com.budgetpulse.app.domain.logic.ImportedAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrencyConversionTest {

    // 1 USD = 16,000 IDR = 4.5 MYR = 0.9 EUR
    private val usdRates = mapOf("USD" to 1.0, "IDR" to 16000.0, "MYR" to 4.5, "EUR" to 0.9)

    @Test
    fun factor_goesThroughTheBaseCurrency() {
        assertEquals(16000.0, CurrencyConversion.factor(usdRates, "USD", "IDR")!!, 1e-9)
        assertEquals(16000.0 / 4.5, CurrencyConversion.factor(usdRates, "MYR", "IDR")!!, 1e-9)
        assertEquals(1.0, CurrencyConversion.factor(usdRates, "EUR", "EUR")!!, 0.0)
    }

    @Test
    fun factor_isNullForUnknownOrBrokenRates() {
        assertNull(CurrencyConversion.factor(usdRates, "USD", "XXX"))
        assertNull(CurrencyConversion.factor(usdRates, "XXX", "USD"))
        assertNull(CurrencyConversion.factor(mapOf("USD" to 1.0, "IDR" to 0.0), "USD", "IDR"))
    }

    @Test
    fun fractionDigits_followTheCurrency() {
        assertEquals(0, CurrencyConversion.fractionDigits("IDR"))
        assertEquals(0, CurrencyConversion.fractionDigits("JPY"))
        assertEquals(2, CurrencyConversion.fractionDigits("USD"))
        assertEquals(2, CurrencyConversion.fractionDigits("MYR"))
    }

    @Test
    fun round_isHalfUp() {
        assertEquals(2.68, CurrencyConversion.round(2.675, 2), 0.0)
        assertEquals(3.0, CurrencyConversion.round(2.5, 0), 0.0)
        assertEquals(1.0, CurrencyConversion.round(0.5, 0), 0.0)
        assertEquals(12.35, CurrencyConversion.round(12.345, 2), 0.0)
    }

    @Test
    fun convert_roundsToTheTargetCurrencyDecimals() {
        // 12.50 USD -> whole rupiah
        assertEquals(200000.0, CurrencyConversion.convert(12.5, 16000.0, "IDR"), 0.0)
        // 25,000 IDR -> USD with cents
        assertEquals(1.56, CurrencyConversion.convert(25000.0, 1.0 / 16000.0, "USD"), 0.0)
    }

    @Test
    fun convert_roundTripStaysWithinOneRoundingStep() {
        val original = 3.17
        val idr = CurrencyConversion.convert(original, 16000.0, "IDR")
        val back = CurrencyConversion.convert(idr, 1.0 / 16000.0, "USD")
        assertTrue(Math.abs(back - original) <= 0.01)
    }

    @Test
    fun convert_negativeAndZeroValues() {
        assertEquals(0.0, CurrencyConversion.convert(0.0, 16000.0, "IDR"), 0.0)
        assertEquals(-200000.0, CurrencyConversion.convert(-12.5, 16000.0, "IDR"), 0.0)
    }

    // ----------------------------------------------------------------- e-mail amounts

    @Test
    fun email_sameCurrencyOrNoCurrency_isStoredAsIs() {
        val same = EmailAmountConverter.toUserCurrency(150000.0, "IDR", "IDR", null)
        assertEquals(ImportedAmount.Ready(150000.0, null), same)
        val none = EmailAmountConverter.toUserCurrency(25.5, null, "MYR", null)
        assertEquals(ImportedAmount.Ready(25.5, null), none)
    }

    @Test
    fun email_foreignCurrency_isConvertedAndKeepsTheOriginalInTheNote() {
        // mail says USD 29.45, user works in IDR; rate table is based on USD
        val r = EmailAmountConverter.toUserCurrency(29.45, "USD", "IDR", usdRates)
        assertTrue(r is ImportedAmount.Ready)
        r as ImportedAmount.Ready
        assertEquals(471200.0, r.amount, 0.0)
        assertEquals("USD 29.45", r.note)
    }

    @Test
    fun email_foreignCurrency_toTwoDecimalCurrency() {
        val r = EmailAmountConverter.toUserCurrency(100.0, "MYR", "EUR", mapOf("MYR" to 1.0, "EUR" to 0.2))
        r as ImportedAmount.Ready
        assertEquals(20.0, r.amount, 0.0)
    }

    @Test
    fun email_withoutRates_isDeferredNotGuessed() {
        assertEquals(ImportedAmount.NeedsRates, EmailAmountConverter.toUserCurrency(29.45, "USD", "IDR", null))
    }

    @Test
    fun email_currencyMissingFromTheRateTable_isUnsupported() {
        assertEquals(ImportedAmount.Unsupported, EmailAmountConverter.toUserCurrency(10.0, "UAH", "IDR", usdRates))
    }
}
