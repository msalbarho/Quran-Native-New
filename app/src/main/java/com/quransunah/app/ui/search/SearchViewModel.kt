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

    init {
        viewModelScope.launch {
            runCatching { searchRepository.ensureIndex() }
            _ui.update { it.copy(indexed = true) }
        }
    }

    fun setQuery(value: String) {
        _ui.update { it.copy(query = value) }
        searchJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(results = emptyList(), loading = false, tooShort = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(280)
            val looksLikeReference = trimmed.contains(':') ||
                trimmed.contains('：') ||
                trimmed.contains("سورة") ||
                (EasternArabic.parseDigits(trimmed).any { it.isDigit() } &&
                    trimmed.any { it.isLetter() })
            val folded = ArabicNormalize.fold(trimmed, stripSpaces = true)
            val tooShort = folded.length < AppConstants.SEARCH_MIN_QUERY_LENGTH && !looksLikeReference
            if (tooShort) {
                _ui.update { it.copy(results = emptyList(), loading = false, tooShort = true) }
                return@launch
            }
            _ui.update { it.copy(loading = true, tooShort = false) }
            val hits = runCatching { searchRepository.search(trimmed) }.getOrDefault(emptyList())
            _ui.update { it.copy(results = hits, loading = false) }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _ui.update { SearchUiState(indexed = it.indexed) }
    }
}
