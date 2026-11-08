package com.quransunah.app.recitation.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

sealed class ModelState {
    data object NotDownloaded : ModelState()
    data class Downloading(val progress: Float) : ModelState()
    data object Ready : ModelState()
    data class Failed(val message: String) : ModelState()
    data object Unsupported : ModelState()
}

/**
 * Downloads the free Apache-2.0 GGML conversion of tarteel-ai/whisper-base-ar-quran (int8, ~78 MB)
 * from Hugging Face once, then the recognizer works fully offline.
 */
class WhisperModelManager(context: Context) {
    val modelFile = File(context.filesDir, "models/ggml-whisper-base-ar-quran-q8_0.bin")
    private val _state = MutableStateFlow(initial())
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private fun initial(): ModelState = when {
        !WhisperLib.loaded -> ModelState.Unsupported
        modelFile.exists() && modelFile.length() > 50_000_000 -> ModelState.Ready
        else -> ModelState.NotDownloaded
    }

    fun refresh() { _state.value = initial() }

    suspend fun download() = withContext(Dispatchers.IO) {
        if (!WhisperLib.loaded) { _state.value = ModelState.Unsupported; return@withContext }
        modelFile.parentFile?.mkdirs()
        val tmp = File(modelFile.path + ".part")
        try {
            _state.value = ModelState.Downloading(0f)
            var url = URL(MODEL_URL)
            var conn: HttpURLConnection
            var redirects = 0
            while (true) {
                conn = url.openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 20000; conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "QuranTrainer/1.0")
                val code = conn.responseCode
                if (code in 300..399 && redirects < 6) {
                    url = URL(url, conn.getHeaderField("Location")); redirects++; conn.disconnect(); continue
                }
                if (code != 200) throw IllegalStateException("HTTP $code")
                break
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: 81_768_585L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    var lastEmit = 0L
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        out.write(buf, 0, n); done += n
                        if (done - lastEmit > 512 * 1024) { lastEmit = done; _state.value = ModelState.Downloading(done.toFloat() / total) }
                    }
                }
            }
            if (!tmp.renameTo(modelFile)) throw IllegalStateException("rename failed")
            _state.value = ModelState.Ready
        } catch (e: Exception) {
            tmp.delete()
            _state.value = ModelState.Failed(e.message ?: "download failed")
        }
    }

    fun delete() { modelFile.delete(); refresh() }

    companion object {
        const val MODEL_URL = "https://huggingface.co/sadrapp/whisper-base-ar-quran-ggml/resolve/main/ggml-model-q8_0.bin"
    }
}
