package com.budgetpulse.app.email

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.budgetpulse.app.BudgetPulseApp
import com.budgetpulse.app.data.prefs.UserPreferencesRepository
import com.budgetpulse.app.domain.logic.SyncInterval
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * One e-mail check. When it is done it schedules the next one, so the chosen interval can be 10 minutes
 * (WorkManager's periodic jobs cannot go below 15). Android may delay a run while the phone is idle (Doze).
 */
class EmailSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        const val KEY_MANUAL = "manual"
        const val KEY_RECOVERY_MONTH = "recovery_month"
        const val KEY_SUMMARY = "summary"
        const val KEY_IMPORTED = "imported"
        const val KEY_RECOVERED_MONTH = "recovered_month"
        const val KEY_REQUESTED_DAYS = "requested_days"
    }
    override suspend fun doWork(): Result {
        val container = (applicationContext as BudgetPulseApp).container
        if (inputData.getBoolean(KEY_MANUAL, false)) {
            val recoveryMonth = inputData.getBoolean(KEY_RECOVERY_MONTH, false)
            val requestedDays = inputData.getInt(KEY_REQUESTED_DAYS, 7)
            if (!recoveryMonth) {
                container.userPreferences.setManualScanOriginalDays(requestedDays)
            }
            container.userPreferences.setInitialScanDays(requestedDays)
            val result = container.emailImportRepository.sync(allowInitialScan = true)
            if (result.error != null || result.waitingForRates > 0) {
                container.userPreferences.setManualEmailScanPending(false)
                container.userPreferences.setManualScanAttempt(0)
                return Result.failure(workDataOf(
                    KEY_SUMMARY to (result.error ?: result.summary),
                    KEY_IMPORTED to -1,
                    KEY_RECOVERED_MONTH to false
                ))
            }
            val attemptedDays = container.userPreferences.manualScanAttempt()
            if (result.imported == 0 && !recoveryMonth && attemptedDays == 7) {
                // Remember that the next user-triggered manual scan should widen to 30 days.
                container.userPreferences.setManualScanAttempt(30)
                return Result.success(workDataOf(
                    KEY_SUMMARY to "No transactions found in 7 days. Tap Sync now to scan 30 days.",
                    KEY_IMPORTED to 0,
                    KEY_RECOVERED_MONTH to false
                ))
            }
            container.userPreferences.setManualEmailScanPending(false)
            container.userPreferences.setManualScanAttempt(0)
            return Result.success(workDataOf(
                KEY_SUMMARY to result.summary,
                KEY_IMPORTED to result.imported,
                KEY_RECOVERED_MONTH to inputData.getBoolean(KEY_RECOVERY_MONTH, false)
            ))
        }
        val slot = inputData.getString(EmailSyncScheduler.KEY_SLOT) ?: EmailSyncScheduler.SLOT_A
        try {
            val result = container.emailImportRepository.sync(allowInitialScan = false)
            AppLog.i(AppLog.EMAIL, "background sync: ${result.summary}")
        } finally {
            withContext(NonCancellable) { EmailSyncScheduler.scheduleNext(applicationContext, container.userPreferences, slot) }
        }
        return Result.success()
    }
}

object EmailSyncScheduler {
    const val SLOT_A = "email-import-sync-a"
    const val SLOT_B = "email-import-sync-b"
    const val KEY_SLOT = "slot"
    const val MANUAL_SCAN = "email-import-manual-scan"
    private const val LEGACY_PERIODIC = "email-import-sync"

    fun enqueueManualScan(context: Context, requestedDays: Int) {
        val request = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInputData(workDataOf(
                EmailSyncWorker.KEY_MANUAL to true,
                EmailSyncWorker.KEY_RECOVERY_MONTH to false,
                EmailSyncWorker.KEY_REQUESTED_DAYS to requestedDays
            ))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL_SCAN, ExistingWorkPolicy.REPLACE, request)
    }

    private fun request(slot: String, delayMinutes: Long): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .setInputData(workDataOf(KEY_SLOT to slot))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()

    /** The user changed the switch or the interval in Settings: restart the chain (first check right away). */
    fun apply(context: Context, enabled: Boolean, hasCompletedInitialSync: Boolean) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(SLOT_A)
        wm.cancelUniqueWork(SLOT_B)
        wm.cancelUniqueWork(LEGACY_PERIODIC)
        val shouldRun = enabled && hasCompletedInitialSync
        if (shouldRun) wm.enqueueUniqueWork(SLOT_A, ExistingWorkPolicy.REPLACE, request(SLOT_A, 0))
        AppLog.i(AppLog.EMAIL, if (shouldRun) "e-mail checks (re)started" else "e-mail checks stopped")
    }

    /**
     * App start: make sure a check is queued, but never touch a chain that is already waiting or running
     * (the worker itself starts the app process, so cancelling here would make it loop).
     */
    fun ensureScheduled(context: Context, enabled: Boolean, hasCompletedInitialSync: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled || !hasCompletedInitialSync) {
            wm.cancelUniqueWork(SLOT_A)
            wm.cancelUniqueWork(SLOT_B)
            wm.cancelUniqueWork(LEGACY_PERIODIC)
            return
        }
        wm.cancelUniqueWork(LEGACY_PERIODIC)
        val alive = listOf(SLOT_A, SLOT_B).any { name ->
            wm.getWorkInfosForUniqueWork(name).get().any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
        }
        if (!alive) wm.enqueueUniqueWork(SLOT_A, ExistingWorkPolicy.REPLACE, request(SLOT_A, 1))
    }

    suspend fun scheduleNext(context: Context, prefs: UserPreferencesRepository, currentSlot: String) {
        val p = prefs.prefs.first()
        if (!p.emailImportEnabled || p.lastEmailSyncAt == 0L) {
            WorkManager.getInstance(context).cancelUniqueWork(SLOT_A)
            WorkManager.getInstance(context).cancelUniqueWork(SLOT_B)
            return
        }
        val next = if (currentSlot == SLOT_A) SLOT_B else SLOT_A
        val minutes = SyncInterval.fromMinutes(p.emailIntervalMinutes).minutes.toLong()
        WorkManager.getInstance(context).enqueueUniqueWork(next, ExistingWorkPolicy.REPLACE, request(next, minutes))
    }
}
