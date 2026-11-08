package com.quransunah.app.recitation.engine

import com.quransunah.app.BuildConfig

/** JNI bridge to whisper.cpp (see src/main/cpp/whisper_jni.cpp). */
object WhisperLib {
    val loaded: Boolean by lazy {
        BuildConfig.WHISPER_NATIVE && runCatching { System.loadLibrary("quranwhisper"); true }.getOrDefault(false)
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(ctx: Long)
    /** Transcribes 16 kHz mono float PCM. Returns the full text. */
    external fun transcribeBytes(ctx: Long, threads: Int, samples: FloatArray, prompt: String?, audioCtx: Int): ByteArray

    /** [audioCtx] limits the encoder to the chunk length (0 = full 30 s window). */
    fun transcribe(ctx: Long, threads: Int, samples: FloatArray, prompt: String?, audioCtx: Int = 0): String =
        String(transcribeBytes(ctx, threads, samples, prompt, audioCtx), Charsets.UTF_8)

    /** Encoder frames needed for [samples] at 16 kHz (50 frames/s after the conv stride) plus margin. */
    fun audioCtxFor(samples: Int): Int = (Math.ceil(samples / 16000.0 * 50.0).toInt() + 64).coerceAtMost(1500)
    external fun systemInfo(): String
}
