package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.local.RatesCacheEntity
import com.budgetpulse.app.data.local.RatesDao
import com.budgetpulse.app.data.remote.ExchangeRateApi
import com.budgetpulse.app.data.remote.FrankfurterApi
import com.budgetpulse.app.domain.model.CurrencyRates
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Currency market data. Strategy:
 *  1. serve the Room cache while it is still valid (the provider publishes a "next update" time),
 *  2. otherwise call ExchangeRate-API, 3. fall back to ECB reference rates (Frankfurter),
 *  4. if everything fails but a cache exists, return it flagged as stale.
 */
class CurrencyRepository(
    private val erApi: ExchangeRateApi,
    private val fxApi: FrankfurterApi,
    private val dao: RatesDao,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val mapSerializer = MapSerializer(String.serializer(), Double.serializer())

    suspend fun getRates(base: String, forceRefresh: Boolean = false): Result<CurrencyRates> =
        withContext(Dispatchers.IO) {
            val cached = dao.get(base)
            val now = clock()
            if (cached != null) {
                val stillValid = now / 1000 < cached.nextUpdateEpochSec
                val refreshedJustNow = now - cached.fetchedAtMillis < MIN_REFRESH_GAP_MS
                if ((!forceRefresh && stillValid) || (forceRefresh && refreshedJustNow)) {
                    AppLog.d(AppLog.NET, "rates[$base] served from cache")
                    return@withContext Result.success(cached.toDomain(stale = false))
                }
            }
            val remote = fetchRemote(base, now)
            when {
                remote != null -> {
                    dao.upsert(remote.toEntity(now))
                    AppLog.i(AppLog.DB, "rates[$base] cached (${remote.provider}, ${remote.rates.size} currencies)")
                    Result.success(remote)
                }
                cached != null -> {
                    AppLog.w(AppLog.NET, "rates[$base] network failed, showing stale cache")
                    Result.success(cached.toDomain(stale = true))
                }
                else -> Result.failure(IOException("Unable to load exchange rates. Check your connection."))
            }
        }

    private suspend fun fetchRemote(base: String, now: Long): CurrencyRates? {
        try {
            val r = erApi.latest(base)
            if (r.result == "success" && r.rates.isNotEmpty()) {
                return CurrencyRates(base, r.rates, r.timeLastUpdateUtc, r.timeNextUpdateUnix, "ExchangeRate-API")
            }
            AppLog.w(AppLog.NET, "ExchangeRate-API returned result=${r.result}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(AppLog.NET, "ExchangeRate-API failed: ${e.message}")
        }
        try {
            val r = fxApi.latest(base)
            if (r.rates.isNotEmpty()) {
                return CurrencyRates(
                    base = base,
                    rates = r.rates + (base to 1.0),
                    updatedLabel = r.date + " (ECB reference rates)",
                    nextUpdateEpochSec = now / 1000 + 6 * 3600,
                    provider = "ECB via Frankfurter"
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.w(AppLog.NET, "Frankfurter fallback failed: ${e.message}")
        }
        return null
    }

    private fun CurrencyRates.toEntity(now: Long) = RatesCacheEntity(
        base, json.encodeToString(mapSerializer, rates), updatedLabel, nextUpdateEpochSec, provider, now
    )

    private fun RatesCacheEntity.toDomain(stale: Boolean) = CurrencyRates(
        base, json.decodeFromString(mapSerializer, ratesJson), updatedLabel, nextUpdateEpochSec, provider, stale
    )

    companion object {
        /** Respect the open API's rate limit: manual refresh at most every 5 minutes. */
        const val MIN_REFRESH_GAP_MS = 5 * 60 * 1000L
    }
}
