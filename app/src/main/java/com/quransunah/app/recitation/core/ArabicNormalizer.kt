package com.quransunah.app.recitation.core

/**
 * Normalizes Uthmani / imla'i Arabic text so that recognizer output (usually plain imla'i
 * spelling without diacritics) can be compared with the Madinah Mushaf Uthmani text.
 */
object ArabicNormalizer {

    private const val SUPERSCRIPT_ALEF = '\u0670'
    private const val TATWEEL = '\u0640'

    private fun isDiacritic(c: Char): Boolean {
        val code = c.code
        return (code in 0x064B..0x065F) ||          // harakat, tanween, shadda, sukun, ...
            code == 0x0610 || code == 0x0611 || code == 0x0612 || code == 0x0613 || code == 0x0614 ||
            code == 0x0615 || code == 0x0616 || code == 0x0617 || code == 0x0618 || code == 0x0619 ||
            code == 0x061A ||
            (code in 0x06D6..0x06ED) ||               // Quranic annotation marks, small letters
            code == 0x08F0 || code == 0x08F1 || code == 0x08F2 || // open tanween variants
            (code in 0x08D3..0x08FF) ||
            code == 0x200C || code == 0x200D || code == 0x200F || code == 0x200E
    }

    /**
     * Full normalization: strips diacritics and Quranic marks, unifies alef / hamza forms,
     * ta marbuta -> ha, alef maqsura -> ya, handles Uthmani superscript alef.
     */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        var prevTatweel = false
        for (raw in input) {
            var c = raw
            if (c == TATWEEL) { prevTatweel = true; continue }
            if (c == SUPERSCRIPT_ALEF && prevTatweel) { prevTatweel = false; sb.append('ا'); continue }
            if (!isDiacritic(c)) prevTatweel = false
            when {
                c == SUPERSCRIPT_ALEF -> {
                    // dagger alef: after alef maqsura/ya it is just a vowel sign (عَلَىٰ -> على),
                    // after waw (صلوٰة) it replaces the waw by an alef (صلاة), otherwise long alef.
                    val last = sb.lastOrNull()
                    when (last) {
                        'ي', 'ى' -> {}
                        'و' -> if (sb.length > 1) { sb.setCharAt(sb.length - 1, 'ا') }
                        'ا', null -> {}
                        else -> sb.append('ا')
                    }
                    continue
                }
                isDiacritic(c) -> continue
            }
            c = when (c) {
                'ٱ', 'أ', 'إ', 'آ', 'ٲ', 'ٳ', 'ٵ' -> 'ا'
                'ؤ' -> 'و'
                'ئ', 'ى', 'ی', 'ے' -> 'ي'
                'ة' -> 'ه'
                'ک' -> 'ك'
                else -> c
            }
            if (isArabicLetter(c)) sb.append(c)
            else if (c.isWhitespace() || c == '-' || c == '،' || c == '.' || c == ',') {
                if (sb.isNotEmpty() && sb.last() != ' ') sb.append(' ')
            }
        }
        return sb.toString().trim()
    }

    private fun isArabicLetter(c: Char) = c in '\u0621'..'\u064A'

    /** Even looser key: removes alef and hamza so that rasm differences (الرحمن/الرحمان, سموت/سماوات) match. */
    fun skeleton(normalized: String): String =
        buildString { for (c in normalized) if (c != 'ا' && c != 'ء' && c != ' ') append(c) }

    fun words(input: String): List<String> =
        normalize(input).split(' ').filter { it.isNotBlank() }

}
