package com.quransunah.app.recitation.core

import kotlin.math.max
import kotlin.math.min

enum class WordStatus { PENDING, CORRECT, WRONG, MISSING }

data class ExtraWord(val afterExpectedIndex: Int, val text: String)

data class AlignmentResult(
    val statuses: List<WordStatus>,
    /** Recognized word(s) aligned to each expected word (null if none). */
    val heard: List<String?>,
    val similarities: List<Float>,
    val extras: List<ExtraWord>,
    /** Index of last expected word covered by the recitation so far (-1 = nothing). */
    val lastCovered: Int,
    /** Status of every recognized word in order (null = extra word). */
    val recognizedStatus: List<WordStatus?> = emptyList(),
) {
    val correct get() = statuses.count { it == WordStatus.CORRECT }
    val wrong get() = statuses.count { it == WordStatus.WRONG }
    val missing get() = statuses.count { it == WordStatus.MISSING }
    val evaluated get() = correct + wrong + missing
    val accuracy: Float get() = if (evaluated + extras.size == 0) 0f else correct.toFloat() / (evaluated + extras.size)
}

/**
 * Needleman–Wunsch global/semi-global word alignment with fuzzy character similarity,
 * plus 1:2 and 2:1 merge transitions (e.g. Uthmani "يَـٰٓأَيُّهَا" vs recognizer "يا أيها").
 */
class WordAligner(
    /** Similarity at or above which a word counts as correctly recited. */
    private val correctThreshold: Float = 0.85f,
    private val matchScore: Float = 3f,
    private val gapPenalty: Float = -1.2f,
) {
    private enum class Op { NONE, DIAG, UP, LEFT, DIAG_E2, DIAG_R2 }

    /**
     * @param expected expected words (any script; normalized internally)
     * @param recognized recognized words
     * @param live when true, trailing expected words not yet reached are PENDING instead of MISSING
     */
    fun align(expected: List<String>, recognized: List<String>, live: Boolean): AlignmentResult {
        val e = expected.map { ArabicNormalizer.normalize(it) }
        val r = recognized.map { ArabicNormalizer.normalize(it) }.filter { it.isNotEmpty() }
        val n = e.size
        val m = r.size
        val statuses = MutableList(n) { if (live) WordStatus.PENDING else WordStatus.MISSING }
        val heard = MutableList<String?>(n) { null }
        val sims = MutableList(n) { 0f }
        if (m == 0 || n == 0) {
            return AlignmentResult(statuses, heard, sims, r.map { ExtraWord(-1, it) }, -1)
        }
        val score = Array(n + 1) { FloatArray(m + 1) }
        val trace = Array(n + 1) { arrayOfNulls<Op>(m + 1) }
        for (i in 1..n) { score[i][0] = i * gapPenalty; trace[i][0] = Op.UP }
        for (j in 1..m) { score[0][j] = j * gapPenalty; trace[0][j] = Op.LEFT }
        trace[0][0] = Op.NONE
        for (i in 1..n) {
            for (j in 1..m) {
                var best = score[i - 1][j - 1] + pairScore(similarity(e[i - 1], r[j - 1]))
                var op = Op.DIAG
                val up = score[i - 1][j] + gapPenalty
                if (up > best) { best = up; op = Op.UP }
                val left = score[i][j - 1] + gapPenalty
                if (left > best) { best = left; op = Op.LEFT }
                if (j >= 2) { // one expected word recognized as two words
                    val s = score[i - 1][j - 2] + pairScore(similarity(e[i - 1], r[j - 2] + r[j - 1])) + 0.2f
                    if (s > best && similarity(e[i - 1], r[j - 2] + r[j - 1]) >= correctThreshold) { best = s; op = Op.DIAG_R2 }
                }
                if (i >= 2) { // two expected words recognized as one word
                    val sim = similarity(e[i - 2] + e[i - 1], r[j - 1])
                    val s = score[i - 2][j - 1] + 2 * pairScore(sim) - 0.2f
                    if (s > best && sim >= correctThreshold) { best = s; op = Op.DIAG_E2 }
                }
                score[i][j] = best
                trace[i][j] = op
            }
        }
        // Semi-global in live mode: the recitation may stop anywhere in the expected text.
        var endI = n
        if (live) {
            var bestScore = Float.NEGATIVE_INFINITY
            for (i in 0..n) if (score[i][m] > bestScore) { bestScore = score[i][m]; endI = i }
        }
        val extras = mutableListOf<ExtraWord>()
        val recStatus = arrayOfNulls<WordStatus>(m)
        var i = endI
        var j = m
        var lastCovered = -1
        while (i > 0 || j > 0) {
            when (trace[i][j]) {
                Op.DIAG -> {
                    val sim = similarity(e[i - 1], r[j - 1])
                    statuses[i - 1] = if (sim >= correctThreshold) WordStatus.CORRECT else WordStatus.WRONG
                    heard[i - 1] = r[j - 1]; sims[i - 1] = sim
                    recStatus[j - 1] = statuses[i - 1]
                    lastCovered = max(lastCovered, i - 1)
                    i--; j--
                }
                Op.DIAG_R2 -> {
                    val txt = r[j - 2] + " " + r[j - 1]
                    statuses[i - 1] = WordStatus.CORRECT; heard[i - 1] = txt
                    recStatus[j - 1] = WordStatus.CORRECT; recStatus[j - 2] = WordStatus.CORRECT
                    sims[i - 1] = similarity(e[i - 1], r[j - 2] + r[j - 1])
                    lastCovered = max(lastCovered, i - 1)
                    i--; j -= 2
                }
                Op.DIAG_E2 -> {
                    val sim = similarity(e[i - 2] + e[i - 1], r[j - 1])
                    statuses[i - 1] = WordStatus.CORRECT; statuses[i - 2] = WordStatus.CORRECT
                    heard[i - 1] = r[j - 1]; heard[i - 2] = r[j - 1]
                    recStatus[j - 1] = WordStatus.CORRECT
                    sims[i - 1] = sim; sims[i - 2] = sim
                    lastCovered = max(lastCovered, i - 1)
                    i -= 2; j--
                }
                Op.UP -> { statuses[i - 1] = WordStatus.MISSING; i-- }
                Op.LEFT -> { extras.add(ExtraWord(i - 1, r[j - 1])); j-- }
                else -> break
            }
        }
        if (live) {
            // expected words after the last aligned position are still pending
            for (k in endI until n) statuses[k] = WordStatus.PENDING
            if (lastCovered < endI - 1) for (k in lastCovered + 1 until endI) if (statuses[k] == WordStatus.MISSING) statuses[k] = WordStatus.PENDING
        }
        extras.reverse()
        return AlignmentResult(statuses, heard, sims, extras, lastCovered, recStatus.toList())
    }

    private fun pairScore(sim: Float): Float = when {
        sim >= correctThreshold -> matchScore * sim
        sim >= 0.5f -> 0.5f * sim       // probably the same word, mispronounced
        else -> -1.5f                   // substitution
    }

    companion object {
        /** Fuzzy similarity in [0,1] between two (already normalized) words. */
        fun similarity(a: String, b: String): Float {
            if (a == b) return 1f
            if (a.isEmpty() || b.isEmpty()) return 0f
            val sa = ArabicNormalizer.skeleton(a)
            val sb = ArabicNormalizer.skeleton(b)
            if (sa.isNotEmpty() && sa == sb) return 0.97f
            // Uthmani rasm writes some long vowels with waw (ٱلسَّمَٰوَٰتِ / ٱلصَّلَوٰةَ): ignore word-internal waw
            val wa = stripInnerWaw(sa); val wb = stripInnerWaw(sb)
            if (wa.length >= 2 && wa == wb) return 0.93f
            val d1 = levenshtein(a, b)
            val r1 = 1f - d1.toFloat() / max(a.length, b.length)
            val r2 = if (sa.isNotEmpty() && sb.isNotEmpty()) (1f - levenshtein(sa, sb).toFloat() / max(sa.length, sb.length)) * 0.95f else 0f
            return max(r1, r2).coerceIn(0f, 1f)
        }

        private fun stripInnerWaw(s: String): String =
            if (s.length < 3) s else buildString { s.forEachIndexed { i, c -> if (c != 'و' || i == 0 || i == s.lastIndex) append(c) } }

        fun levenshtein(a: String, b: String): Int {
            if (a == b) return 0
            var prev = IntArray(b.length + 1) { it }
            var cur = IntArray(b.length + 1)
            for (i in 1..a.length) {
                cur[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else substitutionCost(a[i - 1], b[j - 1])
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                }
                val t = prev; prev = cur; cur = t
            }
            return prev[b.length]
        }

        private fun substitutionCost(x: Char, y: Char): Int = 1
    }
}
