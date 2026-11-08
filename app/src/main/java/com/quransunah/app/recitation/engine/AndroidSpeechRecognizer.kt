package com.quransunah.app.recitation.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Continuous recognition on top of the platform [SpeechRecognizer] (ar-SA) with partial results.
 * The platform recognizer stops after a pause, so it is transparently restarted and the
 * finalized segments are accumulated.
 */
class AndroidSpeechRecognizer(private val context: Context) : RecitationRecognizer {
    override val id = "android"
    override val displayName = "التعرّف الصوتي في أندرويد (ar-SA)"

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: RecitationRecognizer.Listener? = null
    private var active = false
    private val committed = StringBuilder()
    private var consecutiveErrors = 0
    /** Latest partial of the running utterance; committed if the session ends without final results. */
    private var lastPartial = ""

    override fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ar-SA")
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // on-device Arabic pack when installed: lower latency, works offline
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
    }

    override fun start(listener: RecitationRecognizer.Listener) {
        main.post {
            this.listener = listener
            committed.clear(); lastPartial = ""
            active = true
            consecutiveErrors = 0
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also { it.setRecognitionListener(callback) }
            }
            startListening()
        }
    }

    private fun startListening() {
        if (!active) return
        runCatching { recognizer?.startListening(intent()) }.onFailure {
            listener?.onError("تعذّر بدء التعرف الصوتي", true)
        }
    }

    private fun bestResult(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private val callback = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { listener?.onListeningChanged(true) }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { listener?.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (!active) return
            if (lastPartial.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                // keep words already shown as partial instead of dropping them on a restart
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(lastPartial); lastPartial = ""
                listener?.onTranscript(committed.toString(), "")
            }
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    consecutiveErrors++
                    main.postDelayed({ startListening() }, if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 400 else 100)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { active = false; listener?.onError("يلزم منح إذن الميكروفون", true) }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> {
                    consecutiveErrors++
                    if (consecutiveErrors > 3) { active = false; listener?.onError("خدمة التعرف تتطلب الإنترنت أو حزمة العربية دون اتصال. جرّب محرك Whisper المحلي من الإعدادات.", true) }
                    else main.postDelayed({ startListening() }, 600)
                }
                else -> {
                    consecutiveErrors++
                    if (consecutiveErrors > 5) { active = false; listener?.onError("خطأ في التعرف الصوتي ($error)", true) }
                    else main.postDelayed({ startListening() }, 300)
                }
            }
        }
        override fun onResults(results: Bundle?) {
            val text = bestResult(results).ifBlank { lastPartial }
            lastPartial = ""
            if (text.isNotBlank()) {
                if (committed.isNotEmpty()) committed.append(' ')
                committed.append(text)
                consecutiveErrors = 0
            }
            listener?.onTranscript(committed.toString(), "")
            if (active) main.post { startListening() }
        }
        override fun onPartialResults(partialResults: Bundle?) {
            // Delivered several times per second while speaking: forwarded immediately so the
            // incremental aligner can colour each word as soon as it appears.
            val text = bestResult(partialResults)
            if (text.isNotBlank() && text != lastPartial) { lastPartial = text; listener?.onTranscript(committed.toString(), text) }
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun stop() {
        main.post {
            active = false
            runCatching { recognizer?.stopListening() }
            listener?.onListeningChanged(false)
        }
    }

    override fun release() {
        main.post {
            active = false
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }
}
