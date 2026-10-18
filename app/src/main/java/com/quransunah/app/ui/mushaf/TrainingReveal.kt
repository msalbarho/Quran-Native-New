package com.quransunah.app.ui.mushaf

import androidx.compose.runtime.compositionLocalOf

/** Word ids revealed one at a time. Empty outside training. */
internal val LocalTrainingFirstWordHint = compositionLocalOf { false }

val LocalTrainingRevealedWordIds = compositionLocalOf { emptySet<Int>() }

internal data class TrainingWordRef(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    val position: Int,
)

internal data class TrainingRevealState(
    val selected: Pair<Int, Int>? = null,
    val revealedAyahs: Set<Pair<Int, Int>> = emptySet(),
    val revealedWordIds: Set<Int> = emptySet(),
)

internal fun trainingWordHidden(
    isAyahMarker: Boolean,
    surah: Int,
    ayah: Int,
    wordId: Int,
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    revealedWordIds: Set<Int>,
    wordPosition: Int = 1,
    firstWordHint: Boolean = false,
): Boolean {
    if (!hideAyahText || isAyahMarker) return false
    if ((surah to ayah) in revealedAyahs) return false
    return wordId !in revealedWordIds && !(firstWordHint && wordPosition == 1)
}

/**
 * Reveals exactly one still-hidden word of [ayah].
 * Returns null when that verse is already fully visible, so the caller can
 * move to the next verse and reveal only its first word.
 */
internal fun progressiveTrainingStep(
    state: TrainingRevealState,
    ayah: Pair<Int, Int>,
    ayahWords: List<TrainingWordRef>,
    firstWordHint: Boolean = false,
): TrainingRevealState? {
    if (ayah in state.revealedAyahs) return null
    val next = ayahWords
        .sortedBy { it.position }
        .firstOrNull { it.id !in state.revealedWordIds && !(firstWordHint && it.position == 1) }
        ?: return null
    return state.copy(
        selected = ayah,
        revealedWordIds = state.revealedWordIds + next.id,
    )
}

internal fun toggleTrainingAyah(
    state: TrainingRevealState,
    key: Pair<Int, Int>,
    words: List<TrainingWordRef>,
): TrainingRevealState {
    val ayahWords = wordsOf(words, key)
    val ids = ayahWords.map { it.id }.toSet()
    val visible = key in state.revealedAyahs || ayahWords.any { it.id in state.revealedWordIds }
    return if (visible) {
        state.copy(
            selected = key,
            revealedAyahs = state.revealedAyahs - key,
            revealedWordIds = state.revealedWordIds - ids,
        )
    } else {
        state.copy(
            selected = key,
            revealedAyahs = state.revealedAyahs + key,
        )
    }
}

/** Reveal a playing verse without hiding anything already shown. */
internal fun revealPlayingTrainingAyah(
    state: TrainingRevealState,
    key: Pair<Int, Int>,
): TrainingRevealState = state.copy(
    selected = key,
    revealedAyahs = state.revealedAyahs + key,
)

private fun wordsOf(words: List<TrainingWordRef>, key: Pair<Int, Int>): List<TrainingWordRef> =
    words.filter { it.surah == key.first && it.ayah == key.second }.sortedBy { it.position }
