package com.quransunah.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Configured ornament opacity. Edges use 80% of this; the center stays at 0. */
internal const val IslamicEdgePatternAlpha = 0.22f

/** Horizontal fade at both physical edges. The center multiplier is always 0. */
private const val IslamicEdgePatternEdgeMultiplier = 0.8f

/**
 * A non-interactive star-and-polygon lattice. It is strongest at both physical
 * ends and falls smoothly to zero at the horizontal center.
 */
@Composable
internal fun IslamicEdgePattern(
    modifier: Modifier = Modifier,
    maxAlpha: Float = IslamicEdgePatternAlpha,
) {
    val accent = LocalPaperColors.current.accent
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier.drawWithCache {
                val tile = 18.dp.toPx()
                val stroke = Stroke(width = 0.9.dp.toPx())
                val lattice = islamicLattice(size.width, size.height, tile)
                val edgeAlpha = maxAlpha * IslamicEdgePatternEdgeMultiplier
                val fade = Brush.horizontalGradient(
                    0f to Color.Black.copy(alpha = edgeAlpha),
                    0.5f to Color.Black.copy(alpha = 0f),
                    1f to Color.Black.copy(alpha = edgeAlpha),
                )
                val layerPaint = Paint()
                onDrawBehind {
                    if (size.minDimension <= 0f) return@onDrawBehind
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    }
                    drawPath(lattice, color = accent, style = stroke)
                    drawRect(brush = fade, blendMode = BlendMode.DstIn)
                    drawIntoCanvas { canvas -> canvas.restore() }
                }
            },
        )
    }
}

private fun islamicLattice(width: Float, height: Float, tile: Float): Path {
    val lattice = Path()
    if (width <= 0f || height <= 0f || tile <= 0f) return lattice
    val star = polygonStar(outer = tile * 0.34f, inner = tile * 0.15f)
    val octagon = regularPolygon(sides = 8, radius = tile * 0.42f)
    val diamond = regularPolygon(sides = 4, radius = tile * 0.13f)
    val columns = ceil(width / tile).toInt() + 1
    val rows = ceil(height / tile).toInt() + 1
    val originX = (width - (columns - 1) * tile) / 2f
    val originY = (height - (rows - 1) * tile) / 2f
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            val center = Offset(originX + column * tile, originY + row * tile)
            lattice.addPath(star, center)
            lattice.addPath(octagon, center)
            lattice.addPath(diamond, Offset(center.x + tile / 2f, center.y))
            lattice.addPath(diamond, Offset(center.x, center.y + tile / 2f))
        }
    }
    return lattice
}

private fun polygonStar(outer: Float, inner: Float): Path {
    val path = Path()
    repeat(16) { index ->
        val angle = PI / 8.0 * index - PI / 2.0
        val radius = if (index % 2 == 0) outer else inner
        val point = Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}

private fun regularPolygon(sides: Int, radius: Float): Path {
    val path = Path()
    repeat(sides) { index ->
        val angle = 2.0 * PI * index / sides - PI / 2.0
        val point = Offset((cos(angle) * radius).toFloat(), (sin(angle) * radius).toFloat())
        if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    return path
}
