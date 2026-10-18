package com.quransunah.app.ui.shell

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Bottom-bar ends only. Each side is a straight outer edge with a concave
 * quarter-circle cutout at the upper and lower corners.
 */
internal object BottomNavBracketShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(bottomNavBracketPath(size.width, size.height))
}

internal fun bottomNavBracketPath(width: Float, height: Float): Path {
    val radius = min(height * 0.22f, width * 0.12f)
    if (radius < 1f || width <= radius * 2f || height <= radius * 2f) {
        return Path().apply { addRect(Rect(0f, 0f, width, height)) }
    }
    return Path().apply {
        moveTo(radius, 0f)
        lineTo(width - radius, 0f)
        arcTo(Rect(width - radius, -radius, width + radius, radius), 180f, -90f, false)
        lineTo(width, height - radius)
        arcTo(Rect(width - radius, height - radius, width + radius, height + radius), 270f, -90f, false)
        lineTo(radius, height)
        arcTo(Rect(-radius, height - radius, radius, height + radius), 0f, -90f, false)
        lineTo(0f, radius)
        arcTo(Rect(-radius, -radius, radius, radius), 90f, -90f, false)
        close()
    }
}
