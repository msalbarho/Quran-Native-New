package com.quransunah.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.domain.repository.AudioPlayerRepository
import com.quransunah.app.domain.repository.BookmarkRepository
import com.quransunah.app.domain.repository.MemorizationRepository
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.domain.repository.TafsirRepository
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.share.AyahShareContent
import com.quransunah.app.share.AyahShareExporter
import com.quransunah.app.share.AyahShareVerse
import com.quransunah.app.ui.mushaf.SajdahAyah
import com.quransunah.app.ui.mushaf.WordRecord
import com.quransunah.app.ui.mushaf.toWordRecord
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class StudyUiState(
    val word: WordRecord? = null,
    val surahName: String = "",
    val ayahWords: List<QuranWord> = emptyList(),
    val ayahText: String = "",
    val pageNumber: Int = 1,
    val bookmarked: Boolean = false,
    val trackedForMemorization: Boolean = false,
    val reciters: List<AyahReciter> = emptyList(),
    val surahReciters: List<SurahReciter> = emptyList(),
    val selectedReciterId: String = AppConstants.DEFAULT_AYAH_RECITER_ID,
    val videoReciterId: String = AppConstants.DEFAULT_AYAH_RECITER_ID,
    val videoFromAyah: Int = 1,
    val videoToAyah: Int = 1,
    val tafsirOpen: Boolean = false,
    val tafsirLoading: Boolean = false,
    val tafsirText: String? = null,
    val tafsirError: String? = null,
    val tafsirSizeSp: Float = AppConstants.TAFSIR_DEFAULT_SP,
    val sharing: Boolean = false,
    val shareError: String? = null,
    val reciterMenuOpen: Boolean = false,
)

@HiltViewModel
class StudyViewModel @Inject constructor(
    private val mushafRepository: MushafRepository,
    private val bookmarkRepository: BookmarkRepository,
    private val memorizationRepository: MemorizationRepository,
    private val tafsirRepository: TafsirRepository,
    private val reciterCatalog: ReciterCatalog,
    private val preferences: UserPreferences,
    private val shareExporter: AyahShareExporter,
    val audioPlayer: AudioPlayerRepository,
    val fontManager: QcfFontManager,
) : ViewModel() {

    val playback: StateFlow<PlaybackSnapshot> = audioPlayer.snapshot

    private var bindJob: Job? = null
    private var tafsirJob: Job? = null
    private var activeBindKey: Pair<Int, Int>? = null

    private val _ui = MutableStateFlow(
        StudyUiState(
            reciters = reciterCatalog.getAyahCapableReciters(),
            surahReciters = reciterCatalog.surahReciters(),
        ),
    )
    val uiState: StateFlow<StudyUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            preferences.settings.collect { settings ->
                _ui.update { it.copy(tafsirSizeSp = settings.tafsirSizeSp) }
            }
        }
        viewModelScope.launch {
            bookmarkRepository.observeBookmarks().collect { list ->
                val word = _ui.value.word ?: return@collect
                val saved = list.any { bookmark -> bookmark.surah == word.surah && bookmark.ayah == word.ayah }
                if (_ui.value.bookmarked != saved) {
                    _ui.update { it.copy(bookmarked = saved) }
                }
            }
        }
        viewModelScope.launch {
            memorizationRepository.observeItems().collect { list ->
                val word = _ui.value.word ?: return@collect
                val tracked = list.any { item -> item.surah == word.surah && item.ayah == word.ayah }
                if (_ui.value.trackedForMemorization != tracked) {
                    _ui.update { it.copy(trackedForMemorization = tracked) }
                }
            }
        }
    }

    val bookmarks: StateFlow<List<ReadingBookmark>> = bookmarkRepository.observeBookmarks().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    fun bind(word: WordRecord) {
        bindJob?.cancel()
        tafsirJob?.cancel()
        audioPlayer.warmup()
        val current = _ui.value
        if (current.word != null &&
            current.word.surah == word.surah &&
            current.word.ayah == word.ayah &&
            current.ayahWords.isNotEmpty()
        ) {
            val matched = current.ayahWords.firstOrNull { it.id == word.id }?.toWordRecord() ?: word
            _ui.update { it.copy(word = matched, shareError = null) }
            return
        }
        val bindKey = word.surah to word.ayah
        activeBindKey = bindKey
        _ui.update { it.copy(tafsirOpen = false, tafsirLoading = false, tafsirText = null, tafsirError = null) }
        bindJob = viewModelScope.launch {
            val settings = preferences.settings.first()
            val reciters = reciterCatalog.getAyahCapableReciters()
            val surahReciters = reciterCatalog.surahReciters()
            val ayahReciter = reciters.firstOrNull { it.id == settings.lastAyahReciterId }?.id
                ?: reciterCatalog.defaultAyahReciter().id
            val videoReciter = reciters.firstOrNull { it.id == settings.lastVideoReciterId }?.id
                ?: ayahReciter
            val surahReciter = surahReciters.firstOrNull { it.id == settings.lastReciterId }?.id
                ?: reciterCatalog.defaultSurahReciter()?.id
                ?: AppConstants.DEFAULT_SURAH_RECITER_ID
            val words = mushafRepository.getAyahWords(word.surah, word.ayah)
            val surahs = mushafRepository.getSurahs()
            val page = mushafRepository.getPageForAyah(word.surah, word.ayah)
            val text = words.filter { !it.isAyahMarker }.joinToString(" ") { it.textHafs }.trim()
            val resolved = words.firstOrNull { it.id == word.id && !it.isAyahMarker }?.toWordRecord()
                ?: words.firstOrNull { !it.isAyahMarker }?.toWordRecord()
                ?: word
            val bookmarked = bookmarkRepository.isSaved(word.surah, word.ayah)
            val trackedForMemorization = memorizationRepository.isTracked(word.surah, word.ayah)
            if (activeBindKey != bindKey) return@launch
            _ui.update {
                it.copy(
                    word = resolved,
                    surahName = surahs.firstOrNull { s -> s.number == word.surah }?.nameArabic.orEmpty(),
                    ayahWords = words,
                    ayahText = text,
                    pageNumber = page,
                    bookmarked = bookmarked,
                    trackedForMemorization = trackedForMemorization,
                    reciters = reciters,
                    surahReciters = surahReciters,
                    selectedReciterId = ayahReciter,
                    videoReciterId = videoReciter,
                    videoFromAyah = word.ayah,
                    videoToAyah = word.ayah,
                    tafsirOpen = false,
                    tafsirText = null,
                    tafsirError = null,
                    tafsirSizeSp = settings.tafsirSizeSp,
                    shareError = null,
                )
            }
        }
    }

    fun selectAyahWord(ayahWord: QuranWord): WordRecord? {
        if (ayahWord.isAyahMarker) return null
        val record = ayahWord.toWordRecord()
        _ui.update { it.copy(word = record) }
        return record
    }

    fun selectReciter(id: String) {
        _ui.update { it.copy(selectedReciterId = id, reciterMenuOpen = false) }
        viewModelScope.launch { preferences.setLastAyahReciterId(id) }
    }

    fun switchAyahReciterAndPlay(id: String) {
        selectReciter(id)
        val snap = playback.value
        val word = _ui.value.word
        val surah = snap.surah ?: word?.surah ?: return
        val ayah = snap.ayah?.takeIf { it > 0 } ?: word?.ayah ?: return
        viewModelScope.launch {
            audioPlayer.stop()
            audioPlayer.playAyah(id, surah, ayah)
        }
    }

    fun selectVideoReciter(id: String) {
        _ui.update { it.copy(videoReciterId = id) }
        viewModelScope.launch { preferences.setLastVideoReciterId(id) }
    }

    fun setReciterMenuOpen(open: Boolean) {
        _ui.update { it.copy(reciterMenuOpen = open) }
    }

    fun setVideoFrom(ayah: Int) {
        val word = _ui.value.word ?: return
        val max = SurahAyahCounts.ayahCount(word.surah)
        val from = ayah.coerceIn(1, max)
        val toMax = (from + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(max)
        _ui.update {
            it.copy(
                videoFromAyah = from,
                videoToAyah = it.videoToAyah.coerceIn(from, toMax),
            )
        }
    }

    fun setVideoTo(ayah: Int) {
        val word = _ui.value.word ?: return
        val from = _ui.value.videoFromAyah
        val max = SurahAyahCounts.ayahCount(word.surah)
        val toMax = (from + AppConstants.SHARE_AYAH_RANGE_MAX).coerceAtMost(max)
        _ui.update { it.copy(videoToAyah = ayah.coerceIn(from, toMax)) }
    }

    fun playWord(target: WordRecord? = null) {
        val word = target ?: _ui.value.word ?: return
        viewModelScope.launch {
            val snap = playback.value
            if (snap.domain == PlaybackDomain.WORD && snap.wordId == word.id && snap.isPlaying) {
                audioPlayer.pause()
            } else if (snap.domain == PlaybackDomain.WORD && snap.wordId == word.id) {
                audioPlayer.resume()
            } else {
                audioPlayer.playWord(word.id, word.surah, word.ayah, word.position)
            }
        }
    }

    fun playAyah() {
        val word = _ui.value.word ?: return
        val reciterId = _ui.value.selectedReciterId
        viewModelScope.launch {
            audioPlayer.playAyah(reciterId, word.surah, word.ayah)
        }
    }

    suspend fun stepWord(delta: Int): WordRecord? {
        val ui = _ui.value
        val word = ui.word ?: return null
        val body = ui.ayahWords.filter { !it.isAyahMarker }
        val index = body.indexOfFirst { it.id == word.id }.let { if (it < 0) 0 else it }
        val nextIndex = index + delta
        val record = if (nextIndex in body.indices) {
            body[nextIndex].toWordRecord()
        } else {
            val adjacent = if (delta < 0) {
                SurahAyahCounts.previous(word.surah, word.ayah)
            } else {
                SurahAyahCounts.next(word.surah, word.ayah)
            } ?: return null
            val words = mushafRepository.getAyahWords(adjacent.first, adjacent.second)
            val pick = if (delta < 0) {
                words.lastOrNull { !it.isAyahMarker }
            } else {
                words.firstOrNull { !it.isAyahMarker }
            } ?: return null
            val text = words.filter { !it.isAyahMarker }.joinToString(" ") { it.textHafs }.trim()
            val page = mushafRepository.getPageForAyah(adjacent.first, adjacent.second)
            val bookmarked = bookmarkRepository.isSaved(adjacent.first, adjacent.second)
            val trackedForMemorization = memorizationRepository.isTracked(adjacent.first, adjacent.second)
            val nextRecord = pick.toWordRecord()
            _ui.update {
                it.copy(
                    word = nextRecord,
                    ayahWords = words,
                    ayahText = text,
                    pageNumber = page,
                    bookmarked = bookmarked,
                    trackedForMemorization = trackedForMemorization,
                    videoFromAyah = nextRecord.ayah,
                    videoToAyah = nextRecord.ayah,
                )
            }
            playWord(nextRecord)
            return nextRecord
        }
        _ui.update { it.copy(word = record) }
        playWord(record)
        return record
    }

    fun toggleBookmark() {
        val ui = _ui.value
        val word = ui.word ?: return
        viewModelScope.launch {
            val id = ReadingBookmark.idFor(word.surah, word.ayah)
            if (ui.bookmarked) {
                bookmarkRepository.delete(id)
                _ui.update { it.copy(bookmarked = false) }
            } else {
                bookmarkRepository.save(
                    ReadingBookmark(
                        id = id,
                        surah = word.surah,
                        ayah = word.ayah,
                        pageNumber = ui.pageNumber,
                        wordId = word.id,
                        wordIndex = word.position,
                        ayahText = ui.ayahText,
                        savedAt = System.currentTimeMillis(),
                    ),
                )
                _ui.update { it.copy(bookmarked = true) }
            }
        }
    }

    fun addCurrentAyahToMemorization() {
        val ui = _ui.value
        val word = ui.word ?: return
        if (ui.trackedForMemorization) return
        viewModelScope.launch {
            val id = MemorizationItem.idFor(word.surah, word.ayah)
            val now = System.currentTimeMillis()
            memorizationRepository.track(
                MemorizationItem(
                    id = id,
                    surah = word.surah,
                    ayah = word.ayah,
                    pageNumber = ui.pageNumber,
                    ayahText = ui.ayahText,
                    state = MemorizationState.LEARNING,
                    reviewCount = 0,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
            _ui.update { it.copy(trackedForMemorization = true) }
        }
    }

    fun openTafsir() {
        val word = _ui.value.word ?: return
        val key = word.surah to word.ayah
        tafsirJob?.cancel()
        _ui.update { it.copy(tafsirOpen = true, tafsirLoading = true, tafsirError = null) }
        tafsirJob = viewModelScope.launch {
            val text = runCatching { tafsirRepository.getAyahTafsir(word.surah, word.ayah) }
                .onFailure { error ->
                    if (_ui.value.tafsirOpen && _ui.value.word?.let { it.surah to it.ayah } == key) {
                        _ui.update {
                            it.copy(tafsirLoading = false, tafsirError = error.message, tafsirText = null)
                        }
                    }
                }
                .getOrNull()
            if (_ui.value.tafsirOpen && _ui.value.word?.let { it.surah to it.ayah } == key) {
                _ui.update {
                    it.copy(
                        tafsirLoading = false,
                        tafsirText = text,
                        tafsirError = if (text.isNullOrBlank()) it.tafsirError else null,
                    )
                }
            }
        }
    }

    fun closeTafsir() {
        tafsirJob?.cancel()
        tafsirJob = null
        _ui.update { it.copy(tafsirOpen = false, tafsirText = null, tafsirError = null, tafsirLoading = false) }
        tafsirRepository.release()
    }

    fun shareImage() {
        viewModelScope.launch {
            val content = shareContent() ?: return@launch
            _ui.update { it.copy(sharing = true, shareError = null) }
            runCatching { shareExporter.shareImage(content) }
                .onFailure { error -> _ui.update { it.copy(shareError = error.message) } }
            _ui.update { it.copy(sharing = false) }
        }
    }

    fun shareVideo() {
        val reciterId = _ui.value.videoReciterId
        if (reciterId.isBlank()) {
            _ui.update { it.copy(shareError = "اختر قارئاً لإنشاء الفيديو") }
            return
        }
        viewModelScope.launch {
            val content = shareContent(_ui.value.videoFromAyah, _ui.value.videoToAyah) ?: return@launch
            _ui.update { it.copy(sharing = true, shareError = null) }
            runCatching { shareExporter.shareVideo(content, reciterId) }
                .onFailure { error ->
                    _ui.update { it.copy(shareError = error.message ?: "تعذّرت مشاركة الفيديو") }
                }
            _ui.update { it.copy(sharing = false) }
        }
    }

    suspend fun stepAyah(delta: Int): WordRecord? {
        val word = _ui.value.word ?: return null
        val next = if (delta < 0) {
            SurahAyahCounts.previous(word.surah, word.ayah)
        } else {
            SurahAyahCounts.next(word.surah, word.ayah)
        } ?: return null
        val words = mushafRepository.getAyahWords(next.first, next.second)
        val target = words.firstOrNull { !it.isAyahMarker } ?: words.firstOrNull() ?: return null
        return WordRecord(
            id = target.id,
            surah = target.surah,
            ayah = target.ayah,
            position = target.wordIndex,
            uthmanic = target.textHafs,
            qcfLigature = target.textLigature,
            isAyahMarker = target.isAyahMarker,
            meaning = target.meaning,
        )
    }

    fun onDismiss() {
        bindJob?.cancel()
        bindJob = null
        activeBindKey = null
        closeTafsir()
        if (playback.value.domain == PlaybackDomain.WORD) {
            audioPlayer.stop()
        }
        _ui.update { it.copy(word = null, reciterMenuOpen = false) }
    }

    private suspend fun shareContent(from: Int? = null, to: Int? = null): AyahShareContent? {
        val ui = _ui.value
        val word = ui.word ?: return null
        val fromAyah = from ?: word.ayah
        val rawTo = to ?: word.ayah
        val toAyah = rawTo.coerceAtMost(fromAyah + AppConstants.SHARE_AYAH_RANGE_MAX - 1)
        val verses = SurahAyahCounts.range(word.surah, fromAyah, word.surah, toAyah)
            .take(AppConstants.SHARE_AYAH_RANGE_MAX)
            .map { (_, ayah) ->
            val words = if (ayah == word.ayah) ui.ayahWords else mushafRepository.getAyahWords(word.surah, ayah)
            val body = words.filter { !it.isAyahMarker }
            AyahShareVerse(
                ayah = ayah,
                pageNumber = mushafRepository.getPageForAyah(word.surah, ayah),
                qcfText = body.joinToString(" ") { it.textLigature.ifBlank { it.textHafs } }.trim(),
                uthmanicText = body.joinToString(" ") { it.textHafs }.trim(),
                markerLigature = shareMarkerLigature(words),
            )
        }
        return AyahShareContent(
            surah = word.surah,
            surahName = ui.surahName,
            fromAyah = fromAyah,
            toAyah = verses.lastOrNull()?.ayah ?: fromAyah,
            verses = verses,
        )
    }

    private fun shareMarkerLigature(words: List<QuranWord>): String? {
        val markerIndex = words.indexOfLast { it.isAyahMarker }
        if (markerIndex < 0) return null
        val records = words.map { it.toWordRecord() }
        val marker = records[markerIndex]
        val display = marker.displayGlyph.ifBlank { return null }
        val previous = records.getOrNull(markerIndex - 1)
        val number = SajdahAyah.numberLigature(marker, previous)
        // LTR storage order matches Medina canvas: number then sajdah/mark glyph.
        // visualPua() reverses for RTL reading on the share canvas.
        return if (number.isNullOrEmpty()) display else number + display
    }

    override fun onCleared() {
        bindJob?.cancel()
        tafsirJob?.cancel()
        tafsirRepository.release()
        super.onCleared()
    }
}
