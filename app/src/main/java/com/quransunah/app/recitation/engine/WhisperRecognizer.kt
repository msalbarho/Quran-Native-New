package com.quransunah.app.recitation.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Streaming on-device recognizer: whisper.cpp + tarteel-ai/whisper-base-ar-quran (q8_0 GGML).
 *
 * Low-latency design (all inference on a background worker thread, never the main thread):
 *  - The mic is streamed continuously into the current *utterance* buffer (16 kHz mono float).
 *  - Energy VAD with an adaptive noise floor: leading silence is trimmed, so whisper only sees speech.
 *  - While speaking, a partial transcription is produced every [PARTIAL_STEP_S] of new voiced audio.
 *    The encoder only processes the chunk's length (`audio_ctx`), so a 1–4 s chunk costs a fraction
 *    of a full 30 s window.
 *  - The utterance is committed after [COMMIT_SILENCE_S] of silence, or as a sliding chunk once it
 *    reaches [MAX_UTTERANCE_S]; the last [OVERLAP_S] of audio is carried over and words repeated in
 *    the overlap are de-duplicated, so long continuous recitation never grows the chunk unboundedly.
 *  - Only the newest partial matters: if inference is slower than speech, stale requests are skipped.
 */
class WhisperRecognizer(private val models: WhisperModelManager) : RecitationRecognizer {
    override val id = "whisper"
    override val displayName = "Whisper القرآني على الجهاز (tarteel-ai)"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var ctx = 0L
    private var recordThread: Thread? = null
    private var workerThread: Thread? = null
    private val lock = Object()
    private val segment = FloatArray((SAMPLE_RATE * (MAX_UTTERANCE_S + 2)).toInt())
    private var segLen = 0
    private var silenceSamples = 0
    private var voiced = false
    private var noiseFloor = 0.01f
    private val committed = StringBuilder()
    private var listener: RecitationRecognizer.Listener? = null
    private var flushThread: Thread? = null
    /** Serialises every use of the native context (worker, flush, warm-up, free). */
    private val ctxLock = Object()

    override fun warmUp() {
        if (!isAvailable()) return
        thread(name = "whisper-warmup") { synchronized(ctxLock) { if (ctx == 0L) ctx = WhisperLib.initContext(models.modelFile.absolutePath) } }
    }

    /** Last measured inference time (ms) – exposed for diagnostics. */
    @Volatile var lastInferenceMs = 0L
        private set

    override fun isAvailable(): Boolean = WhisperLib.loaded && models.state.value == ModelState.Ready

    override fun start(listener: RecitationRecognizer.Listener) {
        if (running) return
        this.listener = listener
        if (!isAvailable()) { listener.onError("نموذج Whisper غير محمّل. نزّله من الإعدادات.", true); return }
        running = true
        val previousFlush = flushThread
        workerThread = thread(name = "whisper-worker", priority = Thread.MAX_PRIORITY) {
            // a reused recognizer: the previous session's final decode must finish first
            previousFlush?.join(5000)
            committed.clear()
            synchronized(lock) { segLen = 0; silenceSamples = 0; voiced = false }
            synchronized(ctxLock) { if (ctx == 0L) ctx = WhisperLib.initContext(models.modelFile.absolutePath) }
            if (ctx == 0L) { running = false; post { listener.onError("تعذّر تحميل نموذج Whisper", true) }; return@thread }
            post { listener.onListeningChanged(true) }
            startRecording()
            var lastInferredLen = 0
            while (running) {
                Thread.sleep(POLL_MS)
                var snapshot: FloatArray? = null
                var commit = false
                synchronized(lock) {
                    val pauseCommit = voiced && silenceSamples >= SAMPLE_RATE * COMMIT_SILENCE_S
                    val lengthCommit = segLen >= SAMPLE_RATE * MAX_UTTERANCE_S
                    commit = pauseCommit || lengthCommit
                    if (commit) {
                        // drop the trailing silence from the committed chunk
                        val end = if (pauseCommit) max(0, segLen - silenceSamples + SAMPLE_RATE / 10) else segLen
                        snapshot = segment.copyOf(end.coerceAtMost(segLen))
                        if (lengthCommit) {
                            // sliding chunk: keep the last OVERLAP_S seconds as context for the next chunk
                            val keep = (SAMPLE_RATE * OVERLAP_S).toInt()
                            System.arraycopy(segment, segLen - keep, segment, 0, keep)
                            segLen = keep
                        } else {
                            segLen = 0; voiced = false
                        }
                        silenceSamples = 0
                    } else if (voiced && segLen - lastInferredLen >= SAMPLE_RATE * PARTIAL_STEP_S) {
                        snapshot = segment.copyOf(segLen)
                    }
                }
                val snap = snapshot ?: continue
                lastInferredLen = if (commit) 0 else snap.size
                if (snap.size < SAMPLE_RATE * 0.3) continue
                val text = infer(snap)
                if (commit) {
                    appendCommitted(text)
                    val c = committed.toString()
                    post { listener.onTranscript(c, "") }
                } else {
                    val c = committed.toString()
                    // a commit may have happened while we were inferring; then this partial is stale
                    if (running) post { listener.onTranscript(c, dedupe(c, text)) }
                }
            }
        }
    }

    private fun infer(samples: FloatArray): String {
        val t0 = SystemClock.elapsedRealtime()
        val input = padded(samples)
        val prompt = committed.split(' ').filter { it.isNotBlank() }.takeLast(8).joinToString(" ").ifBlank { null }
        val raw = synchronized(ctxLock) {
            if (ctx == 0L) "" else runCatching { WhisperLib.transcribe(ctx, threads(), input, prompt, WhisperLib.audioCtxFor(input.size)) }.getOrDefault("")
        }
        lastInferenceMs = SystemClock.elapsedRealtime() - t0
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "chunk ${samples.size / 16}ms -> ${lastInferenceMs}ms")
        return raw.replace(Regex("[\\[(].*?[\\])]"), "").trim()
    }

    /** Removes words at the start of [text] that repeat the end of [committedText] (overlap region). */
    private fun dedupe(committedText: String, text: String): String {
        val prev = committedText.split(' ').filter { it.isNotBlank() }
        val next = text.split(' ').filter { it.isNotBlank() }
        if (prev.isEmpty() || next.isEmpty()) return text
        val norm = { w: String -> com.quransunah.app.recitation.core.ArabicNormalizer.normalize(w) }
        for (k in minOf(4, prev.size, next.size) downTo 1) {
            if ((0 until k).all { norm(prev[prev.size - k + it]) == norm(next[it]) }) return next.drop(k).joinToString(" ")
        }
        return text
    }

    private fun appendCommitted(text: String) {
        val t = dedupe(committed.toString(), text)
        if (t.isNotBlank()) { if (committed.isNotEmpty()) committed.append(' '); committed.append(t) }
    }

    private fun padded(s: FloatArray): FloatArray =
        if (s.size >= SAMPLE_RATE * 1.05) s else s.copyOf((SAMPLE_RATE * 1.05).toInt())

    private fun threads() = Runtime.getRuntime().availableProcessors().coerceIn(2, 4)

    @SuppressLint("MissingPermission")
    private fun startRecording() {
        recordThread = thread(name = "whisper-rec", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE / 2))
            } catch (e: Exception) { null }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) {
                running = false; post { listener?.onError("تعذّر فتح الميكروفون", true) }; return@thread
            }
            rec.startRecording()
            val buf = ShortArray(FRAME) // 30 ms frames
            var levelTick = 0
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) { val f = buf[i] / 32768f; sum += f * f }
                val rms = sqrt(sum / n).toFloat()
                val speech = rms > max(MIN_SPEECH_RMS, noiseFloor * 3f)
                synchronized(lock) {
                    if (!speech) noiseFloor = noiseFloor * 0.97f + rms * 0.03f
                    if (!voiced && !speech) {
                        // VAD: keep only a short pre-roll before speech starts
                        val preroll = SAMPLE_RATE * 3 / 10
                        if (segLen + n > preroll) {
                            val keep = (preroll - n).coerceAtLeast(0)
                            System.arraycopy(segment, segLen - keep, segment, 0, keep)
                            segLen = keep
                        }
                    }
                    for (i in 0 until n) if (segLen < segment.size) segment[segLen++] = buf[i] / 32768f
                    if (speech) { voiced = true; silenceSamples = 0 } else silenceSamples += n
                }
                if (++levelTick % 2 == 0) post { listener?.onLevel((rms * 12f).coerceIn(0f, 1f)) }
            }
            runCatching { rec.stop() }
            rec.release()
        }
    }

    private fun post(r: () -> Unit) = main.post(r)

    override fun stop() {
        if (!running) return
        running = false
        val tail: FloatArray
        val hadVoice: Boolean
        synchronized(lock) { tail = segment.copyOf(segLen); hadVoice = voiced; segLen = 0 }
        flushThread = thread(name = "whisper-flush") {
            recordThread?.join(500); workerThread?.join(4000)
            if (hadVoice && tail.size > SAMPLE_RATE * 0.3 && ctx != 0L) appendCommitted(infer(tail))
            val c = committed.toString()
            post { listener?.onTranscript(c, ""); listener?.onListeningChanged(false) }
        }
    }

    override fun release() {
        running = false
        thread {
            workerThread?.join(5000)
            flushThread?.join(8000)
            synchronized(ctxLock) { if (ctx != 0L) { WhisperLib.freeContext(ctx); ctx = 0L } }
        }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val TAG = "QuranWhisper"
        private const val FRAME = 480            // 30 ms
        private const val POLL_MS = 60L
        const val PARTIAL_STEP_S = 0.4           // new voiced audio between partial inferences
        const val COMMIT_SILENCE_S = 0.55        // pause that ends an utterance
        const val MAX_UTTERANCE_S = 7.0          // sliding-chunk length for continuous recitation
        const val OVERLAP_S = 1.0                // audio carried into the next chunk
        private const val MIN_SPEECH_RMS = 0.015f
    }
}
