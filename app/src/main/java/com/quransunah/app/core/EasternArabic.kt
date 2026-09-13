package com.quransunah.app.core

object EasternArabic {
    private val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun format(value: Int): String = value.toString().map { ch ->
        if (ch in '0'..'9') eastern[ch - '0'] else ch
    }.joinToString("")

    fun formatDecimal(value: Float, decimals: Int = 1): String {
        val factor = 10f * decimals
        val rounded = kotlin.math.round(value * factor) / factor
        val raw = if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            String.format(java.util.Locale.US, "%.${decimals}f", rounded)
        }
        return raw.map { ch -> if (ch in '0'..'9') eastern[ch - '0'] else ch }.joinToString("")
    }

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
