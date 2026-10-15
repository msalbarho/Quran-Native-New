package com.quransunah.app.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStartPolicyTest {
    private val windowEnd = PlaybackStartPolicy.IMPLICIT_CONNECTION_PLAY_WINDOW_MS

    @Test
    fun phoneUiMayAlwaysStartPlayback() {
        assertTrue(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = true,
                carBrowser = false,
                userAlreadyAllowed = false,
                nowElapsedMs = 1_000L,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }

    @Test
    fun androidAutoConnectionWindowDoesNotStartPlayback() {
        assertFalse(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = false,
                carBrowser = true,
                userAlreadyAllowed = true,
                nowElapsedMs = 1_000L,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }

    @Test
    fun androidAutoPlayAfterConnectionWindowIsAllowed() {
        assertTrue(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = false,
                carBrowser = true,
                userAlreadyAllowed = false,
                nowElapsedMs = windowEnd,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }

    @Test
    fun bluetoothAloneNeverStartsPlayback() {
        assertFalse(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = false,
                carBrowser = false,
                userAlreadyAllowed = false,
                nowElapsedMs = windowEnd + 5_000L,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }

    @Test
    fun bluetoothConnectionWindowDoesNotResumeAPausedSession() {
        assertFalse(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = false,
                carBrowser = false,
                userAlreadyAllowed = true,
                nowElapsedMs = 500L,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }

    @Test
    fun headsetPlayAfterAnExplicitSessionIsAllowed() {
        assertTrue(
            PlaybackStartPolicy.mayStartPlayback(
                ownApp = false,
                carBrowser = false,
                userAlreadyAllowed = true,
                nowElapsedMs = windowEnd + 1L,
                implicitPlayBlockedUntilElapsedMs = windowEnd,
            ),
        )
    }
}
