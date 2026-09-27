package com.quransunah.app.ui.mushaf

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit

/** QCF4 ayah-background overlays in the page face. Advance is zero; draw at the number origin. */
internal const val AYAH_BACKGROUND_LIGHT = "\uE000"
internal const val AYAH_BACKGROUND_NIGHT = "\uE001"

internal fun ayahBackgroundGlyph(night: Boolean): String =
    if (night) AYAH_BACKGROUND_NIGHT else AYAH_BACKGROUND_LIGHT

/** Ayah-marker glyph is slightly smaller than body text (matches React / TextMushaf). */
internal const val AYAH_MARKER_GLYPH_SCALE = 0.92f

internal fun ayahMarkerGlyphSize(fontSize: TextUnit): TextUnit =
    fontSize * AYAH_MARKER_GLYPH_SCALE

internal fun ayahMarkerTextStyle(fontSize: TextUnit): TextStyle =
    TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
