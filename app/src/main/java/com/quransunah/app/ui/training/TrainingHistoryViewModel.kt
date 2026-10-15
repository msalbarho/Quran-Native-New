package com.quransunah.app.ui.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.domain.model.TrainingAssessmentResult
import com.quransunah.app.domain.model.TrainingPosition
import com.quransunah.app.domain.model.TrainingSession
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.domain.repository.TrainingHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TrainingHistoryUiState(
    val sessions: List<TrainingSession> = emptyList(),
    val surahNames: Map<Int, String> = emptyMap(),
    val loaded: Boolean = false,
)

@HiltViewModel
class TrainingHistoryViewModel @Inject constructor(
    private val history: TrainingHistoryRepository,
    private val mushafRepository: MushafRepository,
) : ViewModel() {
    private val surahNames = flow { emit(mushafRepository.getSurahs().associate { it.number to it.nameArabic }) }

    val uiState: StateFlow<TrainingHistoryUiState> = combine(
        history.observeSessions(),
        surahNames,
    ) { sessions, names ->
        TrainingHistoryUiState(sessions = sessions, surahNames = names, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrainingHistoryUiState())

    fun noteVerse(surah: Int, ayah: Int, pageNumber: Int) {
        if (surah <= 0 || ayah <= 0 || pageNumber <= 0) return
        viewModelScope.launch {
            history.recordPractice(surah, ayah, pageNumber, System.currentTimeMillis())
        }
    }

    fun noteAssessment(surah: Int, ayah: Int, pageNumber: Int, mastered: Boolean) {
        if (surah <= 0 || ayah <= 0 || pageNumber <= 0) return
        val result = if (mastered) TrainingAssessmentResult.MASTERED else TrainingAssessmentResult.LEARNING
        viewModelScope.launch {
            history.recordAssessment(surah, ayah, pageNumber, result, System.currentTimeMillis())
        }
    }

    fun rememberPosition(surah: Int, ayah: Int, pageNumber: Int) {
        if (surah <= 0 || ayah <= 0 || pageNumber <= 0) return
        viewModelScope.launch {
            history.rememberPosition(surah, ayah, pageNumber, System.currentTimeMillis())
        }
    }

    fun finishSession() {
        viewModelScope.launch { history.finishActiveSession(System.currentTimeMillis()) }
    }

    suspend fun resumeTarget(): TrainingPosition? {
        history.currentPosition()?.let { return it }
        val session = history.latestSession() ?: return null
        val page = if (session.pageNumber > 0) {
            session.pageNumber
        } else {
            mushafRepository.getPageForAyah(session.lastSurah, session.lastAyah)
        }
        return TrainingPosition(
            surah = session.lastSurah,
            ayah = session.lastAyah,
            pageNumber = page,
            updatedAt = session.updatedAt,
        )
    }
}
