package com.quransunah.app.ui.training

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.recitation.RecitationEngineId
import com.quransunah.app.recitation.RecitationEngines
import com.quransunah.app.recitation.core.IncrementalAligner
import com.quransunah.app.recitation.core.LiveAlignment
import com.quransunah.app.recitation.core.QuranStructure
import com.quransunah.app.recitation.core.RecitationWord
import com.quransunah.app.recitation.core.Strictness
import com.quransunah.app.recitation.engine.AligningRecognizer
import com.quransunah.app.recitation.engine.ModelState
import com.quransunah.app.recitation.engine.RecitationRecognizer
import com.quransunah.app.ui.mushaf.TrainingWordMark
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class TrainingReciteUi(
    val phase: TrainingPhase = TrainingPhase.Before,
    val scope: TrainingReciteScope = TrainingReciteScope.Open,
    val listening: Boolean = false,
    val elapsedMs: Long = 0L,
    val marks: Map<Int, TrainingWordMark> = emptyMap(),
    val completedAyahs: Set<Pair<Int, Int>> = emptySet(),
    val summary: TrainingReciteSummary? = null,
    val panelOpen: Boolean = false,
)

@HiltViewModel
class TrainingRecitationViewModel @Inject constructor(
    private val engines: RecitationEngines,
    private val preferences: UserPreferences,
) : ViewModel() {
    private val _ui = MutableStateFlow(TrainingReciteUi())
    val ui: StateFlow<TrainingReciteUi> = _ui.asStateFlow()

    private var recognizer: RecitationRecognizer? = null
    /** Engine of [recognizer]; the recognizer (and its loaded model) is kept alive between sessions. */
    private var recognizerEngine: RecitationEngineId? = null
    private var aligner: IncrementalAligner? = null
    private var expected: List<TrainingExpectedWord> = emptyList()
    private var timer: Job? = null
    private var startedAt = 0L
    private var generation = 0
    private var heardAlignment = false
    private var lastMarks: List<TrainingWordAssessment> = emptyList()
    private var stopAtAyahEnd = false
    private var stopRequested = false

    fun setScope(scope: TrainingReciteScope) {
        if (_ui.value.phase == TrainingPhase.During) return
        _ui.update { it.copy(scope = scope) }
    }

    fun setEngine(engine: RecitationEngineId) {
        if (_ui.value.phase == TrainingPhase.During || !engineAvailable(engine)) return
        viewModelScope.launch { preferences.setRecitationEngine(engine) }
    }

    fun engineAvailable(engine: RecitationEngineId): Boolean = engines.isAvailable(engine) &&
        (engine != RecitationEngineId.WHISPER || engines.whisperModels.state.value == ModelState.Ready)

    /** Pre-loads the engine when the training view opens, so pressing Start begins listening quickly. */
    fun warmUp(engine: RecitationEngineId) {
        if (!engineAvailable(engine) || _ui.value.phase == TrainingPhase.During) return
        obtainRecognizer(engine).warmUp()
    }

    private fun obtainRecognizer(engine: RecitationEngineId): RecitationRecognizer {
        recognizer?.let { if (recognizerEngine == engine) return it }
        releaseRecognizer()
        return engines.create(engine).also { recognizer = it; recognizerEngine = engine }
    }

    fun arm(words: List<TrainingExpectedWord>, stopAtCompletion: Boolean = false) {
        expected = words
        stopAtAyahEnd = stopAtCompletion
        stopRequested = false
    }

    fun start(engine: RecitationEngineId) {
        if (_ui.value.phase == TrainingPhase.During) return
        val words = expected
        if (words.isEmpty()) {
            _ui.update { it.copy(phase = TrainingPhase.Before, panelOpen = false, summary = null) }
            return
        }
        val gen = ++generation
        heardAlignment = false
        lastMarks = emptyList()
        stopRequested = false
        val created = obtainRecognizer(engine)
        if (created is AligningRecognizer) {
            created.prepare(
                words.mapIndexed { index, word ->
                    RecitationWord(
                        index = index,
                        verseId = QuranStructure.verseId(word.surah, word.ayah),
                        position = word.position,
                        text = word.text,
                    )
                },
                Strictness.NORMAL,
            )
            aligner = null
        } else {
            aligner = IncrementalAligner(words.map { it.text })
        }
        startedAt = SystemClock.elapsedRealtime()
        _ui.update {
            it.copy(
                phase = TrainingPhase.During,
                listening = false,
                elapsedMs = 0L,
                marks = emptyMap(),
                completedAyahs = emptySet(),
                summary = null,
                panelOpen = false,
            )
        }
        timer?.cancel()
        timer = null
        created.start(object : RecitationRecognizer.Listener {
            override fun onTranscript(committed: String, partial: String) {
                if (gen != generation) return
                val live = aligner?.update(committed, partial) ?: return
                apply(gen, live)
            }

            override fun onLiveAlignment(live: LiveAlignment, transcript: String) {
                if (gen != generation) return
                apply(gen, live)
            }

            override fun onListeningChanged(listening: Boolean) {
                if (gen != generation) return
                _ui.update { it.copy(listening = listening) }
                // the timer starts only once the microphone is really listening
                if (listening && timer == null) startTimer(gen)
                if (!listening && _ui.value.phase == TrainingPhase.During) {
                    finish(gen, showResults = heardAlignment)
                }
            }

            override fun onError(message: String, fatal: Boolean) {
                if (gen != generation) return
                if (fatal && _ui.value.phase == TrainingPhase.During) {
                    finish(gen, showResults = false)
                }
            }
        })
    }

    private fun startTimer(gen: Int) {
        startedAt = SystemClock.elapsedRealtime()
        timer = viewModelScope.launch {
            while (isActive) {
                delay(200)
                if (gen != generation) return@launch
                _ui.update { state ->
                    if (state.phase != TrainingPhase.During) state
                    else state.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                }
            }
        }
    }

    fun stop() {
        val current = recognizer
        if (current == null) {
            if (_ui.value.phase == TrainingPhase.During) {
                finish(generation, showResults = heardAlignment)
            }
            return
        }
        current.stop()
    }

    fun onMicDenied() {
        _ui.update { it.copy(phase = TrainingPhase.Before, listening = false) }
    }

    fun collapseResults() {
        _ui.update { it.copy(panelOpen = false) }
    }

    /** Stops the microphone when Training is left. Does not invent a result. */
    fun abandon() {
        generation += 1
        timer?.cancel()
        releaseRecognizer()
        _ui.update {
            it.copy(
                phase = if (it.phase == TrainingPhase.During) TrainingPhase.Before else it.phase,
                listening = false,
                panelOpen = false,
            )
        }
    }

    override fun onCleared() {
        generation += 1
        timer?.cancel()
        releaseRecognizer()
        super.onCleared()
    }

    private fun apply(gen: Int, live: LiveAlignment) {
        if (gen != generation) return
        val assessed = assessRecitation(expected, live)
        if (assessed.isEmpty() && expected.isNotEmpty()) return
        heardAlignment = true
        lastMarks = assessed
        val completed = finalizedTrainingAyahs(expected, live)
        _ui.update { state ->
            state.copy(
                marks = assessed.associate { it.wordId to it.mark },
                completedAyahs = state.completedAyahs + completed,
            )
        }
        val finalAyah = expected.lastOrNull()?.let { it.surah to it.ayah }
        if (stopAtAyahEnd && !stopRequested && finalAyah != null && finalAyah in completed) {
            stopRequested = true
            recognizer?.stop()
        }
    }

    private fun finish(gen: Int, showResults: Boolean) {
        if (gen != generation) return
        if (_ui.value.phase != TrainingPhase.During) return
        timer?.cancel()
        val duration = (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L)
        val summary = summarizeRecitation(lastMarks, duration)
        timer = null
        val show = showResults && summary.fromSession
        _ui.update {
            it.copy(
                phase = if (show) TrainingPhase.After else TrainingPhase.Before,
                listening = false,
                elapsedMs = duration,
                summary = if (summary.fromSession) summary else null,
                panelOpen = show,
            )
        }
        // keep the recognizer (loaded model) for the next session; just make sure it is stopped
        recognizer?.stop()
        aligner = null
    }

    private fun releaseRecognizer() {
        val current = recognizer
        recognizer = null
        recognizerEngine = null
        aligner = null
        if (current is com.quransunah.app.recitation.engine.ZipformerRecognizer) current.cancel()
        current?.release()
    }

}
