package com.quransunah.app.ui.mushaf

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.R
import com.quransunah.app.domain.model.*
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.PaperPaletteId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MushafHeaderInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val page = MushafPage(272, listOf(MushafLine(1, LineType.AYAH, false, 16,
        listOf(QuranWord(1, 16, 43, 1, "", "")))), 14, 28,
        sajda = SajdaMarker(272, 16, 50, false))

    @Test fun identicalFrameBoundsAcrossThemesModesAndWidths() {
        var night by mutableStateOf(false)
        var palette by mutableStateOf(PaperPaletteId.Green)
        var width by mutableStateOf(280)
        var controls by mutableStateOf(true)
        var infoBounds = Rect.Zero
        var controlsBounds = Rect.Zero
        var textBounds = Rect.Zero
        compose.setContent {
            HolyQuranTheme(palette, night) {
                Box(Modifier.width(width.dp).height(400.dp)) {
                    PageHeaderBar(page, 272, null, night, {}, {}, {}, {}, {},
                        surahNames = mapOf(16 to "النحل"), informationOnly = true,
                        hizbLabel = "ثلاثة أرباع الحزب السابع والعشرون", modifier = Modifier.onGloballyPositioned { infoBounds = it.boundsInRoot() })
                    Box(Modifier.padding(top = 44.dp).fillMaxWidth().height(300.dp)
                        .onGloballyPositioned { textBounds = it.boundsInRoot() })
                    if (controls) PageHeaderBar(page, 272, null, night, {}, {}, {}, {}, {},
                        modifier = Modifier.onGloballyPositioned { controlsBounds = it.boundsInRoot() })
                }
            }
        }
        for (theme in PaperPaletteId.entries) for (dark in listOf(false, true)) {
            for (screenWidth in listOf(280, 360)) {
                compose.runOnIdle { palette = theme; night = dark; width = screenWidth; controls = true }
                compose.waitForIdle()
                val before = textBounds
                assertEquals(infoBounds, controlsBounds)
                assertEquals(6, compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size)
                val buttons = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes()
                    .map { it.layoutInfo.coordinates.boundsInRoot() }
                buttons.forEach { assertTrue("button stays inside header: $theme night=$dark width=$screenWidth button=$it header=$infoBounds",
                    it.left >= infoBounds.left - 0.5f && it.right <= infoBounds.right + 0.5f) }
                assertTrue("controls stay in physical RTL order", buttons.zipWithNext().all { (a, b) -> a.center.x > b.center.x })
                buttons.forEach { assertEquals("equal button heights", buttons.first().height, it.height, 0.5f) }
                assertEquals("meanings and search widths", buttons[0].width, buttons[1].width, 1f)
                buttons.drop(2).forEach { assertEquals("four equal icon buttons", buttons[2].width, it.width, 1f) }
                // Row weights distribute the remaining physical pixels after rounding each slot.
                assertEquals("wide buttons are 25 percent wider", buttons[2].width * 1.25f, buttons[0].width, 2.5f)
                val gaps = buttons.zipWithNext().map { (a, b) -> a.left - b.right }
                gaps.forEach { assertEquals("equal gaps", gaps.first(), it, 1f) }
                val info = compose.onNodeWithTag("mushaf-header-info-icon", useUnmergedTree = true)
                    .fetchSemanticsNode().layoutInfo.coordinates.boundsInRoot()
                assertEquals("info centered horizontally", buttons[4].center.x, info.center.x, 0.5f)
                assertEquals("info centered vertically", buttons[4].center.y, info.center.y, 0.5f)
                assertTrue("info has clear space above and below", info.top > buttons[4].top && info.bottom < buttons[4].bottom)
                compose.runOnIdle { controls = false }
                compose.waitForIdle()
                assertEquals("text must not move", before, textBounds)
                assertEquals("header remains mounted", controlsBounds, infoBounds)
                compose.runOnIdle { controls = true }
                compose.waitForIdle()
                assertEquals(before, textBounds)
            }
        }
    }

    @Test fun paletteToggleAndGuideCloseWork() {
        var enabled by mutableStateOf(true)
        var guide by mutableStateOf(false)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            HolyQuranTheme(PaperPaletteId.Green) {
                PageHeaderBar(page, 272, null, false, {}, {}, {}, {}, {},
                    tajweedEnabled = enabled, onToggleTajweed = { enabled = !enabled },
                    onTajweedGuide = { guide = true })
                if (guide) TajweedGuideDialog { guide = false }
            }
        }
        compose.onNodeWithContentDescription(context.getString(R.string.tajweed_colors)).performClick()
        compose.runOnIdle { assertFalse(enabled) }
        compose.onNodeWithContentDescription(context.getString(R.string.tajweed_colors)).performClick()
        compose.runOnIdle { assertTrue(enabled) }
        compose.onNodeWithContentDescription(context.getString(R.string.tajweed_guide)).performClick()
        compose.onNodeWithText(context.getString(R.string.tajweed_rule_1)).assertExists()
        compose.onNodeWithText(context.getString(R.string.tajweed_rule_8)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.close)).performClick()
        compose.runOnIdle { assertFalse(guide) }
    }

    @Test fun monochromeInkPreservesEveryGlyphPixel() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = com.quransunah.app.fonts.QpcV4Glyphs(context)
        val fonts = com.quransunah.app.fonts.QcfFontManager(context, glyphs)
        val face = fonts.loadPageTypeface(1)
        val text = (1..4).joinToString("") { glyphs.forWord(it).orEmpty() }
        assertTrue(text.isNotEmpty())
        fun render(monochrome: Boolean, ink: Int): IntArray {
            val bitmap = android.graphics.Bitmap.createBitmap(900, 160, android.graphics.Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                textSize = 90f
                applyGlyphColor(monochrome, false, ink, android.graphics.Color.BLUE)
            }
            android.graphics.Canvas(bitmap).drawText(text, 50f, 110f, paint)
            return IntArray(900 * 160).also { bitmap.getPixels(it, 0, 900, 0, 0, 900, 160) }
        }
        val colored = render(false, android.graphics.Color.BLACK)
        assertTrue("the real page font should contain tajweed colors",
            colored.any { android.graphics.Color.alpha(it) == 255 &&
                (android.graphics.Color.red(it) != android.graphics.Color.blue(it)) })
        for (ink in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)) {
            val plain = render(true, ink)
            for (i in colored.indices) {
                assertEquals("glyph geometry at pixel $i", android.graphics.Color.alpha(colored[i]),
                    android.graphics.Color.alpha(plain[i]))
                if (android.graphics.Color.alpha(plain[i]) == 255) assertEquals(ink, plain[i])
            }
        }
    }

    @Test fun divisionNumbersAreWordsInAllSupportedLanguages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("التاسع", headerNumberWords(9, "ar", context.resources))
        assertEquals("الثامن عشر", headerNumberWords(18, "ar", context.resources))
        assertEquals("السابع والعشرون", headerNumberWords(27, "ar", context.resources))
        for (language in com.quransunah.app.core.LanguageRegistry.entries) {
            val config = android.content.res.Configuration(context.resources.configuration).apply {
                setLocale(java.util.Locale.forLanguageTag(language.tag))
            }
            val localized = context.createConfigurationContext(config).resources
            for (number in 1..60) {
                val words = headerNumberWords(number, language.tag, localized)
                assertTrue("${language.tag}: $words", words.isNotBlank() && words.none(Char::isDigit))
            }
        }
    }

    @Test fun headerUsesSmallQbsmlAndOnlyFirstSurah() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fonts = com.quransunah.app.fonts.QcfFontManager(context,
            com.quransunah.app.fonts.QpcV4Glyphs(context))
        fonts.ensureMaps()
        val face = fonts.loadQbsmlTypeface()
        assertNotNull(face)
        val multi = MushafPage(604, listOf(112, 113, 114).mapIndexed { i, number ->
            MushafLine(i * 4 + 1, LineType.SURAH_NAME, true, number, emptyList())
        }, 30, 60)
        compose.setContent {
            HolyQuranTheme(PaperPaletteId.Green) {
                CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                    Column(Modifier.width(360.dp).height(100.dp).testTag("qbsml-preview")) {
                        PageHeaderBar(multi, 604, fonts, false, {}, {}, {}, {}, {},
                            informationOnly = true, hizbLabel = "الحزب الستون",
                            surahNames = mapOf(112 to "الإخلاص", 113 to "الفلق", 114 to "الناس"))
                        SurahNameFrame(112, SurahInfo(112, "الإخلاص", 4, "meccan", 604),
                            fonts, face, {}, {}, Modifier.padding(horizontal = 10.dp).height(30.dp))
                    }
                }
            }
        }
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag("mushaf-header-surah", useUnmergedTree = true).fetchSemanticsNodes().size == 1
        }
        compose.onNodeWithTag("mushaf-header-juz", useUnmergedTree = true).assertExists()
        val description = compose.onNodeWithTag("mushaf-header-surah", useUnmergedTree = true)
            .fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description.contains("الإخلاص") || description.contains("Ikhlas", ignoreCase = true))
        assertFalse(description.contains("الفلق") || description.contains("Falaq", ignoreCase = true))
        assertFalse(description.contains("الناس") || description.contains("Nas", ignoreCase = true))
        val bitmap = compose.onNodeWithTag("qbsml-preview").captureToImage().asAndroidBitmap()
        java.io.File(context.filesDir, "qbsml-header-preview.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        Unit
    }

    @Test fun ayahMedallionKeepsOriginalPaletteWithAnyBodyInk() = kotlinx.coroutines.runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = com.quransunah.app.fonts.QpcV4Glyphs(context)
        val fonts = com.quransunah.app.fonts.QcfFontManager(context, glyphs)
        val originalMarkerFace = fonts.loadPageTypeface(1, false)
        val marker = glyphs.forWord(5).orEmpty()
        assertTrue(marker.isNotEmpty())
        fun render(plain: Boolean, ink: Int, highlighted: Boolean): IntArray {
            val bitmap = android.graphics.Bitmap.createBitmap(160, 180, android.graphics.Bitmap.Config.ARGB_8888)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                typeface = originalMarkerFace
                textSize = 110f
                applyGlyphColor(plain, highlighted, ink, android.graphics.Color.BLUE, preservePalette = true)
            }
            android.graphics.Canvas(bitmap).drawText(marker, 25f, 130f, paint)
            return IntArray(160 * 180).also { bitmap.getPixels(it, 0, 160, 0, 0, 160, 180) }
        }
        val original = render(false, android.graphics.Color.BLACK, false)
        for (plain in listOf(false, true)) for (highlight in listOf(false, true)) {
            for (ink in listOf(android.graphics.Color.BLACK, android.graphics.Color.WHITE)) {
                assertArrayEquals("ayah colors remain independent of text ink", original, render(plain, ink, highlight))
            }
        }
    }
}
