package com.quransunah.app.ui.mushaf

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.share.QulRecitations
import com.quransunah.app.share.QulWordTimings
import com.quransunah.app.share.WordSegment
import com.quransunah.app.share.cursorSegments
import com.quransunah.app.share.wordAt
import kotlinx.coroutines.CancellationException

/** Word cursor for the Madinah page. [fromAyah] is the first ayah of this play, so earlier ayat stay lit. */
data class QulPlaybackCursor(
    val surah: Int,
    val ayah: Int,
    val position: Int,
    val progress: Float,
    val fromAyah: Int,
) {
    /** 1 = fully recited, a fraction = the word being read, 0 = not yet. */
    fun fractionFor(wordSurah: Int, wordAyah: Int, wordPosition: Int, marker: Boolean): Float {
        if (marker || wordSurah != surah || wordAyah < fromAyah) return 0f
        return when {
            wordAyah < ayah -> 1f
            wordAyah > ayah -> 0f
            wordPosition < position -> 1f
            wordPosition == position -> progress.coerceIn(0f, 1f)
            else -> 0f
        }
    }
}

val LocalQulPlaybackCursor = staticCompositionLocalOf<QulPlaybackCursor?> { null }

/**
 * Follows ayah audio for reciters that have word clocks.
 * Does not seek or replace the media item.
 */
@Composable
fun rememberQulPlaybackCursor(playback: PlaybackSnapshot): QulPlaybackCursor? {
    val context = LocalContext.current
    val reciterId = playback.reciterId
    val enabled = playback.domain == PlaybackDomain.AYAH &&
        reciterId != null &&
        QulRecitations.hasWordTimingCatalog(reciterId)
    var segments by remember { mutableStateOf(emptyList<WordSegment>()) }
    var loadedAyah by remember { mutableIntStateOf(-1) }
    var origin by remember(playback.domain, playback.surah, reciterId) { mutableIntStateOf(-1) }
    val surah = playback.surah
    val ayah = playback.ayah
    LaunchedEffect(enabled, surah, ayah, reciterId) {
        val current = ayah
        if (current != null) {
            val restart = playback.positionMs < 350L && current > origin + 1
            if (origin < 0 || current < origin || restart) origin = current
        }
        val id = reciterId
        if (enabled && surah != null && ayah != null) {
            try {
                val loaded = QulWordTimings(context).loadForReciter(id, surah, ayah).segments
                segments = loaded
                loadedAyah = ayah
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                segments = emptyList()
                loadedAyah = -1
            }
        } else {
            segments = emptyList()
            loadedAyah = -1
        }
    }
    if (!enabled || surah == null || ayah == null) return null
    val from = if (origin < 0) ayah else origin
    if (loadedAyah != ayah || segments.isEmpty()) {
        return if (ayah > from) QulPlaybackCursor(surah, ayah, 1, 0f, from) else null
    }
    val durationUs = playback.durationMs.coerceAtLeast(1L) * 1000
    val cursor = segments.cursorSegments(durationUs)
    val position = cursor.wordAt(playback.positionMs)
        ?: return QulPlaybackCursor(surah, ayah, segments.maxOf { it.position }, 1f, from)
    val spoken = cursor.firstOrNull { it.position == position }
    val progress = if (spoken == null) {
        1f
    } else {
        ((playback.positionMs - spoken.startMs).toFloat() /
            (spoken.endMs - spoken.startMs).coerceAtLeast(1)).coerceIn(0f, 1f)
    }
    return QulPlaybackCursor(surah, ayah, position, progress, from)
}
