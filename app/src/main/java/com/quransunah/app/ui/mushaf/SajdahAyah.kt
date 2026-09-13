package com.quransunah.app.ui.mushaf

/**
 * QCF4 sajdah ayahs use four consecutive PUA slots, e.g. 84:21 / page 589:
 * - F3D3 last word
 * - F3D4 zero-width overline (not stored as a word)
 * - F3D5 sajdah mihrab (stored as the ayah-marker ligature)
 * - F3D6 circled ayah number (not stored as a word)
 *
 * Never replace the marker ligature. Overlay the line on the word, then
 * append the skipped number glyph beside the sajdah mark.
 */
object SajdahAyah {
    const val MARK = '\u06E9'
    private val pua = 0xE000..0xF8FF

    fun overlayLigature(word: WordRecord, next: WordRecord?): String? {
        if (word.isAyahMarker || next?.isAyahMarker != true) return null
        if (!word.uthmanic.contains(MARK)) return null
        val wordCp = word.qcfLigature.lastOrNull()?.code ?: return null
        val markerCp = next.qcfLigature.firstOrNull()?.code ?: return null
        if (wordCp !in pua || markerCp !in pua) return null
        val overlay = wordCp + 1
        if (overlay == markerCp || overlay !in pua) return null
        return overlay.toChar().toString()
    }

    fun numberLigature(word: WordRecord, previous: WordRecord?): String? {
        if (!word.isAyahMarker || previous == null) return null
        if (!previous.uthmanic.contains(MARK)) return null
        val markerCp = word.qcfLigature.firstOrNull()?.code ?: return null
        if (markerCp !in pua) return null
        val numberCp = markerCp + 1
        if (numberCp !in pua) return null
        return numberCp.toChar().toString()
    }

    internal fun paintRun(
        word: WordRecord,
        display: String,
        previous: WordRecord?,
        next: WordRecord?,
    ): WordPaintRun {
        val parts = word.rubParts(display)
        if (parts != null) {
            return WordPaintRun(body = parts.bodyLigature, marker = parts.markerLigature)
        }
        val number = numberLigature(word, previous)
        if (number != null && display.isNotEmpty()) {
            // Canvas is LTR: number on the left, sajdah mark on the right
            // → RTL reading: mark then number, after the ayah text.
            return WordPaintRun(body = number, marker = display)
        }
        val overlay = overlayLigature(word, next)
        val body = if (overlay.isNullOrEmpty()) display else overlay + display
        return WordPaintRun(body = body)
    }
}
