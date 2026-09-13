package com.quransunah.app.ui.mushaf

/**
 * Rub el Hizb (۞, U+06DE) is stored as a prefix on the first word of a
 * quarter-start ayah (`text_hafs` and the first QCF ligature codepoint).
 * React splits that prefix into its own inline run so RTL places the star
 * at the beginning of the ayah; the canvas must do the same.
 */
object RubElHizb {
    const val MARK = '\u06DE'
    const val GAP_EM = 0.22f

    data class Parts(
        val markerLigature: String,
        val bodyLigature: String,
    )

    fun parts(textHafs: String, textLigature: String): Parts? {
        if (textHafs.isEmpty() || textHafs[0] != MARK) return null
        val bodyHafs = textHafs.substring(1).trimStart()
        if (textLigature.length >= 2) {
            val markerEnd = Character.charCount(textLigature.codePointAt(0))
            return Parts(
                markerLigature = textLigature.substring(0, markerEnd),
                bodyLigature = textLigature.substring(markerEnd),
            )
        }
        if (bodyHafs.isEmpty()) {
            return Parts(
                markerLigature = textLigature.ifBlank { MARK.toString() },
                bodyLigature = "",
            )
        }
        return Parts(
            markerLigature = MARK.toString(),
            bodyLigature = textLigature.ifBlank { bodyHafs },
        )
    }
}
