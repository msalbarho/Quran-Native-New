package com.quransunah.app.ui.shell

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.quransunah.app.ui.theme.PaperPaletteId
import com.quransunah.app.ui.theme.PaperPalettes
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingWordHintThemeTest {
    @Test
    fun messageAndCloseIconHaveReadableContrastInAllEightThemes() {
        for (palette in PaperPaletteId.entries) {
            for (night in listOf(false, true)) {
                val paper = PaperPalettes.resolve(palette, night)
                val background = lerp(paper.surface, paper.chromeFill, 0.15f)
                assertTrue("$palette night=$night message", contrast(paper.textStrong, background) >= 4.5f)
                assertTrue("$palette night=$night close", contrast(paper.textStrong, paper.chromeFill) >= 4.5f)
            }
        }
    }
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance()
        val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
}
