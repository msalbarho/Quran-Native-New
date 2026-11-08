package com.quransunah.app.recitation.engine

/**
 * Pluggable speech recognizer used by the recitation trainer.
 * Implementations: [AndroidSpeechRecognizer] (system recognizer, ar-SA, live partial results)
 * [WhisperRecognizer] (on-device whisper.cpp running the Quran-tuned tarteel-ai/whisper-base-ar-quran)
 * and [ZipformerRecognizer] (on-device streaming phoneme recognizer Zipformer Quran 3.1 via sherpa-onnx; default).
 */
interface RecitationRecognizer {
    val id: String
    val displayName: String

    fun isAvailable(): Boolean

    /** Starts continuous recognition. Transcripts are cumulative for the whole session. */
    fun start(listener: Listener)

    fun stop()

    fun release()

    /** Loads the model / native context in the background so the first start is fast. */
    fun warmUp() {}

    interface Listener {
        /** [committed] = finalized text so far, [partial] = current hypothesis (may change). */
        fun onTranscript(committed: String, partial: String)
        fun onLevel(level: Float) {}
        fun onListeningChanged(listening: Boolean) {}
        fun onError(message: String, fatal: Boolean)
        /** Only from an [AligningRecognizer]: live word statuses plus the raw (phonetic) transcript. */
        fun onLiveAlignment(live: com.quransunah.app.recitation.core.LiveAlignment, transcript: String) {}
    }
}

/**
 * A recognizer that checks the recitation against the expected text itself (phoneme level) and reports a
 * ready-made [com.quransunah.app.recitation.core.LiveAlignment] through [RecitationRecognizer.Listener.onLiveAlignment]
 * instead of word transcripts. Used by the Zipformer Quran engine.
 */
interface AligningRecognizer : RecitationRecognizer {
    /** Must be called before [start] with the words to be recited, in order (ayah-number glyphs excluded). */
    fun prepare(expected: List<com.quransunah.app.recitation.core.RecitationWord>, strictness: com.quransunah.app.recitation.core.Strictness)
}
