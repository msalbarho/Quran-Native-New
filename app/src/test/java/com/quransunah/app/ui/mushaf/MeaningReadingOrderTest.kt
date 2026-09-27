package com.quransunah.app.ui.mushaf

import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.local.mushaf.AyahMarkerRule
import com.quransunah.app.data.local.mushaf.mushafLineWordRange
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine
import com.quransunah.app.domain.model.QuranWord
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningReadingOrderTest {
    @Test
    fun page71And604UseTheSameQcfFaceAsTheMushafMap() {
        val map = File("src/main/assets/json/qcf4-font-map.json").readText()
        assertTrue(map.contains("\"71\": \"QCF4_Hafs_06\""))
        assertTrue(map.contains("\"604\": \"QCF4_Hafs_47\""))
        val marker71 = markerLigature(3, 165)
        assertEquals(1, marker71.length)
        assertTrue(marker71[0].code in 0xE000..0xF8FF)
        val marker604 = markerLigature(114, 6)
        assertEquals(1, marker604.length)
        assertTrue(marker604[0].code in 0xE000..0xF8FF)
        assertFalse(fontHasColorTables(File("../public/fonts/qcf4/QCF4_Hafs_06.ttf")))
        assertFalse(fontHasColorTables(File("../public/fonts/qcf4/QCF4_Hafs_47.ttf")))
    }

    @Test
    fun page71IsAyahsOnlyAndKeepsThe165Marker() {
        val slots = slotsForPage(71)
        assertTrue(slots.all { it is MeaningSlot.Ayah })
        assertTrue(slots.contains(MeaningSlot.Ayah(3, 165)))
        val marker = markerText(3, 165)
        assertEquals(EasternArabic.quranDigits(165), marker)
    }

    @Test
    fun page604KeepsEachShortSurahBoundaryAndBismillah() {
        val slots = slotsForPage(604)
        val kinds = slots.map { slot ->
            when (slot) {
                is MeaningSlot.Surah -> "s${slot.number}"
                MeaningSlot.Bismillah -> "b"
                is MeaningSlot.Ayah -> "a${slot.surah}:${slot.ayah}"
            }
        }
        assertEquals(
            listOf(
                "s112", "b", "a112:1", "a112:2", "a112:3", "a112:4",
                "s113", "b", "a113:1", "a113:2", "a113:3", "a113:4", "a113:5",
                "s114", "b", "a114:1", "a114:2", "a114:3", "a114:4", "a114:5", "a114:6",
            ),
            kinds,
        )
    }

    @Test
    fun surahStartRulesFollowThePageLines() {
        val opening = slotsForPage(1)
        assertEquals(MeaningSlot.Surah(1), opening.first())
        assertFalse(opening.any { it is MeaningSlot.Bismillah })
        val tawbah = slotsForPage(187)
        assertEquals(MeaningSlot.Surah(9), tawbah.first())
        assertFalse(tawbah.any { it is MeaningSlot.Bismillah })
        val baqarah = slotsForPage(2)
        assertEquals(MeaningSlot.Surah(2), baqarah[0])
        assertEquals(MeaningSlot.Bismillah, baqarah[1])
        assertEquals(MeaningSlot.Ayah(2, 1), baqarah[2])
    }

    @Test
    fun aPageThatContinuesOneSurahThenStartsAnotherKeepsThatOrder() {
        Class.forName("org.sqlite.JDBC")
        val page = DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}").use { db ->
            db.createStatement().executeQuery(
                """
                SELECT p.page_number FROM mushaf_pages p
                WHERE p.line_type = 'ayah' AND p.line_number = 1
                AND EXISTS (
                    SELECT 1 FROM mushaf_pages s
                    WHERE s.page_number = p.page_number AND s.line_type = 'surah_name'
                )
                ORDER BY p.page_number
                LIMIT 1
                """.trimIndent(),
            ).use { rows ->
                assertTrue(rows.next())
                rows.getInt(1)
            }
        }
        val slots = slotsForPage(page)
        val firstSurah = slots.indexOfFirst { it is MeaningSlot.Surah }
        assertTrue(firstSurah > 0)
        assertTrue(slots.take(firstSurah).all { it is MeaningSlot.Ayah })
        val header = slots[firstSurah] as MeaningSlot.Surah
        assertEquals(MeaningSlot.Bismillah, slots[firstSurah + 1])
        val nextAyah = slots[firstSurah + 2] as MeaningSlot.Ayah
        assertEquals(header.number, nextAyah.surah)
        assertEquals(1, nextAyah.ayah)
    }

    private fun slotsForPage(page: Int): List<MeaningSlot> {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}").use { db ->
            meaningReadingOrder(loadLines(db, page))
        }
    }

    private fun markerLigature(surah: Int, ayah: Int): String {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}").use { db ->
            db.prepareStatement(
                """
                SELECT qpc_ligature FROM words
                WHERE surah_id = ? AND ayah_number = ? AND is_ayah_marker = 1
                ORDER BY word_id DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, surah)
                statement.setInt(2, ayah)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    rows.getString(1)
                }
            }
        }
    }

    private fun fontHasColorTables(file: File): Boolean {
        assertTrue(file.isFile)
        val data = file.readBytes()
        val count = ((data[4].toInt() and 0xFF) shl 8) or (data[5].toInt() and 0xFF)
        val tags = mutableSetOf<String>()
        var offset = 12
        repeat(count) {
            tags += data.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            offset += 16
        }
        return tags.any { it in setOf("COLR", "CPAL", "CBDT", "CBLC", "SVG ") }
    }

    private fun markerText(surah: Int, ayah: Int): String {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}").use { db ->
            db.prepareStatement(
                """
                SELECT text_hafs FROM words
                WHERE surah_id = ? AND ayah_number = ? AND is_ayah_marker = 1
                ORDER BY word_id DESC
                LIMIT 1
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, surah)
                statement.setInt(2, ayah)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    rows.getString(1).trim()
                }
            }
        }
    }

    private fun loadLines(db: Connection, page: Int): List<MushafLine> {
        val lines = mutableListOf<MushafLine>()
        db.prepareStatement(
            """
            SELECT line_number, line_type, surah_id, first_word_index, last_word_index
            FROM mushaf_pages
            WHERE page_number = ?
            ORDER BY line_number
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, page)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val typeRaw = rows.getString(2).orEmpty()
                    val first = rows.getObject(4) as? Number
                    val last = rows.getObject(5) as? Number
                    val range = mushafLineWordRange(typeRaw, first?.toInt(), last?.toInt())
                    val words = if (range == null) emptyList() else loadWords(db, range)
                    val surah = rows.getObject(3) as? Number
                    lines += MushafLine(
                        lineNumber = rows.getInt(1),
                        lineType = LineType.fromRaw(typeRaw),
                        isCentered = false,
                        surahNumber = surah?.toInt(),
                        words = words,
                    )
                }
            }
        }
        return lines
    }

    private fun loadWords(db: Connection, range: IntRange): List<QuranWord> {
        val words = mutableListOf<QuranWord>()
        db.prepareStatement(
            """
            SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
            FROM words
            WHERE word_id BETWEEN ? AND ?
            ORDER BY word_id
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, range.first)
            statement.setInt(2, range.last)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val hafs = rows.getString(5).orEmpty()
                    val ligature = rows.getString(6).orEmpty()
                    val flag = rows.getInt(7)
                    words += QuranWord(
                        id = rows.getInt(1),
                        surah = rows.getInt(2),
                        ayah = rows.getInt(3),
                        wordIndex = rows.getInt(4),
                        textLigature = ligature,
                        textHafs = hafs,
                        isAyahMarker = AyahMarkerRule.isMarker(flag, hafs, ligature),
                    )
                }
            }
        }
        return words
    }

    private fun assetDb(): File = File("src/main/assets/databases/quran_unified.db").also {
        assertTrue("missing packaged database at ${it.absolutePath}", it.isFile)
    }
}
