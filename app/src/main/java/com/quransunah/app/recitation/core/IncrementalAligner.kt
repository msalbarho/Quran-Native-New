package com.quransunah.app.recitation.core

/** One recognized token and how it was judged (null status = extra word). */
data class HeardToken(val text: String, val status: WordStatus?, val tentative: Boolean)

/** Snapshot of the live recitation state. */
data class LiveAlignment(
    val statuses: List<WordStatus>,
    val heard: List<String?>,
    /** Extra (inserted) words, keyed by the expected index they follow (-1 = before the first word). */
    val extras: List<ExtraWord>,
    /** Next expected word the reciter should say (-1 when finished). */
    val cursor: Int,
    /** Recognized tokens in order, coloured for an inline transcript. */
    val tokens: List<HeardToken>,
    /** Words up to this index are final and will not change. */
    val finalizedUpTo: Int,
) {
    val correct get() = statuses.count { it == WordStatus.CORRECT }
    val wrong get() = statuses.count { it == WordStatus.WRONG }
    val missing get() = statuses.count { it == WordStatus.MISSING }
    val evaluated get() = correct + wrong + missing
    val lastCovered: Int get() = statuses.indexOfLast { it != WordStatus.PENDING }
    val accuracy: Float get() = if (evaluated + extras.size == 0) 0f else correct.toFloat() / (evaluated + extras.size)

}

/**
 * Streaming, word-by-word aligner for live recitation feedback.
 *
 * Recognizers deliver `committed` text (finalized utterances, only ever grows) and `partial`
 * text (the current utterance hypothesis, may be revised). Only the *new* words are aligned, against
 * a forward window of expected words starting at the cursor, so each update costs O(window²) no matter
 * how long the session is.
 *  - Committed words are aligned once and frozen.
 *  - Partial words are aligned tentatively on top of the frozen state and recomputed on each update.
 *    The last partial word is still being spoken, so if it doesn't match it stays as the cursor and
 *    is not marked red yet.
 *  - Expected words that the reciter skipped become MISSING (grey) as soon as a later word matches.
 *  - When the reciter jumps ahead (e.g. skips a verse) the cursor re-anchors by searching further ahead.
 */
class IncrementalAligner(
    expected: List<String>,
    private val correctThreshold: Float = 0.85f,
    private val lookahead: Int = 12,
    private val reanchorRange: Int = 80,
) {
    private val exp: List<String> = expected.map { ArabicNormalizer.normalize(it) }
    private val n = exp.size
    private val aligner = WordAligner(correctThreshold)

    // frozen state
    private val fStatus = MutableList(n) { WordStatus.PENDING }
    private val fHeard = MutableList<String?>(n) { null }
    private val fExtras = mutableListOf<ExtraWord>()
    private val fTokens = mutableListOf<HeardToken>()
    private var fCursor = 0
    private var committedWordCount = 0

    var current: LiveAlignment = snapshot(fStatus, fHeard, fExtras, fCursor, fTokens)
        private set

    fun reset() {
        for (i in 0 until n) { fStatus[i] = WordStatus.PENDING; fHeard[i] = null }
        fExtras.clear(); fTokens.clear(); fCursor = 0; committedWordCount = 0
        current = snapshot(fStatus, fHeard, fExtras, fCursor, fTokens)
    }

    /** Feed the full committed transcript plus the current partial hypothesis. */
    fun update(committed: String, partial: String): LiveAlignment {
        val committedWords = ArabicNormalizer.words(committed)
        if (committedWords.size < committedWordCount) { // recognizer restarted its transcript
            committedWordCount = committedWords.size
        }
        if (committedWords.size > committedWordCount) {
            val newWords = committedWords.subList(committedWordCount, committedWords.size)
            applyChunk(newWords, fStatus, fHeard, fExtras, fTokens, cursorIn = fCursor, isFinal = true).also { fCursor = it }
            committedWordCount = committedWords.size
        }
        val partialWords = ArabicNormalizer.words(partial)
        current = if (partialWords.isEmpty()) {
            snapshot(fStatus, fHeard, fExtras, fCursor, fTokens)
        } else {
            val s = fStatus.toMutableList(); val h = fHeard.toMutableList()
            val e = fExtras.toMutableList(); val t = fTokens.toMutableList()
            val c = applyChunk(partialWords, s, h, e, t, cursorIn = fCursor, isFinal = false)
            snapshot(s, h, e, c, t)
        }
        return current
    }

    /** Aligns [words] starting at [cursorIn]; writes the results into the given state; returns the new cursor. */
    private fun applyChunk(
        words: List<String>, st: MutableList<WordStatus>, heard: MutableList<String?>, extras: MutableList<ExtraWord>,
        tokens: MutableList<HeardToken>, cursorIn: Int, isFinal: Boolean,
    ): Int {
        if (words.isEmpty()) return cursorIn
        var cursor = cursorIn
        if (cursor >= n) {
            words.forEach { extras.add(ExtraWord(n - 1, it)); tokens.add(HeardToken(it, null, !isFinal)) }
            return cursor
        }
        // Re-anchor if the first words clearly belong further ahead (reciter skipped part of the text).
        val anchor = findAnchor(words, cursor)
        if (anchor > cursor) {
            for (k in cursor until anchor) if (st[k] == WordStatus.PENDING) st[k] = WordStatus.MISSING
            cursor = anchor
        }
        val windowEnd = minOf(n, cursor + words.size + lookahead)
        val window = exp.subList(cursor, windowEnd)
        val r = aligner.align(window, words, live = true)
        val tokenStatus = r.recognizedStatus.toMutableList()
        var newCursor = cursor
        var lastMatchedLocal = -1
        for (i in r.statuses.indices) {
            val s = r.statuses[i]
            if (s == WordStatus.PENDING) continue
            st[cursor + i] = s
            heard[cursor + i] = r.heard[i]
            if (s != WordStatus.MISSING) { newCursor = cursor + i + 1; lastMatchedLocal = i }
        }
        val newExtras = r.extras.map { ExtraWord(cursor + it.afterExpectedIndex, it.text) }.toMutableList()
        if (!isFinal) {
            // The last partial word is still being spoken: don't judge it as wrong/extra yet.
            val last = words.lastIndex
            if (tokenStatus[last] == WordStatus.WRONG && lastMatchedLocal >= 0 && r.heard[lastMatchedLocal] == words[last]) {
                st[cursor + lastMatchedLocal] = WordStatus.PENDING
                heard[cursor + lastMatchedLocal] = null
                newCursor = cursor + lastMatchedLocal
                // skipped words right before it are not yet certain either
                var k = newCursor - 1
                while (k >= cursor && st[k] == WordStatus.MISSING) { st[k] = WordStatus.PENDING; k-- }
                if (k + 1 < newCursor) newCursor = k + 1
                tokenStatus[last] = WordStatus.PENDING
            } else if (tokenStatus[last] == null && newExtras.isNotEmpty() && newExtras.last().text == words[last]) {
                newExtras.removeAt(newExtras.lastIndex)
                tokenStatus[last] = WordStatus.PENDING
            }
        }
        extras.addAll(newExtras)
        words.forEachIndexed { i, w -> tokens.add(HeardToken(w, tokenStatus.getOrNull(i), !isFinal)) }
        return newCursor
    }

    /** Returns a later start index if the first recognized words match there much better than at the cursor. */
    private fun findAnchor(words: List<String>, cursor: Int): Int {
        val probe = words.take(3)
        if (probe.size < 2) return cursor
        fun score(at: Int): Int {
            var s = 0
            for (k in probe.indices) {
                val e = exp.getOrNull(at + k) ?: return s
                if (WordAligner.similarity(e, probe[k]) >= correctThreshold) s++
            }
            return s
        }
        // if the text near the cursor already matches, stay
        for (off in 0..2) if (score(cursor + off) >= probe.size - 1) return cursor
        val end = minOf(n - 1, cursor + reanchorRange)
        for (at in cursor + 3..end) if (score(at) == probe.size) return at
        return cursor
    }

    private fun snapshot(st: List<WordStatus>, heard: List<String?>, extras: List<ExtraWord>, cursor: Int, tokens: List<HeardToken>) =
        LiveAlignment(st.toList(), heard.toList(), extras.toList(), if (cursor >= n) -1 else cursor, tokens.toList(), fCursor - 1)
}
