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

/**
 * Builds synthetic Madinah-style mushaf lines for Al-Fatiha 1–7 that would
 * overflow without reflow+paginate, then captures makerPreview evidence.
 */
@RunWith(AndroidJUnit4::class)
class FatihaMushafPreviewTest {
    private fun publish(context: android.content.Context, file: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
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

    @Test
    fun fatihaPreviewNoOverlap() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        fun qcf(id: Int) = glyphs.forWord(id).orEmpty()
        // Pack many words onto few source lines (as on Madinah page) so reflow must wrap.
        val lines = listOf(
            ShareMushafLine(
                pageNumber = 1, lineNumber = 1,
                words = listOf(
                    ShareMushafWord(1, 1, qcf(1), "بِسْمِ"),
                    ShareMushafWord(1, 2, qcf(2), "ٱللَّهِ"),
                    ShareMushafWord(1, 3, qcf(3), "ٱلرَّحْمَٰنِ"),
                    ShareMushafWord(1, 4, qcf(4), "ٱلرَّحِيمِ"),
                    ShareMushafWord(1, 5, "", "١", isMarker = true),
                    ShareMushafWord(2, 1, qcf(6), "ٱلْحَمْدُ"),
                    ShareMushafWord(2, 2, qcf(7), "لِلَّهِ"),
                    ShareMushafWord(2, 3, qcf(8), "رَبِّ"),
                    ShareMushafWord(2, 4, qcf(9), "ٱلْعَٰلَمِينَ"),
                    ShareMushafWord(2, 5, "", "٢", isMarker = true),
                ),
            ),
            ShareMushafLine(
                pageNumber = 1, lineNumber = 2,
                words = listOf(
                    ShareMushafWord(3, 1, qcf(11), "ٱلرَّحْمَٰنِ"),
                    ShareMushafWord(3, 2, qcf(12), "ٱلرَّحِيمِ"),
                    ShareMushafWord(3, 3, "", "٣", isMarker = true),
                    ShareMushafWord(4, 1, qcf(14), "مَٰلِكِ"),
                    ShareMushafWord(4, 2, qcf(15), "يَوْمِ"),
                    ShareMushafWord(4, 3, qcf(16), "ٱلدِّينِ"),
                    ShareMushafWord(4, 4, "", "٤", isMarker = true),
                    ShareMushafWord(5, 1, qcf(18), "إِيَّاكَ"),
                    ShareMushafWord(5, 2, qcf(19), "نَعْبُدُ"),
                    ShareMushafWord(5, 3, qcf(20), "وَإِيَّاكَ"),
                    ShareMushafWord(5, 4, qcf(21), "نَسْتَعِينُ"),
                    ShareMushafWord(5, 5, "", "٥", isMarker = true),
                ),
            ),
            ShareMushafLine(
                pageNumber = 1, lineNumber = 3,
                words = listOf(
                    ShareMushafWord(6, 1, qcf(23), "ٱهْدِنَا"),
                    ShareMushafWord(6, 2, qcf(24), "ٱلصِّرَٰطَ"),
                    ShareMushafWord(6, 3, qcf(25), "ٱلْمُسْتَقِيمَ"),
                    ShareMushafWord(6, 4, "", "٦", isMarker = true),
                    ShareMushafWord(7, 1, qcf(27), "صِرَٰطَ"),
                    ShareMushafWord(7, 2, qcf(28), "ٱلَّذِينَ"),
                    ShareMushafWord(7, 3, qcf(29), "أَنْعَمْتَ"),
                    ShareMushafWord(7, 4, qcf(30), "عَلَيْهِمْ"),
                    ShareMushafWord(7, 5, qcf(31), "غَيْرِ"),
                    ShareMushafWord(7, 6, qcf(32), "ٱلْمَغْضُوبِ"),
                    ShareMushafWord(7, 7, qcf(33), "عَلَيْهِمْ"),
                    ShareMushafWord(7, 8, qcf(34), "وَلَا"),
                    ShareMushafWord(7, 9, qcf(35), "ٱلضَّآلِّينَ"),
                    ShareMushafWord(7, 10, "", "٧", isMarker = true),
                ),
            ),
        )
        val content = AyahShareContent(
            surah = 1,
            surahName = "Al-Fatihah",
            fromAyah = 1,
            toAyah = 7,
            verses = (1..7).map { ayah ->
                val words = lines.flatMap { it.words }.filter { it.ayah == ayah && !it.isMarker }
                AyahShareVerse(
                    ayah = ayah,
                    pageNumber = 1,
                    qcfText = words.joinToString(" ") { it.qcf },
                    uthmanicText = words.joinToString(" ") { it.uthmanic },
                )
            },
            mushafLines = lines,
        )
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
            showTitle = true,
        )
        val preview = exporter.makerPreview(content, options, word = 1, progress = 0.4f)
        val out = File(context.cacheDir, "v8-fatiha-preview.png")
        out.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(out.length() > 5_000L)
        // Sample edges: left/right gutters should stay mostly background (not clipped ink).
        val w = preview.width
        val h = preview.height
        val sampleY = (h * 0.35f).toInt()
        fun lum(x: Int, y: Int): Int {
            val c = preview.getPixel(x.coerceIn(0, w - 1), y.coerceIn(0, h - 1))
            val r = (c shr 16) and 0xff
            val g = (c shr 8) and 0xff
            val b = c and 0xff
            return (r + g + b) / 3
        }
        // Edge columns near the frame sides should not be bright white glyph ink.
        val leftEdge = (0 until 8).map { lum(it, sampleY) }.average()
        val rightEdge = (0 until 8).map { lum(w - 1 - it, sampleY) }.average()
        assertTrue("left edge clipped? lum=$leftEdge", leftEdge < 220.0)
        assertTrue("right edge clipped? lum=$rightEdge", rightEdge < 220.0)
        publish(context, out, "v8-fatiha-preview.png")
        preview.recycle()
    }
}
