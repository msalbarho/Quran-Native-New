package com.quransunah.app.ui.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

class TiledOrnamentBackgroundTest {
    @Test
    fun gridKeepsSixEqualColumnsAndOriginalAspectRatioAtDifferentWidths() {
        for (width in listOf(320f, 393f, 1080f, 1440f)) {
            val grid = ornamentTileGrid(width, 2400f)

            assertEquals(6, grid.columns)
            assertEquals(width, grid.columns * grid.tileWidthPx, 0.001f)
            assertEquals(
                grid.tileWidthPx * (536f / 510f),
                grid.tileHeightPx,
                0.001f,
            )
        }
    }

    @Test
    fun rowCountCoversTheEntireBackgroundWithoutAnUnneededExtraRow() {
        for ((width, height) in listOf(320f to 640f, 393f to 852f, 1080f to 2400f, 1440f to 3200f)) {
            val grid = ornamentTileGrid(width, height)
            val expectedRows = ceil(height / grid.tileHeightPx).toInt()

            assertEquals(expectedRows, grid.rows)
            assertTrue(grid.rows * grid.tileHeightPx >= height)
            assertTrue((grid.rows - 1) * grid.tileHeightPx < height)
        }
    }

    @Test
    fun emptyBoundsDoNotProduceRowsOrInvalidTileDimensions() {
        val grid = ornamentTileGrid(0f, 800f)

        assertEquals(6, grid.columns)
        assertEquals(0, grid.rows)
        assertEquals(0f, grid.tileWidthPx, 0f)
        assertEquals(0f, grid.tileHeightPx, 0f)
    }
}
