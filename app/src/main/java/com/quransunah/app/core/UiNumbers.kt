package com.quransunah.app.core

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import java.text.NumberFormat
import java.util.Locale

/**
 * Locale-aware number formatting for App UI only.
 *
 * Quran/Mushaf glyph content (MedinaCanvasPage, TextMushafPage, ayah markers)
 * must never use this helper.
 */
object UiNumbers {
    private val eastern = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun locale(context: android.content.Context): Locale {
        val tag = ArabicRtl.selectedLanguage(context) ?: currentSystemTag(context)
        return Locale.forLanguageTag(tag)
    }

    fun format(value: Int, locale: Locale): String =
        formatInteger(value.toString(), locale)

    fun format(value: Long, locale: Locale): String =
        formatInteger(value.toString(), locale)

    fun formatDecimal(value: Float, decimals: Int = 1, locale: Locale): String {
        val factor = 10f * decimals
        val rounded = kotlin.math.round(value * factor) / factor
        val raw = if (rounded % 1f == 0f) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.US, "%.${decimals}f", rounded)
        }
        return localizeDigits(raw, locale)
    }

    fun formatClock(positionMs: Long, locale: Locale): String {
        val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return "${format(minutes, locale)}:${padTwo(seconds, locale)}"
    }

    private fun padTwo(value: Int, locale: Locale): String {
        val raw = format(value, locale)
        if (raw.length >= 2) return raw
        val zero = zeroDigit(locale)
        return "$zero$raw"
    }

    private fun formatInteger(raw: String, locale: Locale): String {
        // Never use grouping separators for UI counters/pages; only localize digits.
        return localizeDigits(raw, locale)
    }

    private fun localizeDigits(raw: String, locale: Locale): String {
        if (locale.language == "ar") {
            return raw.map { ch -> if (ch in '0'..'9') eastern[ch - '0'] else ch }
                .joinToString("")
        }
        return try {
            val symbols = java.text.DecimalFormatSymbols(locale)
            val zero = symbols.zeroDigit
            if (zero == '0') {
                raw
            } else {
                raw.map { ch -> if (ch in '0'..'9') (zero + (ch - '0')) else ch }
                    .joinToString("")
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun zeroDigit(locale: Locale): Char {
        if (locale.language == "ar") return '٠'
        return try {
            java.text.DecimalFormatSymbols(locale).zeroDigit
        } catch (_: Exception) {
            '0'
        }
    }

    private fun currentSystemTag(context: android.content.Context): String {
        val system = context.resources.configuration.locales[0]
        return LanguageRegistry.matchTag(system) ?: LanguageRegistry.defaultTag()
    }

    fun resolvedLocale(): Locale {
        // Compose callers already run inside ArabicRtl.wrap(), so Locale.getDefault()
        // matches the selected app language. This avoids needing a Context everywhere.
        val current = Locale.getDefault()
        val tag = LanguageRegistry.matchTag(current)
        return if (tag != null) Locale.forLanguageTag(tag) else current
    }
}

@Composable
@ReadOnlyComposable
fun uiNumber(value: Int): String = UiNumbers.format(value, UiNumbers.resolvedLocale())

@Composable
@ReadOnlyComposable
fun uiNumber(value: Long): String = UiNumbers.format(value, UiNumbers.resolvedLocale())

@Composable
@ReadOnlyComposable
fun uiDecimal(value: Float, decimals: Int = 1): String =
    UiNumbers.formatDecimal(value, decimals, UiNumbers.resolvedLocale())

@Composable
@ReadOnlyComposable
fun uiClock(positionMs: Long): String =
    UiNumbers.formatClock(positionMs, UiNumbers.resolvedLocale())

@Composable
fun rememberUiLocale(): Locale {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(context) { UiNumbers.locale(context) }
}
