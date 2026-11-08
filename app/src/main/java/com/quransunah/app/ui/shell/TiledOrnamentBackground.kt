package com.quransunah.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.core.content.ContextCompat
import com.quransunah.app.R
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.ceil

internal const val OrnamentTileColumns = 6
internal const val OrnamentViewportWidth = 510f
internal const val OrnamentViewportHeight = 536f

internal data class OrnamentTileGrid(
    val columns: Int,
    val rows: Int,
    val tileWidthPx: Float,
    val tileHeightPx: Float,
)

/** Compute a six-column grid while preserving the vector's original 510:536 aspect ratio. */
internal fun ornamentTileGrid(widthPx: Float, heightPx: Float): OrnamentTileGrid {
    if (widthPx <= 0f || heightPx <= 0f) {
        return OrnamentTileGrid(OrnamentTileColumns, 0, 0f, 0f)
    }
    val tileWidth = widthPx / OrnamentTileColumns
    val tileHeight = tileWidth * (OrnamentViewportHeight / OrnamentViewportWidth)
    val rows = ceil(heightPx / tileHeight).toInt().coerceAtLeast(1)
    return OrnamentTileGrid(OrnamentTileColumns, rows, tileWidth, tileHeight)
}

/**
 * A low-contrast, theme-tinted background using one Canvas and the supplied vector resource.
 * Every row has six equal-width tiles; the height is derived from the vector aspect ratio.
 */
@Composable
internal fun TiledOrnamentBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val paper = LocalPaperColors.current
    val nightMode = LocalNightMode.current
    val tint = paper.accent.copy(alpha = if (nightMode) 0.08f else 0.06f)
    val drawable = remember(context, tint) {
        ContextCompat.getDrawable(context, R.drawable.intri11)?.mutate()?.apply {
            setTint(tint.toArgb())
        }
    }

    if (drawable == null) {
        Box(modifier)
        return
    }

    Box(
        modifier = modifier
            .clearAndSetSemantics { }
            .drawWithCache {
                val grid = ornamentTileGrid(size.width, size.height)
                val tileWidth = grid.tileWidthPx
                val tileHeight = grid.tileHeightPx
                val rows = grid.rows
                onDrawBehind {
                    if (rows == 0 || tileWidth <= 0f || tileHeight <= 0f) return@onDrawBehind
                    drawIntoCanvas { composeCanvas ->
                        val canvas = composeCanvas.nativeCanvas
                        val clipSave = canvas.save()
                        canvas.clipRect(0f, 0f, size.width, size.height)
                        drawable.setBounds(
                            0,
                            0,
                            OrnamentViewportWidth.toInt(),
                            OrnamentViewportHeight.toInt(),
                        )
                        for (row in 0 until rows) {
                            for (column in 0 until OrnamentTileColumns) {
                                val tileSave = canvas.save()
                                canvas.translate(column * tileWidth, row * tileHeight)
                                canvas.scale(
                                    tileWidth / OrnamentViewportWidth,
                                    tileHeight / OrnamentViewportHeight,
                                )
                                drawable.draw(canvas)
                                canvas.restoreToCount(tileSave)
                            }
                        }
                        canvas.restoreToCount(clipSave)
                    }
                }
            },
    )
}
