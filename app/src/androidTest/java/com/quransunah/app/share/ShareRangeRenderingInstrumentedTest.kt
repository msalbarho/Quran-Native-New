package com.quransunah.app.share

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.media.MediaMetadataRetriever
import java.io.File
import org.json.JSONArray
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.fonts.QpcV4Glyphs
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareRangeRenderingInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fonts = QcfFontManager(context, QpcV4Glyphs(context))
    private val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
    private val verses = listOf(
        AyahShareVerse(6, 3, "", "إِنَّ الَّذِينَ كَفَرُوا سَوَاءٌ عَلَيْهِمْ أَأَنذَرْتَهُمْ أَمْ لَمْ تُنذِرْهُمْ لَا يُؤْمِنُونَ"),
        AyahShareVerse(7, 3, "", "خَتَمَ اللَّهُ عَلَىٰ قُلُوبِهِمْ وَعَلَىٰ سَمْعِهِمْ وَعَلَىٰ أَبْصَارِهِمْ غِشَاوَةٌ وَلَهُمْ عَذَابٌ عَظِيمٌ"),
        AyahShareVerse(8, 3, "", "وَمِنَ النَّاسِ مَن يَقُولُ آمَنَّا بِاللَّهِ وَبِالْيَوْمِ الْآخِرِ وَمَا هُم بِمُؤْمِنِينَ"),
    )

    private fun content(selection: List<AyahShareVerse>, surah: Int = 2) = AyahShareContent(
        surah, "", selection.first().ayah, selection.last().ayah, selection,
        mushafLines = selection.map { verse ->
            ShareMushafLine(verse.pageNumber, 1, verse.uthmanicText.split(" ").mapIndexed { index, word ->
                ShareMushafWord(verse.ayah, index + 1, "", word)
            })
        },
    )

    @Test
    fun encodedImageUsesEverySelectedAyahEvenWithMushafLines() = runBlocking {
        for (selection in (1..3).map { verses.take(it) } + listOf(verses.mapIndexed { index, verse ->
            if (index == 2) verse.copy(pageNumber = 4, uthmanicText = List(12) { verse.uthmanicText }.joinToString(" "))
            else verse
        })) {
            val original = content(selection)
            val encoded = BitmapFactory.decodeFile(exporter.makeShareImage(original).absolutePath)
            assertEquals(1080, encoded.width)
            assertEquals(1920, encoded.height)
            for (index in selection.indices) {
                // Keep the source lines identical and change only one ayah in the image text.
                val changed = original.copy(verses = selection.mapIndexed { i, verse ->
                    if (i == index) verse.copy(uthmanicText = "الرَّحْمَٰنِ الرَّحِيمِ") else verse
                })
                val comparison = BitmapFactory.decodeFile(exporter.makeShareImage(changed).absolutePath)
                assertFalse("Ayah ${selection[index].ayah} must affect the JPEG", encoded.sameAs(comparison))
                comparison.recycle()
            }
            encoded.recycle()
        }
    }

    @Test
    fun decorativeGlyphsAppearWithFirstPreviewAyahOnly() = runBlocking {
        fonts.ensureMaps()
        val face = requireNotNull(fonts.loadSurahTitleTypeface())
        assertEquals("\uFAD3\uFAD2\uFAD1\uFAD0", fonts.displayBasmalahLigature())
        val paint = Paint().apply { typeface = face }
        assertTrue(fonts.displayBasmalahLigature().all { paint.hasGlyph(it.toString()) })

        val options = VideoMakerOptions(showTitle = false, marker = false)
        val selection = content(verses)
        val image = exporter.shareImagePreview(selection)
        try {
            assertTrue(goldInk(image) > 20)
        } finally {
            image.recycle()
        }
        try {
            assertTrue(goldInk(exporter.makerPreview(selection, options, word = null)) > 20)
            assertEquals(0, goldInk(exporter.makerPreview(selection, options, word = null, activeAyah = 7)))
            assertEquals(0, goldInk(exporter.makerPreview(content(verses, surah = 9), options, word = null)))
        } finally {
            exporter.releaseMakerPreview()
        }
    }

    @Test
    fun exportedVideoHasHeadingOnlyDuringFirstAyah() = runBlocking {
        val options = VideoMakerOptions(showTitle = false, marker = false)
        val video = exporter.makeVideo(content(verses.take(2)), "92", options)
        val timeline = JSONArray(File(context.cacheDir, "share/last-maker-timeline.json").readText())
        assertEquals(2, timeline.length())
        assertEquals(6, timeline.getJSONObject(0).getInt("ayah"))
        val second = timeline.getJSONObject(1)
        assertEquals(7, second.getInt("ayah"))
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(video.absolutePath)
            assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
            val firstFrame = requireNotNull(retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST))
            val secondFrame = requireNotNull(retriever.getFrameAtTime(
                second.getLong("startUs") + second.getLong("durationUs") / 2,
                MediaMetadataRetriever.OPTION_CLOSEST,
            ))
            try {
                assertTrue(goldInk(firstFrame, tolerance = 16) > goldInk(secondFrame, tolerance = 16) + 80)
            } finally {
                firstFrame.recycle()
                secondFrame.recycle()
            }
        } finally {
            retriever.release()
        }
    }

    private fun goldInk(bitmap: Bitmap, tolerance: Int = 0): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return pixels.count {
            kotlin.math.abs(android.graphics.Color.red(it) - 0xD4) <= tolerance &&
                kotlin.math.abs(android.graphics.Color.green(it) - 0xAF) <= tolerance &&
                kotlin.math.abs(android.graphics.Color.blue(it) - 0x37) <= tolerance
        }
    }
}
