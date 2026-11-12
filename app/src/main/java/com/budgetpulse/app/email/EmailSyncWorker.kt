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
    override suspend fun doWork(): Result {
        val container = (applicationContext as BudgetPulseApp).container
        val slot = inputData.getString(EmailSyncScheduler.KEY_SLOT) ?: EmailSyncScheduler.SLOT_A
        try {
            val result = container.emailImportRepository.sync(allowInitialScan = false)
            AppLog.i(AppLog.EMAIL, "background sync: ${result.summary}")
        } finally {
            // Always queue the next check, even after an error, or the chain would silently stop.
            withContext(NonCancellable) { EmailSyncScheduler.scheduleNext(applicationContext, container.userPreferences, slot) }
        }
        return Result.success()
    }
}

object EmailSyncScheduler {
    const val SLOT_A = "email-import-sync-a"
    const val SLOT_B = "email-import-sync-b"
    const val KEY_SLOT = "slot"
    private const val LEGACY_PERIODIC = "email-import-sync"

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
