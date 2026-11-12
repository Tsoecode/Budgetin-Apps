package com.budgetpulse.app.domain.logic

/** "1 [strong] = [value] [weak]" - the form people read: value is always >= 1, so never "0.000062". */
data class RatePair(val strong: String, val weak: String, val value: Double)

object RateDisplay {

    /** [factor] converts 1 [from] into [factor] [to]. Shows whichever of the two currencies is worth more as the "1". */
    fun fromFactor(from: String, to: String, factor: Double): RatePair =
        if (factor >= 1.0) RatePair(from, to, factor) else RatePair(to, from, 1.0 / factor)

    /**
     * Market rows. [rates] is "units of X per 1 [base]". The row for [code] reads
     * "1 USD = 16,000 IDR" no matter which of the two is the base. Null when the rate is unknown.
     */
    fun forRow(rates: Map<String, Double>, base: String, code: String): RatePair? {
        val r = rates[code] ?: return null
        if (!r.isFinite() || r <= 0.0) return null
        return fromFactor(base, code, r)
    }
}
