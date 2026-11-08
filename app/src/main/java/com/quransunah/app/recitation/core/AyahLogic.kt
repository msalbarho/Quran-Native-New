package com.quransunah.app.recitation.core

/** Static structure of the Mushaf (Hafs): ayah count per surah and verse-id arithmetic. */
object QuranStructure {
    val AYAH_COUNTS = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135,
        112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88, 75, 85,
        54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
        14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42,
        29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
        11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
    )
    private val firstIds = IntArray(114).also { var acc = 1; for (i in 0 until 114) { it[i] = acc; acc += AYAH_COUNTS[i] } }
    val VERSE_COUNT = AYAH_COUNTS.sum()

    fun verseId(surah: Int, ayah: Int): Int = firstIds[surah - 1] + ayah - 1

    /** (surah, ayah) of a global verse id 1..6236. */
    fun ref(verseId: Int): Pair<Int, Int> {
        require(verseId in 1..VERSE_COUNT)
        var lo = 0; var hi = 113
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (firstIds[mid] <= verseId) lo = mid else hi = mid - 1 }
        return (lo + 1) to (verseId - firstIds[lo] + 1)
    }

    /** A basmala is recited (and shown) before this verse but is not part of it. Al-Fatiha's basmala IS verse 1:1; At-Tawbah has none. */
    fun basmalaBefore(verseId: Int): Boolean { val (s, a) = ref(verseId); return a == 1 && s != 1 && s != 9 }
}

/** Moves between verses across surah boundaries. */
object AyahNavigator {
    fun next(verseId: Int): Int? = if (verseId < QuranStructure.VERSE_COUNT) verseId + 1 else null
    fun previous(verseId: Int): Int? = if (verseId > 1) verseId - 1 else null
    fun startsNewSurah(verseId: Int) = QuranStructure.ref(verseId).second == 1
}

/**
 * Removes a recited opening that is not part of the verse being tested: the isti'adha
 * (أعوذ بالله من الشيطان الرجيم) always, and the basmala before the first verse of a surah.
 * A transcript that is still only a prefix of an opening is treated as empty, so the first verse word
 * is never coloured red while the reciter is saying the basmala.
 * Works on the committed + partial pair so the incremental aligner stays consistent.
 */
object OpeningStripper {
    private val isti = ArabicNormalizer.words("أعوذ بالله من الشيطان الرجيم")
    private val basmala = ArabicNormalizer.words("بسم الله الرحمن الرحيم")

    fun strip(committed: String, partial: String, stripBasmala: Boolean): Pair<String, String> {
        val c = ArabicNormalizer.words(committed); val p = ArabicNormalizer.words(partial)
        val all = c + p
        var k = 0
        k += matchPrefix(all, k, isti)
        if (stripBasmala) k += matchPrefix(all, k, basmala)
        if (k == 0) return committed to partial
        val cKeep = c.drop(k); val pKeep = if (k > c.size) p.drop(k - c.size) else p
        return cKeep.joinToString(" ") to pKeep.joinToString(" ")
    }

    /** Number of words at [from] belonging to [opening] (full match, or the whole remaining tail being a prefix of it). */
    private fun matchPrefix(words: List<String>, from: Int, opening: List<String>): Int {
        var i = 0
        while (i < opening.size && from + i < words.size && WordAligner.similarity(words[from + i], opening[i]) >= 0.8f) i++
        val remaining = words.size - from
        return when {
            i == opening.size -> i                       // complete opening
            i > 0 && i == remaining -> i                 // still saying it
            i >= 3 -> i                                  // nearly complete (one word misrecognized/dropped)
            else -> 0
        }
    }
}

enum class MistakeType { WRONG, MISSING, EXTRA }
data class VerseMistake(val type: MistakeType, val wordIndex: Int, val expected: String, val heard: String)

/** Final evaluation of one verse after the reciter pressed Stop. */
data class VerseEvaluation(
    val verseId: Int,
    val result: AlignmentResult,
    val mistakes: List<VerseMistake>,
    val heardAnything: Boolean,
) {
    val accuracy get() = result.accuracy
    val passed get() = heardAnything && accuracy >= PASS_ACCURACY && result.wrong == 0 && result.missing == 0
    companion object { const val PASS_ACCURACY = 0.9f }
}

object VerseEvaluator {
    /** Finalizes a live alignment: every word not recited becomes MISSING; mistakes are listed in order. */
    fun evaluate(verseId: Int, expected: List<String>, live: LiveAlignment?): VerseEvaluation {
        val n = expected.size
        val statuses = (live?.statuses ?: List(n) { WordStatus.PENDING }).map { if (it == WordStatus.PENDING) WordStatus.MISSING else it }
        val heard = live?.heard ?: List(n) { null }
        val extras = live?.extras ?: emptyList()
        val heardAnything = (live?.tokens?.isNotEmpty() == true)
        val result = AlignmentResult(statuses, heard, List(n) { 0f }, extras, n - 1)
        val mistakes = ArrayList<VerseMistake>()
        for (i in 0 until n) {
            when (statuses[i]) {
                WordStatus.WRONG -> mistakes.add(VerseMistake(MistakeType.WRONG, i, expected[i], heard[i].orEmpty()))
                WordStatus.MISSING -> mistakes.add(VerseMistake(MistakeType.MISSING, i, expected[i], ""))
                else -> {}
            }
            extras.filter { it.afterExpectedIndex == i }.forEach { mistakes.add(VerseMistake(MistakeType.EXTRA, i, "", it.text)) }
        }
        extras.filter { it.afterExpectedIndex < 0 }.forEach { mistakes.add(0, VerseMistake(MistakeType.EXTRA, -1, "", it.text)) }
        return VerseEvaluation(verseId, result, mistakes, heardAnything)
    }
}

enum class AyahPhase { READY, RECORDING, FINALIZING, EVALUATED }

/** Pure state machine of the ayah-by-ayah trainer (the ViewModel drives it; unit-tested on its own). */
data class AyahFlowState(
    val verseId: Int,
    val phase: AyahPhase = AyahPhase.READY,
    val evaluation: VerseEvaluation? = null,
    /** Latest evaluation per verse in this sitting. */
    val history: Map<Int, VerseEvaluation> = emptyMap(),
    val finishedQuran: Boolean = false,
) {
    val canGoNext get() = AyahNavigator.next(verseId) != null
    val canGoPrevious get() = AyahNavigator.previous(verseId) != null
    val averageAccuracy: Float get() = history.values.filter { it.heardAnything }.map { it.accuracy }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
}

object AyahFlow {
    fun start(s: AyahFlowState) = if (s.phase == AyahPhase.RECORDING) s else s.copy(phase = AyahPhase.RECORDING, evaluation = null)
    fun stopRequested(s: AyahFlowState) = if (s.phase == AyahPhase.RECORDING) s.copy(phase = AyahPhase.FINALIZING) else s
    fun evaluated(s: AyahFlowState, e: VerseEvaluation): AyahFlowState {
        require(e.verseId == s.verseId)
        return s.copy(phase = AyahPhase.EVALUATED, evaluation = e, history = if (e.heardAnything) s.history + (e.verseId to e) else s.history)
    }
    fun retry(s: AyahFlowState) = s.copy(phase = AyahPhase.READY, evaluation = null)
    fun next(s: AyahFlowState): AyahFlowState =
        AyahNavigator.next(s.verseId)?.let { s.copy(verseId = it, phase = AyahPhase.READY, evaluation = null) } ?: s.copy(finishedQuran = true)
    fun previous(s: AyahFlowState): AyahFlowState =
        AyahNavigator.previous(s.verseId)?.let { s.copy(verseId = it, phase = AyahPhase.READY, evaluation = null) } ?: s
    fun jump(s: AyahFlowState, verseId: Int) = s.copy(verseId = verseId.coerceIn(1, QuranStructure.VERSE_COUNT), phase = AyahPhase.READY, evaluation = null)
    /** Auto-advance only after a passed verse; a failed verse waits for Retry/Next. */
    fun shouldAutoAdvance(s: AyahFlowState, autoAdvance: Boolean) =
        autoAdvance && s.phase == AyahPhase.EVALUATED && s.evaluation?.passed == true && s.canGoNext
}

/**
 * Hide-text (memorization) mode: the whole page is hidden except a cue at the start of every verse:
 * the first word, or "بِسْمِ ٱللَّهِ" (first two words) for the basmala (Al-Fatiha 1:1). Ayah-number
 * markers stay visible so the verse structure remains readable.
 */
object HideRule {
    const val BASMALA_CUE_WORDS = 2
    fun cueWords(verseId: Int): Int = if (verseId == 1) BASMALA_CUE_WORDS else 1
    fun visibleWhenHidden(verseId: Int, position: Int, isEnd: Boolean): Boolean = isEnd || position <= cueWords(verseId)
}
