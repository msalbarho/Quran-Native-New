package com.quransunah.app.media

import android.os.Bundle
import androidx.media3.common.Player
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.audio.AyatTimingStore
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackProgress
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persists the current recitation (surah / ayah_id / position) from the
 * Media3 player itself so Android Auto, the media notification, and the
 * lock screen keep the same place even when [MainActivity] is not open.
 */
@Singleton
class PlaybackProgressTracker @Inject constructor(
    private val preferences: UserPreferences,
    private val ayatTimingStore: AyatTimingStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var player: Player? = null
    private var tickerJob: Job? = null
    private var persistJob: Job? = null
    private var lastTimingKey: String? = null
    private var lastWritten: PlaybackProgress? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            capture(player, persistNow = events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
            ))
            if (player.isPlaying) startTicker() else tickerJob?.cancel()
        }
    }

    init {
        scope.launch {
            runCatching { _progress.value = preferences.playbackProgress.first() }
        }
    }

    fun last(): PlaybackProgress = _progress.value

    fun attach(player: Player) {
        if (this.player === player) return
        detach()
        this.player = player
        player.addListener(listener)
        capture(player, persistNow = true)
        if (player.isPlaying) startTicker()
    }

    fun detach() {
        tickerJob?.cancel()
        tickerJob = null
        persistJob?.cancel()
        persistJob = null
        player?.removeListener(listener)
        player?.let { capture(it, persistNow = true) }
        player = null
    }

    private fun startTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                player?.let { capture(it, persistNow = false) }
            }
        }
    }

    private fun capture(player: Player, persistNow: Boolean) {
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: Bundle.EMPTY
        val domain = extras.getString(PlaybackMediaIds.EXTRA_DOMAIN)
            ?.let { runCatching { PlaybackDomain.valueOf(it) }.getOrNull() }
            ?: PlaybackDomain.SURAH
        if (domain == PlaybackDomain.IDLE) return
        val surah = extras.intOrNull(PlaybackMediaIds.EXTRA_SURAH) ?: return
        val moshafId = extras.intOrNull(PlaybackMediaIds.EXTRA_MOSHAF_ID)
        val reciterId = extras.getString(PlaybackMediaIds.EXTRA_RECITER_ID)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val extrasAyah = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH)
        val ayah = when (domain) {
            PlaybackDomain.SURAH -> resolveSurahAyah(moshafId, surah, positionMs) ?: extrasAyah
            else -> extrasAyah
        } ?: 0
        val ayahId = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH_ID)
            ?: SurahAyahCounts.ayahId(surah, ayah)
        val next = PlaybackProgress(
            domain = domain,
            surah = surah,
            ayah = ayah,
            ayahId = ayahId,
            positionMs = positionMs,
            moshafId = moshafId,
            reciterId = reciterId,
        )
        val previous = _progress.value
        val changedPlace = previous.surah != next.surah ||
            previous.ayah != next.ayah ||
            previous.ayahId != next.ayahId ||
            previous.domain != next.domain ||
            previous.moshafId != next.moshafId
        val moved = abs(previous.positionMs - next.positionMs) >= POSITION_WRITE_THRESHOLD_MS
        _progress.value = next
        publishAyahToSession(player, next)
        if (persistNow || changedPlace || moved) {
            persist(next, force = persistNow || changedPlace)
        }
        if (domain == PlaybackDomain.SURAH && moshafId != null) {
            prefetchTimings(moshafId, surah)
        }
    }

    private fun persist(progress: PlaybackProgress, force: Boolean) {
        val previous = lastWritten
        if (!force && previous != null &&
            previous.domain == progress.domain &&
            previous.surah == progress.surah &&
            previous.ayah == progress.ayah &&
            previous.ayahId == progress.ayahId &&
            abs(previous.positionMs - progress.positionMs) < POSITION_WRITE_THRESHOLD_MS &&
            previous.moshafId == progress.moshafId
        ) {
            return
        }
        lastWritten = progress
        persistJob?.cancel()
        persistJob = scope.launch {
            if (!force) delay(PERSIST_DEBOUNCE_MS)
            withContext(Dispatchers.IO) {
                runCatching { preferences.savePlaybackProgress(progress) }
            }
        }
    }

    private fun resolveSurahAyah(moshafId: Int?, surah: Int, positionMs: Long): Int? {
        if (moshafId == null) return null
        prefetchTimings(moshafId, surah)
        return ayatTimingStore.peek(moshafId, surah)?.ayahAt(positionMs)
    }

    private fun prefetchTimings(moshafId: Int, surah: Int) {
        val key = "$moshafId:$surah"
        if (lastTimingKey == key) return
        lastTimingKey = key
        scope.launch(Dispatchers.IO) {
            ayatTimingStore.load(moshafId, surah)
            SurahAyahCounts.next(surah, SurahAyahCounts.ayahCount(surah))?.first?.let { next ->
                ayatTimingStore.load(moshafId, next)
            }
            withContext(Dispatchers.Main.immediate) {
                player?.let { capture(it, persistNow = false) }
            }
        }
    }

    private fun publishAyahToSession(player: Player, progress: PlaybackProgress) {
        if (progress.ayah <= 0) return
        val item = player.currentMediaItem ?: return
        val extras = item.mediaMetadata.extras ?: return
        val storedAyah = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH)
        val storedId = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH_ID)
        if (storedAyah == progress.ayah && storedId == progress.ayahId) return
        extras.putInt(PlaybackMediaIds.EXTRA_AYAH, progress.ayah)
        if (progress.ayahId > 0) extras.putInt(PlaybackMediaIds.EXTRA_AYAH_ID, progress.ayahId)
    }

    private fun Bundle.intOrNull(key: String): Int? {
        if (!containsKey(key)) return null
        val value = getInt(key, Int.MIN_VALUE)
        return value.takeIf { it != Int.MIN_VALUE }
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val PERSIST_DEBOUNCE_MS = 750L
        const val POSITION_WRITE_THRESHOLD_MS = 5_000L
    }
}
