package com.quransunah.app.media

/**
 * Decides whether a media play command may move the player from paused/stopped
 * to playing.
 *
 * Android Auto and Bluetooth deliver connection-time resume as an ordinary play
 * command, with no separate "this was automatic" flag. Commands that arrive in
 * the short window after a non-app controller connects are treated as that
 * implicit resume. The phone UI is recognized by its own package name, and a
 * surah chosen in Auto uses play-from-id rather than this gate.
 */
internal object PlaybackStartPolicy {
    const val IMPLICIT_CONNECTION_PLAY_WINDOW_MS = 4_000L

    fun mayStartPlayback(
        ownApp: Boolean,
        carBrowser: Boolean,
        userAlreadyAllowed: Boolean,
        nowElapsedMs: Long,
        implicitPlayBlockedUntilElapsedMs: Long,
    ): Boolean {
        if (ownApp) return true
        if (nowElapsedMs < implicitPlayBlockedUntilElapsedMs) return false
        if (carBrowser) return true
        return userAlreadyAllowed
    }
}
