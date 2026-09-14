package com.quransunah.app.ui.memorization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.domain.model.MemorizationSession
import com.quransunah.app.domain.model.MemorizationPlan
import com.quransunah.app.domain.model.isValidAyahRange
import com.quransunah.app.domain.model.isDueForReview
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.audio.MemorizationRecorder
import com.quransunah.app.data.audio.RecordingState
import com.quransunah.app.data.audio.OnDeviceSpeechTranscriber
import com.quransunah.app.data.audio.TranscriptionState
import com.quransunah.app.core.RecitationCheckResult
import com.quransunah.app.core.RecitationTextChecker
import com.quransunah.app.core.WordDifferenceType
import com.quransunah.app.domain.model.memorizationSummary
import com.quransunah.app.domain.repository.MemorizationRepository
import com.quransunah.app.domain.repository.MushafRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class MemorizationListItem(
    val item: MemorizationItem,
    val surahName: String,
)

data class MemorizationUiState(
    val items: List<MemorizationListItem> = emptyList(),
    val dailyItems: List<MemorizationListItem> = emptyList(),
    val total: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0,
    val completionPercent: Int = 0,
    val loaded: Boolean = false,
    val plans: List<MemorizationPlan> = emptyList(),
    val activeSessionId: String? = null,
    val reviewDueCount: Int = 0,
    val reviewItems: List<MemorizationListItem> = emptyList(),
)

@HiltViewModel
class MemorizationViewModel @Inject constructor(
    private val memorizationRepository: MemorizationRepository,
    private val mushafRepository: MushafRepository,
    private val recorder: MemorizationRecorder,
    private val transcriber: OnDeviceSpeechTranscriber,
) : ViewModel() {
    private var lastRecordingId: String? = null
    private val _checkResult = kotlinx.coroutines.flow.MutableStateFlow<RecitationCheckResult?>(null)
    val checkResult: StateFlow<RecitationCheckResult?> = _checkResult
    val recordingState: StateFlow<RecordingState> = recorder.state
    val transcriptionState: StateFlow<TranscriptionState> = transcriber.state
    private val surahs = flow { emit(mushafRepository.getSurahs()) }
    private val _sessionId = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            transcriber.state.collect { state ->
                if (state is TranscriptionState.Completed) checkTranscript(state.text)
            }
        }
    }

    val uiState: StateFlow<MemorizationUiState> = combine(
        combine(memorizationRepository.observeItems(), surahs) { items, catalog -> items to catalog },
        memorizationRepository.observePlans(),
        _sessionId,
    ) { (items, catalog), plans, sessionId ->
        val names = catalog.associate { it.number to it.nameArabic }
        val summary = items.memorizationSummary()
        val reviewDueCount = items.count { it.isDueForReview() }
        val listItems = items.map { item ->
            MemorizationListItem(item = item, surahName = names[item.surah].orEmpty())
        }
        val plan = plans.firstOrNull()
        val dailyItems = plan?.let { selected ->
            listItems.filter { entry ->
                com.quransunah.app.core.SurahAyahCounts.ayahId(entry.item.surah, entry.item.ayah) in
                    com.quransunah.app.core.SurahAyahCounts.range(selected.startSurah, selected.startAyah, selected.endSurah, selected.endAyah)
                        .map { (surah, ayah) -> com.quransunah.app.core.SurahAyahCounts.ayahId(surah, ayah) }
            }.take(selected.dailyTarget)
        }.orEmpty()
        MemorizationUiState(
            items = listItems,
            dailyItems = dailyItems,
            total = summary.total,
            learning = summary.learning,
            mastered = summary.mastered,
            completionPercent = summary.completionPercent,
            loaded = true,
            plans = plans,
            activeSessionId = sessionId,
            reviewDueCount = reviewDueCount,
            reviewItems = listItems.filter { it.item.isDueForReview() }.take(5),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemorizationUiState())

    fun markMastered(id: String) {
        viewModelScope.launch {
            memorizationRepository.setState(id, MemorizationState.MASTERED, System.currentTimeMillis())
            advanceSequentialQueue()
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

    fun createPlanFromTrackedItems() {
        viewModelScope.launch {
            val items = memorizationRepository.observeItems().first()
            val first = items.minWithOrNull(compareBy<MemorizationItem> { it.surah }.thenBy { it.ayah }) ?: return@launch
            val last = items.maxWithOrNull(compareBy<MemorizationItem> { it.surah }.thenBy { it.ayah }) ?: first
            val now = System.currentTimeMillis()
            val plan = MemorizationPlan(
                id = MemorizationPlan.idFor(first.surah, first.ayah, last.surah, last.ayah),
                name = "خطة التحفيظ",
                startSurah = first.surah,
                startAyah = first.ayah,
                endSurah = last.surah,
                endAyah = last.ayah,
                dailyTarget = 5,
                createdAt = now,
                updatedAt = now,
            )
            if (isValidAyahRange(plan)) memorizationRepository.savePlan(plan)
        }
    }

    fun startSequentialPlan() {
        viewModelScope.launch {
            if (memorizationRepository.observeItems().first().isNotEmpty()) return@launch
            val now = System.currentTimeMillis()
            val endAyah = SurahAyahCounts.ayahCount(114)
            val dailyTarget = 3
            val plan = MemorizationPlan(
                id = MemorizationPlan.idFor(1, 1, 114, endAyah),
                name = "الحفظ بالتسلسل",
                startSurah = 1,
                startAyah = 1,
                endSurah = 114,
                endAyah = endAyah,
                dailyTarget = dailyTarget,
                createdAt = now,
                updatedAt = now,
            )
            memorizationRepository.savePlan(plan)
            SurahAyahCounts.range(1, 1, 1, dailyTarget).forEach { (surah, ayah) ->
                val words = mushafRepository.getAyahWords(surah, ayah)
                val text = words.filterNot { it.isAyahMarker }.joinToString(" ") { it.textHafs }.trim()
                if (text.isNotBlank()) {
                    memorizationRepository.track(
                        MemorizationItem(
                            id = MemorizationItem.idFor(surah, ayah),
                            surah = surah,
                            ayah = ayah,
                            pageNumber = mushafRepository.getPageForAyah(surah, ayah),
                            ayahText = text,
                            state = MemorizationState.LEARNING,
                            reviewCount = 0,
                            createdAt = now,
                            updatedAt = now,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun advanceSequentialQueue() {
        val plan = memorizationRepository.observePlans().first().firstOrNull { it.name == "الحفظ بالتسلسل" } ?: return
        val items = memorizationRepository.observeItems().first()
        if (items.isEmpty() || items.any { it.state == MemorizationState.LEARNING }) return
        val lastIndex = items.maxOf { SurahAyahCounts.ayahId(it.surah, it.ayah) }
        val endIndex = SurahAyahCounts.ayahId(plan.endSurah, plan.endAyah)
        if (lastIndex >= endIndex) return
        val now = System.currentTimeMillis()
        val nextEnd = (lastIndex + plan.dailyTarget).coerceAtMost(endIndex)
        (lastIndex + 1..nextEnd).forEach { globalIndex ->
            val (surah, ayah) = SurahAyahCounts.fromAyahId(globalIndex) ?: return@forEach
            val words = mushafRepository.getAyahWords(surah, ayah)
            val text = words.filterNot { it.isAyahMarker }.joinToString(" ") { it.textHafs }.trim()
            if (text.isNotBlank()) {
                memorizationRepository.track(
                    MemorizationItem(
                        id = MemorizationItem.idFor(surah, ayah),
                        surah = surah,
                        ayah = ayah,
                        pageNumber = mushafRepository.getPageForAyah(surah, ayah),
                        ayahText = text,
                        state = MemorizationState.LEARNING,
                        reviewCount = 0,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
            }
        }
    }

    fun startSession(planId: String) {
        viewModelScope.launch {
            val id = memorizationRepository.startSession(planId, System.currentTimeMillis())
            // The id is kept only as UI state; session rows remain the source of truth.
            _sessionId.value = id
        }
    }

    fun finishSession() {
        val id = _sessionId.value ?: return
        val summary = uiState.value
        viewModelScope.launch {
            memorizationRepository.finishSession(id, summary.total, summary.mastered, System.currentTimeMillis())
            _sessionId.value = null
        }
    }

    fun startRecording() {
        _checkResult.value = null
        if (recorder.start()) transcriber.start()
    }

    fun stopRecording() {
        val finished = recorder.stop()
        transcriber.stop()
        if (!finished) return
        val sessionId = _sessionId.value ?: return
        val file = (recorder.state.value as? RecordingState.Ready)?.file ?: return
        viewModelScope.launch {
            val recordingId = "recording:${UUID.randomUUID()}"
            lastRecordingId = recordingId
            memorizationRepository.saveRecording(
                com.quransunah.app.domain.model.MemorizationRecording(
                    id = recordingId,
                    sessionId = sessionId,
                    filePath = file.absolutePath,
                    createdAt = System.currentTimeMillis(),
                    durationMs = recorder.lastDurationMs,
                ),
            )
        }
    }
    fun playRecording() { recorder.play() }
    fun stopPlayback() { recorder.stopPlayback() }
    fun deleteRecording() {
        val id = lastRecordingId
        transcriber.cancel()
        recorder.delete()
        if (id != null) viewModelScope.launch { memorizationRepository.deleteRecording(id) }
        lastRecordingId = null
    }

    fun checkTranscript(transcript: String) {
        val expected = uiState.value.dailyItems.joinToString(" ") { it.item.ayahText }
        if (expected.isBlank() || transcript.isBlank()) return
        val result = RecitationTextChecker.compare(expected, transcript)
        _checkResult.value = result
        val sessionId = _sessionId.value ?: return
        viewModelScope.launch {
            memorizationRepository.saveAttempt(
                com.quransunah.app.domain.model.RecitationAttempt(
                    id = "attempt:${UUID.randomUUID()}",
                    sessionId = sessionId,
                    recordingId = lastRecordingId,
                    scorePercent = result.scorePercent,
                    missingCount = result.differences.count { it.type == WordDifferenceType.MISSING },
                    extraCount = result.differences.count { it.type == WordDifferenceType.EXTRA },
                    differentCount = result.differences.count { it.type == WordDifferenceType.DIFFERENT },
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun onCleared() {
        transcriber.release()
        recorder.release()
        super.onCleared()
    }

}
