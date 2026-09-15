package com.quransunah.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.data.prefs.UserSettings
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.AyahSearchHit
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.domain.repository.AudioPlayerRepository
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.hydration.HydrationGate
import com.quransunah.app.hydration.HydrationState
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.mushaf.WordRecord
import com.quransunah.app.ui.theme.PaperPaletteId
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AppTab(val route: String) {
    Home("home"),
    Reading("reading"),
    Listening("listening"),
    Training("training"),
    ;

    companion object {
        fun fromStored(route: String): AppTab = when (route) {
            Home.route -> Home
            Listening.route, "audio" -> Listening
            Training.route -> Training
            else -> Reading
        }
    }
}

enum class ShellPicker {
    None,
    Index,
    LastPosition,
    Progress,
    Settings,
}

enum class StudyOverlay {
    None,
    Meaning,
    WordSheet,
}

data class ReadingHighlight(
    val wordId: Int? = null,
    val ayah: AyahRef? = null,
)

@HiltViewModel
class HolyQuranViewModel @Inject constructor(
    private val hydrationGate: HydrationGate,
    private val preferences: UserPreferences,
    private val mushafRepository: MushafRepository,
    val audioPlayer: AudioPlayerRepository,
    val fontManager: QcfFontManager,
) : ViewModel() {

    val hydration: StateFlow<HydrationState> = hydrationGate.state
    val playback: StateFlow<PlaybackSnapshot> = audioPlayer.snapshot
    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )
    val textSizeSp: StateFlow<Float> = preferences.textSizeSp.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppConstants.TEXT_MUSHAF_DEFAULT_SP,
    )

    private val _currentPage = MutableStateFlow(1)
    val currentPage: StateFlow<Int> = _currentPage.asStateFlow()

    private val _pageCache = MutableStateFlow<Map<Int, MushafPage>>(emptyMap())
    val pageCache: StateFlow<Map<Int, MushafPage>> = _pageCache.asStateFlow()

    private val _page = MutableStateFlow<MushafPage?>(null)
    val page: StateFlow<MushafPage?> = _page.asStateFlow()

    private val _selectedWord = MutableStateFlow<WordRecord?>(null)
    val selectedWord: StateFlow<WordRecord?> = _selectedWord.asStateFlow()

    private val _overlay = MutableStateFlow(StudyOverlay.None)
    val overlay: StateFlow<StudyOverlay> = _overlay.asStateFlow()

    private val _searchOpen = MutableStateFlow(false)
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    private val _jumpHighlight = MutableStateFlow<AyahRef?>(null)
    val jumpHighlight: StateFlow<AyahRef?> = _jumpHighlight.asStateFlow()

    /**
     * Highlight used by the mushaf pager. Distinct from [playback] so 250ms
     * position ticks do not redraw every Medina page.
     */
    val mushafHighlight: StateFlow<ReadingHighlight> = combine(
        playback,
        _jumpHighlight,
        _selectedWord,
        _overlay,
    ) { snap, jump, word, overlay ->
        highlightFrom(snap, jump, word, overlay)
    }.distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, ReadingHighlight())

    private val _pendingPagerPage = MutableStateFlow<Int?>(null)
    val pendingPagerPage: StateFlow<Int?> = _pendingPagerPage.asStateFlow()

    private val _chromeVisible = MutableStateFlow(true)
    val chromeVisible: StateFlow<Boolean> = _chromeVisible.asStateFlow()

    private val _surahsByNumber = MutableStateFlow<Map<Int, SurahInfo>>(emptyMap())
    val surahsByNumber: StateFlow<Map<Int, SurahInfo>> = _surahsByNumber.asStateFlow()

    private val _tab = MutableStateFlow(AppTab.Home)
    val tab: StateFlow<AppTab> = _tab.asStateFlow()

    private val _picker = MutableStateFlow(ShellPicker.None)
    val picker: StateFlow<ShellPicker> = _picker.asStateFlow()

    private val _indexTab = MutableStateFlow(QuranIndexTab.Surah)
    val indexTab: StateFlow<QuranIndexTab> = _indexTab.asStateFlow()

    private var highlightJob: Job? = null
    private val pageLoadJobs = HashMap<Int, Job>()
    private var followAudio = true
    private var lastPlaybackSessionKey: String? = null
    private var lastFollowedAyahKey: String? = null

    init {
        viewModelScope.launch {
            val restored = preferences.settings.first()
            _currentPage.value = restored.lastPage
            launch(Dispatchers.IO) {
                runCatching {
                    fontManager.ensureMaps()
                    fontManager.loadPageTypeface(_currentPage.value)
                    fontManager.loadSurahTitleTypeface()
                }
            }
            runCatching { hydrationGate.hydrate() }
            if (hydration.value is HydrationState.Ready) {
                ensurePage(_currentPage.value)
                ensurePage(_currentPage.value - 1)
                ensurePage(_currentPage.value + 1)
                audioPlayer.warmup()
                launch(Dispatchers.IO) {
                    loadChromeCatalog()
                }
            }
        }
        viewModelScope.launch {
            audioPlayer.snapshot.collect { snapshot -> followPlaybackIfNeeded(snapshot) }
        }
    }

    fun retryHydration() {
        viewModelScope.launch {
            runCatching { hydrationGate.retry() }
            if (hydration.value is HydrationState.Ready) {
                ensurePage(_currentPage.value)
                launch(Dispatchers.IO) {
                    loadChromeCatalog()
                }
            }
        }
    }

    fun selectTab(tab: AppTab) {
        val previousTab = _tab.value
        _tab.value = tab
        closePicker()
        if (tab == AppTab.Listening || tab == AppTab.Training) {
            _chromeVisible.value = true
        }
        if (tab != AppTab.Reading) {
            closeStudyOverlays()
        }
        if (tab != AppTab.Home) {
            viewModelScope.launch { preferences.setLastTab(tab.route) }
        }
        if (tab == AppTab.Reading) {
            lastFollowedAyahKey = null
            if (previousTab != AppTab.Training) {
                followPlaybackIfNeeded(playback.value)
            } else {
                followAudio = false
            }
        }
    }

    fun openIndexPicker(tab: QuranIndexTab = QuranIndexTab.Surah) {
        _searchOpen.value = false
        closeStudyOverlays()
        _tab.value = AppTab.Reading
        _chromeVisible.value = true
        _indexTab.value = tab
        _picker.value = ShellPicker.Index
        viewModelScope.launch { preferences.setLastTab(AppTab.Reading.route) }
    }

    fun jumpToIndexPage(jump: IndexJump) {
        followAudio = false
        closeSearch()
        closePicker()
        closeStudyOverlays()
        _tab.value = AppTab.Reading
        _chromeVisible.value = true
        val clamped = jump.pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        val cached = _pageCache.value[clamped]
        if (cached != null) {
            _page.value = cached
            _currentPage.value = clamped
            _pendingPagerPage.value = clamped
            viewModelScope.launch(Dispatchers.Default) {
                preferences.setLastPage(clamped)
                ensurePage(clamped)
                ensurePage(clamped - 1)
                ensurePage(clamped + 1)
                ensurePage(clamped - 2)
                ensurePage(clamped + 2)
                prunePageCache(clamped)
            }
        } else {
            viewModelScope.launch(Dispatchers.Default) {
                launch(Dispatchers.IO) {
                    runCatching { fontManager.loadPageTypeface(clamped) }
                }
                ensurePage(clamped)
                _currentPage.value = clamped
                _pendingPagerPage.value = clamped
                preferences.setLastPage(clamped)
                ensurePage(clamped - 1)
                ensurePage(clamped + 1)
                prunePageCache(clamped)
            }
        }
        if (jump.hasAyah) {
            val ref = AyahRef(jump.surah, jump.ayah)
            _jumpHighlight.value = ref
            highlightJob?.cancel()
            highlightJob = viewModelScope.launch {
                delay(AppConstants.AYAH_JUMP_HIGHLIGHT_MS)
                if (_jumpHighlight.value == ref) {
                    _jumpHighlight.value = null
                }
            }
        }
        viewModelScope.launch { preferences.setLastTab(AppTab.Reading.route) }
    }

    fun openLastPositionPicker() {
        _searchOpen.value = false
        _picker.value = ShellPicker.LastPosition
    }

    fun openSettingsPicker() {
        _searchOpen.value = false
        _picker.value = ShellPicker.Settings
    }

    fun openProgressPicker() {
        _searchOpen.value = false
        _picker.value = ShellPicker.Progress
    }

    fun markTrainingHintDone() {
        viewModelScope.launch { preferences.setTrainingHintDone() }
    }

    fun closePicker() {
        _picker.value = ShellPicker.None
    }

    fun toggleChrome() {
        if (_tab.value == AppTab.Listening) return
        if (_overlay.value != StudyOverlay.None) {
            closeStudyOverlays()
            return
        }
        _chromeVisible.value = !_chromeVisible.value
    }

    fun goToPage(pageNumber: Int) {
        val clamped = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        _currentPage.value = clamped
        val cached = _pageCache.value[clamped]
        if (cached != null) _page.value = cached
        viewModelScope.launch(Dispatchers.Default) {
            preferences.setLastPage(clamped)
            ensurePage(clamped)
            ensurePage(clamped - 1)
            ensurePage(clamped + 1)
            prunePageCache(clamped)
        }
    }

    fun ensurePageLoaded(pageNumber: Int) {
        val clamped = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        if (_pageCache.value.containsKey(clamped)) return
        if (pageLoadJobs[clamped]?.isActive == true) return
        pageLoadJobs[clamped] = viewModelScope.launch(Dispatchers.Default) {
            ensurePage(clamped)
        }.also { job ->
            job.invokeOnCompletion { pageLoadJobs.remove(clamped) }
        }
    }

    fun consumePendingPagerPage() {
        _pendingPagerPage.value = null
    }

    fun onUserPagerSettled(pageNumber: Int) {
        if (_pendingPagerPage.value != null) return
        val clamped = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        if (clamped != _currentPage.value) {
            followAudio = false
        }
        goToPage(clamped)
    }

    fun openSearch() {
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
    }

    fun onWordShortTap(word: WordRecord) {
        if (word.meaning.isNullOrBlank()) return
        closePicker()
        _selectedWord.value = word
        _overlay.value = StudyOverlay.Meaning
    }

    fun onMushafWordTap(word: WordRecord) {
        // Word pronunciation is a WordSheet action only. A leftover WORD
        // playback domain must never turn page taps into playWord/seekToWord.
        if (!word.meaning.isNullOrBlank()) {
            onWordShortTap(word)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val meaning = runCatching { mushafRepository.meaningForWord(word.id) }.getOrNull()
            if (!meaning.isNullOrBlank()) {
                onWordShortTap(word.copy(meaning = meaning))
            } else {
                toggleChrome()
            }
        }
    }

    fun startFollowingPlayback() {
        followAudio = true
        lastFollowedAyahKey = null
        followPlaybackIfNeeded(playback.value)
    }

    fun skipPlaybackVerse(delta: Int) {
        val snap = playback.value
        if (snap.domain != PlaybackDomain.SURAH && snap.domain != PlaybackDomain.AYAH) return
        val surah = snap.surah ?: return
        val ayah = snap.ayah?.takeIf { it > 0 } ?: 1
        val target = if (delta > 0) {
            SurahAyahCounts.next(surah, ayah)
        } else {
            SurahAyahCounts.previous(surah, ayah)
        } ?: return
        followAudio = true
        lastFollowedAyahKey = null
        viewModelScope.launch { audioPlayer.seekToAyah(target.first, target.second) }
    }

    fun onWordLongPress(word: WordRecord) {
        closePicker()
        _selectedWord.value = word
        _overlay.value = StudyOverlay.WordSheet
        _chromeVisible.value = true
    }

    fun closeStudyOverlays() {
        endWordPronunciationContext()
        _overlay.value = StudyOverlay.None
        _selectedWord.value = null
    }

    /**
     * Drops transient "لفظ الكلمة" state: selected word + WORD-domain playback.
     * Surah/ayah recitation is left running.
     */
    private fun endWordPronunciationContext() {
        if (playback.value.domain == PlaybackDomain.WORD) {
            audioPlayer.stop()
        }
    }

    fun jumpToSearchHit(hit: AyahSearchHit) {
        closeSearch()
        closePicker()
        closeStudyOverlays()
        jumpToAyah(hit.surah, hit.ayah, hit.pageNumber)
    }

    fun jumpToBookmark(bookmark: ReadingBookmark) {
        closePicker()
        closeStudyOverlays()
        jumpToAyah(bookmark.surah, bookmark.ayah, bookmark.pageNumber)
    }

    fun jumpToPageNumber(page: Int) {
        if (_tab.value != AppTab.Reading && _tab.value != AppTab.Training) {
            selectTab(AppTab.Reading)
        }
        followAudio = false
        _chromeVisible.value = true
        val clamped = page.coerceIn(1, AppConstants.TOTAL_PAGES)
        _pendingPagerPage.value = clamped
        goToPage(clamped)
    }

    fun jumpToAyah(surah: Int, ayah: Int, pageNumber: Int) {
        selectTab(AppTab.Reading)
        followAudio = false
        _chromeVisible.value = true
        val clamped = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        _pendingPagerPage.value = clamped
        goToPage(clamped)
        val ref = AyahRef(surah, ayah)
        _jumpHighlight.value = ref
        highlightJob?.cancel()
        highlightJob = viewModelScope.launch {
            delay(AppConstants.AYAH_JUMP_HIGHLIGHT_MS)
            if (_jumpHighlight.value == ref) {
                _jumpHighlight.value = null
            }
        }
    }

    fun openWordSheet(word: WordRecord) {
        _selectedWord.value = word
        _overlay.value = StudyOverlay.WordSheet
        viewModelScope.launch {
            val page = mushafRepository.getPageForAyah(word.surah, word.ayah)
            if (page != _currentPage.value) {
                _pendingPagerPage.value = page
                goToPage(page)
            }
        }
    }

    fun readingHighlight(playback: PlaybackSnapshot): ReadingHighlight =
        highlightFrom(playback, _jumpHighlight.value, _selectedWord.value, _overlay.value)

    private fun highlightFrom(
        playback: PlaybackSnapshot,
        jump: AyahRef?,
        selectedWord: WordRecord?,
        overlay: StudyOverlay,
    ): ReadingHighlight {
        val audioAyah = when (playback.domain) {
            PlaybackDomain.AYAH, PlaybackDomain.SURAH -> {
                val surah = playback.surah
                val ayah = playback.ayah
                if (surah != null && ayah != null && ayah > 0) AyahRef(surah, ayah) else null
            }
            else -> null
        }
        val sheetWord = selectedWord.takeIf { overlay != StudyOverlay.None }
        return ReadingHighlight(
            wordId = playback.wordId.takeIf { playback.domain == PlaybackDomain.WORD }
                ?: sheetWord?.id,
            ayah = jump ?: audioAyah ?: sheetWord?.let { AyahRef(it.surah, it.ayah) },
        )
    }

    private fun followPlaybackIfNeeded(snapshot: PlaybackSnapshot) {
        val sessionKey = when (snapshot.domain) {
            PlaybackDomain.SURAH -> "s:${snapshot.moshafId}"
            PlaybackDomain.AYAH -> "a:${snapshot.reciterId}"
            PlaybackDomain.WORD -> "w"
            PlaybackDomain.IDLE -> null
        }
        if (sessionKey != lastPlaybackSessionKey) {
            lastPlaybackSessionKey = sessionKey
            lastFollowedAyahKey = null
            if (sessionKey != null) followAudio = true
        }
        if (!followAudio || _tab.value != AppTab.Reading) return
        if (snapshot.domain == PlaybackDomain.IDLE) return
        val surah = snapshot.surah ?: return
        val ayah = when (snapshot.domain) {
            PlaybackDomain.SURAH -> snapshot.ayah?.takeIf { it > 0 } ?: 1
            else -> snapshot.ayah?.takeIf { it > 0 } ?: return
        }
        val key = "${snapshot.domain}:$surah:$ayah"
        if (key == lastFollowedAyahKey) return
        lastFollowedAyahKey = key
        viewModelScope.launch {
            val page = mushafRepository.getPageForAyah(surah, ayah)
            if (!followAudio || _tab.value != AppTab.Reading) return@launch
            if (page != _currentPage.value) {
                _pendingPagerPage.value = page
                goToPage(page)
            }
        }
    }

    fun setPalette(id: PaperPaletteId) {
        viewModelScope.launch { preferences.setPalette(id) }
    }

    fun setNightMode(night: Boolean) {
        viewModelScope.launch { preferences.setNightMode(night) }
    }

    fun setMedinaMode(medina: Boolean) {
        viewModelScope.launch { preferences.setMedinaMode(medina) }
    }

    private suspend fun ensurePage(pageNumber: Int) {
        val clamped = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        val cached = _pageCache.value[clamped]
        if (cached != null) {
            if (clamped == _currentPage.value) _page.value = cached
            return
        }
        runCatching { mushafRepository.getPage(clamped) }
            .onSuccess { loaded ->
                _pageCache.update { current ->
                    val next = current + (clamped to loaded)
                    if (next.size <= AppConstants.PAGE_DATA_CACHE_SIZE) {
                        next
                    } else {
                        pruneKeys(next, _currentPage.value, clamped)
                    }
                }
                if (clamped == _currentPage.value) {
                    _page.value = loaded
                }
            }
    }

    private suspend fun loadChromeCatalog() {
        runCatching { mushafRepository.prefetchHeavyData() }
        runCatching {
            _surahsByNumber.value = mushafRepository.getSurahs().associateBy { it.number }
        }
        runCatching { mushafRepository.getDivisions() }
        runCatching { mushafRepository.getQuarters() }
        runCatching { mushafRepository.getSajdas() }
        applyMarkersToCache()
    }

    private fun applyMarkersToCache() {
        _pageCache.update { current ->
            current.mapValues { (_, page) -> mushafRepository.overlayMarkers(page) }
        }
        _page.value = _page.value?.let { mushafRepository.overlayMarkers(it) }
    }

    private fun prunePageCache(center: Int) {
        _pageCache.update { current ->
            if (current.size <= AppConstants.PAGE_DATA_CACHE_SIZE) current
            else pruneKeys(current, center, center)
        }
        val keep = pruneKeepRange(center)
        val extra = _pageCache.value.keys
        val stale = pageLoadJobs.keys.filter { it !in extra && it !in keep }
        for (page in stale) {
            pageLoadJobs.remove(page)?.cancel()
        }
    }

    private fun pruneKeepRange(center: Int): IntRange =
        (center - AppConstants.PAGE_CACHE_RADIUS)..(center + AppConstants.PAGE_CACHE_RADIUS)

    private fun pruneKeys(
        current: Map<Int, MushafPage>,
        center: Int,
        required: Int,
    ): Map<Int, MushafPage> {
        val radius = pruneKeepRange(center)
        val extras = current.keys
            .filter { it !in radius && it != required }
            .sortedBy { kotlin.math.abs(it - center) }
        val reserved = current.keys.count { it in radius || it == required }
        val budget = (AppConstants.PAGE_DATA_CACHE_SIZE - reserved).coerceAtLeast(0)
        val extraKeep = extras.take(budget).toSet()
        return current.filterKeys { it in radius || it == required || it in extraKeep }
    }

    override fun onCleared() {
        pageLoadJobs.values.forEach { it.cancel() }
        pageLoadJobs.clear()
        _pageCache.value = emptyMap()
        super.onCleared()
    }
}
