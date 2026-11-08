package com.quransunah.app.ui.mushaf

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.min

/** Draws original qbsml outlines; combined glyphs expose only the requested surah's ink. */
@Composable
internal fun QbsmlHeaderLabel(
    slice: HeaderGlyphSlice,
    face: Typeface,
    description: String,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val preferredScale = with(density) { 25.sp.toPx() } / 2048f
    val padding = with(density) { 2.dp.toPx() }
    val ink = LocalPaperColors.current.textStrong
    val paint = remember(face) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = 2048f
            textAlign = Paint.Align.LEFT
        }
    }
    val bounds = remember(face, slice.glyph) {
        Rect().also { paint.getTextBounds(slice.glyph, 0, slice.glyph.length, it) }
    }
    Canvas(modifier.height(ChromeTokens.HeaderHeight).clipToBounds()
        .semantics { contentDescription = description }) {
        val left = bounds.left + bounds.width() * slice.left
        val right = bounds.left + bounds.width() * slice.right
        val top = bounds.top + bounds.height() * slice.top
        val bottom = bounds.top + bounds.height() * slice.bottom
        val width = (right - left).coerceAtLeast(1f)
        val height = (bottom - top).coerceAtLeast(1f)
        val scale = min(preferredScale, min(
            (size.width - 2 * padding).coerceAtLeast(1f) / width,
            (size.height - 2 * padding).coerceAtLeast(1f) / height,
        ))
        paint.color = ink.toArgb()
        val native = drawContext.canvas.nativeCanvas
        native.save()
        native.translate((size.width - width * scale) / 2f - left * scale,
            (size.height - height * scale) / 2f - top * scale)
        native.scale(scale, scale)
        val guard = 2f / scale
        native.clipRect(left - guard, top - guard, right + guard, bottom + guard)
        native.drawText(slice.glyph, 0f, 0f, paint)
        native.restore()
    }
}
