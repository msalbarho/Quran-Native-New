package com.quransunah.app.ui.shell

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quransunah.app.R

private val Sun = Color(0xFFFFC107)
private val SunDeep = Color(0xFFD4AF37)
private val SunEdge = Color(0xFF9A7B22)
private val Moon = Color(0xFFC8D0D8)
private val Star = Color(0xFFEEF2F6)

private val MoonPathData =
    "M8.2 4.35a8.05 8.05 0 1 0 9.55 11.9 6.55 6.55 0 1 1-6.7-11.35 8.05 8.05 0 0 0-2.85-.55Z"

@Composable
fun ThemeToggle(
    night: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 26.dp,
    height: Dp = 22.dp,
) {
    val progress by animateFloatAsState(
        targetValue = if (night) 1f else 0f,
        animationSpec = tween(220),
        label = "theme-toggle",
    )
    val label = stringResource(if (night) R.string.theme_to_light else R.string.theme_to_dark)
    val moonPath = remember { PathParser().parsePathString(MoonPathData).toPath() }

    Box(
        modifier = modifier
            .size(width, height)
            .semantics { contentDescription = label }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = onToggle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val pad = size.minDimension * 0.03f
            if (progress < 0.96f) {
                drawSun(pad, 1f - progress)
            }
            if (progress > 0.04f) {
                drawMoon(moonPath, pad, progress)
            }
        }
    }
}

private fun DrawScope.drawSun(pad: Float, alpha: Float) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val radius = size.minDimension * 0.28f
    for (index in 0 until 8) {
        rotate(index * 45f, Offset(cx, cy)) {
            drawRoundRect(
                color = SunDeep.copy(alpha = alpha),
                topLeft = Offset(cx - size.minDimension * 0.035f, pad),
                size = Size(size.minDimension * 0.07f, size.minDimension * 0.15f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.035f),
            )
        }
    }
    drawCircle(color = Sun.copy(alpha = alpha), radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = SunEdge.copy(alpha = alpha),
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = size.minDimension * 0.04f),
    )
}

private fun DrawScope.drawMoon(moon: Path, pad: Float, alpha: Float) {
    val scale = (size.minDimension - pad * 2f) / 24f
    withTransform({
        translate(left = (size.width - 24f * scale) / 2f, top = (size.height - 24f * scale) / 2f)
        scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
    }) {
        drawPath(moon, Moon.copy(alpha = alpha))
        fun sparkle(cx: Float, cy: Float, sparkleSize: Float) {
            val tip = sparkleSize / 2f
            val waist = sparkleSize * 0.12f
            val path = Path().apply {
                moveTo(cx, cy - tip)
                lineTo(cx + waist, cy - waist)
                lineTo(cx + tip, cy)
                lineTo(cx + waist, cy + waist)
                lineTo(cx, cy + tip)
                lineTo(cx - waist, cy + waist)
                lineTo(cx - tip, cy)
                lineTo(cx - waist, cy - waist)
                close()
            }
            drawPath(path, Star.copy(alpha = alpha))
        }
        sparkle(16.35f, 5.15f, 4.6f)
        sparkle(19.55f, 8.45f, 3.3f)
        sparkle(17.65f, 16.55f, 3.0f)
        sparkle(13.45f, 7.05f, 2.35f)
        sparkle(15.05f, 11.15f, 2.0f)
    }
}
