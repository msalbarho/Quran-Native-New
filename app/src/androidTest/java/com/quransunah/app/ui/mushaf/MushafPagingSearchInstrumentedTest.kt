package com.quransunah.app.ui.mushaf

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.quransunah.app.domain.model.*
import com.quransunah.app.ui.search.SearchPaneContent
import com.quransunah.app.ui.search.SearchUiState
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.PaperPaletteId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MushafPagingSearchInstrumentedTest {
    @get:Rule val compose = createComposeRule()

    @Test fun searchStaysSingleLineAndCenteredAcrossWidthsAndModes() {
        var width by mutableStateOf(280)
        var night by mutableStateOf(false)
        var query by mutableStateOf("")
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val config = android.content.res.Configuration(context.resources.configuration).apply {
            setLocales(android.os.LocaleList(java.util.Locale.forLanguageTag("ar")))
        }
        val arabicContext = context.createConfigurationContext(config)
        compose.setContent {
            CompositionLocalProvider(androidx.compose.ui.platform.LocalContext provides arabicContext) {
            HolyQuranTheme(PaperPaletteId.Green, night) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    SearchPaneContent(SearchUiState(query = query), false, { query = it },
                        { query = "" }, {}, {}, Modifier.width(width.dp).height(400.dp).testTag("search-preview"))
                }
            }
        }
        }
        for (dark in listOf(false, true)) for (w in listOf(280, 360)) {
            compose.runOnIdle { night = dark; width = w; query = "" }
            compose.onNodeWithTag("mushaf-search-input").assertHeightIsEqualTo(56.dp)
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("mushaf-search-placeholder", true)
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("overflow width=$w night=$dark size=${layouts.single().size} constraints=${layouts.single().layoutInput.constraints} style=${layouts.single().layoutInput.style} paragraph=${layouts.single().multiParagraph.width}x${layouts.single().multiParagraph.height}", layouts.single().hasVisualOverflow)
            val input = compose.onNodeWithTag("mushaf-search-input").fetchSemanticsNode().boundsInRoot
            val icon = compose.onNodeWithTag("mushaf-search-icon", true).fetchSemanticsNode().boundsInRoot
            assertEquals(input.center.y, icon.center.y, 1f)
            if (!dark && w == 360) {
                val bitmap = compose.onNodeWithTag("search-preview").captureToImage().asAndroidBitmap()
                java.io.File(context.filesDir, "compact-search-preview.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
            }
            compose.onNodeWithTag("mushaf-search-input").performTextInput("2:255")
            compose.runOnIdle { assertEquals("2:255", query) }
        }
    }

    @Test fun headerTravelsWithItsPageAndControlsDoNotChangeTextBounds() {
        val headerBounds = mutableMapOf<Int, Rect>()
        val textBounds = mutableMapOf<Int, Rect>()
        var controls by mutableStateOf(true)
        compose.setContent {
            HolyQuranTheme(PaperPaletteId.Green) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                val pager = rememberPagerState { 2 }
                Box(Modifier.width(300.dp).height(420.dp)) {
                    HorizontalPager(pager, beyondViewportPageCount = 1,
                        modifier = Modifier.fillMaxSize().testTag("test-pager")) { index ->
                        val page = MushafPage(index + 1, listOf(MushafLine(1, LineType.AYAH,
                            false, index + 16, listOf(QuranWord(index + 1, index + 16, 1, 1, "", "")))), index + 1, index + 1)
                        Box(Modifier.fillMaxSize()) {
                            PageHeaderBar(page, index + 1, null, false, {}, {}, {}, {}, {},
                                informationOnly = true, surahNames = mapOf(16 to "النحل", 17 to "الإسراء"),
                                modifier = Modifier.onGloballyPositioned { headerBounds[index] = Rect(it.localToRoot(Offset.Zero), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())) })
                            Box(Modifier.fillMaxSize().padding(top = ChromeTokens.MushafHeaderReserve)) {
                                Box(Modifier.fillMaxSize().testTag("text-$index")
                                    .onGloballyPositioned { textBounds[index] = Rect(it.localToRoot(Offset.Zero), androidx.compose.ui.geometry.Size(it.size.width.toFloat(), it.size.height.toFloat())) })
                            }
                        }
                    }
                    if (controls) PageHeaderBar(null, 1, null, false, {}, {}, {}, {}, {})
                }
                }
            }
        }
        compose.waitForIdle()
        val original = textBounds.getValue(0)
        compose.runOnIdle { controls = false }
        compose.waitForIdle()
        assertEquals(original, textBounds.getValue(0))
        val oldHeader = headerBounds.getValue(0)
        compose.onNodeWithTag("test-pager").performTouchInput {
            down(center); moveBy(Offset(-width * .1f, 0f), 100); moveBy(Offset(-width * .35f, 0f), 300)
        }
        compose.waitForIdle()
        val headerDelta = headerBounds.getValue(0).left - oldHeader.left
        val textDelta = textBounds.getValue(0).left - original.left
        assertTrue("Header moves during the actual pager drag", headerDelta < -10f)
        assertEquals(textDelta, headerDelta, 1f)
        compose.onNodeWithTag("test-pager").performTouchInput { moveBy(Offset(-width * .5f, 0f), 100); up() }
        compose.waitForIdle()
        assertEquals(oldHeader.left, headerBounds.getValue(1).left, 1f)
        assertEquals(original.top, textBounds.getValue(1).top, 1f)
        compose.onNodeWithTag("test-pager").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(oldHeader.left, headerBounds.getValue(0).left, 1f)
        assertEquals(original, textBounds.getValue(0))
        compose.runOnIdle { controls = true }
        compose.waitForIdle()
        assertEquals(original, textBounds.getValue(0))
    }

    @Test fun rebuiltWhisperLoadsAndCallsNativeCode() {
        assertTrue(com.quransunah.app.recitation.engine.WhisperLib.loaded)
        assertTrue(com.quransunah.app.recitation.engine.WhisperLib.systemInfo().isNotBlank())
    }
}

