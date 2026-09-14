package com.quransunah.app.data.audio

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Captures an Arabic transcript through Android's on-device recognizer only.
 *
 * This deliberately does not fall back to [SpeechRecognizer.createSpeechRecognizer], because that
 * recognizer may use a network-backed service. If an on-device Arabic recognition pack is not
 * present, the caller receives [TranscriptionState.Unavailable] and can keep the manual transcript
 * field available instead.
 */
sealed interface TranscriptionState {
    data object Idle : TranscriptionState
    data object Starting : TranscriptionState
    data class Listening(val partialText: String = "") : TranscriptionState
    data class Completed(val text: String) : TranscriptionState
    data object NoSpeech : TranscriptionState
    data object Unavailable : TranscriptionState
    data object Failed : TranscriptionState
}

@Singleton
class OnDeviceSpeechTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<TranscriptionState>(TranscriptionState.Idle)
    val state: StateFlow<TranscriptionState> = _state.asStateFlow()

    /** Starts a new Arabic transcription session when Android can guarantee an on-device engine. */
    fun start(): Boolean {
        destroyRecognizer()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
        ) {
            _state.value = TranscriptionState.Unavailable
            return false
        }

        val localRecognizer = runCatching {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        }.getOrElse {
            _state.value = TranscriptionState.Unavailable
            return false
        }
        recognizer = localRecognizer
        localRecognizer.setRecognitionListener(recognitionListener)
        _state.value = TranscriptionState.Starting

        return runCatching {
            localRecognizer.startListening(recognitionIntent())
        }.onFailure {
            _state.value = TranscriptionState.Failed
            destroyRecognizer()
        }.isSuccess
    }

    /** Signals the local recognizer to finalize the text collected during the recording. */
    fun stop() {
        val active = recognizer ?: return
        runCatching { active.stopListening() }.onFailure {
            _state.value = TranscriptionState.Failed
            destroyRecognizer()
        }
    }

    fun cancel() {
        recognizer?.runCatching { cancel() }
        destroyRecognizer()
        _state.value = TranscriptionState.Idle
    }

    fun release() = cancel()

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = TranscriptionState.Listening()
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            _state.value = when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                -> TranscriptionState.NoSpeech
                else -> TranscriptionState.Failed
            }
            destroyRecognizer()
        }

        override fun onResults(results: Bundle?) {
            val text = firstUsableResult(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION))
            _state.value = if (text.isBlank()) TranscriptionState.NoSpeech else TranscriptionState.Completed(text)
            destroyRecognizer()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = firstUsableResult(
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION),
            )
            _state.value = TranscriptionState.Listening(partial)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun recognitionIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, ARABIC_SAUDI.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, ARABIC_SAUDI.toLanguageTag())
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    private fun destroyRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    internal companion object {
        private val ARABIC_SAUDI = Locale("ar", "SA")

        /** Android orders recognition alternatives from most to least likely. */
        fun firstUsableResult(results: List<String>?): String =
            results?.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }
}
