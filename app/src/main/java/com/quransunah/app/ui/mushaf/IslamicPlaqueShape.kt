package com.quransunah.app.ui.mushaf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

internal enum class PlaqueAxis {
    Horizontal,
    Vertical,
}

/**
 * Elongated cartouche: straight body, a small scalloped shoulder, and a centered point.
 * Horizontal plaques point left and right. Vertical plaques point up and down.
 * The outline is symmetric and stays inside the measured bounds.
 */
internal class IslamicPlaqueShape(private val axis: PlaqueAxis) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(islamicPlaquePath(size.width, size.height, axis))
}

internal val HorizontalIslamicPlaque = IslamicPlaqueShape(PlaqueAxis.Horizontal)
internal val VerticalIslamicPlaque = IslamicPlaqueShape(PlaqueAxis.Vertical)

internal fun islamicPlaquePath(
    width: Float,
    height: Float,
    axis: PlaqueAxis,
    inset: Float = 0f,
): Path {
    val innerW = (width - inset * 2f).coerceAtLeast(1f)
    val innerH = (height - inset * 2f).coerceAtLeast(1f)
    val along = if (axis == PlaqueAxis.Horizontal) innerW else innerH
    val across = if (axis == PlaqueAxis.Horizontal) innerH else innerW
    val tip = plaqueTipDepth(along, across)
    if (tip < 0.5f) {
        return Path().apply {
            addRect(Rect(inset, inset, width - inset, height - inset))
        }
    }
    val mid = across / 2f
    fun map(x: Float, y: Float): Offset {
        return if (axis == PlaqueAxis.Horizontal) {
            Offset(inset + x, inset + y)
        } else {
            Offset(inset + y, inset + x)
        }
    }
    fun Path.corner(c1x: Float, c1y: Float, c2x: Float, c2y: Float, ex: Float, ey: Float) {
        val c1 = map(c1x, c1y)
        val c2 = map(c2x, c2y)
        val end = map(ex, ey)
        cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
    }
    // One cap, from the point at (0, mid) onto the straight edge at (tip, 0).
    val cap = listOf(
        floatArrayOf(tip * 0.16f, mid - across * 0.06f, tip * 0.36f, across * 0.28f, tip * 0.50f, across * 0.20f),
        floatArrayOf(tip * 0.58f, across * 0.16f, tip * 0.66f, across * 0.24f, tip * 0.76f, across * 0.11f),
        floatArrayOf(tip * 0.86f, across * 0.04f, tip * 0.95f, 0f, tip, 0f),
    )
    val path = Path()
    path.moveTo(map(0f, mid).x, map(0f, mid).y)
    for (seg in cap) {
        path.corner(seg[0], seg[1], seg[2], seg[3], seg[4], seg[5])
    }
    path.lineTo(map(along - tip, 0f).x, map(along - tip, 0f).y)
    for (index in cap.indices.reversed()) {
        val seg = cap[index]
        val startX = if (index == 0) 0f else cap[index - 1][4]
        val startY = if (index == 0) mid else cap[index - 1][5]
        path.corner(along - seg[2], seg[3], along - seg[0], seg[1], along - startX, startY)
    }
    for (seg in cap) {
        path.corner(along - seg[0], across - seg[1], along - seg[2], across - seg[3], along - seg[4], across - seg[5])
    }
    path.lineTo(map(tip, across).x, map(tip, across).y)
    for (index in cap.indices.reversed()) {
        val seg = cap[index]
        val startX = if (index == 0) 0f else cap[index - 1][4]
        val startY = if (index == 0) mid else cap[index - 1][5]
        path.corner(seg[2], across - seg[3], seg[0], across - seg[1], startX, across - startY)
    }
    path.close()
    return path
}

/** Point depth follows the short side, and shrinks when the long side cannot spare it. */
private fun plaqueTipDepth(along: Float, across: Float): Float {
    if (along <= 1f || across <= 1f) return 0f
    return min(across * 0.42f, along * 0.22f).coerceAtMost(along * 0.46f)
}
