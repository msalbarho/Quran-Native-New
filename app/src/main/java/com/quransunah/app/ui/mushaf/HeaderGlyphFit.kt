package com.quransunah.app.ui.mushaf

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafPage
import kotlin.math.max

/**
 * Fits a QBSML header ligature (or Arabic fallback) into the remaining chrome slot.
 * Uses ink bounds, not just advance width, so ornamental glyphs are not clipped.
 */
object HeaderGlyphFit {
    const val LigatureSp = 25f
    const val FallbackSp = 15f

    fun sizeSp(
        text: String,
        typeface: Typeface?,
        maxWidthPx: Float,
        ligature: Boolean,
        density: Float,
    ): Float {
        val preferred = if (ligature) LigatureSp else FallbackSp
        if (text.isBlank() || density <= 0f) return preferred
        if (!maxWidthPx.isFinite() || maxWidthPx <= 0f) return preferred
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = preferred * density
        }
        val width = visualWidthPx(paint, text)
        return fittedSp(preferred, width, maxWidthPx)
    }

    fun fittedSp(preferred: Float, textWidthPx: Float, maxWidthPx: Float): Float {
        if (textWidthPx <= 0f || maxWidthPx <= 0f || !maxWidthPx.isFinite()) return preferred
        if (textWidthPx <= maxWidthPx) return preferred
        return (preferred * maxWidthPx / textWidthPx).coerceAtMost(preferred)
    }

    fun composeHeaderLigature(
        surahNumbers: List<Int>,
        combined: Map<String, String>,
        single: Map<Int, String>,
        decorative: Map<Int, String>,
    ): String {
        if (surahNumbers.isEmpty()) return ""
        combined[surahNumbers.joinToString("-")]?.let { return it }
        return surahNumbers.joinToString("") { number ->
            single[number] ?: decorative[number].orEmpty()
        }
    }

    fun visualWidthPx(paint: Paint, text: String): Float {
        if (text.isEmpty()) return 0f
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        val ink = (bounds.right - bounds.left).toFloat()
        return max(ink, paint.measureText(text)) + 2f
    }

    /**
     * Draw origin for a QBSML header ligature.
     *
     * Compact glyphs with a normal advance keep the existing LTR/RTL placement
     * so other surahs do not shift. At-Tawbah's compact header glyph has a
     * ~410-unit advance while its ink spans ~3500 units; using [advancePx]
     * alone would draw that glyph entirely past the canvas edge.
     */
    fun originXPx(
        advancePx: Float,
        inkLeftPx: Float,
        inkRightPx: Float,
        canvasWidthPx: Float,
        ltr: Boolean,
    ): Float {
        val ink = inkRightPx - inkLeftPx
        if (advancePx + 1f < ink) {
            return -inkLeftPx
        }
        return if (ltr) 0f else (canvasWidthPx - advancePx).coerceAtLeast(0f)
    }

    fun headerFallbackLabel(surahNumbers: List<Int>, names: Map<Int, String>): String {
        return surahNumbers.joinToString(" - ") { number ->
            val raw = names[number].orEmpty()
            val trimmed = raw
                .removePrefix("سُورَةُ ")
                .removePrefix("سورة ")
                .trim()
            if (trimmed.isEmpty()) {
                "سورة ${EasternArabic.format(number)}"
            } else {
                "سورة $trimmed"
            }
        }
    }

    fun surahsOnPage(page: MushafPage?): List<Int> {
        if (page == null) return emptyList()
        val banners = page.lines.mapNotNull { line ->
            line.surahNumber.takeIf {
                line.lineType == LineType.SURAH_NAME &&
                    it != null &&
                    it > 0
            }
        }
        if (banners.isNotEmpty()) return banners
        return page.lines.asSequence()
            .flatMap { it.words }
            .map { it.surah }
            .firstOrNull()
            ?.let { listOf(it) }
            .orEmpty()
    }
}
