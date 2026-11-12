package com.budgetpulse.app

import com.budgetpulse.app.util.formatMoney
import com.budgetpulse.app.util.formatMoneyParts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyPartsTest {

    @Test
    fun splitsTheDecimalsOfTwoDecimalCurrencies() {
        val p = formatMoneyParts(28245.54, "USD", Locale.US)
        assertEquals("$28,245", p.main)
        assertEquals(".54", p.decimals)
    }

    @Test
    fun zeroDecimalCurrenciesHaveNoDecimalPart() {
        assertEquals("", formatMoneyParts(1250000.0, "IDR", Locale("id", "ID")).decimals)
        assertEquals("", formatMoneyParts(5000.0, "JPY", Locale.JAPAN).decimals)
    }

    @Test
    fun mainPlusDecimalsRebuildsTheFullAmount() {
        val p = formatMoneyParts(3100.0, "USD", Locale.US)
        assertEquals("$3,100.00", p.main + p.decimals)
    }

    @Test
    fun respectsLocaleDecimalSeparator() {
        val p = formatMoneyParts(1234.5, "EUR", Locale.GERMANY)
        assertTrue(p.decimals.startsWith(","))
    }

    @Test
    fun formatMoney_keepsTheCentsOfCentCurrencies_evenOnARupiahPhone() {
        // regression: History showed "US$1" / "US$0" because the Indonesian locale has no decimals for rupiah
        assertEquals("US$1,28", formatMoney(1.28, "USD", Locale("id", "ID")).replace("\u00a0", ""))
        assertEquals("$1.28", formatMoney(1.28, "USD", Locale.US))
        assertEquals("$0.02", formatMoney(0.02, "USD", Locale.US))
    }

    @Test
    fun formatMoney_zeroDecimalCurrenciesHaveNoDecimals() {
        assertEquals("¥5,000", formatMoney(5000.0, "JPY", Locale.JAPAN).replace("￥", "¥"))
        assertTrue(!formatMoney(25000.0, "IDR", Locale("id", "ID")).contains(","))
    }
}
