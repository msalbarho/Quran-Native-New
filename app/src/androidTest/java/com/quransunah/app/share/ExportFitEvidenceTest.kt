package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.fonts.QpcV4Glyphs
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportFitEvidenceTest {
    private fun publishJpeg(context: android.content.Context, file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v11-share")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun qcfLines(glyphs: QpcV4Glyphs, page: Int, rows: List<List<Triple<Int, Int, Int>>>): List<ShareMushafLine> =
        rows.mapIndexed { li, row ->
            ShareMushafLine(
                pageNumber = page,
                lineNumber = li + 1,
                words = row.map { (ayah, pos, wid) ->
                    if (wid == 0) ShareMushafWord(ayah, pos, "", "0", isMarker = true, sourcePage = page)
                    else ShareMushafWord(ayah, pos, glyphs.forWord(wid).orEmpty(), "w$wid", sourcePage = page)
                },
            )
        }

    /** Build mushaf lines for one ayah from contiguous word_ids (last may be marker). */
    private fun ayahLines(
        glyphs: QpcV4Glyphs,
        surah: Int,
        ayah: Int,
        page: Int,
        firstWid: Int,
        lastWid: Int,
        markerWid: Int,
    ): List<ShareMushafLine> {
        val words = ArrayList<ShareMushafWord>()
        var pos = 1
        for (wid in firstWid..lastWid) {
            if (wid == markerWid) {
                words += ShareMushafWord(ayah, pos, "", "0", isMarker = true, sourcePage = page)
            } else {
                words += ShareMushafWord(ayah, pos, glyphs.forWord(wid).orEmpty(), "w$wid", sourcePage = page)
            }
            pos++
        }
        // Chunk into fake mushaf rows of ~10 so source spans multiple lines (filter is by ayah).
        return words.chunked(10).mapIndexed { i, chunk ->
            ShareMushafLine(page, i + 1, chunk)
        }
    }

    private fun versesFrom(lines: List<ShareMushafLine>, surah: Int, name: String, from: Int, to: Int): AyahShareContent {
        val vs = (from..to).map { ayah ->
            val words = lines.flatMap { it.words }.filter { it.ayah == ayah && !it.isMarker }
            AyahShareVerse(ayah, lines.first().pageNumber, words.joinToString(" ") { it.qcf }, words.joinToString(" ") { it.uthmanic })
        }
        return AyahShareContent(surah, name, from, to, vs, mushafLines = lines.filter { l -> l.words.any { it.ayah in from..to } })
    }

    private fun bodyPositions(lines: List<ShareMushafLine>, ayah: Int): List<Int> =
        lines.flatMap { it.words }.filter { it.ayah == ayah && !it.isMarker }.map { it.position }

    private suspend fun assertPagesComplete(
        exporter: AyahShareExporter,
        content: AyahShareContent,
        options: VideoMakerOptions,
        ayah: Int,
        label: String,
    ) {
        val expected = bodyPositions(content.mushafLines, ayah)
        assertTrue("$label expected words", expected.isNotEmpty())
        val pages = exporter.wrappedAyahPages(content, options, 540, ayah)
        assertTrue("$label pages", pages.isNotEmpty())
        val got = pages.flatten().flatMap { it.words }.filter { !it.isMarker }.map { it.position }
        assertEquals("$label no truncated words across pages", expected, got)
        // Pages must be disjoint on body positions.
        val seen = HashSet<Int>()
        for ((i, page) in pages.withIndex()) {
            for (p in page.flatMap { it.words }.filter { !it.isMarker }.map { it.position }) {
                assertTrue("$label page $i duplicate pos $p", seen.add(p))
            }
        }
    }

    private fun assertMarkerGlued(pages: List<List<ShareMushafLine>>, label: String) {
        val lines = pages.flatten()
        for (line in lines) {
            for ((i, w) in line.words.withIndex()) {
                if (!w.isMarker) continue
                assertTrue(
                    "$label marker alone on line (pos=${w.position} lineWords=${line.words.size})",
                    i > 0 && !line.words[i - 1].isMarker,
                )
            }
        }
        val markers = lines.flatMap { it.words }.count { it.isMarker }
        assertTrue("$label expected marker(s)", markers >= 1)
    }

    private fun assertDragClamps() {
        val g = makerVerticalPlacement(0.95f, 0.02f, 0.25f, 0.06f, showTitle = true, translation = true)
        // Title stays above ayah block; ayah stays above watermark.
        assertTrue("title above ayah", g.titleCenterY + g.titleHeight / 2f <= g.ayahCenterY - g.ayahHeight / 2f + 0.001f)
        assertTrue("ayah above watermark", g.ayahCenterY + g.ayahHeight / 2f <= 0.90f + 0.001f)
        val g2 = makerVerticalPlacement(0.10f, 0.80f, 0.20f, 0.06f, showTitle = true, translation = false)
        assertTrue("title not past ayah top", g2.titleCenterY <= g2.ayahCenterY)
    }

        private fun bitmapSignature(bmp: Bitmap): Long {
        var h = 1125899906842597L
        val stepX = (bmp.width / 40).coerceAtLeast(1)
        val stepY = (bmp.height / 40).coerceAtLeast(1)
        for (y in 0 until bmp.height step stepY) {
            for (x in 0 until bmp.width step stepX) {
                h = h * 1315423911L + bmp.getPixel(x, y)
            }
        }
        return h
    }

    @Test
    fun oneAyahPagesFatihaAndKursi() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        val evidence = File(context.cacheDir, "v11-export-fit").apply { deleteRecursively(); mkdirs() }
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
            showTitle = true,
        )

        val fatihaLines = qcfLines(
            glyphs, 1,
            listOf(
                listOf(Triple(1, 1, 1), Triple(1, 2, 2), Triple(1, 3, 3), Triple(1, 4, 4), Triple(1, 5, 0)),
                listOf(Triple(2, 1, 6), Triple(2, 2, 7), Triple(2, 3, 8), Triple(2, 4, 9), Triple(2, 5, 0)),
                listOf(Triple(3, 1, 11), Triple(3, 2, 12), Triple(3, 3, 0)),
                listOf(Triple(4, 1, 14), Triple(4, 2, 15), Triple(4, 3, 16), Triple(4, 4, 0)),
                listOf(Triple(5, 1, 18), Triple(5, 2, 19), Triple(5, 3, 20), Triple(5, 4, 21), Triple(5, 5, 0)),
                listOf(Triple(6, 1, 23), Triple(6, 2, 24), Triple(6, 3, 25), Triple(6, 4, 0)),
                listOf(
                    Triple(7, 1, 27), Triple(7, 2, 28), Triple(7, 3, 29), Triple(7, 4, 30), Triple(7, 5, 31),
                    Triple(7, 6, 32), Triple(7, 7, 33), Triple(7, 8, 34), Triple(7, 9, 35), Triple(7, 10, 0),
                ),
            ),
        )
        val fatiha = versesFrom(fatihaLines, 1, "Al-Fatihah", 1, 7)
        for (ayah in 1..7) {
            assertPagesComplete(exporter, fatiha, options, ayah, "fatiha $ayah")
            assertMarkerGlued(exporter.wrappedAyahPages(fatiha, options, 540, ayah), "fatiha $ayah")
        }
        assertDragClamps()
        for (ayah in listOf(1, 3, 7)) {
            val bmp = exporter.makerPreview(fatiha, options, word = 1, progress = 0.3f, activeAyah = ayah)
            val f = File(evidence, "v11-export-fatiha-ayah$ayah.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            publishJpeg(context, f, f.name)
            bmp.recycle()
            System.gc()
        }

        // Real 2:255 word_ids 5436..5486 (marker 5486).
        val kursiLines = ayahLines(glyphs, 2, 255, 42, 5436, 5486, 5486)
        val kursi = versesFrom(kursiLines, 2, "Al-Baqarah", 255, 255)
        assertPagesComplete(exporter, kursi, options, 255, "kursi")
        val kursiPages = exporter.wrappedAyahPages(kursi, options, 540, 255)
        assertMarkerGlued(kursiPages, "kursi")
        assertTrue("kursi should paginate or hold all words", kursiPages.isNotEmpty())
        val kursiBody = bodyPositions(kursi.mushafLines, 255)
        val earlyWord = kursiBody.first()
        // Must pick a word on the LAST wrapped page — 4/5 through the ayah can
        // still fall on page 1 when page 1 holds most of the words.
        val lateWord = kursiPages.last().flatMap { it.words }.first { !it.isMarker }.position
        val k1 = exporter.makerPreview(kursi, options, word = earlyWord, progress = 0.2f, activeAyah = 255)
        val k1f = File(evidence, "v11-export-kursi-p1.jpg")
        k1f.outputStream().use { k1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, k1f, k1f.name)
        val sig1 = bitmapSignature(k1)
        k1.recycle()
        System.gc()
        val k2 = exporter.makerPreview(kursi, options, word = lateWord, progress = 0.8f, activeAyah = 255)
        val k2f = File(evidence, "v11-export-kursi-p2.jpg")
        k2f.outputStream().use { k2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, k2f, k2f.name)
        val sig2 = bitmapSignature(k2)
        k2.recycle()
        assertTrue("kursi must paginate (got ${kursiPages.size} pages)", kursiPages.size >= 2)
        assertNotEquals("kursi p1/p2 must differ (early=$earlyWord late=$lateWord pages=${kursiPages.size})", sig1, sig2)

        // Longest ayah 2:282 word_ids 6171..6299 (marker last).
        val debtLines = ayahLines(glyphs, 2, 282, 48, 6171, 6299, 6299)
        val debt = versesFrom(debtLines, 2, "Al-Baqarah", 282, 282)
        assertPagesComplete(exporter, debt, options, 282, "2:282")
        val debtPages = exporter.wrappedAyahPages(debt, options, 540, 282)
        assertMarkerGlued(debtPages, "2:282")
        assertTrue("2:282 must span multiple pages at readable size", debtPages.size >= 2)
        val debtBody = bodyPositions(debt.mushafLines, 282)
        val d1 = exporter.makerPreview(debt, options, word = debtBody.first(), progress = 0.2f, activeAyah = 282)
        val d1f = File(evidence, "v11-export-282-p1.jpg")
        d1f.outputStream().use { d1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, d1f, d1f.name)
        val dsig1 = bitmapSignature(d1)
        d1.recycle()
        System.gc()
        val d2 = exporter.makerPreview(debt, options, word = debtBody.last(), progress = 0.8f, activeAyah = 282)
        val d2f = File(evidence, "v11-export-282-p2.jpg")
        d2f.outputStream().use { d2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, d2f, d2f.name)
        val dsig2 = bitmapSignature(d2)
        d2.recycle()
        assertNotEquals("2:282 first/last page frames must differ", dsig1, dsig2)

        // Translation-on evidence (t178u): Fatiha 7 + Kursi pages must keep translation in-frame.
        val trOptions = options.copy(translation = true)
        val fatihaTr = fatiha.copy(
            verses = fatiha.verses.map {
                it.copy(
                    englishTranslation = when (it.ayah) {
                        1 -> "In the Name of Allah—the Most Compassionate, Most Merciful."
                        3 -> "the Most Compassionate, Most Merciful,"
                        7 -> "the Path of those You have blessed—not those You are displeased with, or those who are astray."
                        else -> "Ayah ${it.ayah}"
                    },
                )
            },
        )
        for (ayah in listOf(1, 3, 7)) {
            val bmp = exporter.makerPreview(fatihaTr, trOptions, word = 1, progress = 0.3f, activeAyah = ayah)
            val f = File(evidence, "v11-export-fatiha-ayah${ayah}-tr.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            publishJpeg(context, f, f.name)
            bmp.recycle()
            System.gc()
        }
        val kursiTr = kursi.copy(
            verses = kursi.verses.map {
                it.copy(
                    englishTranslation = "Allah! There is no god except Him, the Ever-Living, All-Sustaining… His Seat encompasses the heavens and the earth, and the preservation of both does not tire Him. For He is the Most High, the Greatest.",
                )
            },
        )
        val kTr1 = exporter.makerPreview(kursiTr, trOptions, word = earlyWord, progress = 0.2f, activeAyah = 255)
        File(evidence, "v11-export-kursi-p1-tr.jpg").outputStream().use { kTr1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, File(evidence, "v11-export-kursi-p1-tr.jpg"), "v11-export-kursi-p1-tr.jpg")
        kTr1.recycle()
        val kTr2 = exporter.makerPreview(kursiTr, trOptions, word = lateWord, progress = 0.8f, activeAyah = 255)
        File(evidence, "v11-export-kursi-p2-tr.jpg").outputStream().use { kTr2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, File(evidence, "v11-export-kursi-p2-tr.jpg"), "v11-export-kursi-p2-tr.jpg")
        kTr2.recycle()
    }
}
