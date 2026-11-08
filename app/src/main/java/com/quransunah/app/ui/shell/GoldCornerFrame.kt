package com.quransunah.app.ui.shell

import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * The bottom-nav gold frame (rectangle with concave quarter-circle corner cut-outs) as a reusable shape.
 * The cut radius scales with the element: at most [maxRadius], and never more than a quarter of the
 * shorter side, so small chips get small notches. Symmetric, so it is identical in LTR and RTL.
 */
/** Global depth of the concave corner cut-outs (1f = original size). */
internal const val GoldCornerScale = 0.5f

internal data class GoldCornerFrameShape(val maxRadius: Dp = 14.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.width
        val h = size.height
        val r = min(with(density) { maxRadius.toPx() }, min(w, h) * 0.25f) * GoldCornerScale
        if (r < 1f) return Outline.Rectangle(Rect(0f, 0f, w, h))
        return Outline.Generic(
            Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                arcTo(Rect(w - r, -r, w + r, r), 180f, -90f, false)
                lineTo(w, h - r)
                arcTo(Rect(w - r, h - r, w + r, h + r), 270f, -90f, false)
                lineTo(r, h)
                arcTo(Rect(-r, h - r, r, h + r), 0f, -90f, false)
                lineTo(0f, r)
                arcTo(Rect(-r, -r, r, r), 90f, -90f, false)
                close()
            },
        )
    }
}

internal val GoldCornerFrame = GoldCornerFrameShape()

/** Gold stroke on the shared corner-cut frame (frame only: no fill, ornament or mosque art). */
internal fun Modifier.goldCornerFrame(
    shape: Shape = GoldCornerFrame,
    width: Dp = ChromeTokens.GoldStroke,
    color: Color = ChromeTokens.Gold,
): Modifier = border(width, color, shape)