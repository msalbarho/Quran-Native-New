package com.quransunah.app.ui.mushaf

import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit

/** QCF4 ayah-background overlays in the page face. Advance is zero; draw at the number origin. */
internal const val AYAH_BACKGROUND_LIGHT = "\uE000"
internal const val AYAH_BACKGROUND_NIGHT = "\uE001"

internal fun ayahBackgroundGlyph(@Suppress("UNUSED_PARAMETER") night: Boolean): String =
    // The 604 QPC V4 page fonts draw the complete coloured ayah medallion in the marker glyph itself;
    // the 47-face backdrop glyphs (U+E000/U+E001) do not exist there.
    ""

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
