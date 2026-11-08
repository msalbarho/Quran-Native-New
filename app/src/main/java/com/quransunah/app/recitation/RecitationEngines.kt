package com.quransunah.app.recitation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.quransunah.app.recitation.engine.AndroidSpeechRecognizer
import com.quransunah.app.recitation.engine.RecitationRecognizer
import com.quransunah.app.recitation.engine.WhisperLib
import com.quransunah.app.recitation.engine.WhisperModelManager
import com.quransunah.app.recitation.engine.WhisperRecognizer
import com.quransunah.app.recitation.engine.ZipformerEngine
import com.quransunah.app.recitation.engine.ZipformerRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The three recitation checking engines, in the order shown in Settings. */
enum class RecitationEngineId(val id: String) {
    /** Zipformer Quran 3.1 (Quran Lab, NPL-1.2) via sherpa-onnx — bundled, offline, real-time phoneme check. Default. */
    ZIPFORMER("zipformer"),
    /** whisper.cpp + tarteel-ai/whisper-base-ar-quran, downloaded once on demand into app storage. */
    WHISPER("whisper"),
    /** The platform SpeechRecognizer (ar-SA). */
    ANDROID("android");

    companion object {
        fun fromId(id: String?): RecitationEngineId = entries.firstOrNull { it.id == id } ?: ZIPFORMER
    }
}

/**
 * Single entry point for recitation checking (used by the «تدريبي» page in the next step).
 * Holds the heavy, app-wide engine state (loaded model, Whisper download) and creates recognizers on demand.
 * Callers own the returned recognizer: call stop()/release() when leaving the screen.
 */
@Singleton
class RecitationEngines @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val zipformer: ZipformerEngine by lazy { ZipformerEngine(context) }
    val whisperModels: WhisperModelManager by lazy { WhisperModelManager(context) }

    fun isAvailable(id: RecitationEngineId): Boolean = when (id) {
        RecitationEngineId.ZIPFORMER -> zipformer.isAvailable()
        RecitationEngineId.WHISPER -> WhisperLib.loaded
        RecitationEngineId.ANDROID -> AndroidSpeechRecognizer(context).isAvailable()
    }

    /** Creates a recognizer for [id]. A Zipformer recognizer is an AligningRecognizer: call prepare() before start(). */
    fun create(id: RecitationEngineId): RecitationRecognizer = when (id) {
        RecitationEngineId.ZIPFORMER -> ZipformerRecognizer(zipformer)
        RecitationEngineId.WHISPER -> WhisperRecognizer(whisperModels)
        RecitationEngineId.ANDROID -> AndroidSpeechRecognizer(context)
    }

    companion object {
        const val ZIPFORMER_LICENSE_ASSET = ZipformerEngine.LICENSE
        const val SHERPA_LICENSE_ASSET = "licenses/sherpa-onnx-LICENSE.txt"
    }
}
