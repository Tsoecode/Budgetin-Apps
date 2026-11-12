package com.budgetpulse.app.data.repository

import androidx.room.withTransaction
import com.budgetpulse.app.data.local.AppDatabase
import com.budgetpulse.app.data.prefs.UserPreferencesRepository
import com.budgetpulse.app.domain.logic.CurrencyConversion
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** What the user is shown before anything is changed. */
data class ConversionPreview(
    val from: String,
    val to: String,
    /** 1 [from] = [factor] [to] */
    val factor: Double,
    val updatedLabel: String,
    val provider: String
)

data class ConversionSummary(val transactions: Int, val accounts: Int, val limits: Int, val goals: Int, val factor: Double)

/**
 * Converts EVERYTHING that holds money (transactions, account balances, category limits, savings goals and
 * the monthly savings target) from the current currency to a new one, using the live rates of the same
 * exchange-rate API the Market tab uses.
 *
 *  - [preview] fetches the freshest rate and refuses to continue on stale/offline data,
 *  - [convertAll] changes all Room tables inside ONE database transaction (all-or-nothing) and then stores
 *    the new currency in DataStore,
 *  - both share [lock] with the e-mail importer so a background sync can never insert a row half-way through.
 */
class CurrencyConversionRepository(
    private val database: AppDatabase,
    private val prefs: UserPreferencesRepository,
    private val rates: CurrencyRepository,
    private val lock: Mutex
) {
    suspend fun preview(to: String): Result<ConversionPreview> {
        val from = prefs.prefs.first().currency
        if (from == to) return Result.failure(IllegalArgumentException("Your data is already in $to."))
        val table = rates.getRates(from, forceRefresh = true).getOrElse { return Result.failure(it) }
        if (table.stale) {
            return Result.failure(IOException("Cannot reach the exchange-rate service right now. Connect to the internet and try again."))
        }
        val factor = CurrencyConversion.factor(table.rates, from, to)
            ?: return Result.failure(IOException("No exchange rate is available for $to."))
        AppLog.i(AppLog.NET, "conversion preview $from -> $to factor=$factor (${table.provider})")
        return Result.success(ConversionPreview(from, to, factor, table.updatedLabel, table.provider))
    }

    suspend fun convertAll(preview: ConversionPreview): Result<ConversionSummary> = lock.withLock {
        try {
            val current = prefs.prefs.first().currency
            if (current != preview.from) {
                return@withLock Result.failure(IllegalStateException("The currency changed in the meantime. Please try again."))
            }
            val digits = CurrencyConversion.fractionDigits(preview.to)
            val factor = preview.factor
            val summary = database.withTransaction {
                ConversionSummary(
                    transactions = database.transactionDao().convertAll(factor, digits),
                    accounts = database.accountDao().convertAll(factor, digits),
                    limits = database.budgetDao().convertAll(factor, digits),
                    goals = database.goalDao().convertAll(factor, digits),
                    factor = factor
                ).also { database.notificationDao().convertAll(factor, digits) }
            }
            prefs.applyCurrencyConversion(preview.to, factor)
            AppLog.i(AppLog.DB, "CONVERT ${preview.from}->${preview.to}: ${summary.transactions} transactions, ${summary.accounts} accounts, ${summary.limits} limits, ${summary.goals} goals")
            Result.success(summary)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(AppLog.DB, "currency conversion failed (nothing was changed)", e)
            Result.failure(e)
        }
    }
}
