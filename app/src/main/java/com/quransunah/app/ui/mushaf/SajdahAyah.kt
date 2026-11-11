package com.quransunah.app.ui.mushaf

/** QPC V4 page-font glyph runs carry sajdah marks natively; never synthesize legacy slots. */
object SajdahAyah {
    const val MARK = '\u06E9'

    /** V4 encodes the sajdah decoration in its stored word/marker glyphs. */
    @Suppress("UNUSED_PARAMETER")
    fun overlayLigature(word: WordRecord, next: WordRecord?): String? = null

    /** V4 encodes the ayah number in its stored marker glyph; there is no extra slot to append. */
    @Suppress("UNUSED_PARAMETER")
    fun numberLigature(word: WordRecord, previous: WordRecord?): String? = null

    internal fun paintRun(
        word: WordRecord,
        display: String,
        @Suppress("UNUSED_PARAMETER")
        previous: WordRecord?,
        @Suppress("UNUSED_PARAMETER")
        next: WordRecord?,
    ): WordPaintRun {
        val parts = word.rubParts(display)
        if (parts != null) {
            return WordPaintRun(body = parts.bodyLigature, marker = parts.markerLigature)
        }
        return WordPaintRun(body = display)
    }
}
