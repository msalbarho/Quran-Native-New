package com.quransunah.app.ui.memorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.domain.model.memorizationSummary
import com.quransunah.app.domain.repository.MemorizationRepository
import com.quransunah.app.domain.repository.MushafRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemorizationListItem(
    val item: MemorizationItem,
    val surahName: String,
)

data class MemorizationUiState(
    val items: List<MemorizationListItem> = emptyList(),
    val total: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0,
    val completionPercent: Int = 0,
    val loaded: Boolean = false,
)

@HiltViewModel
class MemorizationViewModel @Inject constructor(
    private val memorizationRepository: MemorizationRepository,
    mushafRepository: MushafRepository,
) : ViewModel() {
    private val surahs = flow { emit(mushafRepository.getSurahs()) }

    val uiState: StateFlow<MemorizationUiState> = combine(
        memorizationRepository.observeItems(),
        surahs,
    ) { items, catalog ->
        val names = catalog.associate { it.number to it.nameArabic }
        val summary = items.memorizationSummary()
        MemorizationUiState(
            items = items.map { item ->
                MemorizationListItem(item = item, surahName = names[item.surah].orEmpty())
            },
            total = summary.total,
            learning = summary.learning,
            mastered = summary.mastered,
            completionPercent = summary.completionPercent,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemorizationUiState())

    fun markMastered(id: String) {
        viewModelScope.launch {
            memorizationRepository.setState(id, MemorizationState.MASTERED, System.currentTimeMillis())
        }
    }

    fun markLearning(id: String) {
        viewModelScope.launch {
            memorizationRepository.setState(id, MemorizationState.LEARNING, System.currentTimeMillis())
        }
    }

    fun recordReview(id: String) {
        viewModelScope.launch {
            memorizationRepository.recordReview(id, System.currentTimeMillis())
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { memorizationRepository.untrack(id) }
    }
}
