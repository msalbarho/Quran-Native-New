package com.quransunah.app.share

import android.content.ContentValues
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Short multi-ayah Maher (102) export for v8 KFGQPC audio evidence. */
@RunWith(AndroidJUnit4::class)
class MaherShortExportTest {
    private fun publish(context: android.content.Context, file: File, mime: String, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/v8-maher")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert failed for $name")
        resolver.openOutputStream(uri).use { out ->
            requireNotNull(out)
            file.inputStream().use { it.copyTo(out) }
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    }

    @Test
    fun fatiha1to3MaherKfc() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        fun qcf(ids: IntRange) = ids.mapNotNull { glyphs.forWord(it) }.joinToString(" ")
        val content = AyahShareContent(
            surah = 1,
            surahName = "Al-Fatihah",
            fromAyah = 1,
            toAyah = 3,
            verses = listOf(
                AyahShareVerse(1, 1, qcf(1..4), "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
                AyahShareVerse(2, 1, qcf(6..9), "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ"),
                AyahShareVerse(3, 1, qcf(11..12), "ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"),
            ),
        )
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
        )
        val evidence = File(context.cacheDir, "v8-maher").apply {
            deleteRecursively()
            mkdirs()
        }
        val timing = QulWordTimings(context).loadForReciter("102", 1, 1)
        assertTrue(QulRecitations.hasWordTimingCatalog("102"))
        assertTrue(QulRecitations.spec("102")?.isKfc == true)
        assertTrue(timing.surahAudioUrl?.contains("Almusshaf-Al-Mojawwad") == true)
        assertTrue((timing.cropEndMs ?: 0L) > (timing.cropStartMs ?: 0L))
        assertTrue(timing.segments.isNotEmpty())
        assertTrue(QulRecitations.ayahAudioUrl("102", 1, 1) == null)

        val preview = exporter.makerPreview(content, options, 1, 0.3f)
        val previewFile = File(evidence, "v8-maher-preview.png")
        previewFile.outputStream().use { preview.compress(Bitmap.CompressFormat.PNG, 100, it) }
        preview.recycle()

        val video = exporter.makeVideo(content, "102", options)
        assertTrue(video.exists() && video.length() > 10_000L)
        val videoCopy = File(evidence, "v8-maher-fatiha-1-3.mp4")
        video.copyTo(videoCopy, overwrite = true)
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(video.absolutePath)
            val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
        } finally {
            extractor.release()
        }
        val branded = exporter.makeMakerImage(content, options)
        val brandedFile = File(evidence, "v8-maher-branded.jpg")
        branded.copyTo(brandedFile, overwrite = true)
        val report = File(evidence, "report.txt")
        report.writeText(
            "reciter=102 kfc=${timing.surahAudioUrl} crop=${timing.cropStartMs}-${timing.cropEndMs} " +
                "words=${timing.segments.size} file=${video.name} bytes=${video.length()}\n" +
                "attr=${QulRecitations.KFGQPC_MAHER_ATTRIBUTION}\n" +
                runCatching { File(context.cacheDir, "share/last-maker-sync.txt").readText() }.getOrDefault("")
        )
        publish(context, previewFile, "image/png", "v8-maher-preview.png")
        publish(context, brandedFile, "image/jpeg", "v8-maher-branded.jpg")
        publish(context, videoCopy, "video/mp4", "v8-maher-fatiha-1-3.mp4")
        publish(context, report, "text/plain", "report.txt")
    }

    @Test
    fun baqarahPassageMaherKfc() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        fonts.ensureMaps()
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        fun qcf(ids: IntRange) = ids.mapNotNull { glyphs.forWord(it) }.joinToString(" ")
        // 2:1-3 Alif-Lam-Mim / Dhalika / Alladhina
        val content = AyahShareContent(
            surah = 2,
            surahName = "Al-Baqarah",
            fromAyah = 1,
            toAyah = 3,
            verses = listOf(
                AyahShareVerse(1, 2, qcf(1..1), "الم"),
                AyahShareVerse(2, 2, qcf(2..8), "ذلك الكتاب لا ريب فيه"),
                AyahShareVerse(3, 2, qcf(9..16), "الذين يؤمنون بالغيب"),
            ),
        )
        val options = VideoMakerOptions(
            translation = false,
            backgrounds = ShareBackgrounds.FILES.take(1),
            trackingMode = ShareMushafLayout.TRACKING_BAND,
        )
        val timing = QulWordTimings(context).loadForReciter("102", 2, 2)
        assertTrue(timing.surahAudioUrl?.contains("Almusshaf-Al-Mojawwad") == true)
        assertTrue(timing.segments.isNotEmpty())
        val evidence = File(context.cacheDir, "v8-maher-baq").apply { mkdirs() }
        val video = exporter.makeVideo(content, "102", options)
        assertTrue(video.exists() && video.length() > 10_000L)
        val videoCopy = File(evidence, "v8-maher-baqarah-1-3.mp4")
        video.copyTo(videoCopy, overwrite = true)
        // Grab a mid-ayah highlight still via makerPreview at word progress
        val still = exporter.makerPreview(content, options, 2, 0.55f)
        val stillFile = File(evidence, "v8-maher-baqarah-word.png")
        stillFile.outputStream().use { still.compress(Bitmap.CompressFormat.PNG, 100, it) }
        still.recycle()
        val report = File(evidence, "baq-report.txt")
        report.writeText(
            "reciter=102 kfc=${timing.surahAudioUrl} crop=${timing.cropStartMs}-${timing.cropEndMs} " +
                "words=${timing.segments.size} file=${video.name} bytes=${video.length()}\n"
        )
        publish(context, stillFile, "image/png", "v8-maher-baqarah-word.png")
        publish(context, videoCopy, "video/mp4", "v8-maher-baqarah-1-3.mp4")
        publish(context, report, "text/plain", "baq-report.txt")
    }

}
