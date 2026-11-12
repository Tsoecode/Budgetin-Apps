package com.budgetpulse.app.data.repository

import com.budgetpulse.app.data.prefs.SecureStore
import com.budgetpulse.app.data.prefs.UserPreferencesRepository
import com.budgetpulse.app.domain.logic.EmailAmountConverter
import com.budgetpulse.app.domain.logic.EmailParser
import com.budgetpulse.app.domain.logic.ImportedAmount
import com.budgetpulse.app.domain.logic.InitialSyncRange
import com.budgetpulse.app.domain.model.Transaction
import com.budgetpulse.app.domain.model.TxSource
import com.budgetpulse.app.email.EmailAuthException
import com.budgetpulse.app.email.EmailFetcher
import com.budgetpulse.app.email.ImapConfig
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

data class SyncResult(
    val scanned: Int = 0,
    val matched: Int = 0,
    val imported: Int = 0,
    /** Mails in another currency that wait for exchange rates; they are picked up again on the next sync. */
    val waitingForRates: Int = 0,
    /** Mails whose currency has no exchange rate at all (skipped for good). */
    val unsupported: Int = 0,
    val error: String? = null,
    val retryable: Boolean = false
) {
    val summary: String
        get() = error ?: buildString {
            append("Scanned $scanned e-mails, $matched looked like bank notifications, $imported new transaction(s) added.")
            if (waitingForRates > 0) append(" $waitingForRates waiting for exchange rates.")
            if (unsupported > 0) append(" $unsupported skipped (currency not supported).")
        }
}

/**
 * Unique feature: pulls m-banking / e-wallet notification e-mails from the mailbox configured in
 * Settings and turns each one into an income or expense transaction.
 */
class EmailImportRepository(
    private val prefs: UserPreferencesRepository,
    private val secure: SecureStore,
    private val fetcher: EmailFetcher,
    private val transactions: TransactionRepository,
    private val accounts: AccountRepository,
    private val currency: CurrencyRepository,
    private val lock: Mutex,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    /** Serialised with currency conversions: a sync never writes while all amounts are being converted. */
    suspend fun sync(allowInitialScan: Boolean = true): SyncResult = lock.withLock { doSync(allowInitialScan) }

    private suspend fun doSync(allowInitialScan: Boolean): SyncResult {
        val p = prefs.prefs.first()
        if (p.lastEmailSyncAt == 0L && !allowInitialScan) {
            return SyncResult(error = "Initial e-mail scan requires manual sync.")
        }
        if (!p.emailImportEnabled) return SyncResult(error = "E-mail import is switched off.")
        val encrypted = prefs.encryptedImapPassword()
        if (p.imapUser.isBlank() || encrypted.isNullOrBlank()) {
            return SyncResult(error = "Enter your e-mail address and app password in Settings first.")
        }
        val password = runCatching { secure.decrypt(encrypted) }.getOrNull()
            ?: return SyncResult(error = "Saved password could not be decrypted. Please enter it again.")

        val accountList = accounts.observeAll().first()
        val target = accountList.firstOrNull { it.id == p.emailAccountId } ?: accountList.firstOrNull()
            ?: return SyncResult(error = "Create an account first.")

        val now = clock()
        val initialRange = InitialSyncRange.fromDays(p.initialScanDays)
        val since = if (p.lastEmailSyncAt > 0) p.lastEmailSyncAt - TWO_DAYS_MS else now - initialRange.durationMs
        val maxMessages = if (p.lastEmailSyncAt > 0) MAX_MESSAGES else if (initialRange == InitialSyncRange.ONE_MONTH) MAX_MESSAGES_MONTH else MAX_MESSAGES
        val keywords = p.senderKeywords.split(',').map { it.trim() }.filter { it.isNotEmpty() }

        return try {
            AppLog.i(AppLog.EMAIL, "sync started host=${p.imapHost} since=$since initialRange=${initialRange.label}")
            val mails = fetcher.fetchRecent(ImapConfig(p.imapHost, p.imapUser, password), since, maxMessages)
            var matched = 0
            var imported = 0
            var waiting = 0
            var unsupported = 0
            val ratesByCurrency = HashMap<String, Map<String, Double>?>()
            for (mail in mails) {
                if (!EmailParser.isFromBank(mail.from, keywords)) continue
                val parsed = EmailParser.parse(mail) ?: continue
                matched++

                // Mail in another currency than the one chosen in Settings -> convert with the live rates.
                val code = parsed.currencyCode
                val rates = if (code != null && code != p.currency) {
                    ratesByCurrency.getOrPut(code) { currency.getRates(code).getOrNull()?.rates }
                } else null
                val money = EmailAmountConverter.toUserCurrency(parsed.amount, code, p.currency, rates)
                when (money) {
                    ImportedAmount.NeedsRates -> { waiting++; continue }
                    ImportedAmount.Unsupported -> { unsupported++; continue }
                    is ImportedAmount.Ready -> {
                        val note = if (money.note != null) parsed.description + " (" + money.note + ")" else parsed.description
                        val saved = transactions.importFromEmail(
                            Transaction(
                                accountId = target.id, type = parsed.type, amount = money.amount, category = parsed.category,
                                note = note, timestamp = mail.receivedAt, source = TxSource.EMAIL, externalId = mail.messageId
                            )
                        )
                        if (saved) imported++
                    }
                }
            }
            val result = SyncResult(mails.size, matched, imported, waiting, unsupported)
            // While mails wait for rates keep the old "last check" time so the next sync scans them again.
            prefs.setLastEmailSync(if (waiting > 0) p.lastEmailSyncAt else now, result.summary)
            AppLog.i(AppLog.EMAIL, "sync finished: ${result.summary}")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: EmailAuthException) {
            AppLog.w(AppLog.EMAIL, "auth failed")
            SyncResult(error = e.message ?: "Login failed.").also { prefs.setLastEmailSync(now, it.summary) }
        } catch (e: IOException) {
            AppLog.w(AppLog.EMAIL, "network error: ${e.message}")
            SyncResult(error = e.message ?: "Network error.", retryable = true)
        } catch (e: Exception) {
            AppLog.e(AppLog.EMAIL, "unexpected error", e)
            SyncResult(error = "Unexpected error: ${e.message}")
        }
    }

    companion object {
        private const val MAX_MESSAGES = 100
        private const val MAX_MESSAGES_MONTH = 300
        private const val TWO_DAYS_MS = 2L * 24 * 60 * 60 * 1000
    }
}
