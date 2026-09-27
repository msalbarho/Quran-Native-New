package com.quransunah.app.ui.mushaf

import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.QuranWord
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HeaderGlyphFitTest {
    @Test
    fun fittedSpKeepsPreferredWhenTextFits() {
        assertEquals(25f, HeaderGlyphFit.fittedSp(25f, 120f, 200f))
    }

    @Test
    fun fittedSpScalesDownToFillTheSlotWithoutTruncation() {
        val fitted = HeaderGlyphFit.fittedSp(25f, 400f, 200f)
        assertEquals(12.5f, fitted, 0.01f)
    }

    @Test
    fun tawbahShortAdvanceUsesInkOrigin() {
        val x = HeaderGlyphFit.originXPx(
            advancePx = 410f,
            inkLeftPx = 414f,
            inkRightPx = 3937f,
            canvasWidthPx = 3525f,
            ltr = false,
        )
        assertEquals(-414f, x, 0.01f)
    }

    @Test
    fun normalRtlSurahKeepsAdvanceOrigin() {
        val x = HeaderGlyphFit.originXPx(
            advancePx = 4412f,
            inkLeftPx = 411f,
            inkRightPx = 4410f,
            canvasWidthPx = 4414f,
            ltr = false,
        )
        assertEquals(2f, x, 0.01f)
    }

    @Test
    fun combinedLtrKeepsZeroOrigin() {
        val x = HeaderGlyphFit.originXPx(
            advancePx = 4412f,
            inkLeftPx = 411f,
            inkRightPx = 4410f,
            canvasWidthPx = 4414f,
            ltr = true,
        )
        assertEquals(0f, x, 0.01f)
    }

    @Test
    fun tawbahFallbackKeepsTheFullName() {
        val label = HeaderGlyphFit.headerFallbackLabel(
            listOf(9),
            mapOf(9 to "سُورَةُ التوبة"),
        )
        assertEquals("سورة التوبة", label)
        assertTrue(label.contains("التوبة"))
        assertTrue(!label.contains("…"))
    }

    @Test
    fun page604FallbackKeepsEverySurahName() {
        val label = HeaderGlyphFit.headerFallbackLabel(
            listOf(112, 113, 114),
            mapOf(
                112 to "سُورَةُ الإخلاص",
                113 to "سُورَةُ الفلق",
                114 to "سُورَةُ الناس",
            ),
        )
        assertEquals("سورة الإخلاص - سورة الفلق - سورة الناس", label)
    }

    @Test
    fun everySurahHasANonBlankHeaderLigature() {
        val map = loadQbsmlMap()
        val missing = (1..114).filter { number ->
            HeaderGlyphFit.composeHeaderLigature(
                listOf(number),
                map.combined,
                map.single,
                map.decorative,
            ).isBlank()
        }
        assertTrue("Missing header ligatures: $missing", missing.isEmpty())
    }

    @Test
    fun combinedHeaderGroupsResolveToASingleGlyph() {
        val map = loadQbsmlMap()
        map.combined.keys.forEach { key ->
            val numbers = key.split("-").map { it.toInt() }
            val ligature = HeaderGlyphFit.composeHeaderLigature(
                numbers,
                map.combined,
                map.single,
                map.decorative,
            )
            assertEquals(map.combined.getValue(key), ligature)
            assertTrue("blank combined glyph for $key", ligature.isNotBlank())
        }
    }

    @Test
    fun allThirtyJuzHeaderGlyphsArePresent() {
        val map = loadQbsmlMap()
        val missing = (1..30).filter { map.juz[it].isNullOrBlank() }
        assertTrue("Missing juz ligatures: $missing", missing.isEmpty())
    }

    @Test
    fun headerSurahsOnAyahOnlyPageUsesTheActiveSurah() {
        val page = MushafPage(
            pageNumber = 188,
            juzNumber = 10,
            hizbNumber = 20,
            lines = listOf(
                MushafLine(
                    lineNumber = 1,
                    lineType = LineType.AYAH,
                    isCentered = false,
                    surahNumber = null,
                    words = listOf(
                        QuranWord(
                            id = 1,
                            surah = 9,
                            ayah = 2,
                            wordIndex = 1,
                            textLigature = "x",
                            textHafs = "x",
                        ),
                    ),
                ),
            ),
        )
        assertEquals(listOf(9), HeaderGlyphFit.surahsOnPage(page))
    }

    private fun loadQbsmlMap(): QbsmlTables {
        val file = File("src/main/assets/json/qcf4-qbsml-map.json")
        val raw = file.readText()
        val root = Json.parseToJsonElement(raw).jsonObject
        return QbsmlTables(
            decorative = intTable(root, "decorative_surahs"),
            single = intTable(root, "header_single_surahs"),
            combined = stringTable(root, "header_combined_surahs"),
            juz = intTable(root, "header_juz_map"),
        )
    }

    private fun stringTable(
        root: kotlinx.serialization.json.JsonObject,
        key: String,
    ): Map<String, String> {
        val table = root[key]?.jsonObject ?: return emptyMap()
        return table.entries.associate { (id, value) -> id to value.jsonPrimitive.content }
    }

    private fun intTable(
        root: kotlinx.serialization.json.JsonObject,
        key: String,
    ): Map<Int, String> {
        return stringTable(root, key).mapNotNull { (id, value) ->
            id.toIntOrNull()?.let { it to value }
        }.toMap()
    }

    private data class QbsmlTables(
        val decorative: Map<Int, String>,
        val single: Map<Int, String>,
        val combined: Map<String, String>,
        val juz: Map<Int, String>,
    )
}
