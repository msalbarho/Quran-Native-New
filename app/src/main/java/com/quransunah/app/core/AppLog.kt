package com.quransunah.app.core

import android.util.Log
import com.quransunah.app.BuildConfig

/**
 * Debug-only logging. Release builds compile these into no-ops via R8
 * `assumenosideeffects` and the [BuildConfig.DEBUG] guard.
 */
object AppLog {
    inline fun d(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    inline fun i(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.i(tag, message())
    }

    inline fun v(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.v(tag, message())
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        if (!BuildConfig.DEBUG) return
        if (error != null) Log.w(tag, message, error) else Log.w(tag, message)
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        // Errors remain in release for crash triage; prefer CrashFileLogger for fatals.
        if (error != null) Log.e(tag, message, error) else Log.e(tag, message)
    }
}
