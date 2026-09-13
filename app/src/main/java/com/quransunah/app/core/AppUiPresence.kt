package com.quransunah.app.core

/**
 * Tracks whether [com.quransunah.app.MainActivity] is in the STARTED state.
 * Used by [com.quransunah.app.media.PlaybackService] to reject Bluetooth
 * auto-resume when the user has not opened the phone UI (Android Auto is
 * allowed separately via car browser package checks).
 */
object AppUiPresence {
    @Volatile
    private var startedCount: Int = 0

    val isUiStarted: Boolean
        get() = startedCount > 0

    fun onActivityStarted() {
        startedCount += 1
    }

    fun onActivityStopped() {
        startedCount = (startedCount - 1).coerceAtLeast(0)
    }
}
