package com.budgetpulse.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.budgetpulse.app.di.AppContainer
import com.budgetpulse.app.di.DefaultAppContainer
import com.budgetpulse.app.email.EmailSyncScheduler
import com.budgetpulse.app.ui.common.PerfTier
import com.budgetpulse.app.ui.common.Performance
import com.budgetpulse.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BudgetPulseApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    /** Small image caches (flags, news banners): keeps memory use low on under-spec phones. */
    override fun newImageLoader(): ImageLoader {
        val lite = Performance.tierFor(this) == PerfTier.Lite
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (lite) 0.06 else 0.12).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(24L * 1024 * 1024).build() }
            .allowRgb565(true)          // opaque images use half the memory
            .crossfade(false)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        AppLog.i(AppLog.STATE, "Budgetin started")
        // Background e-mail checks: restart the chain when the user changes the switch / interval (skip the
        // initial value), and make sure one is queued at start.
        container.applicationScope.launch {
            val initial = container.userPreferences.prefs.first()
            withContext(Dispatchers.IO) {
                EmailSyncScheduler.ensureScheduled(this@BudgetPulseApp, initial.emailImportEnabled, initial.lastEmailSyncAt > 0L)
            }
            container.userPreferences.prefs
                .map { Triple(it.emailImportEnabled, it.emailIntervalMinutes, it.lastEmailSyncAt > 0L) }
                .distinctUntilChanged()
                .drop(1)
                .collect { (enabled, _, hasCompletedInitialSync) ->
                    withContext(Dispatchers.IO) { EmailSyncScheduler.apply(this@BudgetPulseApp, enabled, hasCompletedInitialSync) }
                }
        }
    }
}
