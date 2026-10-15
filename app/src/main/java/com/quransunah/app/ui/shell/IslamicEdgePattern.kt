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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin

/** Configured ornament opacity. Fade modes multiply this; they do not replace it. */
internal const val IslamicEdgePatternAlpha = 0.22f

/** Horizontal fade: physical edges. The horizontal center stays at 0. */
private const val HorizontalEdgeMultiplier = 0.8f

/** Vertical fade on sheets: top, then bottom. */
private const val VerticalTopMultiplier = 0.10f
private const val VerticalBottomMultiplier = 0.90f

/** Home background stays quieter than the sheet fade. */
private const val HomeBottomMultiplier = 0.60f

/** Vertical fade on narrow cards: top and bottom, with a quiet center. */
private const val CardEdgeMultiplier = 0.90f
private const val CardCenterMultiplier = 0.10f

/** Fill is a softer tone of the same accent; the stroke stays at full accent. */
private const val OrnamentFillAlpha = 0.62f

internal enum class IslamicOrnamentFade {
    /** Physical left and right at 0.8, horizontal center at 0. */
    Horizontal,
    /** Top at 0.10, bottom at 0.90. */
    Vertical,
    /** Home background: top at 0.10, bottom at 0.60. */
    VerticalHome,
    /** Top and bottom at 0.90, vertical center at 0.10. */
    VerticalEdges,
}

/** Nearest-star spacing. Fractions below are measured from that pitch. */
private const val StarOuterFraction = 0.119f
private const val StarInnerFraction = 0.056f
private const val RhombusCenterFraction = 0.311f
private const val RhombusRadialFraction = 0.148f
private const val RhombusTransverseFraction = 0.111f
private const val Sqrt2 = 1.41421356f

/**
 * A non-interactive eight-point star lattice. Stars sit on a diagonal grid,
 * each ringed by eight rhombi. Fills and outlines share the theme accent.
 * [fade] decides where that ink is strongest.
 */
@Composable
internal fun IslamicEdgePattern(
    modifier: Modifier = Modifier,
    maxAlpha: Float = IslamicEdgePatternAlpha,
    fade: IslamicOrnamentFade = IslamicOrnamentFade.Horizontal,
    pitch: Dp = 28.dp,
) {
    val accent = LocalPaperColors.current.accent
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier.clearAndSetSemantics { }.drawWithCache {
                val pitchPx = pitch.toPx()
                val stroke = Stroke(width = 0.7.dp.toPx() * (pitchPx / 28.dp.toPx()).coerceIn(0.65f, 1f))
                val lattice = islamicLattice(size.width, size.height, pitchPx)
                val mask = when (fade) {
                    IslamicOrnamentFade.Horizontal -> Brush.horizontalGradient(
                        0f to maskColor(maxAlpha * HorizontalEdgeMultiplier),
                        0.5f to maskColor(0f),
                        1f to maskColor(maxAlpha * HorizontalEdgeMultiplier),
                    )
                    IslamicOrnamentFade.Vertical -> Brush.verticalGradient(
                        0f to maskColor(maxAlpha * VerticalTopMultiplier),
                        1f to maskColor(maxAlpha * VerticalBottomMultiplier),
                    )
                    IslamicOrnamentFade.VerticalHome -> Brush.verticalGradient(
                        0f to maskColor(maxAlpha * VerticalTopMultiplier),
                        1f to maskColor(maxAlpha * HomeBottomMultiplier),
                    )
                    IslamicOrnamentFade.VerticalEdges -> Brush.verticalGradient(
                        0f to maskColor(maxAlpha * CardEdgeMultiplier),
                        0.5f to maskColor(maxAlpha * CardCenterMultiplier),
                        1f to maskColor(maxAlpha * CardEdgeMultiplier),
                    )
                }
                val fill = accent.copy(alpha = OrnamentFillAlpha)
                val layerPaint = Paint()
                onDrawBehind {
                    if (size.minDimension <= 0f) return@onDrawBehind
                    drawIntoCanvas { canvas ->
                        canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    }
                    drawPath(lattice, color = fill)
                    drawPath(lattice, color = accent, style = stroke)
                    drawRect(brush = mask, blendMode = BlendMode.DstIn)
                    drawIntoCanvas { canvas -> canvas.restore() }
                }
            },
        )
    }
}

private fun maskColor(alpha: Float) = Color.Black.copy(alpha = alpha.coerceIn(0f, 1f))

private fun islamicLattice(width: Float, height: Float, pitch: Float): Path {
    val lattice = Path()
    if (width <= 0f || height <= 0f || pitch <= 0f) return lattice
    val motif = khatamMotif(pitch)
    val step = pitch / Sqrt2
    val margin = pitch * 0.55f
    val originX = width / 2f
    val originY = height / 2f
    val iMax = ceil((width + height) / (2f * step)).toInt() + 1
    for (i in -iMax..iMax) {
        val ix = i * step
        val iy = i * step
        for (j in -iMax..iMax) {
            val x = originX + ix - j * step
            val y = originY + iy + j * step
            if (x < -margin || y < -margin || x > width + margin || y > height + margin) continue
            lattice.addPath(motif, Offset(x, y))
        }
    }
    return lattice
}

/** One star plus the eight rhombi around it, centered on the origin. */
private fun khatamMotif(pitch: Float): Path {
    val path = Path()
    val outer = pitch * StarOuterFraction
    val inner = pitch * StarInnerFraction
    repeat(16) { index ->
        val angle = PI / 8.0 * index - PI / 2.0
        val radius = if (index % 2 == 0) outer else inner
        val x = (cos(angle) * radius).toFloat()
        val y = (sin(angle) * radius).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()

    val center = pitch * RhombusCenterFraction
    val radial = pitch * RhombusRadialFraction
    val transverse = pitch * RhombusTransverseFraction
    val localX = floatArrayOf(center + radial, center, center - radial, center)
    val localY = floatArrayOf(0f, transverse, 0f, -transverse)
    repeat(8) { index ->
        val angle = PI / 4.0 * index - PI / 2.0
        val c = cos(angle)
        val s = sin(angle)
        repeat(4) { vertex ->
            val lx = localX[vertex]
            val ly = localY[vertex]
            val x = (c * lx - s * ly).toFloat()
            val y = (s * lx + c * ly).toFloat()
            if (vertex == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
    }
    return path
}
