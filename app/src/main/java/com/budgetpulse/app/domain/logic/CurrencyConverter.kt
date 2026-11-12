package com.budgetpulse.app.domain.logic

object CurrencyConverter {
    /**
     * Converts using a rate table where every value is expressed relative to one base currency
     * (so rates[base] == 1.0). Returns null when either currency is unknown.
     */
    fun convert(amount: Double, from: String, to: String, rates: Map<String, Double>): Double? {
        val f = rates[from] ?: return null
        val t = rates[to] ?: return null
        if (f == 0.0) return null
        return amount * t / f
    }
}
