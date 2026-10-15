package com.quransunah.app.core

/**
 * Tracks whether [com.quransunah.app.MainActivity] is in the STARTED state.
 * A visible activity is not permission for Android Auto or Bluetooth to start
 * playback; those commands are gated in [com.quransunah.app.media.PlaybackService].
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
