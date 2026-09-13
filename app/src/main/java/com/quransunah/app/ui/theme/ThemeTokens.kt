package com.quransunah.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Theme-aware tokens from the active paper palette.
 * [SunGold] is reserved for the day/night toggle only.
 * Structural chrome frames use [ChromeTokens.Gold] (`#C5A059`) like React.
 */
object ThemeTokens {
    /** Authentic warm gold for the sun/moon toggle (`#D4AF37`). */
    val SunGold = Color(0xFFD4AF37)
    val SunGoldBright = Color(0xFFFFC107)
    val SunGoldDeep = Color(0xFF9A7B22)

    /** Interactive / selection stroke (palette accent). */
    val DarkAccent: Color
        @Composable get() = LocalPaperColors.current.darkAccent

    val DarkAccentInner: Color
        @Composable get() = LocalPaperColors.current.darkAccentInner

    /** Soft girih/star pattern tint. */
    val DecorPattern: Color
        @Composable get() = LocalPaperColors.current.decorPattern

    /** Ayah number glyph on the medallion (not the backdrop image). */
    val AyahMarker: Color
        @Composable get() {
            val paper = LocalPaperColors.current
            val night = LocalNightMode.current
            return if (night) paper.textPrimary else paper.textStrong
        }
}
