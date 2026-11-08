package com.quransunah.app.ui.mushaf

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.share.QulRecitations
import com.quransunah.app.share.QulWordTimings
import com.quransunah.app.share.WordSegment
import com.quransunah.app.share.wordAt
import com.quransunah.app.share.cursorSegments
import kotlinx.coroutines.CancellationException

/** Presentation-only observer: cannot seek, replace a URL, or alter the media session. */
@Composable
fun rememberQulPlaybackWord(playback: PlaybackSnapshot, lines: List<LineRecord>): Int? {
    val context = LocalContext.current
    val reciterId = playback.reciterId
    val recitationId = reciterId?.let { QulRecitations.recitationId(it) }
    val enabled = playback.domain == PlaybackDomain.AYAH && recitationId != null
    var segments by remember { mutableStateOf(emptyList<WordSegment>()) }
    val surah = playback.surah
    val ayah = playback.ayah
    LaunchedEffect(enabled, surah, ayah, reciterId) {
        segments = emptyList()
        if (enabled && surah != null && ayah != null && recitationId != null) {
            try { segments = QulWordTimings(context).load(surah, ayah, recitationId) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Existing ayah highlight is the offline fallback. */ }
        }
    }
    if (!enabled || playback.isBuffering) return null
    val position = segments.cursorSegments(playback.durationMs.coerceAtLeast(1L) * 1000).wordAt(playback.positionMs) ?: return null
    return lines.asSequence().flatMap { it.words.asSequence() }
        .firstOrNull { !it.isAyahMarker && it.surah == surah && it.ayah == ayah && it.position == position }?.id
}
