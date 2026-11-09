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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExportFitEvidenceTest {
    private fun publishJpeg(context: android.content.Context, file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v8-maher")
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
                    if (wid == 0) ShareMushafWord(ayah, pos, "", "0", isMarker = true)
                    else ShareMushafWord(ayah, pos, glyphs.forWord(wid).orEmpty(), "w$wid")
                },
            )
        }

    private fun edgeInkRatio(bmp: Bitmap, marginPx: Int = 14): Double {
        val w = bmp.width
        val h = bmp.height
        var ink = 0
        var total = 0
        fun sample(x: Int, y: Int) {
            val c = bmp.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            if ((r + g + b) / 3 > 180) ink++
            total++
        }
        for (y in (h * 0.18).toInt() until (h * 0.55).toInt() step 3) {
            for (x in 0 until marginPx) sample(x, y)
            for (x in 0 until marginPx) sample(w - 1 - x, y)
        }
        return if (total == 0) 0.0 else ink.toDouble() / total
    }

    private fun versesFrom(lines: List<ShareMushafLine>, surah: Int, name: String, from: Int, to: Int): AyahShareContent {
        val vs = (from..to).map { ayah ->
            val words = lines.flatMap { it.words }.filter { it.ayah == ayah && !it.isMarker }
            AyahShareVerse(
                ayah,
                lines.first().pageNumber,
                words.joinToString(" ") { it.qcf },
                words.joinToString(" ") { it.uthmanic },
            )
        }
        return AyahShareContent(
            surah,
            name,
            from,
            to,
            vs,
            mushafLines = lines.filter { l -> l.words.any { it.ayah in from..to } },
        )
    }

    @Test
    fun fatihaAndBaqarahFitFrames() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        val evidence = File(context.cacheDir, "v8-export-fit").apply {
            deleteRecursively()
            mkdirs()
        }
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
            showTitle = true,
        )

        val fatihaLines = qcfLines(
            glyphs,
            1,
            listOf(
                listOf(Triple(1, 1, 1), Triple(1, 2, 2), Triple(1, 3, 3), Triple(1, 4, 4), Triple(1, 5, 0)),
                listOf(
                    Triple(2, 1, 6), Triple(2, 2, 7), Triple(2, 3, 8), Triple(2, 4, 9), Triple(2, 5, 0),
                    Triple(3, 1, 11), Triple(3, 2, 12), Triple(3, 3, 0),
                ),
                listOf(
                    Triple(4, 1, 14), Triple(4, 2, 15), Triple(4, 3, 16), Triple(4, 4, 0),
                    Triple(5, 1, 18), Triple(5, 2, 19), Triple(5, 3, 20), Triple(5, 4, 21), Triple(5, 5, 0),
                ),
                listOf(Triple(6, 1, 23), Triple(6, 2, 24), Triple(6, 3, 25), Triple(6, 4, 0)),
                listOf(
                    Triple(7, 1, 27), Triple(7, 2, 28), Triple(7, 3, 29), Triple(7, 4, 30), Triple(7, 5, 31),
                    Triple(7, 6, 32), Triple(7, 7, 33), Triple(7, 8, 34), Triple(7, 9, 35), Triple(7, 10, 0),
                ),
            ),
        )
        val fatiha = versesFrom(fatihaLines, 1, "Al-Fatihah", 1, 7)
        val f1 = exporter.makerPreview(fatiha, options, word = 1, progress = 0.3f, activeAyah = 1)
        val f1File = File(evidence, "v8-export-fatiha-p1-v2.jpg")
        f1File.outputStream().use { f1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val f1Edge = edgeInkRatio(f1)
        f1.recycle()
        System.gc()

        val f2 = exporter.makerPreview(fatiha, options, word = 1, progress = 0.3f, activeAyah = 7)
        val f2File = File(evidence, "v8-export-fatiha-p2-v2.jpg")
        f2File.outputStream().use { f2.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val f2Edge = edgeInkRatio(f2)
        f2.recycle()
        System.gc()

        val baqLines = qcfLines(
            glyphs,
            2,
            listOf(
                listOf(
                    Triple(3, 1, 9), Triple(3, 2, 10), Triple(3, 3, 11), Triple(3, 4, 12), Triple(3, 5, 13),
                    Triple(3, 6, 14), Triple(3, 7, 15), Triple(3, 8, 16), Triple(3, 9, 0),
                ),
                listOf(
                    Triple(4, 1, 17), Triple(4, 2, 18), Triple(4, 3, 19), Triple(4, 4, 20),
                    Triple(4, 5, 21), Triple(4, 6, 22), Triple(4, 7, 23), Triple(4, 8, 0),
                ),
                listOf(
                    Triple(5, 1, 24), Triple(5, 2, 25), Triple(5, 3, 26), Triple(5, 4, 27), Triple(5, 5, 28),
                    Triple(5, 6, 29), Triple(5, 7, 30), Triple(5, 8, 31), Triple(5, 9, 32), Triple(5, 10, 0),
                ),
            ),
        )
        val baq = versesFrom(baqLines, 2, "Al-Baqarah", 3, 5)
        val b1 = exporter.makerPreview(baq, options, word = 2, progress = 0.5f, activeAyah = 3)
        val bFile = File(evidence, "v8-export-baqarah-3-5-v2.jpg")
        bFile.outputStream().use { b1.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        val bEdge = edgeInkRatio(b1)
        b1.recycle()

        File(evidence, "fit-report.txt").writeText("f1Edge=$f1Edge f2Edge=$f2Edge bEdge=$bEdge\n")
        assertTrue("fatiha p1 edge ink $f1Edge", f1Edge < 0.10)
        assertTrue("fatiha p2 edge ink $f2Edge", f2Edge < 0.10)
        assertTrue("baq edge ink $bEdge", bEdge < 0.10)
        publishJpeg(context, f1File, f1File.name)
        publishJpeg(context, f2File, f2File.name)
        publishJpeg(context, bFile, bFile.name)
    }
}
