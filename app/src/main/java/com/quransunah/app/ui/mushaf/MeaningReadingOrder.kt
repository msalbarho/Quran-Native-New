package com.quransunah.app.ui.mushaf

import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine

/** Reading order of one Mushaf page for the Meanings view. */
internal sealed class MeaningSlot {
    data class Surah(val number: Int) : MeaningSlot()
    data object Bismillah : MeaningSlot()
    data class Ayah(val surah: Int, val ayah: Int) : MeaningSlot()
}

/**
 * Walks the page lines in stored order. Surah frames and Bismillah appear only
 * where the Mushaf page already has those lines.
 */
internal fun meaningReadingOrder(lines: List<MushafLine>): List<MeaningSlot> {
    val slots = ArrayList<MeaningSlot>()
    val seenAyahs = LinkedHashSet<Pair<Int, Int>>()
    for (line in lines) {
        when (line.lineType) {
            LineType.SURAH_NAME -> {
                val number = line.surahNumber ?: continue
                if (number > 0) slots += MeaningSlot.Surah(number)
            }
            LineType.BASMALLAH -> slots += MeaningSlot.Bismillah
            LineType.AYAH -> {
                for (word in line.words) {
                    if (word.isAyahMarker || word.surah <= 0 || word.ayah <= 0) continue
                    if (seenAyahs.add(word.surah to word.ayah)) {
                        slots += MeaningSlot.Ayah(word.surah, word.ayah)
                    }
                }
            }
            LineType.EMPTY -> Unit
        }
    }
    return slots
}

/** Glyphs for one ayah, in Mushaf reading order (first item is the start of the ayah). */
internal data class MeaningGlyphs(
    val glyphs: List<String>,
    val marker: String,
)

/**
 * Same split the Medina page uses: a quarter star is its own glyph before the
 * word it was prefixed to, and a sajdah sign stays in front of the ayah number
 * instead of replacing it.
 */
internal fun meaningDisplayGlyphs(
    words: List<WordRecord>,
    display: (WordRecord) -> String,
): MeaningGlyphs {
    val glyphs = ArrayList<String>()
    var marker = ""
    for (index in words.indices) {
        val word = words[index]
        val shown = display(word)
        if (shown.isBlank()) continue
        val run = SajdahAyah.paintRun(
            word = word,
            display = shown,
            previous = words.getOrNull(index - 1),
            next = words.getOrNull(index + 1),
        )
        if (word.isAyahMarker) {
            val sajdah = run.marker
            if (!sajdah.isNullOrEmpty()) {
                glyphs += sajdah
                marker = run.body
            } else {
                marker = run.body.ifBlank { shown }
            }
        } else if (!run.marker.isNullOrEmpty()) {
            glyphs += run.marker
            if (run.body.isNotBlank()) glyphs += run.body
        } else if (run.body.isNotBlank()) {
            glyphs += run.body
        }
    }
    return MeaningGlyphs(glyphs, marker)
}
