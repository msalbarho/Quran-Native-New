package com.quransunah.app.ui.mushaf

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.share.QulWordTimings
import com.quransunah.app.share.WordSegment
import com.quransunah.app.share.wordAt
import com.quransunah.app.share.cursorSegments
import kotlinx.coroutines.CancellationException

/** Presentation-only observer: cannot seek, replace a URL, or alter the media session. */
@Composable
fun rememberQulPlaybackWord(playback: PlaybackSnapshot, lines: List<LineRecord>): Int? {
    val context = LocalContext.current
    val enabled = playback.domain == PlaybackDomain.AYAH && playback.reciterId in setOf("123", "92")
    var segments by remember { mutableStateOf(emptyList<WordSegment>()) }
    val surah = playback.surah
    val ayah = playback.ayah
    LaunchedEffect(enabled, surah, ayah, playback.reciterId) {
        segments = emptyList()
        if (enabled && surah != null && ayah != null) {
            try { segments = QulWordTimings(context).load(surah, ayah, if (playback.reciterId == "92") 26 else 18) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Existing ayah highlight is the offline fallback. */ }
        }
    }
    if (!enabled || playback.isBuffering) return null
    val position = segments.cursorSegments(playback.durationMs.coerceAtLeast(1L) * 1000).wordAt(playback.positionMs) ?: return null
    return lines.asSequence().flatMap { it.words.asSequence() }
        .firstOrNull { !it.isAyahMarker && it.surah == surah && it.ayah == ayah && it.position == position }?.id
}
