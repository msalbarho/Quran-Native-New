package com.quransunah.app.ui.bookmarks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.domain.repository.BookmarkRepository
import com.quransunah.app.domain.repository.MushafRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BookmarkListItem(
    val bookmark: ReadingBookmark,
    val surahName: String,
)

data class BookmarkTarget(
    val pageNumber: Int = 1,
    val surah: Int = 1,
    val ayah: Int = 1,
    val wordId: Int = 0,
    val wordIndex: Int = 0,
)

data class BookmarksUiState(
    val items: List<BookmarkListItem> = emptyList(),
    val target: BookmarkTarget = BookmarkTarget(),
    val targetSurahName: String = "",
    val currentSaved: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class BookmarksViewModel @Inject constructor(
    private val bookmarkRepository: BookmarkRepository,
    private val mushafRepository: MushafRepository,
) : ViewModel() {

    private val surahs = flow {
        emit(mushafRepository.getSurahs())
    }

    private val _target = MutableStateFlow(BookmarkTarget())

    val currentSaved: StateFlow<Boolean> = combine(
        bookmarkRepository.observeBookmarks(),
        _target,
    ) { bookmarks, target ->
        bookmarks.any { it.surah == target.surah && it.ayah == target.ayah }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<BookmarksUiState> = combine(
        bookmarkRepository.observeBookmarks(),
        surahs,
        _target,
    ) { bookmarks, catalog, target ->
        val names = catalog.associate { it.number to it.nameArabic }
        BookmarksUiState(
            items = bookmarks.map { bookmark ->
                BookmarkListItem(
                    bookmark = bookmark,
                    surahName = names[bookmark.surah].orEmpty(),
                )
            },
            target = target,
            targetSurahName = names[target.surah].orEmpty(),
            currentSaved = bookmarks.any { it.surah == target.surah && it.ayah == target.ayah },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BookmarksUiState())

    fun bindContext(page: MushafPage?, preferred: AyahRef?) {
        val word = resolveWord(page, preferred)
        _target.value = BookmarkTarget(
            pageNumber = page?.pageNumber ?: 1,
            surah = word?.surah ?: preferred?.surah ?: 1,
            ayah = word?.ayah ?: preferred?.ayah ?: 1,
            wordId = word?.id ?: 0,
            wordIndex = word?.wordIndex ?: 0,
        )
    }

    fun bindContext(pageNumber: Int, preferred: AyahRef?) {
        if (preferred != null) {
            _target.value = BookmarkTarget(
                pageNumber = pageNumber,
                surah = preferred.surah,
                ayah = preferred.ayah,
            )
            return
        }
        _target.updatePageOnly(pageNumber)
    }

    fun toggleCurrent() {
        val target = _target.value
        viewModelScope.launch {
            val id = ReadingBookmark.idFor(target.surah, target.ayah)
            if (bookmarkRepository.isSaved(target.surah, target.ayah)) {
                bookmarkRepository.delete(id)
                return@launch
            }
            val words = runCatching {
                mushafRepository.getAyahWords(target.surah, target.ayah)
            }.getOrDefault(emptyList())
            bookmarkRepository.save(
                ReadingBookmark(
                    id = id,
                    surah = target.surah,
                    ayah = target.ayah,
                    pageNumber = target.pageNumber,
                    wordId = target.wordId,
                    wordIndex = target.wordIndex,
                    ayahText = buildAyahText(words),
                    savedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { bookmarkRepository.delete(id) }
    }

    private fun resolveWord(page: MushafPage?, preferred: AyahRef?): QuranWord? {
        val words = page?.lines.orEmpty().asSequence().flatMap { it.words.asSequence() }
            .filter { it.surah > 0 && it.ayah > 0 }
        if (preferred != null) {
            words.firstOrNull { it.surah == preferred.surah && it.ayah == preferred.ayah }?.let { return it }
        }
        return words.firstOrNull()
    }

    private fun buildAyahText(words: List<QuranWord>): String =
        words.joinToString(" ") { it.textHafs }
            .replace(Regex("\\s+"), " ")
            .trim()
}

private fun MutableStateFlow<BookmarkTarget>.updatePageOnly(pageNumber: Int) {
    val current = value
    if (current.pageNumber == pageNumber) return
    value = current.copy(pageNumber = pageNumber)
}
