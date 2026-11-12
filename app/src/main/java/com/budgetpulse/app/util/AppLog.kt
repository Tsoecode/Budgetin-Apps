package com.budgetpulse.app.util

import android.util.Log

/** One place for logging so every key operation has a clear tag in Logcat. */
object AppLog {
    const val NET = "BP-Net"
    const val DB = "BP-DB"
    const val STATE = "BP-State"
    const val EMAIL = "BP-Email"
    const val NAV = "BP-Nav"

    fun d(tag: String, msg: String) { Log.d(tag, msg) }
    fun i(tag: String, msg: String) { Log.i(tag, msg) }
    fun w(tag: String, msg: String, t: Throwable? = null) { Log.w(tag, msg, t) }
    fun e(tag: String, msg: String, t: Throwable? = null) { Log.e(tag, msg, t) }
}
