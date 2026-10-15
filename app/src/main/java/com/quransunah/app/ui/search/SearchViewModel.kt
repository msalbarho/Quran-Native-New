package com.quransunah.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicNormalize
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.AyahSearchHit
import com.quransunah.app.domain.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<AyahSearchHit> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val indexed: Boolean = false,
    val tooShort: Boolean = false,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _ui.asStateFlow()
    private var searchJob: Job? = null
    private var searchGeneration = 0

    init {
        viewModelScope.launch {
            runCatching { searchRepository.ensureIndex() }
            _ui.update { it.copy(indexed = true) }
        }
    }

    fun setQuery(value: String) {
        _ui.update { it.copy(query = value, loadingMore = false, hasMore = false) }
        searchJob?.cancel()
        val generation = ++searchGeneration
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _ui.update {
                it.copy(
                    results = emptyList(),
                    loading = false,
                    loadingMore = false,
                    hasMore = false,
                    tooShort = false,
                )
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            if (generation != searchGeneration) return@launch
            val reference = isReferenceQuery(trimmed)
            val folded = ArabicNormalize.fold(trimmed, stripSpaces = true)
            val tooShort = folded.length < AppConstants.SEARCH_MIN_QUERY_LENGTH && !reference
            if (tooShort) {
                _ui.update {
                    it.copy(
                        results = emptyList(),
                        loading = false,
                        loadingMore = false,
                        hasMore = false,
                        tooShort = true,
                    )
                }
                return@launch
            }
            _ui.update {
                it.copy(
                    results = emptyList(),
                    loading = true,
                    loadingMore = false,
                    hasMore = false,
                    tooShort = false,
                )
            }
            if (reference) {
                val hits = runCatching { searchRepository.search(trimmed) }.getOrDefault(emptyList())
                if (generation != searchGeneration) return@launch
                _ui.update { it.copy(results = hits, loading = false, hasMore = false) }
                return@launch
            }
            val fetched = runCatching {
                searchRepository.searchVerses(
                    trimmed,
                    limit = MushafSearchPaging.PAGE_SIZE + 1,
                    offset = 0,
                )
            }.getOrDefault(emptyList())
            if (generation != searchGeneration) return@launch
            val page = MushafSearchPaging.append(emptyList(), fetched)
            _ui.update { it.copy(results = page.items, loading = false, hasMore = page.hasMore) }
        }
    }

    fun loadMore() {
        val snapshot = _ui.value
        val trimmed = snapshot.query.trim()
        if (
            snapshot.loading ||
            snapshot.loadingMore ||
            !snapshot.hasMore ||
            trimmed.isEmpty() ||
            isReferenceQuery(trimmed)
        ) {
            return
        }
        val generation = searchGeneration
        val offset = snapshot.results.size
        searchJob = viewModelScope.launch {
            _ui.update { it.copy(loadingMore = true) }
            val fetched = runCatching {
                searchRepository.searchVerses(
                    trimmed,
                    limit = MushafSearchPaging.PAGE_SIZE + 1,
                    offset = offset,
                )
            }.getOrDefault(emptyList())
            if (generation != searchGeneration) return@launch
            val page = MushafSearchPaging.append(snapshot.results, fetched)
            _ui.update {
                it.copy(results = page.items, loadingMore = false, hasMore = page.hasMore)
            }
        }
    }

    fun clear() {
        searchJob?.cancel()
        searchGeneration++
        _ui.update { SearchUiState(indexed = it.indexed) }
    }

    private fun isReferenceQuery(trimmed: String): Boolean =
        trimmed.contains(':') ||
            trimmed.contains('：') ||
            trimmed.contains("سورة") ||
            (EasternArabic.parseDigits(trimmed).any { it.isDigit() } && trimmed.any { it.isLetter() })
}
