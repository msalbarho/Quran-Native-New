package com.quransunah.app.recitation.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlin.concurrent.thread

/** 16 kHz mono PCM source for the streaming recognizer. */
interface AudioInput {
    /** Starts delivering ~100 ms float chunks in [-1, 1] on a background thread. */
    fun start(onSamples: (FloatArray) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit = {})
    fun stop()
}

/** The microphone (VOICE_RECOGNITION source). */
class MicInput : AudioInput {
    @Volatile private var running = false
    private var t: Thread? = null

    @SuppressLint("MissingPermission")
    override fun start(onSamples: (FloatArray) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit) {
        running = true
        t = thread(name = "zipformer-mic", priority = Thread.MAX_PRIORITY) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = try {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SAMPLE_RATE))
            } catch (e: Exception) { null }
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { running = false; onError("تعذّر فتح الميكروفون"); return@thread }
            rec.startRecording()
            val buf = ShortArray(CHUNK)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                onSamples(FloatArray(n) { buf[it] / 32768f })
            }
            runCatching { rec.stop() }; rec.release()
            onEnd()
        }
    }

    override fun stop() { running = false; t?.join(500) }

    companion object { const val SAMPLE_RATE = 16000; const val CHUNK = 1600 }
}

/**
 * Feeds pre-recorded PCM in real time through exactly the same path as the microphone (used by the
 * instrumented on-device tests and the debug audio injection; never by normal users).
 */
class InjectedPcmInput(private val pcm: ShortArray, private val realtime: Boolean = true) : AudioInput {
    @Volatile private var running = false
    private var t: Thread? = null
    /** Elapsed-realtime (ms) when the first sample was delivered: audio time 0. */
    @Volatile var startedAtMs = 0L
        private set

    override fun start(onSamples: (FloatArray) -> Unit, onError: (String) -> Unit, onEnd: () -> Unit) {
        running = true
        t = thread(name = "zipformer-inject", priority = Thread.MAX_PRIORITY) {
            startedAtMs = SystemClock.elapsedRealtime()
            var pos = 0
            while (running && pos < pcm.size) {
                val n = minOf(MicInput.CHUNK, pcm.size - pos)
                val chunk = FloatArray(n) { pcm[pos + it] / 32768f }
                pos += n
                if (realtime) {
                    // deliver the chunk when it would have been fully recorded
                    val due = startedAtMs + pos * 1000L / MicInput.SAMPLE_RATE
                    val wait = due - SystemClock.elapsedRealtime()
                    if (wait > 0) Thread.sleep(wait)
                }
                onSamples(chunk)
            }
            running = false
            onEnd()
        }
    }

    override fun stop() { running = false; t?.join(500) }

    companion object {
        /** Reads a 16 kHz mono 16-bit PCM WAV file (header skipped). */
        fun readWav(bytes: ByteArray): ShortArray {
            var off = 12
            while (off + 8 <= bytes.size) {
                val id = String(bytes, off, 4, Charsets.US_ASCII)
                val size = (bytes[off + 4].toInt() and 0xff) or ((bytes[off + 5].toInt() and 0xff) shl 8) or
                    ((bytes[off + 6].toInt() and 0xff) shl 16) or ((bytes[off + 7].toInt() and 0xff) shl 24)
                if (id == "data") {
                    val n = minOf(size, bytes.size - off - 8) / 2
                    return ShortArray(n) { i -> ((bytes[off + 8 + 2 * i].toInt() and 0xff) or (bytes[off + 9 + 2 * i].toInt() shl 8)).toShort() }
                }
                off += 8 + size + (size and 1)
            }
            return ShortArray(0)
        }
    }
}
