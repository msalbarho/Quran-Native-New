package com.quransunah.app.media

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.audio.AudioUrls
import com.quransunah.app.data.audio.AyatTimingStore
import com.quransunah.app.data.audio.PlaybackNetwork
import com.quransunah.app.data.audio.SurahAudioStore
import com.quransunah.app.data.catalog.AyahAudioType
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.model.AudioQuality
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.domain.repository.AudioPlayerRepository
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.media.auto.SurahArabicNames
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

@Singleton
class AudioController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reciterCatalog: ReciterCatalog,
    private val surahAudioStore: SurahAudioStore,
    private val ayatTimingStore: AyatTimingStore,
    private val mushafRepository: MushafRepository,
    private val sessionPolicy: PlaybackSessionPolicy,
    private val preferences: UserPreferences,
) : AudioPlayerRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectMutex = Mutex()
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var requestedRepeat: SurahRepeatMode = SurahRepeatMode.OFF
    private var playbackRate: Float = AppConstants.DEFAULT_PLAYBACK_RATE
    private var audioQuality: AudioQuality = AudioQuality.HIGH
    private var surahNames: Map<Int, String> = emptyMap()
    private var lastTimingPrefetchKey: String? = null
    private var pendingSeekMediaIndex: Int = C.INDEX_UNSET
    private var pendingSeekPositionMs: Long = C.TIME_UNSET
    private var cachedPlaylistKey: String? = null
    private var cachedPlaylistItems: List<MediaItem> = emptyList()
    private val playbackGeneration = AtomicLong(0L)
    private val playbackMutex = Mutex()
    /** Black «القرآن الكريم» mark for notification / lockscreen artwork (not adaptive launcher). */
    private val artworkUri: Uri =
        Uri.parse("android.resource://${context.packageName}/${R.drawable.ic_media_artwork}")

    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    init {
        scope.launch {
            preferences.settings.collect { settings ->
                playbackRate = settings.playbackRate
                audioQuality = settings.audioQuality
                controller?.setPlaybackSpeed(settings.playbackRate)
            }
        }
        warmup()
    }

    override fun warmup() {
        scope.launch {
            runCatching { ensureController() }
            runCatching { surahName(1) }
        }
    }

    override fun prefetchSurahPlayback(reciterId: Int, moshafId: Int, surah: Int) {
        scope.launch {
            runCatching {
                val reciter = reciterCatalog.surahReciter(reciterId) ?: return@runCatching
                val moshaf = reciter.moshaf(moshafId) ?: reciter.preferredMoshaf() ?: return@runCatching
                if (!moshaf.contains(surah)) return@runCatching
                launch(Dispatchers.IO) { ayatTimingStore.load(moshaf.id, surah) }
                val snap = _snapshot.value
                if (snap.isPlaying || snap.playWhenReady) return@runCatching
                val items = surahPlaylistItems(moshaf, SurahRepeatMode.REMAINING, surah, 1)
                playbackMutex.withLock {
                    if (snap.isPlaying || _snapshot.value.playWhenReady) return@withLock
                    val player = ensureController()
                    if (hasSurahPlaylist(player, moshaf.id, items.size)) return@withLock
                    val playlist = (1..AppConstants.SURAH_COUNT).filter { moshaf.contains(it) }
                    val startIndex = playlist.indexOf(surah).coerceAtLeast(0)
                    player.setMediaItems(items, startIndex, 0L)
                    player.prepare()
                    player.pause()
                }
            }
        }
    }

    override suspend fun playSurah(
        reciterId: Int,
        moshafId: Int,
        surah: Int,
        repeatMode: SurahRepeatMode,
        startAyah: Int,
    ): Result<Unit> {
        val requestId = beginPlaybackRequest()
        return runCatching {
        val reciter = reciterCatalog.surahReciter(reciterId)
            ?: error(context.getString(R.string.error_reciter_missing))
        val moshaf = reciter.moshaf(moshafId) ?: reciter.preferredMoshaf()
            ?: error(context.getString(R.string.error_reciter_missing))
        val playlist = (1..AppConstants.SURAH_COUNT).filter { moshaf.contains(it) }
        if (playlist.isEmpty() || !moshaf.contains(surah)) {
            error(context.getString(R.string.error_missing_file))
        }
        val startIndex = playlist.indexOf(surah).coerceAtLeast(0)
        val currentLocal = surahAudioStore.has(moshaf.id, surah)
        if (!currentLocal && !PlaybackNetwork.isOnline(context)) {
            error(context.getString(R.string.error_offline_no_cache))
        }
        val verse = startAyah.coerceIn(1, SurahAyahCounts.ayahCount(surah).coerceAtLeast(1))
        val seekMs = ayatTimingStore.peek(moshaf.id, surah)?.startOf(verse)?.coerceAtLeast(0L) ?: 0L
        val items = surahPlaylistItems(moshaf, repeatMode, surah, verse)
        prefetchSurahTimings(moshaf.id, surah)
        playItems(
            items = items,
            startIndex = startIndex,
            repeatMode = repeatMode,
            pauseAtEnd = repeatMode == SurahRepeatMode.OFF,
            startPositionMs = seekMs,
            reuseMoshafId = moshaf.id,
            requestId = requestId,
        )
        if (verse > 1 && seekMs == 0L && isCurrentPlaybackRequest(requestId)) {
            scope.launch {
                val loaded = ayatTimingStore.load(moshaf.id, surah)?.startOf(verse)?.coerceAtLeast(0L)
                    ?: return@launch
                if (!isCurrentPlaybackRequest(requestId)) return@launch
                rememberPendingSeek(startIndex, loaded)
                withContext(Dispatchers.Main.immediate) {
                    if (isCurrentPlaybackRequest(requestId)) {
                        controller?.let { applyPendingSeekIfNeeded(it) }
                    }
                }
            }
        }
        }.onFailure { error ->
        if (isCurrentPlaybackRequest(requestId)) publishError(error)
        }
    }

    override suspend fun playAyah(reciterId: String, surah: Int, ayah: Int): Result<Unit> {
        val requestId = beginPlaybackRequest()
        return runCatching {
        val reciter = reciterCatalog.ayahReciter(reciterId) ?: reciterCatalog.defaultAyahReciter()
        if (reciter.type == AyahAudioType.MP3QURAN_TIMING) {
            val timingMoshafId = reciter.timingMoshafId
                ?: error(context.getString(R.string.error_reciter_missing))
            val timingReciterId = reciter.timingReciterId ?: timingMoshafId
            return@runCatching playSurah(
                reciterId = timingReciterId,
                moshafId = timingMoshafId,
                surah = surah,
                repeatMode = SurahRepeatMode.OFF,
                startAyah = ayah,
            ).getOrThrow()
        }
        stop()
        if (!PlaybackNetwork.isOnline(context)) {
            error(context.getString(R.string.error_offline))
        }
        val count = SurahAyahCounts.ayahCount(surah)
        if (count <= 0 || ayah !in 1..count) error(context.getString(R.string.error_missing_file))
        val items = (1..count).map { number -> ayahMediaItem(reciter, surah, number) }
        playItems(
            items = items,
            startIndex = ayah - 1,
            repeatMode = SurahRepeatMode.OFF,
            pauseAtEnd = false,
            requestId = requestId,
        )
        }.onFailure { error ->
        if (isCurrentPlaybackRequest(requestId)) publishError(error)
        }
    }

    override suspend fun playAyahRange(
        reciterId: String,
        startSurah: Int,
        startAyah: Int,
        endSurah: Int,
        endAyah: Int,
    ): Result<Unit> {
        val requestId = beginPlaybackRequest()
        return runCatching {
        val reciter = reciterCatalog.ayahReciter(reciterId) ?: reciterCatalog.defaultAyahReciter()
        if (!PlaybackNetwork.isOnline(context)) {
            error(context.getString(R.string.error_offline))
        }
        val range = SurahAyahCounts.range(startSurah, startAyah, endSurah, endAyah)
        if (range.isEmpty()) error(context.getString(R.string.error_missing_file))
        val items = range.map { (surah, ayah) -> ayahMediaItem(reciter, surah, ayah) }
        playItems(
            items = items,
            startIndex = 0,
            repeatMode = SurahRepeatMode.OFF,
            pauseAtEnd = false,
            requestId = requestId,
        )
        }.onFailure { error ->
        if (isCurrentPlaybackRequest(requestId)) publishError(error)
        }
    }

    override suspend fun playWord(
        wordId: Int,
        surah: Int,
        ayah: Int,
        wordPosition: Int,
    ): Result<Unit> {
        val requestId = beginPlaybackRequest()
        return runCatching {
        val localPath = AudioUrls.wordAssetPath(wordId)
        val hasAsset = runCatching { context.assets.open(localPath).close() }.isSuccess
        val uri = if (hasAsset) {
            AudioUrls.wordAssetUri(wordId)
        } else {
            if (!PlaybackNetwork.isOnline(context)) {
                error(context.getString(R.string.error_offline))
            }
            AudioUrls.wordCdnUri(surah, ayah, wordPosition)
        }
        val title = context.getString(R.string.playback_word_title)
        val extras = PlaybackMediaIds.extras(
            domain = PlaybackDomain.WORD,
            reciterName = title,
            reciterId = "word",
            surah = surah,
            surahName = surahName(surah),
            ayah = ayah,
            wordId = wordId,
            wordPosition = wordPosition,
            fromLocal = hasAsset,
        )
        val item = PlaybackMediaIds.mediaItem(
            mediaId = PlaybackMediaIds.word(wordId, surah, ayah, wordPosition),
            uri = uri,
            title = title,
            artist = surahName(surah),
            subtitle = if (hasAsset) context.getString(R.string.playback_from_local) else null,
            extras = extras,
            artwork = artworkUri,
        )
        playItems(
            listOf(item),
            startIndex = 0,
            repeatMode = SurahRepeatMode.OFF,
            pauseAtEnd = true,
            requestId = requestId,
        )
        }.onFailure { error ->
        if (isCurrentPlaybackRequest(requestId)) publishError(error)
        }
    }

    override fun pause() {
        controller?.pause()
    }

    override fun resume() {
        controller?.play()
    }

    override fun stop() {
        beginPlaybackRequest()
        controller?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        _snapshot.value = PlaybackSnapshot()
        requestedRepeat = SurahRepeatMode.OFF
        lastTimingPrefetchKey = null
    }

    override fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0L))
    }

    override suspend fun seekToAyah(surah: Int, ayah: Int) {
        val requestId = beginPlaybackRequest()
        val player = runCatching { ensureController() }.getOrNull() ?: return
        if (!isCurrentPlaybackRequest(requestId)) return
        val current = _snapshot.value
        when (current.domain) {
            PlaybackDomain.AYAH -> {
                val index = findAyahMediaIndex(player, surah, ayah)
                if (index >= 0) {
                    player.seekTo(index, 0L)
                    if (current.isPlaying || current.playWhenReady) player.play()
                } else {
                    val reciterId = current.reciterId ?: reciterCatalog.defaultAyahReciter().id
                    playAyah(reciterId, surah, ayah)
                }
            }
            PlaybackDomain.SURAH -> {
                val moshafId = current.moshafId ?: return
                val reciterId = current.reciterId?.toIntOrNull() ?: return
                val peeked = ayatTimingStore.peek(moshafId, surah)?.startOf(ayah)
                val index = findSurahMediaIndex(player, surah)
                if (index >= 0) {
                    if (peeked != null) {
                        rememberPendingSeek(index, peeked)
                        player.seekTo(index, peeked.coerceAtLeast(0L))
                        player.play()
                    } else if (ayah <= 1) {
                        player.seekTo(index, 0L)
                        player.play()
                    } else {
                        player.play()
                        scope.launch {
                            val startMs = ayatTimingStore.load(moshafId, surah)?.startOf(ayah)
                                ?: return@launch
                            if (!isCurrentPlaybackRequest(requestId)) return@launch
                            rememberPendingSeek(index, startMs)
                            withContext(Dispatchers.Main.immediate) {
                                if (isCurrentPlaybackRequest(requestId)) {
                                    applyPendingSeekIfNeeded(player)
                                }
                            }
                        }
                    }
                } else {
                    playSurah(reciterId, moshafId, surah, current.repeatMode, startAyah = ayah)
                }
            }
            PlaybackDomain.WORD, PlaybackDomain.IDLE -> Unit
        }
        controller?.let { emitSnapshot(it) }
    }

    override suspend fun seekToWord(wordId: Int, surah: Int, ayah: Int, wordPosition: Int) {
        when (_snapshot.value.domain) {
            PlaybackDomain.WORD -> playWord(wordId, surah, ayah, wordPosition)
            PlaybackDomain.AYAH, PlaybackDomain.SURAH -> seekToAyah(surah, ayah)
            PlaybackDomain.IDLE -> Unit
        }
    }

    override fun skipNext() {
        val player = controller ?: return
        beginPlaybackRequest()
        val current = _snapshot.value
        when (current.domain) {
            PlaybackDomain.AYAH -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                } else {
                    val surah = current.surah ?: return
                    val ayah = current.ayah ?: return
                    val next = SurahAyahCounts.next(surah, ayah) ?: return
                    val reciterId = current.reciterId ?: reciterCatalog.defaultAyahReciter().id
                    scope.launch { playAyah(reciterId, next.first, next.second) }
                }
            }
            PlaybackDomain.SURAH -> if (player.hasNextMediaItem()) player.seekToNextMediaItem()
            else -> Unit
        }
    }

    override fun skipPrevious() {
        val player = controller ?: return
        beginPlaybackRequest()
        val current = _snapshot.value
        when (current.domain) {
            PlaybackDomain.AYAH -> {
                if (player.hasPreviousMediaItem()) {
                    player.seekToPreviousMediaItem()
                } else {
                    val surah = current.surah ?: return
                    val ayah = current.ayah ?: return
                    val previous = SurahAyahCounts.previous(surah, ayah) ?: return
                    val reciterId = current.reciterId ?: reciterCatalog.defaultAyahReciter().id
                    scope.launch { playAyah(reciterId, previous.first, previous.second) }
                }
            }
            PlaybackDomain.SURAH -> if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
            else -> Unit
        }
    }

    override fun setRepeatMode(mode: SurahRepeatMode) {
        requestedRepeat = mode
        sessionPolicy.pauseAtEndOfItems = mode == SurahRepeatMode.OFF
        controller?.repeatMode = playerRepeatMode(mode)
        _snapshot.update { it.copy(repeatMode = mode) }
    }

    override fun setPlaybackRate(rate: Float) {
        val nearest = AppConstants.PLAYBACK_RATES.minBy { kotlin.math.abs(it - rate) }
        playbackRate = nearest
        controller?.setPlaybackSpeed(nearest)
    }

    private suspend fun playItems(
        items: List<MediaItem>,
        startIndex: Int,
        repeatMode: SurahRepeatMode,
        pauseAtEnd: Boolean,
        startPositionMs: Long = 0L,
        reuseMoshafId: Int? = null,
        requestId: Long = playbackGeneration.get(),
    ) {
        withContext(Dispatchers.Main.immediate) {
            playbackMutex.withLock {
                if (!isCurrentPlaybackRequest(requestId)) return@withLock
                val player = ensureController()
                requestedRepeat = repeatMode
                _snapshot.update { it.copy(errorMessage = null, isBuffering = true, repeatMode = repeatMode) }
                val index = startIndex.coerceIn(0, items.lastIndex)
                val position = startPositionMs.coerceAtLeast(0L)
                rememberPendingSeek(index, position)
                val reuse = reuseMoshafId != null &&
                    hasSurahPlaylist(player, reuseMoshafId, items.size)
                if (reuse) {
                    player.seekTo(index, position)
                } else {
                    player.setMediaItems(items, index, position)
                    player.prepare()
                }
                player.repeatMode = playerRepeatMode(repeatMode)
                player.setPlaybackSpeed(playbackRate)
                sessionPolicy.pauseAtEndOfItems = pauseAtEnd
                player.play()
                if (position > 0L) {
                    player.seekTo(index, position)
                }
                emitSnapshot(player)
            }
        }
    }

    private fun beginPlaybackRequest(): Long {
        clearPendingSeek()
        return playbackGeneration.incrementAndGet()
    }

    private fun isCurrentPlaybackRequest(requestId: Long): Boolean =
        playbackGeneration.get() == requestId

    private fun rememberPendingSeek(index: Int, positionMs: Long) {
        if (positionMs > 0L) {
            pendingSeekMediaIndex = index
            pendingSeekPositionMs = positionMs
        } else {
            clearPendingSeek()
        }
    }

    private fun clearPendingSeek() {
        pendingSeekMediaIndex = C.INDEX_UNSET
        pendingSeekPositionMs = C.TIME_UNSET
    }

    private fun applyPendingSeekIfNeeded(player: Player) {
        val index = pendingSeekMediaIndex
        val target = pendingSeekPositionMs
        if (index == C.INDEX_UNSET || target == C.TIME_UNSET || target <= 0L) return
        val state = player.playbackState
        if (state == Player.STATE_IDLE || state == Player.STATE_ENDED) return
        val currentIndex = player.currentMediaItemIndex
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val closeEnough = currentIndex == index && kotlin.math.abs(currentPos - target) <= SEEK_MATCH_TOLERANCE_MS
        if (closeEnough && currentPos > 0L) {
            clearPendingSeek()
            return
        }
        val canSeek = state == Player.STATE_READY ||
            (player.duration != C.TIME_UNSET && player.duration > 0L)
        if (canSeek) {
            player.seekTo(index, target)
        }
    }

    private suspend fun surahPlaylistItems(
        moshaf: com.quransunah.app.data.catalog.MoshafEdition,
        repeatMode: SurahRepeatMode,
        startSurah: Int,
        startAyah: Int,
    ): List<MediaItem> {
        val cacheKey = "${moshaf.id}:$startSurah:$startAyah:$repeatMode"
        val cached = cachedPlaylistItems
        if (cachedPlaylistKey == cacheKey && cached.size > 1) {
            return cached
        }
        surahName(1)
        val playlist = (1..AppConstants.SURAH_COUNT).filter { moshaf.contains(it) }
        val items = playlist.map { number ->
            surahMediaItem(
                reciterName = moshaf.reciterName,
                moshaf = moshaf,
                surah = number,
                repeatMode = repeatMode,
                ayah = if (number == startSurah) startAyah else 1,
            )
        }
        cachedPlaylistKey = cacheKey
        cachedPlaylistItems = items
        return items
    }

    private fun hasSurahPlaylist(player: Player, moshafId: Int, expectedCount: Int): Boolean {
        if (player.mediaItemCount != expectedCount || expectedCount <= 0) return false
        val extras = player.getMediaItemAt(0).mediaMetadata.extras ?: return false
        val domain = extras.getString(PlaybackMediaIds.EXTRA_DOMAIN)
        val id = extras.getInt(PlaybackMediaIds.EXTRA_MOSHAF_ID, Int.MIN_VALUE)
        return domain == PlaybackDomain.SURAH.name && id == moshafId
    }

    private suspend fun surahMediaItem(
        reciterName: String,
        moshaf: com.quransunah.app.data.catalog.MoshafEdition,
        surah: Int,
        repeatMode: SurahRepeatMode,
        ayah: Int? = null,
    ): MediaItem {
        val (uri, fromLocal) = surahAudioStore.playbackUri(moshaf, surah)
        val name = surahName(surah)
        val extras = PlaybackMediaIds.extras(
            domain = PlaybackDomain.SURAH,
            reciterName = reciterName,
            reciterId = moshaf.reciterId.toString(),
            surah = surah,
            surahName = name,
            ayah = ayah,
            moshafId = moshaf.id,
            fromLocal = fromLocal,
            repeatMode = repeatMode,
        )
        return PlaybackMediaIds.mediaItem(
            mediaId = PlaybackMediaIds.surah(moshaf.id, surah),
            uri = uri,
            title = name,
            artist = reciterName,
            subtitle = if (fromLocal) context.getString(R.string.playback_from_local) else moshaf.name,
            extras = extras,
            artwork = artworkUri,
        )
    }

    private suspend fun ayahMediaItem(reciter: AyahReciter, surah: Int, ayah: Int): MediaItem {
        val name = surahName(surah)
        val title = context.getString(
            R.string.playback_ayah_title,
            name,
            EasternArabic.format(ayah),
        )
        val extras = PlaybackMediaIds.extras(
            domain = PlaybackDomain.AYAH,
            reciterName = reciter.name,
            reciterId = reciter.id,
            surah = surah,
            surahName = name,
            ayah = ayah,
        )
        return PlaybackMediaIds.mediaItem(
            mediaId = PlaybackMediaIds.ayah(reciter.id, surah, ayah),
            uri = AudioUrls.ayahStreamUri(reciter, surah, ayah, audioQuality),
            title = title,
            artist = reciter.name,
            subtitle = name,
            extras = extras,
            artwork = artworkUri,
        )
    }

    private suspend fun surahName(surah: Int): String {
        if (surahNames.isEmpty()) {
            surahNames = runCatching {
                mushafRepository.getSurahs().associate { it.number to it.nameArabic }
            }.getOrDefault(emptyMap())
        }
        return surahNames[surah]
            ?: SurahArabicNames.label(surah)
            ?: context.getString(
                R.string.playback_surah_fallback,
                EasternArabic.format(surah),
            )
    }

    private suspend fun ensureController(): MediaController {
        controller?.let { current ->
            if (current.isConnected) return current
        }
        return connectMutex.withLock {
            controller?.takeIf { it.isConnected }?.let { return@withLock it }
            withContext(Dispatchers.Main.immediate) {
                releaseControllerLocked()
                val token = SessionToken(
                    context,
                    ComponentName(context, PlaybackService::class.java),
                )
                val future = MediaController.Builder(context, token).buildAsync()
                suspendCancellableCoroutine { continuation ->
                    future.addListener(
                        {
                            runCatching { future.get() }
                                .onSuccess { connected ->
                                    controller = connected
                                    connected.addListener(playerListener)
                                    connected.setPlaybackSpeed(playbackRate)
                                    if (connected.isPlaying) startTicker()
                                    continuation.resume(connected)
                                }
                                .onFailure { error ->
                                    continuation.resumeWithException(error)
                                }
                        },
                        ContextCompat.getMainExecutor(context),
                    )
                    continuation.invokeOnCancellation {
                        MediaController.releaseFuture(future)
                    }
                }
            }
        }
    }

    private fun releaseControllerLocked() {
        tickerJob?.cancel()
        tickerJob = null
        val current = controller ?: return
        current.removeListener(playerListener)
        current.release()
        controller = null
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                controller?.let { emitSnapshot(it) }
                delay(250)
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            applyPendingSeekIfNeeded(player)
            emitSnapshot(player)
            if (player.isPlaying) startTicker() else tickerJob?.cancel()
        }

        override fun onPlayerError(error: PlaybackException) {
            _snapshot.update { current ->
                current.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = mapPlaybackError(error),
                )
            }
        }
    }

    private fun emitSnapshot(player: Player) {
        val item = player.currentMediaItem
        val extras = item?.mediaMetadata?.extras ?: Bundle.EMPTY
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it >= 0L } ?: 0L
        val domain = extras.getString(PlaybackMediaIds.EXTRA_DOMAIN)
            ?.let { runCatching { PlaybackDomain.valueOf(it) }.getOrNull() }
            ?: if (item == null) PlaybackDomain.IDLE else PlaybackDomain.SURAH
        val resolvedDomain = if (item == null) PlaybackDomain.IDLE else domain
        val moshafId = extras.intOrNull(PlaybackMediaIds.EXTRA_MOSHAF_ID)
        val surah = extras.intOrNull(PlaybackMediaIds.EXTRA_SURAH)
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        val extrasAyah = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH)
        val ayah = when (resolvedDomain) {
            PlaybackDomain.SURAH -> resolveSurahAyah(moshafId, surah, positionMs) ?: extrasAyah
            else -> extrasAyah
        }
        _snapshot.value = PlaybackSnapshot(
            domain = resolvedDomain,
            isPlaying = player.isPlaying,
            playWhenReady = player.playWhenReady,
            isBuffering = player.playbackState == Player.STATE_BUFFERING || player.isLoading,
            fromLocalCache = extras.getBoolean(PlaybackMediaIds.EXTRA_FROM_LOCAL),
            reciterName = extras.getString(PlaybackMediaIds.EXTRA_RECITER_NAME)
                ?: item?.mediaMetadata?.artist?.toString(),
            reciterId = extras.getString(PlaybackMediaIds.EXTRA_RECITER_ID),
            moshafId = moshafId,
            surah = surah,
            surahName = extras.getString(PlaybackMediaIds.EXTRA_SURAH_NAME)
                ?: item?.mediaMetadata?.title?.toString(),
            ayah = ayah,
            ayahId = extras.intOrNull(PlaybackMediaIds.EXTRA_AYAH_ID)
                ?: ayah?.let { verse -> surah?.let { chapter -> SurahAyahCounts.ayahId(chapter, verse) } }
                    ?.takeIf { it > 0 },
            wordId = extras.intOrNull(PlaybackMediaIds.EXTRA_WORD_ID),
            wordPosition = extras.intOrNull(PlaybackMediaIds.EXTRA_WORD_POSITION),
            positionMs = positionMs,
            durationMs = duration,
            repeatMode = requestedRepeat,
            errorMessage = _snapshot.value.errorMessage,
        )
    }

    private fun resolveSurahAyah(moshafId: Int?, surah: Int?, positionMs: Long): Int? {
        if (moshafId == null || surah == null) return null
        prefetchSurahTimings(moshafId, surah)
        return ayatTimingStore.peek(moshafId, surah)?.ayahAt(positionMs)
    }

    private fun prefetchSurahTimings(moshafId: Int, surah: Int) {
        val key = "$moshafId:$surah"
        if (lastTimingPrefetchKey == key) return
        lastTimingPrefetchKey = key
        scope.launch(Dispatchers.IO) {
            ayatTimingStore.load(moshafId, surah)
            val next = SurahAyahCounts.next(surah, SurahAyahCounts.ayahCount(surah))?.first
            if (next != null) ayatTimingStore.load(moshafId, next)
            withContext(Dispatchers.Main.immediate) {
                controller?.let { emitSnapshot(it) }
            }
        }
    }

    private fun findAyahMediaIndex(player: Player, surah: Int, ayah: Int): Int {
        for (index in 0 until player.mediaItemCount) {
            val parsed = PlaybackMediaIds.parseAyah(player.getMediaItemAt(index).mediaId) ?: continue
            if (parsed.second == surah && parsed.third == ayah) return index
        }
        return -1
    }

    private fun findSurahMediaIndex(player: Player, surah: Int): Int {
        for (index in 0 until player.mediaItemCount) {
            val parsed = PlaybackMediaIds.parseSurah(player.getMediaItemAt(index).mediaId) ?: continue
            if (parsed.second == surah) return index
        }
        return -1
    }

    private fun publishError(error: Throwable) {
        val message = when (error) {
            is PlaybackException -> mapPlaybackError(error)
            else -> error.message?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.error_playback_generic)
        }
        _snapshot.update {
            it.copy(
                isPlaying = false,
                isBuffering = false,
                errorMessage = message,
            )
        }
    }

    private fun mapPlaybackError(error: PlaybackException): String {
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> context.getString(R.string.error_offline)
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            -> context.getString(R.string.error_missing_file)
            else -> context.getString(R.string.error_playback_generic)
        }
    }

    private companion object {
        const val SEEK_MATCH_TOLERANCE_MS = 800L
    }

    private fun playerRepeatMode(mode: SurahRepeatMode): Int = when (mode) {
        SurahRepeatMode.ONE -> Player.REPEAT_MODE_ONE
        SurahRepeatMode.OFF, SurahRepeatMode.REMAINING -> Player.REPEAT_MODE_OFF
    }

    private fun Bundle.intOrNull(key: String): Int? {
        if (!containsKey(key)) return null
        val value = getInt(key, Int.MIN_VALUE)
        return value.takeIf { it != Int.MIN_VALUE }
    }
}
