package com.quransunah.app.fonts

import java.io.File
import java.nio.ByteBuffer
import java.sql.DriverManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Every word of every page (83,668 rows incl. ayah markers) has v4 glyphs, and every glyph exists in the
 * page's own coloured font (no tofu). Night palette patch keeps a valid single-palette CPAL.
 */
class QpcV4FontCoverageTest {
    private val glyphs: Array<String?> by lazy {
        File("src/main/assets/json/qpc-v4-glyphs.tsv").inputStream().use { QpcV4Glyphs.parse(it) }
    }

    @Test
    fun everyWordOnEveryPageHasGlyphsInItsPageFont() {
        assumeTrue("QCF4 page fonts are gitignored in this checkout", File("../public/fonts/qcf4/p1.ttf").isFile)
        Class.forName("org.sqlite.JDBC")
        val pageOf = HashMap<Int, Int>(90_000)
        var wordCount = 0
        DriverManager.getConnection("jdbc:sqlite:${File("../public/db/quran_unified.db").absolutePath}").use { db ->
            db.createStatement().use { st ->
                st.executeQuery(
                    "SELECT page_number, first_word_index, last_word_index FROM mushaf_pages WHERE line_type = 'ayah'",
                ).use { r ->
                    while (r.next()) for (w in r.getInt(2)..r.getInt(3)) pageOf.putIfAbsent(w, r.getInt(1))
                }
                st.executeQuery("SELECT COUNT(*) FROM words").use { r -> r.next(); wordCount = r.getInt(1) }
            }
        }
        assertEquals(83_668, wordCount)
        assertEquals(wordCount, pageOf.size)
        val cmaps = HashMap<Int, Set<Int>>()
        var missing = 0
        val pages = HashSet<Int>()
        for ((wordId, page) in pageOf) {
            val g = glyphs.getOrNull(wordId)
            assertTrue("word $wordId has no v4 glyphs", !g.isNullOrEmpty())
            val cmap = cmaps.getOrPut(page) { cmap(File("../public/fonts/qcf4/p$page.ttf").readBytes()) }
            g!!.codePoints().forEach { if (it !in cmap) missing++ }
            pages += page
        }
        assertEquals(604, pages.size)
        assertEquals("glyphs missing from their page font", 0, missing)
    }

    @Test
    fun nightPaletteBecomesPaletteZero() {
        assumeTrue("QCF4 page fonts are gitignored in this checkout", File("../public/fonts/qcf4/p50.ttf").isFile)
        val font = File("../public/fonts/qcf4/p50.ttf").readBytes()
        assertTrue(ColrPalette.selectPalette(font, ColrPalette.DARK))
        val bb = ByteBuffer.wrap(font)
        val cpal = table(bb, "CPAL")
        assertEquals(1, bb.getShort(cpal + 4).toInt())          // one palette
        val first = cpal + bb.getInt(cpal + 8)
        assertEquals(0xFFFFFFFF.toInt(), bb.getInt(first))       // ink = white (BGRA) in the dark palette
    }

    private fun table(bb: ByteBuffer, tag: String): Int {
        val n = bb.getShort(4).toInt() and 0xFFFF
        for (i in 0 until n) {
            val rec = 12 + i * 16
            val t = String(ByteArray(4) { bb.get(rec + it) }, Charsets.US_ASCII)
            if (t == tag) return bb.getInt(rec + 8)
        }
        error("no $tag")
    }

    /** Minimal cmap reader (formats 4 and 12). */
    private fun cmap(font: ByteArray): Set<Int> {
        val bb = ByteBuffer.wrap(font)
        val base = table(bb, "cmap")
        val n = bb.getShort(base + 2).toInt() and 0xFFFF
        val out = HashSet<Int>()
        for (i in 0 until n) {
            val sub = base + bb.getInt(base + 4 + i * 8 + 4)
            when (bb.getShort(sub).toInt() and 0xFFFF) {
                4 -> {
                    val segX2 = bb.getShort(sub + 6).toInt() and 0xFFFF
                    val ends = sub + 14; val starts = ends + segX2 + 2; val deltas = starts + segX2; val ranges = deltas + segX2
                    for (s in 0 until segX2 / 2) {
                        val end = bb.getShort(ends + s * 2).toInt() and 0xFFFF
                        val start = bb.getShort(starts + s * 2).toInt() and 0xFFFF
                        val delta = bb.getShort(deltas + s * 2).toInt()
                        val ro = bb.getShort(ranges + s * 2).toInt() and 0xFFFF
                        for (c in start..end) {
                            if (c == 0xFFFF) continue
                            val gid = if (ro == 0) (c + delta) and 0xFFFF else {
                                val g = bb.getShort(ranges + s * 2 + ro + (c - start) * 2).toInt() and 0xFFFF
                                if (g == 0) 0 else (g + delta) and 0xFFFF
                            }
                            if (gid != 0) out += c
                        }
                    }
                }
                12 -> {
                    val groups = bb.getInt(sub + 12)
                    for (g in 0 until groups) {
                        val p = sub + 16 + g * 12
                        for (c in bb.getInt(p)..bb.getInt(p + 4)) out += c
                    }
                }
            }
        }
        return out
    }
}
