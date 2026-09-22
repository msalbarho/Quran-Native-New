package com.quransunah.app.ui.listening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.audio.DownloadProgress
import com.quransunah.app.data.audio.PlaybackNetwork
import com.quransunah.app.data.audio.SurahAudioStore
import com.quransunah.app.data.catalog.MoshafEdition
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.domain.model.SleepTimerMode
import com.quransunah.app.domain.model.SleepTimerState
import com.quransunah.app.domain.repository.AudioPlayerRepository
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.media.PlaybackSessionPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListeningUiState(
    val reciters: List<SurahReciter> = emptyList(),
    val selectedReciter: SurahReciter? = null,
    val selectedMoshaf: MoshafEdition? = null,
    val selectedSurah: Int = 1,
    val availableSurahs: List<Int> = emptyList(),
    val surahs: List<SurahInfo> = emptyList(),
    val query: String = "",
    val repeatMode: SurahRepeatMode = SurahRepeatMode.REMAINING,
    val downloadFrom: Int = 1,
    val downloadTo: Int = 1,
    val download: DownloadProgress? = null,
    val cachedInRange: Int = 0,
    val rangeTotal: Int = 0,
    val selectedIsCached: Boolean = false,
)

@HiltViewModel
class ListeningViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val reciterCatalog: ReciterCatalog,
    private val mushafRepository: MushafRepository,
    private val audioPlayer: AudioPlayerRepository,
    private val audioStore: SurahAudioStore,
    private val preferences: UserPreferences,
    private val sessionPolicy: PlaybackSessionPolicy,
    val fontManager: QcfFontManager,
) : ViewModel() {

    val playback: StateFlow<PlaybackSnapshot> = audioPlayer.snapshot
    val sleepTimer: StateFlow<SleepTimerState> = audioPlayer.sleepTimer
    val playbackRate: StateFlow<Float> = preferences.settings
        .map { it.playbackRate }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppConstants.DEFAULT_PLAYBACK_RATE)

    private val activePlayingSurah = playback
        .map { snapshot ->
            snapshot.surah.takeIf { snapshot.domain == PlaybackDomain.SURAH }
        }
        .distinctUntilChanged()

    private val reciters = MutableStateFlow<List<SurahReciter>>(emptyList())
    private val surahCatalog = MutableStateFlow<List<SurahInfo>>(emptyList())
    private val selectedReciterId = MutableStateFlow(AppConstants.DEFAULT_SURAH_RECITER_ID)
    private val selectedSurah = MutableStateFlow(1)
    private val query = MutableStateFlow("")
    private val repeatMode = MutableStateFlow(SurahRepeatMode.REMAINING)
    private val downloadFrom = MutableStateFlow(1)
    private val downloadTo = MutableStateFlow(1)
    private val download = MutableStateFlow<DownloadProgress?>(null)

    val uiState: StateFlow<ListeningUiState> = combine(
        combine(reciters, selectedReciterId, selectedSurah, surahCatalog) { reciterList, reciterId, surah, catalog ->
            Quad(reciterList, reciterId, surah, catalog)
        },
        combine(query, repeatMode, downloadFrom, downloadTo) { q, repeat, from, to ->
            Quad(q, repeat, from, to)
        },
        download,
        audioStore.revision,
        activePlayingSurah,
    ) { selection, filters, progress, _, playingSurah ->
        val reciterList = selection.a
        val reciter = reciterList.firstOrNull { it.id == selection.b } ?: reciterList.firstOrNull()
        val moshaf = reciter?.preferredMoshaf()
        val available = moshaf?.surahNumbers?.sorted().orEmpty()
        val surah = if (available.contains(selection.c)) selection.c else available.firstOrNull() ?: 1
        val from = if (available.contains(filters.c)) filters.c else surah
        val to = if (available.contains(filters.d)) filters.d else surah
        val lo = minOf(from, to)
        val hi = maxOf(from, to)
        val range = available.filter { it in lo..hi }
        ListeningUiState(
            reciters = reciterList,
            selectedReciter = reciter,
            selectedMoshaf = moshaf,
            selectedSurah = playingSurah ?: surah,
            availableSurahs = available,
            surahs = selection.d.filter { available.contains(it.number) },
            query = filters.a,
            repeatMode = filters.b,
            downloadFrom = from,
            downloadTo = to,
            download = progress,
            cachedInRange = moshaf?.let { audioStore.cachedCount(it.id, range) } ?: 0,
            rangeTotal = range.size,
            selectedIsCached = moshaf?.let { audioStore.has(it.id, playingSurah ?: surah) } == true,
        )
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListeningUiState())

    init {
        viewModelScope.launch {
            reciters.value = reciterCatalog.surahReciters()
            surahCatalog.value = runCatching { mushafRepository.getSurahs() }.getOrDefault(emptyList())
            val restored = preferences.settings.first()
            val reciterId = reciters.value.firstOrNull { it.id == restored.lastReciterId }?.id
                ?: reciterCatalog.defaultSurahReciter()?.id
                ?: AppConstants.DEFAULT_SURAH_RECITER_ID
            selectedReciterId.value = reciterId
            reciters.value.firstOrNull { it.id == reciterId }?.let { sessionPolicy.activate(it) }
            selectedSurah.value = restored.lastListeningSurah
            downloadFrom.value = restored.lastListeningSurah
            downloadTo.value = restored.lastListeningSurah
            repeatMode.value = runCatching { SurahRepeatMode.valueOf(restored.lastRepeatMode) }
                .getOrDefault(SurahRepeatMode.REMAINING)
        }
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                if (settings.lastReciterId != selectedReciterId.value) {
                    selectedReciterId.value = settings.lastReciterId
                    reciters.value.firstOrNull { it.id == settings.lastReciterId }
                        ?.let { sessionPolicy.activate(it) }
                }
            }
        }
        viewModelScope.launch {
            activePlayingSurah.collect { surah ->
                if (surah != null && selectedSurah.value != surah) {
                    selectedSurah.value = surah
                }
            }
        }
    }

    override fun onCleared() {
        download.value = null
        super.onCleared()
    }

    fun selectReciter(reciter: SurahReciter) {
        if (reciter.id == selectedReciterId.value) return
        audioPlayer.stop()
        applyReciter(reciter)
    }

    fun switchSurahReciterAndPlay(reciter: SurahReciter) {
        if (reciter.id == selectedReciterId.value) return
        applyReciter(reciter)
        val snapshot = playback.value
        val surah = snapshot.surah?.takeIf { snapshot.domain == PlaybackDomain.SURAH }
            ?: selectedSurah.value
        val moshaf = reciter.preferredMoshaf() ?: return
        val playSurah = if (moshaf.contains(surah)) surah else moshaf.surahNumbers.minOrNull() ?: 1
        selectSurah(playSurah)
        viewModelScope.launch {
            audioPlayer.playSurah(reciter.id, moshaf.id, playSurah, repeatMode.value)
        }
    }

    private fun applyReciter(reciter: SurahReciter) {
        selectedReciterId.value = reciter.id
        sessionPolicy.activate(reciter)
        val first = reciter.preferredMoshaf()?.surahNumbers?.minOrNull() ?: 1
        if (!reciter.preferredMoshaf().orEmptyContains(selectedSurah.value)) {
            selectedSurah.value = first
            downloadFrom.value = first
            downloadTo.value = first
        }
        viewModelScope.launch { preferences.setLastReciterId(reciter.id) }
    }

    fun selectSurah(surah: Int) {
        selectedSurah.value = surah
        viewModelScope.launch { preferences.setLastListeningSurah(surah) }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setDownloadFrom(surah: Int) {
        downloadFrom.value = surah
    }

    fun setDownloadTo(surah: Int) {
        downloadTo.value = surah
    }

    fun cycleRepeatMode() {
        val next = when (repeatMode.value) {
            SurahRepeatMode.REMAINING -> SurahRepeatMode.OFF
            SurahRepeatMode.OFF -> SurahRepeatMode.ONE
            SurahRepeatMode.ONE -> SurahRepeatMode.REMAINING
        }
        repeatMode.value = next
        audioPlayer.setRepeatMode(next)
        viewModelScope.launch { preferences.setLastRepeatMode(next) }
    }

    fun playOrToggle(surah: Int = uiState.value.selectedSurah) {
        val reciter = uiState.value.selectedReciter ?: return
        val moshaf = reciter.preferredMoshaf() ?: return
        val snapshot = playback.value
        val sameTrack = snapshot.domain == PlaybackDomain.SURAH &&
            snapshot.surah == surah &&
            snapshot.moshafId == moshaf.id
        if (sameTrack && (snapshot.isPlaying || snapshot.playWhenReady || snapshot.isBuffering || snapshot.durationMs > 0L)) {
            if (snapshot.isPlaying) audioPlayer.pause() else audioPlayer.resume()
            return
        }
        selectSurah(surah)
        viewModelScope.launch {
            audioPlayer.playSurah(reciter.id, moshaf.id, surah, repeatMode.value)
        }
    }

    fun playPreviousSurah() {
        val state = uiState.value
        val index = state.availableSurahs.indexOf(state.selectedSurah)
        if (index > 0) playOrToggle(state.availableSurahs[index - 1])
    }

    fun playNextSurah() {
        val state = uiState.value
        val index = state.availableSurahs.indexOf(state.selectedSurah)
        if (index >= 0 && index < state.availableSurahs.lastIndex) {
            playOrToggle(state.availableSurahs[index + 1])
        }
    }

    fun pause() = audioPlayer.pause()
    fun resume() = audioPlayer.resume()
    fun stop() = audioPlayer.stop()
    fun skipNext() = audioPlayer.skipNext()
    fun skipPrevious() = audioPlayer.skipPrevious()
    fun seekTo(positionMs: Long) = audioPlayer.seekTo(positionMs)

    fun setPlaybackRate(rate: Float) {
        audioPlayer.setPlaybackRate(rate)
        viewModelScope.launch { preferences.setPlaybackRate(rate) }
    }

    fun setSleepTimer(mode: SleepTimerMode) = audioPlayer.setSleepTimer(mode)

    fun togglePlayPause() {
        val snapshot = playback.value
        if (snapshot.isPlaying) {
            audioPlayer.pause()
        } else if (snapshot.playWhenReady || snapshot.surah != null) {
            audioPlayer.resume()
        } else {
            playOrToggle()
        }
    }

    fun downloadRange() {
        val state = uiState.value
        val moshaf = state.selectedMoshaf ?: return
        if (download.value?.running == true) return
        val lo = minOf(state.downloadFrom, state.downloadTo)
        val hi = maxOf(state.downloadFrom, state.downloadTo)
        val range = state.availableSurahs.filter { it in lo..hi }
        val missing = range.filter { !audioStore.has(moshaf.id, it) }
        if (missing.isEmpty()) return
        if (!PlaybackNetwork.isOnline(appContext)) {
            download.value = DownloadProgress(
                done = 0,
                total = missing.size,
                errorMessage = appContext.getString(R.string.error_offline),
                running = false,
            )
            return
        }
        viewModelScope.launch {
            download.value = DownloadProgress(done = 0, total = missing.size, running = true)
            missing.forEachIndexed { index, surah ->
                val result = audioStore.download(moshaf, surah)
                if (result.isFailure) {
                    download.value = DownloadProgress(
                        done = index,
                        total = missing.size,
                        currentSurah = surah,
                        errorMessage = appContext.getString(R.string.audio_download_failed),
                        running = false,
                    )
                    delay(3_200)
                    if (download.value?.running != true) download.value = null
                    return@launch
                }
                download.value = DownloadProgress(
                    done = index + 1,
                    total = missing.size,
                    currentSurah = surah,
                    running = true,
                )
            }
            download.value = DownloadProgress(done = missing.size, total = missing.size, running = false)
        }
    }

    fun isSurahCached(surah: Int): Boolean {
        val moshaf = uiState.value.selectedMoshaf ?: return false
        return audioStore.has(moshaf.id, surah)
    }

    fun filteredSurahs(): List<SurahInfo> {
        val state = uiState.value
        val raw = EasternArabic.parseDigits(state.query).trim()
        if (raw.isBlank()) return state.surahs
        val asNumber = raw.toIntOrNull()
        return state.surahs.filter { surah ->
            (asNumber != null && surah.number == asNumber) ||
                surah.nameArabic.contains(state.query.trim()) ||
                EasternArabic.format(surah.number).contains(state.query.trim())
        }
    }

    private fun MoshafEdition?.orEmptyContains(surah: Int): Boolean = this?.contains(surah) == true

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
