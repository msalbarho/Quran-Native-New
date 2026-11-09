package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
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
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ExportFitEvidenceTest {
    private fun publishJpeg(context: android.content.Context, file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v12-share")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert")
        resolver.openOutputStream(uri)!!.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    private fun ayahLines(
        glyphs: QpcV4Glyphs,
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
        return words.chunked(10).mapIndexed { i, chunk -> ShareMushafLine(page, i + 1, chunk) }
    }

    private fun versesFrom(lines: List<ShareMushafLine>, surah: Int, name: String, from: Int, to: Int): AyahShareContent {
        val vs = (from..to).map { ayah ->
            val words = lines.flatMap { it.words }.filter { it.ayah == ayah && !it.isMarker }
            AyahShareVerse(
                ayah, lines.first().pageNumber,
                words.joinToString(" ") { it.qcf },
                words.joinToString(" ") { it.uthmanic },
            )
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
        val got = pages.flatten().flatMap { it.words }.filter { !it.isMarker }.map { it.position }
        assertEquals("$label no truncated words", expected, got)
    }

    private fun assertMarkerGlued(pages: List<List<ShareMushafLine>>, label: String) {
        for (line in pages.flatten()) {
            for ((i, w) in line.words.withIndex()) {
                if (!w.isMarker) continue
                assertTrue("$label marker alone", i > 0 && !line.words[i - 1].isMarker)
            }
        }
    }

    /**
     * Measure QCF page-4 widths for 2:17 words 1–7 and solve for the fraction of
     * frame width where those seven fill VIDEO_WRAP_WIDTH and the 8th does not.
     */
    private suspend fun calibrateFractionFrom217(fonts: QcfFontManager, glyphs: QpcV4Glyphs): Float {
        fonts.ensureMaps()
        val face = fonts.loadPageTypeface(4, night = true)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        paint.typeface = if (face != Typeface.DEFAULT) face else Typeface.DEFAULT
        val probe = 100f
        paint.textSize = probe
        val gap = ShareMushafLayout.wordGap(probe, pageFont = true)
        fun widthOf(wid: Int): Float {
            val raw = glyphs.forWord(wid).orEmpty()
            // Match exporter visualPua: reverse only BMP PUA.
            val shown = if (raw.any { it.code in 0xE000..0xF8FF }) raw.reversed() else raw
            return paint.measureText(shown)
        }
        val w7 = (216..222).sumOf { widthOf(it).toDouble() }.toFloat() + 6f * gap
        val w8 = w7 + gap + widthOf(223)
        // size S scales linearly with probe; at S: total7 = w7 * (S/probe)
        // want total7 == WRAP * frameW  =>  S/frameW = WRAP * probe / w7
        val frac = ShareMushafLayout.VIDEO_WRAP_WIDTH * probe / w7
        // Sanity: 8 words must exceed wrap at this size.
        val at = { f: Float -> w8 * (f * 540f / probe) }
        assertTrue("8th word must overflow wrap at calibrated size (frac=$frac)", at(frac) > 540f * ShareMushafLayout.VIDEO_WRAP_WIDTH)
        return frac
    }

    @Test
    fun fixedSizeBaqarah17AndEvidence() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        val evidence = File(context.cacheDir, "v12-export-fit").apply { deleteRecursively(); mkdirs() }
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
            showTitle = true,
            marker = true,
        )

        val calibrated = calibrateFractionFrom217(fonts, glyphs)
        val report = File(evidence, "v12-size-calibrate.txt")
        report.writeText(
            "calibratedFraction=$calibrated constant=${ShareMushafLayout.VIDEO_AYAH_SIZE_FRACTION} " +
                "delta=${abs(calibrated - ShareMushafLayout.VIDEO_AYAH_SIZE_FRACTION)}\n",
        )
        run {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "v12-size-calibrate.txt")
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v12-share")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("insert")
            resolver.openOutputStream(uri)!!.use { it.write(report.readBytes()) }
            values.clear(); values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        // Constant must match calibration within 1.5% (frozen value).
        assertTrue(
            "VIDEO_AYAH_SIZE_FRACTION=${ShareMushafLayout.VIDEO_AYAH_SIZE_FRACTION} vs calibrated=$calibrated",
            abs(ShareMushafLayout.VIDEO_AYAH_SIZE_FRACTION - calibrated) / calibrated < 0.015f,
        )

        // 2:17 word_ids 216..233 (marker 233), page 4.
        val baq17Lines = ayahLines(glyphs, 17, 4, 216, 233, 233)
        val baq17 = versesFrom(baq17Lines, 2, "Al-Baqarah", 17, 17)
        assertPagesComplete(exporter, baq17, options, 17, "2:17")
        val pages17 = exporter.wrappedAyahPages(baq17, options, 540, 17)
        assertMarkerGlued(pages17, "2:17")
        val line1 = pages17.first().first().words.filter { !it.isMarker }
        assertEquals("2:17 line1 word count", 7, line1.size)
        assertEquals("2:17 line1 positions", (1..7).toList(), line1.map { it.position })
        assertTrue("2:17 has continuation", pages17.first().size >= 2 || pages17.size >= 1 && pages17.first().size >= 2)
        val allLines = pages17.flatten()
        assertTrue("2:17 multi-line", allLines.size >= 2)
        assertEquals("2:17 line1 last pos", 7, allLines.first().words.last { !it.isMarker }.position)
        assertEquals("2:17 line2 starts with ما (pos 8)", 8, allLines[1].words.first { !it.isMarker }.position)

        val bmp17 = exporter.makerPreview(baq17, options, word = 1, progress = 0.35f, activeAyah = 17)
        val f17 = File(evidence, "v12-export-baqarah17.jpg")
        f17.outputStream().use { bmp17.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, f17, f17.name)
        bmp17.recycle()

        val baq17Tr = baq17.copy(
            verses = baq17.verses.map {
                it.copy(
                    englishTranslation = "Their example is that of someone who kindles a fire, but when it lights up all around them, Allah takes away their light, leaving them in complete darkness—unable to see.",
                )
            },
        )
        val bmp17tr = exporter.makerPreview(baq17Tr, options.copy(translation = true), word = 1, progress = 0.35f, activeAyah = 17)
        val f17tr = File(evidence, "v12-export-baqarah17-tr.jpg")
        f17tr.outputStream().use { bmp17tr.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, f17tr, f17tr.name)
        bmp17tr.recycle()

        // Fatiha 7 + Kursi p1/p2 at the same fixed size.
        fun qcfLines(page: Int, rows: List<List<Triple<Int, Int, Int>>>) = rows.mapIndexed { li, row ->
            ShareMushafLine(page, li + 1, row.map { (ayah, pos, wid) ->
                if (wid == 0) ShareMushafWord(ayah, pos, "", "0", isMarker = true, sourcePage = page)
                else ShareMushafWord(ayah, pos, glyphs.forWord(wid).orEmpty(), "w$wid", sourcePage = page)
            })
        }
        val fatihaLines = qcfLines(
            1,
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
        val f7 = exporter.makerPreview(fatiha, options, word = 1, progress = 0.3f, activeAyah = 7)
        File(evidence, "v12-export-fatiha-ayah7.jpg").outputStream().use { f7.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, File(evidence, "v12-export-fatiha-ayah7.jpg"), "v12-export-fatiha-ayah7.jpg")
        f7.recycle()

        val kursiLines = ayahLines(glyphs, 255, 42, 5436, 5486, 5486)
        val kursi = versesFrom(kursiLines, 2, "Al-Baqarah", 255, 255)
        assertPagesComplete(exporter, kursi, options, 255, "kursi")
        val kursiPages = exporter.wrappedAyahPages(kursi, options, 540, 255)
        assertMarkerGlued(kursiPages, "kursi")
        assertTrue("kursi paginates", kursiPages.size >= 2)
        val early = bodyPositions(kursi.mushafLines, 255).first()
        val late = kursiPages.last().flatMap { it.words }.first { !it.isMarker }.position
        val k1 = exporter.makerPreview(kursi, options, word = early, progress = 0.2f, activeAyah = 255)
        File(evidence, "v12-export-kursi-p1.jpg").outputStream().use { k1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, File(evidence, "v12-export-kursi-p1.jpg"), "v12-export-kursi-p1.jpg")
        k1.recycle()
        val k2 = exporter.makerPreview(kursi, options, word = late, progress = 0.8f, activeAyah = 255)
        File(evidence, "v12-export-kursi-p2.jpg").outputStream().use { k2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        publishJpeg(context, File(evidence, "v12-export-kursi-p2.jpg"), "v12-export-kursi-p2.jpg")
        k2.recycle()
        assertNotEquals("kursi pages differ", early, late)
    }
}
