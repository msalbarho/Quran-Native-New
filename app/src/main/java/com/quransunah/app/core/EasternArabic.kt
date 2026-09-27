package com.quransunah.app.core

object EasternArabic {
    private val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    /**
     * App UI numbers follow the selected UI locale.
     * Arabic UI -> Eastern Arabic digits, other UI locales -> their own digits.
     * Never use this for Quran/Mushaf glyph content; use [quranDigits] there.
     */
    fun format(value: Int): String =
        UiNumbers.format(value, UiNumbers.resolvedLocale())

    fun format(value: Long): String =
        UiNumbers.format(value, UiNumbers.resolvedLocale())

    /** Fixed Quran-style digits for Mushaf/page artwork, independent of UI language. */
    fun quranDigits(value: Int): String = value.toString().map { ch ->
        if (ch in '0'..'9') eastern[ch - '0'] else ch
    }.joinToString("")

    fun formatDecimal(value: Float, decimals: Int = 1): String =
        UiNumbers.formatDecimal(value, decimals, UiNumbers.resolvedLocale())

    fun formatDecimal(value: Float, decimals: Int, locale: java.util.Locale): String =
        UiNumbers.formatDecimal(value, decimals, locale)

    fun parseDigits(raw: String): String = buildString {
        raw.forEach { ch ->
            when (ch) {
                in '0'..'9' -> append(ch)
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                in '۰'..'۹' -> append('0' + (ch - '۰'))
                else -> append(ch)
            }
        }
    }

    fun westernDigits(raw: String): String = buildString {
        raw.forEach { ch ->
            when (ch) {
                in '0'..'9' -> append(ch)
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                in '۰'..'۹' -> append('0' + (ch - '۰'))
            }
        }
    }
}
