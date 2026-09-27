package com.quransunah.app.core

/**
 * Flexible Arabic folding for mushaf search and index matching.
 *
 * Drops tashkeel / Quranic annotation marks, unifies hamza-bearing alefs to
 * bare alef, maps alef maqsura to yaa, and treats common hamza seats as their
 * base letters so queries match regardless of how the user typed them.
 */
object ArabicNormalize {
    fun fold(value: String, stripSpaces: Boolean = false): String {
        val out = StringBuilder(value.length)
        var pendingSpace = false
        var wrote = false
        for (ch in value) {
            val mapped = foldChar(ch) ?: continue
            if (mapped == ' ') {
                if (!stripSpaces && wrote) pendingSpace = true
                continue
            }
            if (pendingSpace) {
                out.append(' ')
                pendingSpace = false
            }
            out.append(mapped)
            wrote = true
        }
        return out.toString()
    }

    fun stripArticle(value: String): String {
        val folded = fold(value, stripSpaces = true)
        return if (folded.startsWith("ال") && folded.length > 2) folded.drop(2) else folded
    }

    internal fun foldChar(ch: Char): Char? {
        if (isIgnorableMark(ch) || ch == TATWEEL || ch == HAMZA) return null
        if (ch.isWhitespace()) return ' '
        return when (ch) {
            ALEF_MADDA, ALEF_HAMZA_ABOVE, ALEF_HAMZA_BELOW, ALEF_WASLA, AE -> ALEF
            ALEF_MAQSURA -> YEH
            TEH_MARBUTA -> HEH
            WAW_HAMZA -> WAW
            YEH_HAMZA -> YEH
            else -> ch
        }
    }

    private fun isIgnorableMark(ch: Char): Boolean =
        ch in '\u0610'..'\u061A' ||
            ch in '\u064B'..'\u065F' ||
            ch == '\u0670' ||
            ch in '\u06D6'..'\u06ED' ||
            ch in '\u08D3'..'\u08E1' ||
            ch in '\u08E3'..'\u08FF'

    private const val HAMZA = '\u0621'
    private const val ALEF_MADDA = '\u0622'
    private const val ALEF_HAMZA_ABOVE = '\u0623'
    private const val WAW_HAMZA = '\u0624'
    private const val ALEF_HAMZA_BELOW = '\u0625'
    private const val YEH_HAMZA = '\u0626'
    private const val ALEF = '\u0627'
    private const val TEH_MARBUTA = '\u0629'
    private const val TATWEEL = '\u0640'
    private const val WAW = '\u0648'
    private const val ALEF_MAQSURA = '\u0649'
    private const val YEH = '\u064A'
    private const val HEH = '\u0647'
    private const val ALEF_WASLA = '\u0671'
    private const val AE = '\u06D5'
}
