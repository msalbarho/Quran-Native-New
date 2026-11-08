package com.quransunah.app.recitation.core

/**
 * Normalisation of the Zipformer Quran phonetic script (251 symbols, tajweed included) for matching.
 *
 * The model writes tajweed explicitly (madd length as repeated letters, gemination, ghunna, ikhfa, qalqala…).
 * Its model card warns that free-choice madd lengths and ikhfa/idgham quality are unreliable, and reciters may
 * legitimately differ there, so those distinctions are removed before comparison:
 *  - qalqala/sakt/other marks are dropped (ڇ ۜ ؙ ۪ ـ),
 *  - ikhfa noon ں → ن, ikhfa/iqlab meem ۾ → م, madd letters ۥ → و, ۦ → ي, ٲ → ا,
 *  - a run of the same symbol (madd 2/4/6, shadda, ghunna) counts once.
 * Short vowels (fatha/damma/kasra) and consonants are kept, so a wrong word or a clearly wrong vowel still costs.
 */
object PhonemeNormalizer {
    private val dropped = setOf('\u0687', '\u06DC', '\u0619', '\u06EA', '\u0640', ' ')
    private fun map(c: Char): Char = when (c) {
        '\u06BA' -> '\u0646'   // ں ikhfa noon -> ن
        '\u06FE' -> '\u0645'   // ۾ -> م
        '\u06E5' -> '\u0648'   // ۥ -> و
        '\u06E6' -> '\u064A'   // ۦ -> ي
        '\u0672' -> '\u0627'   // ٲ -> ا
        else -> c
    }

    /** Normalised string plus, for each normalised char, the index of the raw char it came from. */
    class Result(val text: String, val rawIndex: IntArray)

    fun normalize(raw: String): Result {
        val sb = StringBuilder(raw.length); val idx = IntArray(raw.length); var n = 0
        for (i in raw.indices) {
            val ch = raw[i]
            if (ch in dropped) continue
            val m = map(ch)
            if (n > 0 && sb[n - 1] == m) continue
            sb.append(m); idx[n++] = i
        }
        return Result(sb.toString(), idx.copyOf(n))
    }

    /**
     * Incremental [normalize]: when the new transcript extends the previous one, only the appended characters are
     * processed (O(new) per update instead of O(total)). Any other change falls back to a full normalisation.
     * The result is always identical to [normalize].
     */
    class Incremental {
        private var raw = ""
        private val sb = StringBuilder()
        private var idx = IntArray(64)
        private var cached: Result = Result("", IntArray(0))

        fun normalize(transcript: String): Result {
            if (transcript == raw) return cached
            if (!transcript.startsWith(raw)) { raw = ""; sb.setLength(0) }
            for (i in raw.length until transcript.length) {
                val ch = transcript[i]
                if (ch in dropped) continue
                val m = map(ch)
                if (sb.isNotEmpty() && sb[sb.length - 1] == m) continue
                if (sb.length == idx.size) idx = idx.copyOf(idx.size * 2)
                idx[sb.length] = i; sb.append(m)
            }
            raw = transcript
            cached = Result(sb.toString(), idx.copyOf(sb.length))
            return cached
        }
    }

    fun isVowel(c: Char) = c == '\u064E' || c == '\u064F' || c == '\u0650'
    fun isLongVowel(c: Char) = c == '\u0627' || c == '\u0648' || c == '\u064A'

    private val similar: Set<Long> = listOf(
        "سص", "تط", "دض", "ذظ", "ذز", "ثس", "هح", "ءع", "كق", "ظض", "حخ", "ءا", "نم",
    ).flatMap { listOf(pair(it[0], it[1]), pair(it[1], it[0])) }.toSet()
    private fun pair(a: Char, b: Char) = (a.code.toLong() shl 16) or b.code.toLong()

    /** Substitution cost (0 = same). Vowel confusions and phonetically close consonants are cheaper. */
    fun sub(a: Char, b: Char): Float = when {
        a == b -> 0f
        isVowel(a) && isVowel(b) -> 0.5f
        (isVowel(a) && isLongVowel(b)) || (isLongVowel(a) && isVowel(b)) -> 0.5f
        pair(a, b) in similar -> 0.6f
        else -> 1f
    }
    fun indel(c: Char): Float = if (isVowel(c)) 0.5f else 1f
}

/**
 * Streaming phoneme-level aligner for the Zipformer Quran recognizer: turns the continuously growing
 * phoneme transcript into word-by-word statuses for the expected text (same [LiveAlignment] the UI already
 * renders: green = correct, red = wrong, grey = missing, "+" = extra).
 *
 * Each update aligns only the not-yet-final tail of the transcript against a forward window of expected
 * words (semi-global edit distance with a free end, a "skip whole word" transition, and zero-cost optional
 * phrases such as the isti'adha / basmala). Safeguards against hasty judgements:
 *  - a word turns green as soon as all of its phonemes are heard with similarity ≥ [correctThreshold];
 *  - a word is only marked red (or grey when skipped) once it is *final*: the reciter has clearly moved on
 *    (≥ [moveOnEvidence] matching phonemes of later words), a pause was detected, or the session ended;
 *  - an insertion between words becomes an extra "+" word only when it is final and at least
 *    [extraMinChars] normalised phonemes long (shorter blips such as a waqf/wasl vowel are tolerated);
 *  - the word currently being recited is never judged while it may still be incomplete.
 */
class PhonemeAligner(
    expectedPhonemes: List<String>,
    private val display: List<String>,
    /** Optional phrases (raw phonemes) that may be recited right before word k without counting as extra. */
    optionalBefore: Map<Int, List<String>> = emptyMap(),
    private val correctThreshold: Float = 0.7f,
    private val moveOnEvidence: Int = 3,
    private val extraMinChars: Int = 4,
    private val skipCost: Float = 1.5f,
    private val lookaheadWords: Int = 30,
) {
    private class Unit(val word: Int, val start: Int, val end: Int) { val len get() = end - start }

    private val n = expectedPhonemes.size
    private val e: String
    /** Cost of not hearing expected char j (cheap for a verse-initial hamzat al-wasl, dropped when verses are joined). */
    private val delCost: FloatArray
    private val units: List<Unit>
    /** First unit index of each word. */
    private val unitOfWord: IntArray

    init {
        require(display.size == n)
        val sb = StringBuilder(); val us = ArrayList<Unit>(); val uow = IntArray(n)
        val cheap = HashSet<Int>()
        fun add(word: Int, raw: String) {
            val t = PhonemeNormalizer.normalize(raw).text
            val s = sb.length
            var k = 0
            // a run continuing across the word boundary counts once (unless the unit would become empty)
            if (t.isNotEmpty() && sb.isNotEmpty() && sb[sb.length - 1] == t[0] && t.length > 1) k = 1
            sb.append(t, k, t.length)
            us.add(Unit(word, s, sb.length))
            // "ٱ…" read as ءَ/ءِ/ءُ only when starting after a stop: optional when the reciter joins the verses
            if (word >= 0 && display[word].startsWith('\u0671') && k == 0 && t.length > 2 && t[0] == '\u0621' && PhonemeNormalizer.isVowel(t[1])) {
                cheap.add(s); cheap.add(s + 1)
            }
        }
        for (w in 0 until n) {
            optionalBefore[w]?.forEach { add(-1, it) }
            uow[w] = us.size
            add(w, expectedPhonemes[w])
        }
        e = sb.toString(); units = us; unitOfWord = uow
        delCost = FloatArray(e.length) { if (it in cheap) 0.1f else PhonemeNormalizer.indel(e[it]) }
    }

    // ---- frozen state ----
    private val fStatus = MutableList(n) { WordStatus.PENDING }
    private val fHeard = MutableList<String?>(n) { null }
    private val fExtras = mutableListOf<ExtraWord>()
    private val fTokens = mutableListOf<HeardToken>()
    /** First unit that is not final yet. */
    private var cu = 0
    /** Normalised transcript index where the non-final part starts. */
    private var rOffset = 0
    private var lastRaw = ""
    /** Normalises only the newly appended part of the (growing) transcript. */
    private val normalizer = PhonemeNormalizer.Incremental()

    var current: LiveAlignment = snapshot(fStatus, fHeard, fTokens, cursorWord())
        private set

    /** Index of the last word that is final (-1 = none). */
    val finalizedUpTo: Int get() = lastFinalWord()

    /**
     * @param transcript the recognizer's full phoneme transcript of this session (only ever grows)
     * @param pause true when the reciter paused (no new phonemes for a while): words already passed become final
     * @param end true when the session is over: everything heard becomes final, including the last word
     */
    fun update(transcript: String, pause: Boolean = false, end: Boolean = false): LiveAlignment {
        lastRaw = transcript
        val norm = normalizer.normalize(transcript)
        val r = norm.text
        if (rOffset > r.length) rOffset = r.length
        val m = r.length - rOffset
        if (cu >= units.size) {
            // past the last word: anything else is extra
            if (end && m >= extraMinChars) {
                fExtras.add(ExtraWord(n - 1, rawSpan(norm, rOffset, r.length)))
                fTokens.add(HeardToken(rawSpan(norm, rOffset, r.length), null, false)); rOffset = r.length
            }
            current = snapshot(fStatus, fHeard, fTokens, cursorWord()); return current
        }
        if (m == 0 && !end) { current = snapshot(fStatus, fHeard, fTokens, cursorWord()); return current }
        if (m > MAX_ACTIVE) {
            // a long stretch that matches nothing (e.g. reciting another passage): forget the oldest part
            rOffset = r.length - MAX_ACTIVE / 2
            return update(transcript, pause, end)
        }
        val pauseNow = pause || m > FORCE_PAUSE_CHARS

        // ---- window ----
        var uEnd = cu; var chars = 0; var words = 0
        val needChars = (m * 1.4f).toInt() + 40
        while (uEnd < units.size && (chars < needChars || words < 3) && words < lookaheadWords + m / 3) {
            chars += units[uEnd].len; if (units[uEnd].word >= 0) words++; uEnd++
        }
        val wu = units.subList(cu, uEnd)
        val base = wu.first().start
        val L = wu.last().end - base
        // unit index (within window) whose end column is j; -1 if none
        val endsAt = IntArray(L + 1) { -1 }
        wu.forEachIndexed { k, u -> if (u.len > 0) endsAt[u.end - base] = k }
        val unitAtCol = IntArray(L + 1) // unit containing column j (char j-1), for j>=1
        wu.forEachIndexed { k, u -> for (j in (u.start - base + 1)..(u.end - base)) unitAtCol[j] = k }

        // ---- DP: rows = transcript chars, cols = expected chars ----
        val W = L + 1
        val d = FloatArray((m + 1) * W) { Float.MAX_VALUE }
        val tr = ByteArray((m + 1) * W)
        d[0] = 0f
        for (i in 0..m) {
            for (j in 0..L) {
                if (i == 0 && j == 0) continue
                var best = Float.MAX_VALUE; var op: Byte = 0
                if (i > 0 && j > 0) {
                    val v = d[(i - 1) * W + j - 1] + PhonemeNormalizer.sub(r[rOffset + i - 1], e[base + j - 1])
                    if (v < best) { best = v; op = 1 }
                }
                if (j > 0) {
                    val v = d[i * W + j - 1] + delCost[base + j - 1]
                    if (v < best) { best = v; op = 2 }
                }
                if (i > 0) {
                    // an insertion inside a word costs a hair more, so ties put extra material between words
                    val v = d[(i - 1) * W + j] + PhonemeNormalizer.indel(r[rOffset + i - 1]) + if (j == 0 || endsAt[j] >= 0) 0f else 0.01f
                    if (v < best) { best = v; op = 3 }
                }
                val k = endsAt[j]
                if (k >= 0) {
                    val u = wu[k]
                    val v = d[i * W + (u.start - base)] + if (u.word < 0) 0f else skipCost
                    if (v < best) { best = v; op = 4 }
                }
                d[i * W + j] = best; tr[i * W + j] = op
            }
        }
        var jEnd = 0
        for (j in 0..L) if (d[m * W + j] < d[m * W + jEnd] - 1e-4f) jEnd = j

        // ---- backtrack ----
        val ku = wu.size
        val matched = IntArray(ku); val cost = FloatArray(ku); val skipped = BooleanArray(ku)
        val rFirst = IntArray(ku) { -1 }; val rLast = IntArray(ku) { -1 }
        val gapIns = arrayOfNulls<MutableList<Int>>(ku + 1) // insertions before unit k (k = ku: after the end)
        var i = m; var j = jEnd
        while (i > 0 || j > 0) {
            when (tr[i * W + j].toInt()) {
                1 -> {
                    val k = unitAtCol[j]; val c = PhonemeNormalizer.sub(r[rOffset + i - 1], e[base + j - 1])
                    if (c == 0f) matched[k]++
                    cost[k] += c; rFirst[k] = i - 1; if (rLast[k] < 0) rLast[k] = i - 1
                    i--; j--
                }
                2 -> { val k = unitAtCol[j]; cost[k] += delCost[base + j - 1]; j-- }
                3 -> {
                    // inside a unit (not at its boundary) -> counts against that unit; at a boundary -> gap
                    val k = if (j == 0) -1 else unitAtCol[j]
                    if (k >= 0 && j < wu[k].end - base) {
                        cost[k] += PhonemeNormalizer.indel(r[rOffset + i - 1]); rFirst[k] = i - 1; if (rLast[k] < 0) rLast[k] = i - 1
                    } else {
                        val g = if (j == 0) 0 else unitAtCol[j] + 1
                        (gapIns[g] ?: mutableListOf<Int>().also { gapIns[g] = it }).add(i - 1)
                    }
                    i--
                }
                4 -> { val k = endsAt[j]; skipped[k] = true; j = wu[k].start - base }
                else -> break
            }
        }
        // unit containing the end of the alignment
        val endUnit = if (jEnd == 0) -1 else unitAtCol[jEnd]
        val endComplete = endUnit >= 0 && jEnd == wu[endUnit].end - base
        // evidence of moving on: exact phoneme matches after unit k
        val after = IntArray(ku + 1)
        for (k in ku - 1 downTo 0) after[k] = after[k + 1] + matched[k]
        // A skipped word with other material recited in its place is a wrong word, not "missing + extra".
        val substitute = arrayOfNulls<List<Int>>(ku)
        for (k in 0 until ku) {
            if (wu[k].word < 0 || !(skipped[k] || rFirst[k] < 0)) continue
            val g = gapIns[k + 1]?.takeIf { it.size >= 3 && (k + 1 >= ku || wu[k + 1].word >= 0) }?.also { gapIns[k + 1] = null }
                ?: gapIns[k]?.takeIf { it.size >= 3 && (k == 0 || wu[k - 1].word >= 0) }?.also { gapIns[k] = null }
            if (g != null) {
                substitute[k] = g; skipped[k] = false
                rFirst[k] = g.min(); rLast[k] = g.max(); cost[k] = maxOf(cost[k], wu[k].len.toFloat())
            }
        }
        fun sim(k: Int) = if (wu[k].len == 0) 1f else 1f - cost[k] / wu[k].len.coerceAtLeast(1)
        fun judged(k: Int): WordStatus = when {
            skipped[k] || rFirst[k] < 0 -> WordStatus.MISSING
            matched[k] == 0 -> WordStatus.WRONG
            sim(k) >= correctThreshold -> WordStatus.CORRECT
            else -> WordStatus.WRONG
        }

        // ---- decide which units become final ----
        var lastFinal = -1
        for (k in 0 until ku) {
            val passed = k < endUnit || (k == endUnit && endComplete)
            if (!passed) break
            val final = end || (k < endUnit && (pauseNow || after[k + 1] >= moveOnEvidence)) ||
                (k == endUnit && pauseNow && judged(k) == WordStatus.CORRECT)
            if (!final) break
            lastFinal = k
        }
        if (end && endUnit >= 0) {
            lastFinal = maxOf(lastFinal, endUnit)
            // the session ended inside this word: its unheard rest counts as deleted
            for (jj in jEnd until wu[endUnit].end - base) cost[endUnit] += delCost[base + jj]
        }

        // ---- freeze ----
        val tokensTentative = mutableListOf<HeardToken>()
        val sT = fStatus.toMutableList(); val hT = fHeard.toMutableList()
        var consumed = rOffset
        for (k in 0 until ku) {
            val u = wu[k]
            val isFinal = k <= lastFinal
            // gap insertions before this unit
            gapIns[k]?.let { g -> handleGap(g, k, wu, skipped, norm, isFinal, tokensTentative) }
            if (u.word >= 0) {
                val span = if (rFirst[k] >= 0) rawSpan(norm, rOffset + rFirst[k], rOffset + rLast[k] + 1) else null
                val inWindow = k < endUnit || (k == endUnit)
                if (isFinal) {
                    val st = judged(k)
                    fStatus[u.word] = st; fHeard[u.word] = span
                    if (st != WordStatus.MISSING) fTokens.add(HeardToken(if (st == WordStatus.CORRECT) display[u.word] else span ?: "", st, false))
                } else if (inWindow) {
                    val passed = k < endUnit || endComplete
                    val st = judged(k)
                    if (passed && st == WordStatus.CORRECT) {
                        sT[u.word] = WordStatus.CORRECT; hT[u.word] = span
                        tokensTentative.add(HeardToken(display[u.word], WordStatus.CORRECT, true))
                    } else if (span != null) {
                        tokensTentative.add(HeardToken(span, WordStatus.PENDING, true))
                    }
                }
            }
            if (isFinal) {
                if (rLast[k] >= 0) consumed = maxOf(consumed, rOffset + rLast[k] + 1)
                // boundary insertions right after a final unit are final too (extras / tolerated blips)
                if (k == lastFinal) gapIns[k + 1]?.let { g ->
                    if (k + 1 < ku || end) { handleGap(g, k + 1, wu, skipped, norm, true, tokensTentative); consumed = maxOf(consumed, rOffset + g.max() + 1) }
                    gapIns[k + 1] = null
                }
            }
        }
        if (end) gapIns[ku]?.let { g -> handleGap(g, ku, wu, skipped, norm, true, tokensTentative); consumed = maxOf(consumed, rOffset + g.max() + 1) }
        if (lastFinal >= 0) { cu += lastFinal + 1; rOffset = consumed }
        if (end) rOffset = r.length
        // tentative = frozen + tentative greens
        for (w in 0 until n) if (fStatus[w] != WordStatus.PENDING) { sT[w] = fStatus[w]; hT[w] = fHeard[w] }
        val toks = fTokens.toList() + tokensTentative.filter { it.tentative }
        current = snapshot(sT, hT, toks, cursorWord(sT))
        return current
    }

    /** Ends the session: the last word and any trailing material become final. */
    fun finish(): LiveAlignment = update(lastRaw, pause = true, end = true)

    companion object {
        private const val MAX_ACTIVE = 600
        private const val FORCE_PAUSE_CHARS = 300
    }

    private fun handleGap(g: List<Int>, beforeUnit: Int, wu: List<Unit>, skipped: BooleanArray, norm: PhonemeNormalizer.Result,
                          final: Boolean, tentative: MutableList<HeardToken>) {
        if (!final) return
        val sorted = g.sorted()
        if (sorted.size < extraMinChars) return
        // material next to an optional phrase that was recited belongs to that phrase
        val prevOpt = beforeUnit - 1 in wu.indices && wu[beforeUnit - 1].word < 0 && !skipped[beforeUnit - 1]
        val nextOpt = beforeUnit in wu.indices && wu[beforeUnit].word < 0 && !skipped[beforeUnit]
        if (prevOpt || nextOpt) return
        val afterWord = run {
            var k = beforeUnit - 1
            while (k >= 0 && wu[k].word < 0) k--
            if (k >= 0) wu[k].word else (cuWordBefore(wu))
        }
        val text = rawSpan(norm, rOffset + sorted.first(), rOffset + sorted.last() + 1)
        fExtras.add(ExtraWord(afterWord, text))
        fTokens.add(HeardToken(text, null, false))
    }

    private fun cuWordBefore(wu: List<Unit>): Int {
        val first = wu.firstOrNull { it.word >= 0 }?.word ?: n
        return first - 1
    }

    private fun rawSpan(norm: PhonemeNormalizer.Result, from: Int, to: Int): String {
        if (from >= to || from >= norm.rawIndex.size) return ""
        val a = norm.rawIndex[from]
        val b = if (to < norm.rawIndex.size) norm.rawIndex[to] else lastRaw.length
        return lastRaw.substring(a, b).trim()
    }

    private fun lastFinalWord(): Int { var k = -1; for (w in 0 until n) if (fStatus[w] != WordStatus.PENDING) k = w; return k }

    private fun cursorWord(st: List<WordStatus> = fStatus): Int {
        val last = st.indexOfLast { it != WordStatus.PENDING }
        return last + 1
    }

    private fun snapshot(st: List<WordStatus>, heard: List<String?>, tokens: List<HeardToken>, cursor: Int) =
        LiveAlignment(st.toList(), heard.toList(), fExtras.toList(), if (cursor >= n) -1 else cursor, tokens, lastFinalWord())
}
