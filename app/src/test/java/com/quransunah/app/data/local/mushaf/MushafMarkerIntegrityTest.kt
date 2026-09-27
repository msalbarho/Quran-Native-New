package com.quransunah.app.data.local.mushaf

import com.quransunah.app.BuildConfig
import com.quransunah.app.core.AppConstants
import com.quransunah.app.ui.mushaf.RubElHizb
import com.quransunah.app.ui.mushaf.SajdahAyah
import com.quransunah.app.ui.mushaf.WordRecord
import java.io.File
import java.security.MessageDigest
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Walks the packaged Quran database with the same line-range and marker rules
 * the Mushaf repository uses, then the same paint-run rule the canvas uses.
 */
class MushafMarkerIntegrityTest {
    @Test
    fun quranIdentityIncludesContentHashSoAStaleInstallIsReplaced() {
        val identity = AppConstants.quranDatabaseIdentity()
        val parts = identity.split(':')
        assertEquals(BuildConfig.QURAN_DB_CONTENT_HASH, parts[1])
        assertEquals(sourceDb().length(), parts[2].toLong())
        val currentStamp = "$identity:${AppConstants.QURAN_DB_ASSET}"
        val staleStamp = "${AppConstants.QURAN_DB_VERSION}:${AppConstants.QURAN_DB_ASSET}"
        assertNotEquals(staleStamp, currentStamp)
        assertEquals(sha256(sourceDb()), BuildConfig.QURAN_DB_CONTENT_HASH)
        assertEquals(sha256(sourceDb()), sha256(assetDb()))
    }

    @Test
    fun everyAyahMarkerSurvivesPageLoadAndCanvasClassification() {
        Class.forName("org.sqlite.JDBC")
        val connection = DriverManager.getConnection("jdbc:sqlite:${assetDb().absolutePath}")
        connection.use { db ->
            assertEquals(QURAN_TEXT_SHA256, textDigest(db))
            val words = loadWords(db)
            val lines = loadLines(db)
            val pages = lines.map { it.page }.toSet()
            assertEquals((1..604).toSet(), pages)

            val wordsById = words.associateBy { it.id }
            val rendered = linkedMapOf<Int, RenderedMarker>()
            val overlaps = mutableListOf<String>()
            val covered = BooleanArray(words.maxOf { it.id } + 1)
            val ayahPages = HashMap<Long, MutableSet<Int>>()

            val byPage = lines.groupBy { it.page }
            for (page in 1..604) {
                val rows = byPage.getValue(page).sortedBy { it.line }
                val ids = rows.mapNotNull { row ->
                    val range = mushafLineWordRange(row.type, row.first, row.last) ?: return@mapNotNull null
                    range
                }
                val firstId = ids.minOfOrNull { it.first }
                val lastId = ids.maxOfOrNull { it.last }
                val pageWords = if (firstId != null && lastId != null && firstId <= lastId) {
                    words.filter { it.id in firstId..lastId }
                } else {
                    emptyList()
                }
                for (row in rows) {
                    val included = mushafLineWordRange(row.type, row.first, row.last) ?: continue
                    val onLine = pageWords.filter { it.id in included }.sortedBy { it.id }
                    for (word in onLine) {
                        if (word.id in covered.indices && covered[word.id] && row.type == "ayah") {
                            overlaps += "word ${word.id} already covered before page $page line ${row.line}"
                        }
                        if (word.id in covered.indices) covered[word.id] = true
                        if (row.type == "ayah") {
                            ayahPages.getOrPut(ayahKey(word.surah, word.ayah)) { linkedSetOf() }.add(page)
                        }
                        if (AyahMarkerRule.isMarker(word.flag, word.hafs, word.ligature)) {
                            val record = WordRecord(
                                id = word.id,
                                surah = word.surah,
                                ayah = word.ayah,
                                position = word.position,
                                uthmanic = word.hafs,
                                qcfLigature = word.ligature,
                                isAyahMarker = true,
                            )
                            val display = word.ligature.ifBlank { word.hafs }
                            val body = SajdahAyah.paintRun(record, display, previous = null, next = null).body
                            assertTrue(
                                "marker ${word.id} has an empty canvas body",
                                body.isNotEmpty() && RubElHizb.parts(word.hafs, display)?.bodyLigature != "",
                            )
                            val previous = rendered[word.id]
                            if (previous != null && previous.page != page) {
                                overlaps += "marker ${word.id} on page ${previous.page} and $page"
                            }
                            rendered[word.id] = RenderedMarker(word, page, row.line, body)
                        }
                    }
                }
            }

            val byAyah = words.groupBy { ayahKey(it.surah, it.ayah) }
            assertEquals(6236, byAyah.size)
            val missing = mutableListOf<String>()
            val duplicates = mutableListOf<String>()
            val flagOff = mutableListOf<String>()
            for ((_, group) in byAyah) {
                val markers = group.filter { AyahMarkerRule.isMarker(it.flag, it.hafs, it.ligature) }
                if (markers.size != 1) {
                    if (markers.size > 1) duplicates += "${group.first().surah}:${group.first().ayah} has ${markers.size}"
                    else missing += "${group.first().surah}:${group.first().ayah} has no marker"
                    continue
                }
                val marker = markers.single()
                val drawn = rendered[marker.id]
                if (drawn == null) missing += "${marker.surah}:${marker.ayah} word ${marker.id} dropped before render"
                if (marker.flag != 1) flagOff += "${marker.surah}:${marker.ayah} word ${marker.id}"
            }
            val omitted = words.map { it.id }.filter { id -> id !in covered.indices || !covered[id] }
            val split = ayahPages.filterValues { it.size > 1 }

            assertTrue("missing markers: $missing", missing.isEmpty())
            assertTrue("duplicate markers: $duplicates", duplicates.isEmpty())
            assertTrue("marker flag still 0: $flagOff", flagOff.isEmpty())
            assertTrue("omitted word ids: $omitted", omitted.isEmpty())
            assertTrue("ayahs split across pages: ${split.keys}", split.isEmpty())
            assertTrue("unexpected overlaps: $overlaps", overlaps.isEmpty())
            assertEquals(6236, rendered.size)

            val expected = listOf(
                9446 to (3 to 165),
                21269 to (7 to 63),
                21362 to (7 to 69),
                54497 to (29 to 10),
                61171 to (36 to 81),
            )
            for ((wordId, ayah) in expected) {
                val drawn = rendered[wordId]
                assertTrue("word $wordId was not delivered to the renderer", drawn != null)
                assertEquals(ayah.first, drawn!!.word.surah)
                assertEquals(ayah.second, drawn.word.ayah)
                assertTrue(drawn.body.isNotEmpty())
            }
        }
    }

    private data class WordRow(
        val id: Int,
        val surah: Int,
        val ayah: Int,
        val position: Int,
        val hafs: String,
        val ligature: String,
        val flag: Int,
    )

    private data class LineRow(
        val page: Int,
        val line: Int,
        val type: String,
        val first: Int?,
        val last: Int?,
    )

    private data class RenderedMarker(
        val word: WordRow,
        val page: Int,
        val line: Int,
        val body: String,
    )

    private fun loadWords(db: java.sql.Connection): List<WordRow> {
        val sql = """
            SELECT word_id, surah_id, ayah_number, word_position, text_hafs, qpc_ligature, is_ayah_marker
            FROM words
            ORDER BY word_id
        """.trimIndent()
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                val words = mutableListOf<WordRow>()
                while (rows.next()) {
                    words += WordRow(
                        id = rows.getInt(1),
                        surah = rows.getInt(2),
                        ayah = rows.getInt(3),
                        position = rows.getInt(4),
                        hafs = rows.getString(5).orEmpty(),
                        ligature = rows.getString(6).orEmpty(),
                        flag = rows.getInt(7),
                    )
                }
                return words
            }
        }
    }

    private fun loadLines(db: java.sql.Connection): List<LineRow> {
        val sql = """
            SELECT page_number, line_number, line_type, first_word_index, last_word_index
            FROM mushaf_pages
            ORDER BY page_number, line_number
        """.trimIndent()
        db.createStatement().use { statement ->
            statement.executeQuery(sql).use { rows ->
                val lines = mutableListOf<LineRow>()
                while (rows.next()) {
                    val first = rows.getObject(4) as? Number
                    val last = rows.getObject(5) as? Number
                    lines += LineRow(
                        page = rows.getInt(1),
                        line = rows.getInt(2),
                        type = rows.getString(3).orEmpty(),
                        first = first?.toInt(),
                        last = last?.toInt(),
                    )
                }
                return lines
            }
        }
    }

    private fun textDigest(db: java.sql.Connection): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun absorb(sql: String) {
            db.createStatement().use { statement ->
                statement.executeQuery(sql).use { rows ->
                    while (rows.next()) {
                        digest.update(rows.getString(1).orEmpty().toByteArray(Charsets.UTF_8))
                        digest.update(0)
                    }
                }
            }
        }
        absorb("SELECT text_hafs FROM words ORDER BY word_id")
        absorb("SELECT qpc_ligature FROM words ORDER BY word_id")
        absorb("SELECT text FROM quran_text")
        digest.update("text".toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun ayahKey(surah: Int, ayah: Int): Long = (surah.toLong() shl 32) or ayah.toLong()

    private fun assetDb(): File = File("src/main/assets/databases/quran_unified.db").also {
        assertTrue("missing packaged database at ${it.absolutePath}", it.isFile)
    }

    private fun sourceDb(): File = File("../public/db/quran_unified.db").also {
        assertTrue("missing source database at ${it.absolutePath}", it.isFile)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val QURAN_TEXT_SHA256 = "9162a94fd4d006478422f6b3cd78245e9ef96c9b56066955e6a3bb3c7244c47d"
    }
}
