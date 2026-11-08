package com.quransunah.app.recitation.core

import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Replays real Zipformer Quran 3.1 streaming transcripts (recorded with the same sherpa-onnx release and model as
 * the app, fed in 100 ms chunks) through [PhonemeAligner] and measures colouring against ground truth.
 * Needs the private, git-ignored test data (model output timelines + word phonemes generated from the user's
 * licensed model files), so it is skipped when they are absent, e.g. in a clean checkout.
 */
class ZipformerReplayTest {
    private val dataDir = File(System.getProperty("zf.test.dir") ?: System.getenv("ZF_TEST_DIR") ?: "/workspace/zf-test")
    private val assets = File("src/main/assets")

    data class W(val verse: Int, val pos: Int, val text: String)

    private fun loadWords(): Map<Int, List<W>> {
        val m = HashMap<Int, MutableList<W>>()
        GZIPInputStream(File("src/test/resources/recitation/words.tsvz").inputStream()).bufferedReader().forEachLine { l ->
            val p = l.split('\t'); if (p[2] == "w") m.getOrPut(p[0].toInt()) { mutableListOf() }.add(W(p[0].toInt(), p[1].toInt(), p[6]))
        }
        return m
    }
    private fun loadPhonemes(): Map<Long, String> {
        val m = HashMap<Long, String>()
        GZIPInputStream(File(assets, "zipformer/word_phonemes.tsvz").inputStream()).bufferedReader().forEachLine { l ->
            val p = l.split('\t'); m[p[0].toLong() * 1000 + p[1].toLong()] = p[2]
        }
        return m
    }

    @Test
    fun replayRealRecitations() {
        val tl = File(dataDir, "sessions/timeline.json"); val truthF = File(dataDir, "sessions/truth.json")
        assumeTrue(tl.exists() && truthF.exists() && File(assets, "zipformer/word_phonemes.tsvz").exists())
        val words = loadWords(); val ph = loadPhonemes()
        val timelines = JSONObject(tl.readText()); val truth = JSONObject(truthF.readText())
        val report = JSONObject()
        val pauseMs = (System.getenv("ZF_PAUSE") ?: "1200").toInt()
        for (sid in truth.keys()) {
            val t = truth.getJSONObject(sid)
            val verses = if (sid.contains("fatiha")) (1..7).toList() else (8..12).toList()
            val exp = verses.flatMap { words.getValue(it) }
            val opt = HashMap<Int, List<String>>()
            opt[0] = listOf(ZipformerPhrases.ISTIADHA) + if (verses.first() == 8) listOf(ZipformerPhrases.BASMALA) else emptyList()
            val al = PhonemeAligner(exp.map { ph.getValue(it.verse * 1000L + it.pos) }, exp.map { it.text }, opt,
                correctThreshold = (System.getenv("ZF_THR") ?: "0.7").toFloat())
            val firstSeen = LongArray(exp.size) { -1 }; val firstStatus = arrayOfNulls<WordStatus>(exp.size); val lastStatus = arrayOfNulls<WordStatus>(exp.size)
            var flips = 0; var shownRedCorrect = 0
            val tlArr = timelines.getJSONArray(sid)
            var lastT = 0L; var lastText = ""
            fun record(now: Long, live: LiveAlignment) {
                live.statuses.forEachIndexed { i, s ->
                    if (s != WordStatus.PENDING) {
                        if (firstSeen[i] < 0) { firstSeen[i] = now; firstStatus[i] = s }
                        else if (lastStatus[i] != s) { flips++ }
                        lastStatus[i] = s
                    }
                }
            }
            for (k in 0 until tlArr.length()) {
                val e = tlArr.getJSONArray(k); val now = e.getLong(0); val text = e.getString(1)
                if (lastText.isNotEmpty() && now - lastT >= pauseMs) record(lastT + pauseMs, al.update(lastText, pause = true))
                record(now, al.update(text)); lastT = now; lastText = text
            }
            val endT = (t.getDouble("duration_s") * 1000).toLong()
            if (endT - lastT >= pauseMs) record(lastT + pauseMs, al.update(lastText, pause = true))
            val fin = al.finish(); record(endT, fin)
            // ---- metrics ----
            val tw = t.getJSONArray("words")
            var ok = 0; var falseRed = 0; var correctN = 0; val lat = mutableListOf<Long>(); val rows = mutableListOf<String>()
            for (i in 0 until tw.length()) {
                val w = tw.getJSONObject(i); val want = WordStatus.valueOf(w.getString("expect")); val got = fin.statuses[i]
                if (got == want) ok++
                if (want == WordStatus.CORRECT) { correctN++; if (got == WordStatus.WRONG) falseRed++ }
                if (!w.isNull("end_ms") && firstSeen[i] >= 0 && firstStatus[i] == want) lat.add(firstSeen[i] - w.getLong("end_ms"))
                if (got != want) rows.add("${exp[i].verse}:${exp[i].pos} ${exp[i].text} want=$want got=$got heard=${fin.heard[i]}")
            }
            val extrasWant = t.getJSONArray("extras").length()
            lat.sort()
            val r = JSONObject().put("words", tw.length()).put("correct_status", ok).put("false_red", falseRed).put("correct_words", correctN)
                .put("extras_detected", fin.extras.size).put("extras_expected", extrasWant).put("flips", flips)
                .put("latency_ms_median", if (lat.isEmpty()) -1 else lat[lat.size / 2]).put("latency_ms_p90", if (lat.isEmpty()) -1 else lat[(lat.size * 9) / 10])
                .put("latency_ms_max", lat.lastOrNull() ?: -1).put("mismatches", rows).put("extras", fin.extras.map { "${it.afterExpectedIndex}:${it.text}" })
            report.put(sid, r)
            println("$sid: $r")
        }
        File("build/zipformer-replay-report.json").apply { parentFile.mkdirs() }.writeText(report.toString(1))
    }
}
