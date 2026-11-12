package com.budgetpulse.app.ui.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Budgetin adapts to the phone it runs on, so under-spec devices stay smooth:
 *  Full - Android 12+ with enough memory: real glass blur, auto-scrolling news, embedded guide opens at once.
 *  Lite - old / low-memory phones: no screen capture or blur (a plain frosted tint instead), news only scrolls
 *         by hand, and the heavy embedded web page loads only when asked for.
 */
enum class PerfTier { Full, Lite }

object Performance {
    private const val MIN_TOTAL_RAM_BYTES = 3L * 1024 * 1024 * 1024
    private const val MIN_HEAP_MB = 192

    fun tierFor(context: Context): PerfTier {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val lite = am.isLowRamDevice ||
            info.totalMem < MIN_TOTAL_RAM_BYTES ||
            am.memoryClass <= MIN_HEAP_MB ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        return if (lite) PerfTier.Lite else PerfTier.Full
    }
}

val LocalPerfTier = staticCompositionLocalOf { PerfTier.Full }
