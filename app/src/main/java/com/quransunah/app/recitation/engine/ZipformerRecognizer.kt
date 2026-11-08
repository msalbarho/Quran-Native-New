package com.quransunah.app.recitation.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.quransunah.app.recitation.core.LiveAlignment
import com.quransunah.app.recitation.core.PhonemeAligner
import com.quransunah.app.recitation.core.QuranStructure
import com.quransunah.app.recitation.core.ZipformerPhrases
import com.quransunah.app.recitation.core.Strictness
import com.quransunah.app.recitation.core.RecitationWord as Word
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineZipformer2CtcModelConfig
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Bundled Zipformer Quran 3.1 (Quran Lab, NPL-1.2) loaded through sherpa-onnx (Apache-2.0) from the APK assets,
 * plus the per-word canonical phonemes of the whole mushaf. Loaded once, lazily, on a background thread.
 */
class ZipformerEngine(private val context: Context) {
    @Volatile private var recognizer: OnlineRecognizer? = null
    @Volatile private var lexicon: Map<Long, String>? = null
    private val lock = Object()
    @Volatile var loadMs = 0L
        private set

    /** True if the model files are bundled in this build (the source zip ships without them). */
    val bundled: Boolean by lazy {
        runCatching { context.assets.list(DIR)?.toSet().orEmpty().containsAll(listOf(MODEL, TOKENS, LEXICON)) }.getOrDefault(false)
    }
    val nativeOk: Boolean by lazy { runCatching { System.loadLibrary("sherpa-onnx-jni"); true }.getOrElse { Log.w(TAG, "no sherpa-onnx", it); false } }
    fun isAvailable() = bundled && nativeOk

    fun get(): Pair<OnlineRecognizer, Map<Long, String>> {
        recognizer?.let { r -> lexicon?.let { return r to it } }
        synchronized(lock) {
            if (recognizer == null || lexicon == null) {
                val t0 = SystemClock.elapsedRealtime()
                lexicon = loadLexicon()
                val cfg = OnlineRecognizerConfig(
                    featConfig = FeatureConfig(sampleRate = 16000, featureDim = 80),
                    modelConfig = OnlineModelConfig(
                        zipformer2Ctc = OnlineZipformer2CtcModelConfig(model = "$DIR/$MODEL"),
                        tokens = "$DIR/$TOKENS",
                        numThreads = Runtime.getRuntime().availableProcessors().coerceIn(1, 2),
                        provider = "cpu",
                    ),
                    endpointConfig = EndpointConfig(),
                    // pauses are detected by the app itself; the transcript is never reset mid-session
                    enableEndpoint = false,
                    decodingMethod = "greedy_search",
                )
                recognizer = OnlineRecognizer(context.assets, cfg)
                loadMs = SystemClock.elapsedRealtime() - t0
                Log.i(TAG, "Zipformer loaded in $loadMs ms")
            }
            return recognizer!! to lexicon!!
        }
    }

    private fun loadLexicon(): Map<Long, String> {
        val m = HashMap<Long, String>(80_000)
        GZIPInputStream(context.assets.open("$DIR/$LEXICON")).bufferedReader().forEachLine { l ->
            val a = l.indexOf('\t'); val b = l.indexOf('\t', a + 1)
            if (a > 0 && b > a) m[l.substring(0, a).toLong() * 1000 + l.substring(a + 1, b).toLong()] = l.substring(b + 1)
        }
        return m
    }

    companion object {
        const val DIR = "zipformer"
        const val MODEL = "model.int8.onnx"
        const val TOKENS = "tokens.txt"
        const val LEXICON = "word_phonemes.tsvz"
        const val LICENSE = "zipformer/LICENSE.txt"
        private const val TAG = "QuranZipformer"
    }
}

/**
 * Streaming recitation checker: microphone → sherpa-onnx OnlineRecognizer (Zipformer Quran 3.1, cache-aware
 * streaming CTC, 0.48 s chunks) → [PhonemeAligner] → live word statuses.
 *
 * Everything heavy runs on a dedicated worker thread; only finished [LiveAlignment] snapshots are posted to the
 * main thread. Tracking is continuous while reciting: words turn green as soon as their phonemes are heard;
 * red/grey only once final (the reciter moved on, a pause of [PAUSE_MS], or Stop).
 */
class ZipformerRecognizer(
    private val engine: ZipformerEngine,
    /** Audio source factory: the microphone, or injected PCM in on-device tests. */
    private val input: () -> AudioInput = { MicInput() },
) : AligningRecognizer {
    override val id = "zipformer"
    override val displayName = "Zipformer Quran 3.1 على الجهاز"

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var worker: Thread? = null
    private var audio: AudioInput? = null
    private var listener: RecitationRecognizer.Listener? = null
    private var expected: List<Word> = emptyList()
    private var threshold = 0.7f
    private var queue = LinkedBlockingQueue<FloatArray>()
    private val END = FloatArray(0)
    /** Incremented per start(): late results of an earlier, stopped session are dropped. */
    @Volatile private var session = 0

    /** Diagnostics for tests: total decode time and audio fed (ms). */
    @Volatile var decodeMsTotal = 0L; private set
    @Volatile var audioMsTotal = 0L; private set
    /** Optional observer (tests): called on the worker thread with every new alignment. */
    @Volatile var debugObserver: ((LiveAlignment, String) -> Unit)? = null

    override fun isAvailable() = engine.isAvailable()
    override fun warmUp() { if (isAvailable()) thread(name = "zipformer-warmup") { runCatching { engine.get() } } }

    override fun prepare(expected: List<Word>, strictness: Strictness) {
        this.expected = expected
        threshold = when (strictness) { Strictness.LENIENT -> 0.6f; Strictness.NORMAL -> 0.7f; Strictness.STRICT -> 0.8f }
    }

    override fun start(listener: RecitationRecognizer.Listener) {
        if (running) return
        this.listener = listener
        if (!isAvailable()) { listener.onError("محرك Zipformer غير متوفر في هذا الإصدار.", true); return }
        if (expected.isEmpty()) { listener.onError("لا يوجد نص متوقع للتسميع.", true); return }
        running = true
        val my = ++session
        val q = LinkedBlockingQueue<FloatArray>(); queue = q
        decodeMsTotal = 0; audioMsTotal = 0
        val words = expected
        val previous = worker
        worker = thread(name = "zipformer-worker", priority = Thread.MAX_PRIORITY) {
            // a reused recognizer: let the previous session flush and release the microphone first
            previous?.join(2000)
            val (rec, lex) = try { engine.get() } catch (e: Throwable) {
                running = false; post { listener.onError("تعذّر تحميل نموذج Zipformer: ${e.message}", true) }; return@thread
            }
            val aligner = PhonemeAligner(
                words.map { lex[it.verseId * 1000L + it.position] ?: "" },
                words.map { it.text },
                optionalPhrases(words),
                correctThreshold = threshold,
            )
            val stream = rec.createStream()
            val src = input(); audio = src
            if (session != my) { q.put(END) }
            src.start(onSamples = { q.put(it) }, onError = { m -> post { listener.onError(m, true) }; q.put(END) }, onEnd = { q.put(END) })
            var text = ""; var lastChange = SystemClock.elapsedRealtime(); var paused = true
            var levelTick = 0
            var announced = false
            fun publish(live: LiveAlignment) {
                debugObserver?.invoke(live, text)
                val t = text
                post { if (session == my) listener.onLiveAlignment(live, t) }
            }
            try {
                while (true) {
                    val chunk = q.poll(100, TimeUnit.MILLISECONDS)
                    if (chunk === END || ((!running || session != my) && chunk == null)) break
                    if (chunk != null) {
                        // the mic is really delivering audio: only now tell the UI it is listening
                        if (!announced) { announced = true; post { if (session == my) listener.onListeningChanged(true) } }
                        audioMsTotal += chunk.size / 16
                        val t0 = SystemClock.elapsedRealtime()
                        stream.acceptWaveform(chunk, 16000)
                        while (rec.isReady(stream)) rec.decode(stream)
                        decodeMsTotal += SystemClock.elapsedRealtime() - t0
                        if (++levelTick % 2 == 0) {
                            var s = 0.0; for (f in chunk) s += f * f
                            val lvl = (sqrt(s / chunk.size).toFloat() * 12f).coerceIn(0f, 1f)
                            post { listener.onLevel(lvl) }
                        }
                        val now = rec.getResult(stream).text
                        if (now != text) {
                            text = now; lastChange = SystemClock.elapsedRealtime(); paused = false
                            publish(aligner.update(text))
                        }
                    }
                    if (!paused && text.isNotEmpty() && SystemClock.elapsedRealtime() - lastChange >= PAUSE_MS) {
                        paused = true; publish(aligner.update(text, pause = true))
                    }
                }
                // flush: let the model emit what is still in its lookahead
                stream.acceptWaveform(FloatArray(8000), 16000)
                stream.inputFinished()
                while (rec.isReady(stream)) rec.decode(stream)
                text = rec.getResult(stream).text
                aligner.update(text)
                publish(aligner.finish())
            } catch (e: Throwable) {
                Log.e("QuranZipformer", "worker", e)
                post { listener.onError("خطأ في محرك Zipformer: ${e.message}", false) }
            } finally {
                stream.release()
                src.stop()
                if (session == my) { running = false; audio = null }
                if (announced) post { if (session == my) listener.onListeningChanged(false) }
                else post { if (session == my) { listener.onListeningChanged(true); listener.onListeningChanged(false) } }
            }
        }
    }

    private fun optionalPhrases(words: List<Word>): Map<Int, List<String>> {
        val m = HashMap<Int, List<String>>()
        words.forEachIndexed { k, w ->
            val list = mutableListOf<String>()
            if (k == 0) list.add(ZipformerPhrases.ISTIADHA)
            if (w.position == 1 && QuranStructure.basmalaBefore(w.verseId)) list.add(ZipformerPhrases.BASMALA)
            if (list.isNotEmpty()) m[k] = list
        }
        return m
    }

    private fun post(r: () -> Unit) = main.post(r)

    override fun stop() {
        if (!running) return
        running = false
        // non-blocking: the worker stops (and joins) the audio source itself
        queue.put(END)
    }

    /** Stops and drops any late result of the current session (e.g. leaving the screen). */
    fun cancel() { stop(); session++ }

    override fun release() { stop() }

    companion object {
        /** No new phonemes for this long = a pause: words already passed become final. */
        const val PAUSE_MS = 700L
    }
}
