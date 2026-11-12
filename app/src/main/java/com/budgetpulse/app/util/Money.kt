package com.budgetpulse.app.util

import com.budgetpulse.app.domain.logic.RatePair
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Currency text for any amount. The decimals always follow the CURRENCY being shown (USD/MYR 2, IDR/JPY 0),
 * never the phone's locale: a rupiah phone must still show US$1.28, not "US$1".
 */
fun formatMoney(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): String {
    val nf = NumberFormat.getCurrencyInstance(locale)
    runCatching { nf.currency = Currency.getInstance(currencyCode) }
    val digits = fractionDigitsFor(currencyCode)
    nf.minimumFractionDigits = digits
    nf.maximumFractionDigits = digits
    return nf.format(amount)
}

private val ZERO_DECIMAL = setOf("IDR", "VND", "JPY", "KRW")

internal fun fractionDigitsFor(code: String): Int =
    if (code in ZERO_DECIMAL) 0 else runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceIn(0, 3)

/** 1.2K / 3.4M style label for chart axes. */
fun compactAmount(value: Double): String {
    val v = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        v >= 1_000_000_000 -> sign + "%.1fB".format(Locale.ENGLISH, v / 1_000_000_000)
        v >= 1_000_000 -> sign + "%.1fM".format(Locale.ENGLISH, v / 1_000_000)
        v >= 1_000 -> sign + "%.1fK".format(Locale.ENGLISH, v / 1_000)
        else -> sign + "%.0f".format(Locale.ENGLISH, v)
    }
}

/** A formatted amount split so the UI can grey out the decimals, e.g. "$28,245" + ".54". */
data class MoneyParts(val main: String, val decimals: String)

fun formatMoneyParts(amount: Double, currencyCode: String, locale: Locale = Locale.getDefault()): MoneyParts {
    val nf = NumberFormat.getCurrencyInstance(locale)
    runCatching { nf.currency = Currency.getInstance(currencyCode) }
    val digits = fractionDigitsFor(currencyCode)
    nf.minimumFractionDigits = digits
    nf.maximumFractionDigits = digits
    val text = nf.format(amount)
    if (digits == 0) return MoneyParts(text, "")
    val separator = (nf as? java.text.DecimalFormat)?.decimalFormatSymbols?.monetaryDecimalSeparator ?: '.'
    val index = text.lastIndexOf(separator)
    return if (index < 0) MoneyParts(text, "") else MoneyParts(text.substring(0, index), text.substring(index))
}

/** Readable rate number: 16,022 / 4.06 / 0.0123 (always English separators, as in the Market tab). */
fun formatRate(rate: Double): String = when {
    rate >= 1000 -> String.format(Locale.ENGLISH, "%,.0f", rate)
    rate >= 1 -> String.format(Locale.ENGLISH, "%,.2f", rate)
    else -> String.format(Locale.ENGLISH, "%.4f", rate)
}

/** "1 USD = 4.06 MYR" */
fun RatePair.label(): String = "1 " + strong + " = " + formatRate(value) + " " + weak
