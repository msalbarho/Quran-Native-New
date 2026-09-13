package com.quransunah.app.ui.shell

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.dp
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

enum class ChromeFrameOrientation {
    Horizontal,
    Vertical,
}

/**
 * Port of React `ChromeFrameDecor`: cream fill, 8-fold girih, side star bands,
 * and double gold strokes. Used for surah banners and hizb/sajdah badges.
 */
@Composable
fun ChromeFrameDecor(
    modifier: Modifier = Modifier,
    orientation: ChromeFrameOrientation = ChromeFrameOrientation.Horizontal,
) {
    val paper = LocalPaperColors.current
    val fill = paper.chromeFill
    // React: fixed gold strokes; night soft-mixes with accent via decorPattern alpha.
    val frame = ChromeTokens.Gold
    val frameInner = ChromeTokens.GoldInner
    val girih = paper.decorPattern
    val starFill = paper.decorPattern
    val starDot = paper.decorPattern.copy(alpha = (paper.decorPattern.alpha * 1.35f).coerceAtMost(0.22f))

    Canvas(modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 1f || height <= 1f) return@Canvas
        val minSide = min(width, height)
        val radius = ChromeTokens.Corner.toPx().coerceAtMost(minSide * 0.18f)
        val framePath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = width,
                    bottom = height,
                    cornerRadius = CornerRadius(radius, radius),
                ),
            )
        }
        clipPath(framePath) {
        val outerStroke = (minSide * 0.0375f).coerceIn(0.55.dp.toPx(), 1.1.dp.toPx())
        val innerStroke = (minSide * 0.025f).coerceIn(0.45.dp.toPx(), 0.75.dp.toPx())
        val outerInset = outerStroke * 0.85f
        val innerInset = outerInset + innerStroke + (minSide * 0.02f).coerceAtLeast(0.35.dp.toPx())
        val contentInset = innerInset + (minSide * 0.04f).coerceAtLeast(1.dp.toPx())
        val bandRatio = if (minSide < 40f) 0.22f else 0.28f
        val fadeRatio = if (minSide < 40f) 0.1f else 0.12f
        val clearRatio = (1f - bandRatio * 2f - fadeRatio * 0.35f).coerceAtLeast(0.28f)
        val tile = (minSide * 0.42f).coerceIn(8f, 14f)
        val girihTile = (minSide * 0.72f).coerceIn(18f, 28f)
        val vertical = orientation == ChromeFrameOrientation.Vertical

        drawRoundRect(color = fill, cornerRadius = CornerRadius(radius, radius))

        var gy = 0f
        while (gy < height + girihTile) {
            var gx = 0f
            while (gx < width + girihTile) {
                drawGirihStar(
                    center = Offset(gx + girihTile / 2f, gy + girihTile / 2f),
                    outer = girihTile * 0.36f,
                    color = girih,
                    stroke = 0.7.dp.toPx(),
                )
                gx += girihTile
            }
            gy += girihTile
        }

        val bandA: Offset
        val bandB: Offset
        val bandSize: Size
        val fadeA: Offset
        val fadeB: Offset
        val fadeSize: Size
        val clearOffset: Offset
        val clearSize: Size
        if (vertical) {
            val bandH = height * bandRatio
            val fadeH = height * fadeRatio
            val clearH = height * clearRatio
            val clearY = (height - clearH) / 2f
            val bandW = width - contentInset * 2f
            bandA = Offset(contentInset, contentInset)
            bandB = Offset(contentInset, height - contentInset - bandH)
            bandSize = Size(bandW, bandH)
            fadeA = Offset(contentInset, clearY - fadeH)
            fadeB = Offset(contentInset, clearY + clearH)
            fadeSize = Size(bandW, fadeH)
            clearOffset = Offset(contentInset, clearY)
            clearSize = Size(bandW, clearH)
        } else {
            val bandW = width * bandRatio
            val fadeW = width * fadeRatio
            val clearW = width * clearRatio
            val clearX = (width - clearW) / 2f
            val bandH = height - contentInset * 2f
            bandA = Offset(contentInset, contentInset)
            bandB = Offset(width - contentInset - bandW, contentInset)
            bandSize = Size(bandW, bandH)
            fadeA = Offset(clearX - fadeW, contentInset)
            fadeB = Offset(clearX + clearW, contentInset)
            fadeSize = Size(fadeW, bandH)
            clearOffset = Offset(clearX, contentInset)
            clearSize = Size(clearW, bandH)
        }

        drawStarBand(bandA, bandSize, tile, starFill, starDot)
        drawStarBand(bandB, bandSize, tile, starFill, starDot)

        val fadeNear = if (vertical) {
            Brush.verticalGradient(listOf(fill.copy(alpha = 0f), fill), startY = fadeA.y, endY = fadeA.y + fadeSize.height)
        } else {
            Brush.horizontalGradient(listOf(fill.copy(alpha = 0f), fill), startX = fadeA.x, endX = fadeA.x + fadeSize.width)
        }
        val fadeFar = if (vertical) {
            Brush.verticalGradient(listOf(fill, fill.copy(alpha = 0f)), startY = fadeB.y, endY = fadeB.y + fadeSize.height)
        } else {
            Brush.horizontalGradient(listOf(fill, fill.copy(alpha = 0f)), startX = fadeB.x, endX = fadeB.x + fadeSize.width)
        }
        drawRect(brush = fadeNear, topLeft = fadeA, size = fadeSize)
        drawRect(brush = fadeFar, topLeft = fadeB, size = fadeSize)
        drawRect(color = fill, topLeft = clearOffset, size = clearSize)

        val outerRx = (radius - outerInset * 0.35f).coerceAtLeast(0f)
        val innerRx = (radius - innerInset * 0.45f).coerceAtLeast(0f)
        drawRoundRect(
            color = frame,
            topLeft = Offset(outerInset, outerInset),
            size = Size((width - outerInset * 2f).coerceAtLeast(0f), (height - outerInset * 2f).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(outerRx, outerRx),
            style = Stroke(width = outerStroke),
        )
        drawRoundRect(
            color = frameInner,
            topLeft = Offset(innerInset, innerInset),
            size = Size((width - innerInset * 2f).coerceAtLeast(0f), (height - innerInset * 2f).coerceAtLeast(0f)),
            cornerRadius = CornerRadius(innerRx, innerRx),
            style = Stroke(width = innerStroke),
        )
        }
    }
}

private fun DrawScope.drawStarBand(
    origin: Offset,
    size: Size,
    tile: Float,
    star: Color,
    dot: Color,
) {
    if (size.width <= 1f || size.height <= 1f) return
    var y = origin.y
    while (y < origin.y + size.height + tile) {
        var x = origin.x
        while (x < origin.x + size.width + tile) {
            val center = Offset(x + tile / 2f, y + tile / 2f)
            drawGirihStar(center, tile * 0.36f, star, stroke = 0f, fill = true)
            drawCircle(color = dot, radius = tile * 0.06f, center = center)
            x += tile
        }
        y += tile
    }
}

internal fun DrawScope.drawGirihStar(
    center: Offset,
    outer: Float,
    color: Color,
    stroke: Float,
    fill: Boolean = false,
) {
    val inner = outer * 0.41f
    val path = Path()
    for (i in 0 until 16) {
        val angle = (PI / 8.0) * i - PI / 2.0
        val radius = if (i % 2 == 0) outer else inner
        val x = center.x + (cos(angle) * radius).toFloat()
        val y = center.y + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    if (fill) {
        drawPath(path, color)
    } else {
        drawPath(path, color, style = Stroke(width = stroke.coerceAtLeast(0.6f)))
    }
}
