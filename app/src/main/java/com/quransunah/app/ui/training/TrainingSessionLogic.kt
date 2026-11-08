package com.quransunah.app.ui.training

import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.recitation.core.LiveAlignment
import com.quransunah.app.recitation.core.WordStatus
import com.quransunah.app.ui.mushaf.TrainingWordMark

/** One Quran word the recognizer may judge, in mushaf order. */
data class TrainingExpectedWord(
    val wordId: Int,
    val surah: Int,
    val ayah: Int,
    val position: Int,
    val text: String,
)

data class TrainingWordAssessment(
    val wordId: Int,
    val surah: Int,
    val ayah: Int,
    val position: Int,
    val mark: TrainingWordMark,
)

enum class TrainingPhase { Before, During, After }

data class TrainingReciteSummary(
    val evaluated: Int,
    val correct: Int,
    val possibleErrors: Int,
    val uncertain: Int,
    val unread: Int,
    /** Words (or whole ayahs) the reciter skipped. */
    val missing: Int = 0,
    val durationMs: Long,
    val issues: List<TrainingWordAssessment>,
) {
    /** True only after a real session. An empty object is not a result. */
    val fromSession: Boolean get() = durationMs > 0L || evaluated > 0 || issues.isNotEmpty()
}

enum class TrainingReciteScope { Open, Page, Range }

/**
 * Maps a recognizer snapshot onto mushaf words.
 * A wrong word is red only when that word is already frozen by the engine.
 * Anything still moving stays orange or unread. Tajweed is not scored here.
 * Returns an empty list when there is no alignment, so the page never shows invented marks.
 */
fun assessRecitation(
    expected: List<TrainingExpectedWord>,
    live: LiveAlignment?,
): List<TrainingWordAssessment> {
    if (live == null || expected.isEmpty() || live.statuses.size != expected.size) return emptyList()
    val frozenUntil = live.finalizedUpTo
    return expected.mapIndexed { index, word ->
        val status = live.statuses[index]
        val frozen = index <= frozenUntil
        val mark = when (status) {
            WordStatus.CORRECT -> TrainingWordMark.Correct
            WordStatus.WRONG -> if (frozen) TrainingWordMark.PossibleError else TrainingWordMark.Uncertain
            WordStatus.MISSING -> if (frozen) TrainingWordMark.Missing else TrainingWordMark.Unread
            WordStatus.PENDING -> TrainingWordMark.Unread
        }
        TrainingWordAssessment(word.wordId, word.surah, word.ayah, word.position, mark)
    }
}

fun summarizeRecitation(
    marks: List<TrainingWordAssessment>,
    durationMs: Long,
): TrainingReciteSummary {
    val correct = marks.count { it.mark == TrainingWordMark.Correct }
    val possible = marks.count { it.mark == TrainingWordMark.PossibleError }
    val uncertain = marks.count { it.mark == TrainingWordMark.Uncertain }
    val unread = marks.count { it.mark == TrainingWordMark.Unread }
    val missing = marks.count { it.mark == TrainingWordMark.Missing }
    return TrainingReciteSummary(
        evaluated = correct + possible + uncertain + missing,
        correct = correct,
        possibleErrors = possible,
        uncertain = uncertain,
        unread = unread,
        missing = missing,
        durationMs = durationMs.coerceAtLeast(0L),
        issues = marks.filter {
            it.mark == TrainingWordMark.PossibleError || it.mark == TrainingWordMark.Uncertain ||
                it.mark == TrainingWordMark.Missing
        },
    )
}

/** Words the reciter has been judged on. Unread words stay hidden in memory mode. */
fun recognizedWordIds(marks: Map<Int, TrainingWordMark>): Set<Int> =
    marks.filterValues { it != TrainingWordMark.Unread }.keys

/** Ayahs with every word actually heard and finalized, not merely flushed as missing at Stop. */
fun finalizedTrainingAyahs(
    expected: List<TrainingExpectedWord>,
    live: LiveAlignment?,
): Set<Pair<Int, Int>> {
    if (live == null || expected.isEmpty() || live.statuses.size != expected.size) return emptySet()
    return expected.indices.groupBy { expected[it].surah to expected[it].ayah }
        .filterValues { indices ->
            indices.all { index ->
                index <= live.finalizedUpTo &&
                    live.statuses[index] in setOf(WordStatus.CORRECT, WordStatus.WRONG) &&
                    !live.heard.getOrNull(index).isNullOrBlank()
            }
        }
        .keys
}

fun trainingRangeValid(fromSurah: Int, fromAyah: Int, toSurah: Int, toAyah: Int): Boolean {
    val start = SurahAyahCounts.ayahId(fromSurah, fromAyah)
    val end = SurahAyahCounts.ayahId(toSurah, toAyah)
    return start > 0 && end >= start
}

fun parseTrainingAyahReference(value: String, defaultSurah: Int): Pair<Int, Int>? {
    val normalized = normalizeTrainingDigits(value.trim())
    val parts = normalized.split(':')
    val surah = if (parts.size == 1) defaultSurah else parts[0].toIntOrNull() ?: return null
    val ayah = (if (parts.size == 1) parts[0] else parts[1]).toIntOrNull() ?: return null
    if (parts.size !in 1..2 || SurahAyahCounts.ayahId(surah, ayah) <= 0) return null
    return surah to ayah
}

fun parseTrainingNumber(value: String): Int? = normalizeTrainingDigits(value.trim()).toIntOrNull()

private fun normalizeTrainingDigits(value: String): String = buildString(value.length) {
    value.forEach { char ->
        val digit = char.digitToIntOrNull()
        append(if (digit == null) char else ('0'.code + digit).toChar())
    }
}

/** Ayah-marker ornaments never take a training colour. */
fun visibleTrainingMark(isAyahMarker: Boolean, mark: TrainingWordMark?): TrainingWordMark? =
    if (isAyahMarker) null else mark
