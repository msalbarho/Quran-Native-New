package com.quransunah.app.ui.shell

import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

object ChromeTokens {
    /** React chrome frame strokes (`#c5a059` / `#e3d0ad`). */
    val Gold = Color(0xFFC5A059)
    val BasmalaGold = Color(0xFFD4AF37)
    val GoldInner = Color(0xFFE3D0AD)
    val GoldStroke = 0.9.dp
    val HeaderHeight = 30.dp
    val Corner = 6.dp
    val HeaderTopGap = 6.dp
    val HeaderBottomGap = 8.dp
    val HorizontalInset = 10.dp

    /** Space reserved above the 15-line page so the header never covers line 1. */
    val MushafHeaderReserve = HeaderTopGap + HeaderHeight + HeaderBottomGap

    /** Space reserved below the 15-line page so the bottom nav never covers line 15. */
    val MushafNavReserve = 62.dp

    /**
     * Shared corner radius for side panels (settings + long-press word sheet).
     * Matches React `1rem` on `.settings-picker` / `.word-popover`.
     */
    val SidePanelCorner = 16.dp

    /** Playing-ayah glyph color (day and night); no translucent fill. */
    val PlaybackAyahBlue = Color(0xFF1E88E5)
}

/** Thin outline matching the outer stroke around the Medina page-number plaque. */
fun Modifier.goldFrame(shape: Shape): Modifier =
    border(width = ChromeTokens.GoldStroke, color = ChromeTokens.Gold, shape = shape)
