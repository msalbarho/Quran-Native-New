package com.quransunah.app.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.catalog.TranslationRegistry
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
import com.quransunah.app.domain.repository.TranslationRepository
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.share.AyahPlainText
import com.quransunah.app.share.AyahShareContent
import com.quransunah.app.share.AyahShareExporter
import com.quransunah.app.share.AyahShareVerse
import com.quransunah.app.share.ShareMushafLayout
import com.quransunah.app.share.ShareMushafWord
import com.quransunah.app.share.SharePageLines
import com.quransunah.app.share.QulRecitations
import com.quransunah.app.share.ShareSourceLine
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

private const val ALJUHANI_RECITER_ID = "62"

data class StudyUiState(
    val word: WordRecord? = null,
    val surahName: String = "",
    val ayahWords: List<QuranWord> = emptyList(),
    val ayahText: String = "",
    val pageNumber: Int = 1,
    val bookmarked: Boolean = false,
    val trackedForMemorization: Boolean = false,
    val reciters: List<AyahReciter> = emptyList(),
    val videoReciters: List<AyahReciter> = emptyList(),
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
    private val translationRepository: TranslationRepository,
    private val reciterCatalog: ReciterCatalog,
    private val preferences: UserPreferences,
    private val shareExporter: AyahShareExporter,
    val audioPlayer: AudioPlayerRepository,
    val fontManager: QcfFontManager,
) : ViewModel() {

    private fun wordSheetAyahReciters(): List<AyahReciter> =
        reciterCatalog.getAyahCapableReciters().filterNot { it.id == ALJUHANI_RECITER_ID }

    private fun videoMakerReciters(): List<AyahReciter> {
        val all = reciterCatalog.getAyahCapableReciters()
        return all.filter { QulRecitations.hasWordTimingCatalog(it.id) } +
            all.filterNot { QulRecitations.hasWordTimingCatalog(it.id) }
    }

    val playback: StateFlow<PlaybackSnapshot> = audioPlayer.snapshot

    private var bindJob: Job? = null
    private var tafsirJob: Job? = null
    private var activeBindKey: Pair<Int, Int>? = null

    private val _ui = MutableStateFlow(
        StudyUiState(
            reciters = wordSheetAyahReciters(),
            videoReciters = videoMakerReciters(),
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
            val reciters = wordSheetAyahReciters()
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
                    videoReciters = videoMakerReciters(),
                    surahReciters = surahReciters,
                    selectedReciterId = ayahReciter,
                    videoReciterId = videoReciter,
                    videoFromAyah = word.ayah,
                    videoToAyah = (word.ayah + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(SurahAyahCounts.ayahCount(word.surah)),
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

    /** Start the video range at the selected ayah, retaining the existing ten-ayah maximum. */
    fun prepareVideoMakerRange(word: WordRecord) {
        val max = SurahAyahCounts.ayahCount(word.surah).coerceAtLeast(1)
        val range = ShareMushafLayout.initialAyahRange(
            selectedAyah = word.ayah,
            surahAyahCount = max,
            maxAyat = AppConstants.SHARE_AYAH_RANGE_MAX,
        )
        _ui.update {
            it.copy(
                videoFromAyah = range.first,
                videoToAyah = range.last,
            )
        }
    }

    fun setVideoFrom(ayah: Int) {
        val word = _ui.value.word ?: return
        val max = SurahAyahCounts.ayahCount(word.surah)
        val from = ayah.coerceIn(1, max)
        val toMax = (from + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(max)
        _ui.update {
            it.copy(
                videoFromAyah = from,
                videoToAyah = toMax, // a new start always offers the full 10-ayah range within this surah
            )
        }
    }

    fun setVideoTo(ayah: Int) {
        val word = _ui.value.word ?: return
        val from = _ui.value.videoFromAyah.coerceIn(1, SurahAyahCounts.ayahCount(word.surah))
        val max = SurahAyahCounts.ayahCount(word.surah)
        val toMax = (from + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(max)
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

    fun playAyah(target: WordRecord? = null, onStarted: () -> Unit = {}) {
        val word = target ?: _ui.value.word ?: return
        val reciterId = _ui.value.selectedReciterId
        viewModelScope.launch {
            audioPlayer.playAyah(reciterId, word.surah, word.ayah)
                .onSuccess { onStarted() }
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
                    videoToAyah = (nextRecord.ayah + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(SurahAyahCounts.ayahCount(nextRecord.surah)),
                )
            }
            playWord(nextRecord)
            return nextRecord
        }
        _ui.update { it.copy(word = record) }
        playWord(record)
        return record
    }

    fun saveReadingPage(page: com.quransunah.app.domain.model.MushafPage?, onSaved: () -> Unit) {
        val first = page?.lines?.flatMap { it.words }?.firstOrNull { !it.isAyahMarker } ?: return
        viewModelScope.launch {
            val words = mushafRepository.getAyahWords(first.surah, first.ayah)
            bookmarkRepository.save(ReadingBookmark(
                id = ReadingBookmark.idFor(first.surah, first.ayah),
                surah = first.surah, ayah = first.ayah, pageNumber = page.pageNumber,
                wordId = first.id, wordIndex = first.wordIndex,
                ayahText = words.filter { !it.isAyahMarker }.joinToString(" ") { it.textHafs },
                savedAt = System.currentTimeMillis(),
            ))
            onSaved()
        }
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

    fun shareText(translationTag: String?) {
        viewModelScope.launch {
            val ui = _ui.value
            val content = shareContent(ui.videoFromAyah, ui.videoToAyah) ?: return@launch
            val locale = translationTag?.takeIf { it.isNotBlank() }
            val text = AyahPlainText.document(
                content.verses.map { verse ->
                    val translation = locale?.let {
                        translationRepository.getAyahTranslation(it, content.surah, verse.ayah)
                    }
                    AyahPlainText.block(content.surah, verse.ayah, verse.uthmanicText, translation)
                },
            )
            shareExporter.sharePlainText(text)
        }
    }

    fun shareImage(fromAyah: Int? = null, toAyah: Int? = null) {
        viewModelScope.launch {
            val content = shareContent(fromAyah, toAyah) ?: return@launch
            _ui.update { it.copy(sharing = true, shareError = null) }
            runCatching { shareExporter.shareImage(content) }
                .onFailure { error -> _ui.update { it.copy(shareError = error.message) } }
            _ui.update { it.copy(sharing = false) }
        }
    }

    suspend fun makerContent(): AyahShareContent? = shareContent(_ui.value.videoFromAyah, _ui.value.videoToAyah)
    val videoMakerExporter: AyahShareExporter get() = shareExporter

    fun shareVideo() {
        val reciterId = _ui.value.videoReciterId
        if (reciterId.isBlank()) {
            _ui.update { it.copy(shareError = SHARE_ERROR_NO_RECITER) }
            return
        }
        viewModelScope.launch {
            val content = shareContent(_ui.value.videoFromAyah, _ui.value.videoToAyah) ?: return@launch
            _ui.update { it.copy(sharing = true, shareError = null) }
            runCatching { shareExporter.shareVideo(content, reciterId) }
                .onFailure { error ->
                    _ui.update { it.copy(shareError = error.message ?: SHARE_ERROR_VIDEO_FAILED) }
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
            qcfLigature = fontManager.v4Glyphs(target.id).orEmpty(),
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
        val settings = preferences.settings.first()
        val translationTag = settings.translationLanguage
            ?.takeIf { TranslationRegistry.forLanguage(it) != null }
            ?: TranslationRegistry.forLanguage(settings.appLanguage)?.languageTag
            ?: "en"
        val surahMax = SurahAyahCounts.ayahCount(word.surah)
        val fromAyah = (from ?: word.ayah).coerceIn(1, surahMax)
        val rawTo = (to ?: word.ayah).coerceIn(1, surahMax)
        // Stay inside this surah only; never spill into the next surah.
        val toAyah = rawTo.coerceAtMost(fromAyah + AppConstants.SHARE_AYAH_RANGE_MAX - 1).coerceAtMost(surahMax).coerceAtLeast(fromAyah)
        val verses = SurahAyahCounts.range(word.surah, fromAyah, word.surah, toAyah)
            .take(AppConstants.SHARE_AYAH_RANGE_MAX)
            .map { (_, ayah) ->
            val words = if (ayah == word.ayah) ui.ayahWords else mushafRepository.getAyahWords(word.surah, ayah)
            val body = words.filter { !it.isAyahMarker }
            AyahShareVerse(
                ayah = ayah,
                pageNumber = mushafRepository.getPageForAyah(word.surah, ayah),
                qcfText = body.joinToString(" ") { fontManager.v4Glyphs(it.id).orEmpty() }.trim(),
                uthmanicText = body.joinToString(" ") { it.textHafs }.trim(),
                markerLigature = shareMarkerLigature(words),
                englishTranslation = translationRepository.getAyahTranslation(translationTag, word.surah, ayah),
            )
        }
        val pages = verses.map { it.pageNumber }.distinct().sorted()
        val mushafLines = ShareMushafLayout.cutLines(
            pages = pages.map { pageNumber ->
                val page = mushafRepository.getPage(pageNumber)
                SharePageLines(
                    pageNumber = pageNumber,
                    lines = page.lines.map { line ->
                        ShareSourceLine(
                            lineNumber = line.lineNumber,
                            // Keep only this surah's words so ayah 1..N never pulls
                            // the previous/next surah that shares the Madinah page.
                            words = line.words.filter { it.surah == word.surah }.map { token ->
                                ShareMushafWord(
                                    ayah = token.ayah,
                                    position = token.wordIndex,
                                    qcf = fontManager.v4Glyphs(token.id).orEmpty(),
                                    uthmanic = token.textHafs,
                                    isMarker = token.isAyahMarker,
                                )
                            },
                        )
                    },
                )
            },
            fromAyah = fromAyah,
            toAyah = toAyah,
        )
        return AyahShareContent(
            surah = word.surah,
            surahName = ui.surahName,
            fromAyah = fromAyah,
            toAyah = verses.lastOrNull()?.ayah ?: fromAyah,
            verses = verses,
            mushafLines = mushafLines,
            translationTag = translationTag,
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

internal const val SHARE_ERROR_NO_RECITER = "share_error:no_reciter"
internal const val SHARE_ERROR_VIDEO_FAILED = "share_error:video_failed"
