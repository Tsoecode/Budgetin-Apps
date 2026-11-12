package com.budgetpulse.app.domain.logic

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency
import java.util.Locale

/**
 * Pure maths for switching the app's money from one currency to another.
 * A rate table is always "units of X per 1 unit of one base currency" (rates[base] == 1.0).
 */
object CurrencyConversion {

    /** Currencies shown without decimals in the app (matches [com.budgetpulse.app.util.formatMoneyParts]). */
    private val ZERO_DECIMAL = setOf("IDR", "VND", "JPY", "KRW")

    fun fractionDigits(code: String): Int {
        if (code in ZERO_DECIMAL) return 0
        val d = runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
        return d.coerceIn(0, 3)
    }

    /** Half-up rounding (same as SQLite's ROUND, which the database conversion uses). */
    fun round(value: Double, digits: Int): Double {
        if (!value.isFinite()) return value
        return BigDecimal(value.toString()).setScale(digits, RoundingMode.HALF_UP).toDouble()
    }

    /** Multiplier that turns an amount in [from] into [to]; null when either currency has no usable rate. */
    fun factor(rates: Map<String, Double>, from: String, to: String): Double? {
        if (from == to) return 1.0
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        if (f <= 0.0 || t <= 0.0) return null
        return t / f
    }

    /** [value] x [factor], rounded to the decimals the target currency uses. */
    fun convert(value: Double, factor: Double, targetCurrency: String): Double =
        round(value * factor, fractionDigits(targetCurrency))
}

/** Result of turning the amount of an imported e-mail into the user's currency. */
sealed interface ImportedAmount {
    /** [note] is the original amount ("USD 29.45") when a conversion happened, otherwise null. */
    data class Ready(val amount: Double, val note: String?) : ImportedAmount

    /** Exchange rates could not be loaded: leave the e-mail for the next sync instead of storing a wrong amount. */
    data object NeedsRates : ImportedAmount

    /** The mail's currency is not in the rate table, so it can never be converted. */
    data object Unsupported : ImportedAmount
}

object EmailAmountConverter {
    /**
     * @param mailCurrency currency found in the e-mail, or null when the mail gave no currency (assumed to be the user's).
     * @param rates rate table whose base is [mailCurrency], or null when it could not be loaded.
     */
    fun toUserCurrency(amount: Double, mailCurrency: String?, userCurrency: String, rates: Map<String, Double>?): ImportedAmount {
        val digits = CurrencyConversion.fractionDigits(userCurrency)
        if (mailCurrency == null || mailCurrency == userCurrency) {
            return ImportedAmount.Ready(CurrencyConversion.round(amount, digits), null)
        }
        if (rates == null) return ImportedAmount.NeedsRates
        val factor = CurrencyConversion.factor(rates, mailCurrency, userCurrency) ?: return ImportedAmount.Unsupported
        val note = mailCurrency + " " + String.format(Locale.ENGLISH, "%.2f", amount)
        return ImportedAmount.Ready(CurrencyConversion.convert(amount, factor, userCurrency), note)
    }
}
