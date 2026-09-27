package com.quransunah.app.ui.mushaf

import com.quransunah.app.data.local.mushaf.AyahMarkerRule
import com.quransunah.app.domain.model.QuranWord
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningGlyphOrderTest {
    @Test
    fun quarterMarkIsItsOwnGlyphBeforeTheFirstWord() {
        val words = loadAyah("text_hafs LIKE ? AND is_ayah_marker = 0", "${RubElHizb.MARK}%")
        val painted = paint(words)
        val first = words.first { !it.isAyahMarker }
        val parts = RubElHizb.parts(first.uthmanic, display(first))
        assertNotNull(parts)
        val at = painted.glyphs.indexOf(parts!!.markerLigature)
        assertTrue(at >= 0)
        assertEquals(parts.bodyLigature, painted.glyphs[at + 1])
        assertFalse(painted.glyphs.contains(display(first)))
        val marker = words.last { it.isAyahMarker }
        assertEquals(display(marker), painted.marker)
    }

    @Test
    fun sajdahSignStaysBeforeTheAyahNumber() {
        val words = loadAyah("instr(text_hafs, ?) > 0", SajdahAyah.MARK.toString())
        val painted = paint(words)
        val marker = words.last { it.isAyahMarker }
        val previous = words[words.indexOf(marker) - 1]
        val number = SajdahAyah.numberLigature(marker, previous)
        assertNotNull(number)
        assertEquals(display(marker), painted.glyphs.last())
        assertEquals(number, painted.marker)
        assertNotEquals(display(marker), painted.marker)
        assertTrue(previous.uthmanic.contains(SajdahAyah.MARK))
    }

    @Test
    fun aNormalAyahKeepsWordOrderAndItsStoredMarker() {
        val words = loadAyah(
            """
            surah_id = 2 AND ayah_number = 255
            AND NOT EXISTS (
                SELECT 1 FROM words mark
                WHERE mark.surah_id = words.surah_id AND mark.ayah_number = words.ayah_number
                AND (instr(mark.text_hafs, '${RubElHizb.MARK}') > 0 OR instr(mark.text_hafs, '${SajdahAyah.MARK}') > 0)
            )
            """.trimIndent(),
            null,
        )
        val painted = paint(words)
        val body = words.filter { !it.isAyahMarker }.map { display(it) }
        val marker = words.last { it.isAyahMarker }
        assertEquals(body, painted.glyphs)
        assertEquals(display(marker), painted.marker)
        assertFalse(painted.glyphs.contains(painted.marker))
    }

    private fun paint(words: List<WordRecord>): MeaningGlyphs =
        meaningDisplayGlyphs(words, ::display)

    private fun display(word: WordRecord): String =
        word.qcfLigature.ifBlank { word.uthmanic }

    private fun loadAyah(where: String, arg: String?): List<WordRecord> {
        Class.forName("org.sqlite.JDBC")
        return DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}").use { db ->
            val sql = """
                SELECT surah_id, ayah_number FROM words
                WHERE $where
                ORDER BY word_id
                LIMIT 1
            """.trimIndent()
            val hit = db.prepareStatement(sql).use { statement ->
                if (arg != null) statement.setString(1, arg)
                statement.executeQuery().use { rows ->
                    assertTrue(rows.next())
                    rows.getInt(1) to rows.getInt(2)
                }
            }
            loadAyahWords(db, hit.first, hit.second)
        }
    }

    private fun loadAyahWords(db: Connection, surah: Int, ayah: Int): List<WordRecord> {
        val words = mutableListOf<WordRecord>()
        db.prepareStatement(
            """
            SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
            FROM words
            WHERE surah_id = ? AND ayah_number = ?
            ORDER BY word_position, word_id
            """.trimIndent(),
        ).use { statement ->
            statement.setInt(1, surah)
            statement.setInt(2, ayah)
            statement.executeQuery().use { rows ->
                while (rows.next()) {
                    val hafs = rows.getString(5).orEmpty()
                    val ligature = rows.getString(6).orEmpty()
                    words += QuranWord(
                        id = rows.getInt(1),
                        surah = rows.getInt(2),
                        ayah = rows.getInt(3),
                        wordIndex = rows.getInt(4),
                        textLigature = ligature,
                        textHafs = hafs,
                        isAyahMarker = AyahMarkerRule.isMarker(rows.getInt(7), hafs, ligature),
                    ).toWordRecord()
                }
            }
        }
        assertTrue(words.any { it.isAyahMarker })
        return words
    }

    private fun assetDb(): File = File("src/main/assets/databases/quran_unified.db").also {
        assertTrue(it.isFile)
    }
}
