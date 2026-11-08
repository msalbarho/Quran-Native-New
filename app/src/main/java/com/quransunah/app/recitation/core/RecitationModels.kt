package com.quransunah.app.recitation.core

/**
 * One expected word of the passage to be recited.
 * [verseId] is the global ayah id 1..6236 ([QuranStructure.verseId]), [position] the 1-based word position in the ayah
 * (same numbering as the Quran.com / Madinah word list used by the Zipformer phoneme lexicon).
 */
data class RecitationWord(
    val index: Int,
    val verseId: Int,
    val position: Int,
    val text: String,
)

/** How strictly a word must match to count as correct. */
enum class Strictness(val threshold: Float) { LENIENT(0.75f), NORMAL(0.85f), STRICT(0.95f) }
