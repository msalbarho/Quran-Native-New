package com.quransunah.app.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object ChromeTokens {
    /** React chrome frame strokes (`#c5a059` / `#e3d0ad`). */
    val Gold = Color(0xFFC5A059)
    val BasmalaGold = Color(0xFFD4AF37)
    val GoldInner = Color(0xFFE3D0AD)
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
