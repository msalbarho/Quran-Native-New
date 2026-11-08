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

    @Test
    fun page604HeaderSelectsOnlyIkhlas() {
        val page = MushafPage(604, listOf(112, 113, 114).mapIndexed { index, number ->
            MushafLine(index * 4 + 1, LineType.SURAH_NAME, true, number, emptyList())
        }, 30, 60)
        assertEquals(112, HeaderGlyphFit.firstSurahOnPage(page))
    }

    @Test
    fun firstSurahIncludesContinuationBeforeANewSurahBanner() {
        val page = MushafPage(1, listOf(
            MushafLine(5, LineType.SURAH_NAME, true, 3, emptyList()),
            MushafLine(1, LineType.AYAH, false, null,
                listOf(QuranWord(1, 2, 286, 1, "", ""))),
        ), 3, 6)
        assertEquals(2, HeaderGlyphFit.firstSurahOnPage(page))
    }

    @Test
    fun everyAvailableSmallSurahHasAnIndependentInkRegion() {
        val map = loadQbsmlMap()
        for (number in 2..114) {
            val slice = QbsmlHeaderSlices.forSurah(number, map.single[number].orEmpty()) {
                map.combined.getValue(it.joinToString("-"))
            }
            assertTrue("small header missing for surah $number", slice != null)
            slice!!
            assertTrue(slice.glyph.codePointAt(0) in 0xF174..0xF1D5)
            assertTrue(slice.left >= 0f && slice.left < slice.right && slice.right <= 1f)
            assertTrue(slice.top >= 0f && slice.top < slice.bottom && slice.bottom <= 1f)
        }
        for ((key, glyph) in map.combined) {
            val slices = key.split("-").map { number ->
                QbsmlHeaderSlices.forSurah(number.toInt(), "") { glyph }!!
            }
            slices.zipWithNext().forEach { (right, left) ->
                assertTrue("neighbor names overlap for $key", right.left > left.right)
            }
        }
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
