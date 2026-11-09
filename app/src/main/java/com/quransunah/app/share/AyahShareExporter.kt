package com.quransunah.app.share

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.quransunah.app.R
import com.quransunah.app.core.AppPermissions
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.audio.AudioUrls
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.ayahBackgroundGlyph
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

private const val SHARE_WIDTH = 1080
private const val SHARE_HEIGHT = 1920
/** Higher canvas for share-video export (React uses 1080├ù1920 @ 8 Mbps). */
private const val SHARE_VIDEO_WIDTH = 1440
private const val SHARE_VIDEO_HEIGHT = 2560
private const val SHARE_VIDEO_BITRATE = 8_000_000
/** Maker export: enough for sharp script, fewer bits than the slideshow so encoding finishes sooner. */
private const val MAKER_VIDEO_BITRATE = 6_500_000
private const val OVERLAY_TOP = 0x99000000.toInt()
private const val OVERLAY_MID = 0x66000000.toInt()
private const val TEXT_WHITE = 0xFFF7F4EE.toInt()
private const val BASMALAH_GOLD = 0xFFC5A059.toInt()
/** Accent for Medina-style ayah-end marks on dark share slides. */
private const val TEXT_BACKDROP = 0x8C000000.toInt()
private const val AYAH_MARKER_FILL = 0xFF1B2433.toInt()
private const val AYAH_MARKER_ACCENT = 0xFFD4B06A.toInt()
private const val MAX_SLIDE_US = 280_000_000L
private const val INTRO_NAME_ONLY_MS = 2_200L
private const val MAX_VIDEO_EXPORT_DURATION_MS = 5 * 60 * 1000L

private enum class MakerPlate { FULL, BACKDROP, GLYPHS }

private enum class SharePaintMode {
    FULL,
    INTRO,
    BASMALAH,
    AYAH,
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Singleton
class AyahShareExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fontManager: QcfFontManager,
    private val reciterCatalog: ReciterCatalog,
) {
    private val backgroundCache = object : android.util.LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private data class PreviewPlate(
        val key: Int,
        val backdrop: Bitmap,
        val glyphs: Bitmap,
        val boxes: List<ShareWordBox>,
        val geometry: MakerPreviewGeometry,
    )
    private var previewPlate: PreviewPlate? = null
    private var previewFrame: Bitmap? = null

    fun releaseMakerPreview() {
        previewPlate?.backdrop?.takeIf { !it.isRecycled }?.recycle()
        previewPlate?.glyphs?.takeIf { !it.isRecycled }?.recycle()
        previewPlate = null
        previewFrame?.takeIf { !it.isRecycled }?.recycle()
        previewFrame = null
    }

    private val brandingBitmap by lazy {
        context.assets.open("share-brand/icon.jpg").use { BitmapFactory.decodeStream(it) }
            ?: error("Project branding icon unavailable")
    }
    internal fun arabicBrandingName(): String {
        val configuration=android.content.res.Configuration(context.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("ar"))
        }
        return context.createConfigurationContext(configuration).getString(R.string.app_name)
    }

    internal fun promotionalWatermarkLabel(): String = "القرآن الكريم - تلاوة واستماع"

    suspend fun shareImage(content: AyahShareContent) = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val verse = content.verses.firstOrNull() ?: return@withContext
        val bitmap = renderMaker(
            content,
            verse,
            VideoMakerOptions(
                ayahY = 0.50f,
                titleY = 0.13f,
            ),
            null,
            1080,
            1920,
            displayVerses = content.verses,
            lineSpacing = ShareMushafLayout.IMAGE_LINE_MULTIPLIER,
            fitWidthFraction = 0.72f,
        )
        val file = writeJpeg(bitmap, "ayah-${content.surah}-${verse.ayah}.jpg")
        bitmap.recycle()
        saveToGalleryThenShare(
            file = file,
            mime = "image/jpeg",
            displayName = file.name,
            isVideo = false,
        )
    }

    suspend fun shareVideo(
        content: AyahShareContent,
        reciterId: String,
    ) = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val reciter = reciterCatalog.ayahReciter(reciterId)
            ?: reciterCatalog.defaultAyahReciter()
        val verses = content.verses
        if (verses.isEmpty()) return@withContext

        val slides = ArrayList<File>()
        val rawAudio = ArrayList<File>()
        var previousBackground: String? = null
        val firstVerse = verses.first()

        if (content.shouldPrependIntro()) {
            val introBg = ShareBackgrounds.pick()
            previousBackground = introBg
            val intro = renderSlide(content, firstVerse, SharePaintMode.INTRO, introBg, SHARE_VIDEO_WIDTH, SHARE_VIDEO_HEIGHT)
            slides += writeJpeg(intro, "slide-name-${content.surah}.jpg")
            intro.recycle()
            rawAudio += writeSilentWav(INTRO_NAME_ONLY_MS)
        }

        if (content.shouldPrependBasmalahRecitation()) {
            val basmalahBg = ShareBackgrounds.pick(previousBackground)
            previousBackground = basmalahBg
            val basmalah = renderSlide(content, firstVerse, SharePaintMode.BASMALAH, basmalahBg, SHARE_VIDEO_WIDTH, SHARE_VIDEO_HEIGHT)
            slides += writeJpeg(basmalah, "slide-basmalah-${content.surah}.jpg")
            basmalah.recycle()
            rawAudio += downloadAyahAudio(reciter, 1, 1).first()
        }

        verses.forEach { verse ->
            val background = ShareBackgrounds.pick(previousBackground)
            previousBackground = background
            val frame = renderSlide(content, verse, SharePaintMode.AYAH, background, SHARE_VIDEO_WIDTH, SHARE_VIDEO_HEIGHT)
            slides += writeJpeg(frame, "slide-${content.surah}-${verse.ayah}.jpg")
            frame.recycle()
            rawAudio += downloadAyahAudio(reciter, content.surah, verse.ayah).first()
        }

        val preparedAudio = rawAudio.mapIndexed { index, source ->
            ShareAudioClip.prepare(
                source = source,
                dest = File(shareDir(), "clip-$index.wav"),
                trimStart = index > 0,
                trimEnd = index < rawAudio.lastIndex,
            )
        }

        val output = File(
            shareDir(),
            "ayah-${content.surah}-${content.fromAyah}-${content.toAyah}.mp4",
        )
        if (output.exists()) output.delete()
        runCatching {
            exportVideo(slides, preparedAudio, output)
        }.onFailure { error ->
            shareImage(content)
            throw error
        }
        saveToGalleryThenShare(
            file = output,
            mime = "video/mp4",
            displayName = output.name,
            isVideo = true,
        )
    }

    /** Offline-safe editor preview uses the very same renderer as the MP4. */
    suspend fun makerPreview(
        content: AyahShareContent,
        options: VideoMakerOptions,
        word: Int? = 1,
        progress: Float = .5f,
        elapsedUs: Long = 0L,
        totalUs: Long = 1L,
        onGeometry: ((MakerPreviewGeometry) -> Unit)? = null,
        previewBackground: String? = null,
        activeAyah: Int? = content.verses.firstOrNull()?.ayah,
    ): Bitmap = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val current = content.verses.firstOrNull { it.ayah == activeAyah } ?: content.verses.first()
        val textSize = sharedAyahTextSize(content.mushafLines, options, 540)
        val plateOk = content.mushafLines.isNotEmpty() && !options.motion && options.backgrounds.size <= 1 &&
            options.trackingMode != ShareMushafLayout.TRACKING_CUMULATIVE
        if (!plateOk) {
            return@withContext renderMaker(
                content, current, options, word, 540, 960, progress, elapsedUs, totalUs,
                displayVerses = displayPageForAyah(content, current.ayah), onGeometry = onGeometry, previewBackground = previewBackground,
                activeAyah = current.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER,
            )
        }
        val key = java.util.Objects.hash(
            content.surah, content.fromAyah, content.toAyah, content.translationTag, current.ayah,
            options.copy(highlightColor = 0, trackingMode = 0), previewBackground,
        )
        var plate = previewPlate
        if (plate == null || plate.key != key || plate.backdrop.isRecycled || plate.glyphs.isRecycled) {
            plate?.backdrop?.takeIf { !it.isRecycled }?.recycle()
            plate?.glyphs?.takeIf { !it.isRecycled }?.recycle()
            val boxes = ArrayList<ShareWordBox>()
            var geometry = MakerPreviewGeometry()
            val backdrop = renderMaker(
                content, current, options, null, 540, 960, 0f, elapsedUs, totalUs,
                displayVerses = displayPageForAyah(content, current.ayah), onGeometry = { geometry = it }, previewBackground = previewBackground,
                activeAyah = current.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.BACKDROP,
            )
            val glyphs = renderMaker(
                content, current, options, null, 540, 960, 0f, elapsedUs, totalUs,
                displayVerses = displayPageForAyah(content, current.ayah), previewBackground = previewBackground,
                activeAyah = current.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.GLYPHS, wordBoxes = boxes,
            )
            plate = PreviewPlate(key, backdrop, glyphs, boxes, geometry)
            previewPlate = plate
        }
        onGeometry?.invoke(plate.geometry)
        val frame = previewFrame?.takeIf { !it.isRecycled && it.width == 540 && it.height == 960 }
            ?: createBitmap(540, 960).also { previewFrame = it }
        val canvas = Canvas(frame)
        canvas.drawBitmap(plate.backdrop, 0f, 0f, null)
        drawExportTracking(canvas, plate.boxes, options, word, current.ayah, progress, 540)
        canvas.drawBitmap(plate.glyphs, 0f, 0f, null)
        frame
    }


    /**
     * One ayah per video page. Lines belonging to other ayat are dropped.
     * An ayah longer than [maxLines] mushaf lines is split across consecutive
     * pages at line boundaries (word timings drive which slice is shown).
     */
    private fun paginateMushafLines(
        lines: List<ShareMushafLine>,
        minLines: Int = 1,
        maxLines: Int = ShareMushafLayout.VIDEO_AYAH_PAGE_MAX_LINES,
    ): List<List<ShareMushafLine>> {
        if (lines.isEmpty()) return emptyList()
        fun ayahOf(line: ShareMushafLine): Int =
            line.words.firstOrNull { !it.isMarker }?.ayah
                ?: line.words.firstOrNull()?.ayah
                ?: -1
        val pages = ArrayList<List<ShareMushafLine>>()
        var i = 0
        while (i < lines.size) {
            val ayah = ayahOf(lines[i])
            var ayahEnd = i
            while (ayahEnd + 1 < lines.size && ayahOf(lines[ayahEnd + 1]) == ayah) {
                ayahEnd++
            }
            // Emit this ayah in chunks of at most maxLines.
            var start = i
            while (start <= ayahEnd) {
                val end = minOf(start + maxLines, ayahEnd + 1)
                pages += lines.subList(start, end)
                start = end
            }
            i = ayahEnd + 1
        }
        return pages
    }

    private fun displayPageLines(
        content: AyahShareContent,
        activeAyah: Int?,
        activeWord: Int? = null,
    ): List<ShareMushafLine> {
        val lines = content.mushafLines
        if (lines.isEmpty()) return lines
        val pages = paginateMushafLines(lines)
        if (pages.isEmpty()) return lines
        val ayah = activeAyah ?: content.verses.firstOrNull()?.ayah
        if (ayah == null) return pages.first()
        if (activeWord != null) {
            for (page in pages) {
                val hit = page.any { line ->
                    line.words.any { w -> !w.isMarker && w.ayah == ayah && w.position == activeWord }
                }
                if (hit) return page
            }
        }
        return pages.firstOrNull { page ->
            page.any { line -> line.words.any { it.ayah == ayah } }
        } ?: pages.first()
    }

    private fun ayahSourceLines(lines: List<ShareMushafLine>, ayah: Int): List<ShareMushafLine> =
        lines.mapNotNull { line ->
            val kept = line.words.filter { it.ayah == ayah }
            if (kept.isEmpty()) null else line.copy(words = kept)
        }

    private fun ayahsInOrder(lines: List<ShareMushafLine>): List<Int> {
        val seen = LinkedHashSet<Int>()
        for (line in lines) {
            for (word in line.words) seen += word.ayah
        }
        return seen.toList()
    }

    /** Chunk already-reflowed lines into pages of ≤ MAX lines; pick page by active word. */
    private fun selectWrappedAyahPage(
        wrapped: List<ShareMushafLine>,
        activeWord: Int? = null,
    ): List<ShareMushafLine> {
        if (wrapped.isEmpty()) return wrapped
        val maxLines = ShareMushafLayout.VIDEO_AYAH_PAGE_MAX_LINES
        if (wrapped.size <= maxLines) return wrapped
        val pages = ArrayList<List<ShareMushafLine>>()
        var i = 0
        while (i < wrapped.size) {
            val end = minOf(i + maxLines, wrapped.size)
            pages += wrapped.subList(i, end)
            i = end
        }
        if (activeWord != null) {
            for (page in pages) {
                if (page.any { line -> line.words.any { w -> !w.isMarker && w.position == activeWord } }) {
                    return page
                }
            }
        }
        return pages.first()
    }


    private fun displayPageForAyah(content: AyahShareContent, activeAyah: Int?): List<AyahShareVerse> {
        val verses = content.verses
        if (verses.isEmpty()) return verses
        val ayah = activeAyah ?: verses.first().ayah
        val one = verses.filter { it.ayah == ayah }
        return one.ifEmpty { verses.take(1) }
    }


    suspend fun makeVideo(content: AyahShareContent, reciterId: String, options: VideoMakerOptions): File = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val reciter = reciterCatalog.ayahReciter(reciterId) ?: error("Choose a reciter")
        val frames = ArrayList<File>()
        val durations = ArrayList<Long>()
        val clips = ArrayList<ShareTimedClip>()
        val timeline = org.json.JSONArray()
        var timelineStartUs = 0L
        data class PreparedVerse(val verse: AyahShareVerse, val clip: ShareTimedClip, val segments: List<WordSegment>)
        val prepared = ArrayList<PreparedVerse>()
        val timingStore = QulWordTimings(context)
        content.verses.forEachIndexed { verseIndex, verse ->
            val timing = runCatching { timingStore.loadForReciter(reciter.id, content.surah, verse.ayah) }
                .getOrDefault(QulAyahTiming())
            val source = resolveAyahAudio(reciter, content.surah, verse.ayah, timing)
            val originalSegments = timing.segments
            // Word timestamps are highlight anchors, NOT safe audio crop boundaries.
            // Preserve the complete source ayah, including its real consonant attack and margins.
            val clip = ShareAudioClip.prepareMaker(
                source,
                File(shareDir(), "maker-${verse.ayah}.wav"),
                fromMs = timing.cropStartMs ?: 0L,
                toMs = timing.cropEndMs,
                fadeIn = false,
                fadeOut = false,
            )
            clips += clip
            val segments = originalSegments.cursorSegments(clip.durationUs)
            timeline.put(org.json.JSONObject().put("ayah", verse.ayah).put("startUs", timelineStartUs)
                .put("durationUs", clip.durationUs).put("cropOffsetMs", 0)
                .put("words", org.json.JSONArray().apply {
                    segments.forEach { put(org.json.JSONArray(listOf(it.position, it.startMs, it.endMs))) }
                }))
            timelineStartUs += clip.durationUs
            prepared += PreparedVerse(verse, clip, segments)
        }
        val totalUs = clips.sumOf { it.durationUs }
        val textSize = sharedAyahTextSize(content.mushafLines, options, 1080)
        val smooth = makerNeedsSmoothFrames(options)
        val frameUs = makerExportFrameUs(totalUs, smooth)
        val plateOk = content.mushafLines.isNotEmpty() && !options.motion && options.backgrounds.size <= 1 &&
            options.trackingMode != ShareMushafLayout.TRACKING_CUMULATIVE
        val frameBitmap = if (plateOk) createBitmap(1080, 1920) else null
        val frameCanvas = frameBitmap?.let { Canvas(it) }
        var verseStartUs = 0L
        try {
        prepared.forEach { item ->
            val boxes = ArrayList<ShareWordBox>()
            val backdrop = if (plateOk) {
                renderMaker(content, item.verse, options, null, 1080, 1920,
                    0f, verseStartUs, totalUs, displayVerses = displayPageForAyah(content, item.verse.ayah),
                    activeAyah = item.verse.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                    lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.BACKDROP)
            } else null
            val glyphs = if (plateOk) {
                renderMaker(content, item.verse, options, null, 1080, 1920,
                    0f, verseStartUs, totalUs, displayVerses = displayPageForAyah(content, item.verse.ayah),
                    activeAyah = item.verse.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                    lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.GLYPHS,
                    wordBoxes = boxes)
            } else null
            var platePageKey: List<ShareMushafLine>? = null
            var plateBackdrop = backdrop
            var plateGlyphs = glyphs
            var plateBoxes = boxes
            makerRenderFrames(item.segments, item.clip.durationUs, smooth, frameUs).forEach { interval ->
                val localUs = interval.startUs + interval.durationUs / 2
                val word = item.segments.firstOrNull { it.position == interval.word && localUs / 1000 in it.startMs until it.endMs }
                val progress = word?.let { ((localUs / 1000f - it.startMs) / (it.endMs - it.startMs).coerceAtLeast(1)).coerceIn(0f,1f) } ?: 0f
                val pageLines = displayPageLines(content, item.verse.ayah, interval.word)
                if (plateOk && (platePageKey == null || platePageKey != pageLines)) {
                    plateBackdrop?.takeIf { it !== backdrop }?.recycle()
                    plateGlyphs?.takeIf { it !== glyphs }?.recycle()
                    plateBoxes = ArrayList()
                    plateBackdrop = renderMaker(content, item.verse, options, interval.word, 1080, 1920,
                        0f, verseStartUs, totalUs, displayVerses = displayPageForAyah(content, item.verse.ayah),
                        activeAyah = item.verse.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                        lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.BACKDROP)
                    plateGlyphs = renderMaker(content, item.verse, options, interval.word, 1080, 1920,
                        0f, verseStartUs, totalUs, displayVerses = displayPageForAyah(content, item.verse.ayah),
                        activeAyah = item.verse.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                        lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER, plate = MakerPlate.GLYPHS,
                        wordBoxes = plateBoxes)
                    platePageKey = pageLines
                }
                val bitmap = if (plateBackdrop != null && plateGlyphs != null && frameBitmap != null && frameCanvas != null) {
                    frameCanvas.drawBitmap(plateBackdrop, 0f, 0f, null)
                    drawExportTracking(frameCanvas, plateBoxes, options, interval.word, item.verse.ayah, progress, 1080)
                    frameCanvas.drawBitmap(plateGlyphs, 0f, 0f, null)
                    frameBitmap
                } else {
                    renderMaker(content, item.verse, options, interval.word, 1080, 1920,
                        progress, verseStartUs + localUs, totalUs, displayVerses = displayPageForAyah(content, item.verse.ayah),
                        activeAyah = item.verse.ayah, oneAyahAtATime = false, lockedTextSize = textSize,
                        lineSpacing = ShareMushafLayout.VIDEO_LINE_MULTIPLIER)
                }
                frames += writeJpeg(bitmap, "maker-frame-${frames.size}.jpg", quality = 80)
                if (bitmap !== frameBitmap) bitmap.recycle()
                durations += interval.durationUs
                if (frames.size % 12 == 0) kotlinx.coroutines.yield()
            }
            if (plateBackdrop !== backdrop) plateBackdrop?.recycle()
            if (plateGlyphs !== glyphs) plateGlyphs?.recycle()
            backdrop?.recycle()
            glyphs?.recycle()
            verseStartUs += item.clip.durationUs
        }
        } finally {
            frameBitmap?.recycle()
        }
        val output = File(shareDir(), "quran-${content.surah}-${content.fromAyah}-${content.toAyah}-${System.currentTimeMillis()}.mp4")
        val combined = ShareAudioClip.concatenateMaker(clips, File(shareDir(), "maker-selection.wav"))
        File(shareDir(), "last-maker-timeline.json").writeText(timeline.toString(2))
        val encoded = File(shareDir(), "maker-encoded.mp4")
        if (encoded.exists()) encoded.delete()
        exportVideo(frames, listOf(combined), encoded, durations, motion = false, cleanAudio = true, videoBitrate = MAKER_VIDEO_BITRATE)
        val encoderDelayUs = ShareAudioClip.makerEncoderDelayUs(encoded, combined.file)
        File(shareDir(), "last-maker-sync.txt").writeText("measuredVideoDelayUs=$encoderDelayUs\n")
        MakerAvSync.align(encoded, output, encoderDelayUs)
        output
    }

    suspend fun saveMakerVideo(file: File): Uri = withContext(Dispatchers.IO) {
        ShareGallerySaver.saveVideo(context, file, file.name) ?: error("Could not save video")
    }

    fun shareMakerVideo(file: File, tikTok: Boolean = false) {
        if (!tikTok) { launchShare(file, "video/mp4", context.getString(R.string.app_name)); return }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newRawUri("Quran video", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val target = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill").firstOrNull { pkg ->
            intent.setPackage(pkg).resolveActivity(context.packageManager) != null
        }
        if (target != null) { intent.setPackage(target); context.startActivity(intent) }
        else launchShare(file, "video/mp4", context.getString(R.string.app_name))
    }

    suspend fun shareImagePreview(content: AyahShareContent): Bitmap = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        renderMaker(content,content.verses.first(),VideoMakerOptions(),null,1080,1920,displayVerses=content.verses)
    }

    suspend fun markerPreview(number: Int, color: Int = 0xFFD4B06A.toInt()): Bitmap = withContext(Dispatchers.IO) {
        val bitmap = createBitmap(420, 420)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (color == 0xFF000000.toInt()) 0xFFF8F3E8.toInt() else 0xFF161C24.toInt())
        HafsAyahMarker.draw(canvas, fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT,
            number, 210f, 210f, 310f, color)
        bitmap
    }

    suspend fun makeMakerImage(content: AyahShareContent, options: VideoMakerOptions): File = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val image = renderMaker(content, content.verses.first(), options, null, 1080, 1920, displayVerses=content.verses)
        writeJpeg(image, "quran-image-${System.currentTimeMillis()}.jpg").also { image.recycle() }
    }
    suspend fun saveMakerImage(file: File): Uri = withContext(Dispatchers.IO) {
        ShareGallerySaver.saveImage(context,file,file.name) ?: error("Could not save image")
    }

    private suspend fun renderMaker(
        content: AyahShareContent,
        verse: AyahShareVerse,
        options: VideoMakerOptions,
        activeWord: Int?,
        width: Int,
        height: Int,
        activeProgress: Float = 0f,
        elapsedUs: Long = 0L,
        totalUs: Long = 1L,
        displayVerses: List<AyahShareVerse> = listOf(verse),
        onGeometry: ((MakerPreviewGeometry) -> Unit)? = null,
        previewBackground: String? = null,
        activeAyah: Int? = verse.ayah,
        oneAyahAtATime: Boolean = false,
        lockedTextSize: Float? = null,
        lineSpacing: Float = ShareMushafLayout.VIDEO_LINE_MULTIPLIER,
        fitWidthFraction: Float = 0.82f,
        plate: MakerPlate = MakerPlate.FULL,
        wordBoxes: MutableList<ShareWordBox>? = null,
    ): Bitmap {
        val glyphsOnly = plate == MakerPlate.GLYPHS
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val backgrounds = options.backgrounds.ifEmpty { listOf(options.background) }
        val zoom = if (options.motion) ShareMushafLayout.kenBurnsScale(elapsedUs, totalUs) else 1f
        val focused = previewBackground?.takeIf { it in backgrounds }
        if (!glyphsOnly) {
        if (focused != null) {
            drawCoverBackground(canvas, focused, scale = zoom)
        } else {
            val mix = makerBackgroundMix(elapsedUs, totalUs, backgrounds.size)
            drawCoverBackground(canvas, backgrounds[mix.from], scale = zoom)
            if (mix.from != mix.to) drawCoverBackground(canvas, backgrounds[mix.to], (mix.blend * 255).toInt(), zoom)
        }
        drawDarkOverlay(canvas)
        if (options.vignette) drawVignette(canvas)
        }
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val tracking = options.trackingMode.coerceIn(0, 2)
        val maxWidth = (width * .82f).toInt()
        val uiLanguage = com.quransunah.app.core.ArabicRtl.currentTag(context)
        val localizedSurah = if (options.showTitle) {
            com.quransunah.app.ui.index.SurahNames.displayName(uiLanguage, content.surah, content.surahName)
        } else {
            ""
        }
        val ligature = if (options.showTitle) fontManager.surahNameLigature(content.surah).orEmpty() else ""
        val arabicTitle = uiLanguage.equals("ar", ignoreCase = true) && ligature.isNotEmpty()
        val titleFace = if (arabicTitle) fontManager.loadSurahTitleTypeface() else null
        val title = if (!options.showTitle) "" else if (arabicTitle) visualPua(ligature) else localizedSurah
        val titlePaint = ligaturePaint(
            if (arabicTitle) titleFace ?: hafs else Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD),
            if (arabicTitle) width * .10f * ShareMushafLayout.TITLE_SIZE_FACTOR else width * .048f,
            AYAH_MARKER_ACCENT,
        )
        if (!arabicTitle && title.isNotEmpty()) {
            titlePaint.textLocale = java.util.Locale.forLanguageTag(uiLanguage)
        }
        fitWidth(titlePaint, title, width * .62f, 16f)
        applyTextEffects(titlePaint, options, width)
        val titleBounds = Rect()
        if (title.isNotEmpty()) titlePaint.getTextBounds(title, 0, title.length, titleBounds)
        val titleInk = if (options.showTitle) maxOf(titleBounds.height().toFloat(), width * .062f) else 0f
        val titleFrameH = titleInk * (1f + 2f * ShareMushafLayout.TITLE_PAD_Y_FACTOR)
        // One ayah per page: keep only that ayah's words (by ayah number), reflow
        // to wrap width at the uniform size, paginate only if > MAX wrapped lines.
        val mushaf = if (content.mushafLines.isNotEmpty()) {
            val ayah = activeAyah ?: content.verses.firstOrNull()?.ayah
            if (ayah == null) {
                null
            } else {
                val source = ayahSourceLines(content.mushafLines, ayah)
                if (source.isEmpty()) {
                    null
                } else {
                    val size = lockedTextSize
                        ?: sharedAyahTextSize(content.mushafLines, options, width)
                    val wrapped = reflowMushafLines(
                        source,
                        options,
                        size,
                        width * ShareMushafLayout.VIDEO_WRAP_WIDTH,
                    )
                    selectWrappedAyahPage(wrapped, activeWord)
                }
            }
        } else {
            null
        }
        val bodyHeight: Float
        val bodyTop: Float
        val ayahTextSize: Float
        if (mushaf != null) {
            val measured = measureMushafBlock(mushaf, options, hafs, width, height, lockedTextSize, lineSpacing, fitWidthFraction)
            val titleHFrac = titleFrameH / height
            val ayahHFrac = measured.height / height
            // Center the single-ayah block in the text band (below title, above branding).
            val bandTop = if (options.showTitle) (options.titleY + titleHFrac / 2f + 0.02f) else 0.10f
            val bandBottom = if (options.translation) 0.78f else 0.86f
            val idealCenter = (bandTop + bandBottom) / 2f
            val geometry = makerVerticalPlacement(
                idealCenter.coerceIn(bandTop + ayahHFrac / 2f, bandBottom - ayahHFrac / 2f),
                options.titleY,
                ayahHFrac,
                titleHFrac,
                options.showTitle,
                options.translation,
            )
            onGeometry?.invoke(geometry)
            bodyTop = geometry.ayahCenterY * height - measured.height / 2f
            bodyHeight = measured.height
            ayahTextSize = measured.textSize
            if (options.textBackdrop && !glyphsOnly) drawTextBackdrop(canvas, bodyTop, bodyTop + bodyHeight)
            if (plate != MakerPlate.BACKDROP) {
            drawMushafBlock(
                canvas, mushaf, options, hafs, width, bodyTop, measured.textSize, tracking,
                activeWord, activeAyah, activeProgress, lineSpacing, measured.pad,
                drawHighlight = plate == MakerPlate.FULL,
                wordBoxes = wordBoxes,
            )
            }
        } else {
            val pageFace = if (options.font == 0) fontManager.loadPageTypeface(verse.pageNumber, night = true) else null
            val qcf = options.font == 0 && verse.qcfText.any { it.code in 0xFB50..0xFEFF || it.code in 0xE000..0xF8FF } && pageFace != Typeface.DEFAULT
            val face = if (qcf) pageFace!! else if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
            val words = (if (qcf) verse.qcfText else verse.uthmanicText).trim().split(Regex("\\s+"))
            val starts = words.indices.map { index -> words.take(index).sumOf { it.length + 1 } }
            val text = android.text.SpannableStringBuilder()
            displayVerses.forEachIndexed { index, item ->
                val itemPage = if (options.font == 0) fontManager.loadPageTypeface(item.pageNumber, night = true) else null
                val itemQcf = options.font == 0 && item.qcfText.any { it.code in 0xFB50..0xFEFF || it.code in 0xE000..0xF8FF } && itemPage != Typeface.DEFAULT
                val itemFace = if (itemQcf) itemPage!! else if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
                val itemWords = (if (itemQcf) item.qcfText else item.uthmanicText).trim().split(Regex("\\s+"))
                val begin = text.length
                text.append(itemWords.joinToString(" "))
                text.setSpan(object : android.text.style.MetricAffectingSpan() {
                    override fun updateDrawState(tp: TextPaint) {
                        tp.typeface = itemFace
                        applyAyahPaint(tp, options)
                    }
                    override fun updateMeasureState(tp: TextPaint) { tp.typeface = itemFace }
                }, begin, text.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (options.marker) HafsAyahMarker.appendInline(text, hafs, item.ayah, options.markerColor, itemWords.last(), itemFace)
                if (index < displayVerses.lastIndex) text.append(" ")
            }
            if (activeWord != null && tracking == ShareMushafLayout.TRACKING_CUMULATIVE) {
                val currentAyah = activeAyah ?: verse.ayah
                var cursor = 0
                displayVerses.forEach { item ->
                    val itemWords = item.uthmanicText.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
                    itemWords.indices.forEach { index ->
                        val recited = item.ayah < currentAyah || (item.ayah == currentAyah && index < activeWord)
                        if (recited) {
                            val start = cursor
                            val end = cursor + itemWords[index].length
                            if (end <= text.length) {
                                text.setSpan(object : android.text.style.CharacterStyle() {
                                    override fun updateDrawState(tp: TextPaint) {
                                        tp.colorFilter = PorterDuffColorFilter(options.highlightColor, PorterDuff.Mode.SRC_IN)
                                    }
                                }, start, end, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            }
                        }
                        cursor += itemWords[index].length + 1
                    }
                }
            }
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = face
                textSize = width * ShareMushafLayout.VIDEO_FIXED_WIDTH_FRACTION *
                    if (oneAyahAtATime) ShareMushafLayout.VIDEO_ARABIC_SIZE_FACTOR else 1f
                applyAyahPaint(this, options)
                applyTextEffects(this, options, width)
            }
            fun layout() = android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, maxWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL)
                .setLineSpacing(if (lineSpacing > 1.2f) paint.textSize * 0.45f else 0f, 1f)
                .setIncludePad(false)
                .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE)
                .build()
            var body = layout()
            while (body.height > height * .48f && paint.textSize > width * .025f) {
                paint.textSize *= .9f
                body = layout()
            }
            val visibleWidth = (0 until body.lineCount).maxOf { body.getLineRight(it) - body.getLineLeft(it) }
            val bodyX = (width * options.ayahX).coerceIn(visibleWidth / 2f, width - visibleWidth / 2f) - maxWidth / 2f
            val geometry = makerVerticalPlacement(
                options.ayahY, options.titleY, body.height.toFloat() / height,
                titleFrameH / height, options.showTitle, options.translation,
            )
            onGeometry?.invoke(geometry)
            bodyTop = geometry.ayahCenterY * height - body.height / 2f
            bodyHeight = body.height.toFloat()
            ayahTextSize = paint.textSize
            if (options.textBackdrop) drawTextBackdrop(canvas, bodyTop, bodyTop + bodyHeight)
            canvas.save()
            canvas.translate(bodyX, bodyTop)
            if (activeWord != null && tracking in listOf(ShareMushafLayout.TRACKING_BAND, ShareMushafLayout.TRACKING_UNDERLINE)) {
                drawWrappedTracking(canvas, body, words, starts, activeWord, activeProgress, tracking, options, width, paint)
            }
            body.draw(canvas)
            canvas.restore()
        }
        if (options.showTitle && !glyphsOnly) {
            drawSurahTitle(
                canvas, title, titlePaint, titleBounds,
                width * options.titleX,
                height * makerVerticalPlacement(
                    options.ayahY, options.titleY, bodyHeight / height, titleFrameH / height,
                    options.showTitle, options.translation,
                ).titleCenterY,
                options.titleFrame, width,
            )
        }
        val english = displayVerses.mapNotNull { it.englishTranslation }.joinToString("\n")
        if (!glyphsOnly && options.translation && english.isNotBlank()) {
            val en = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TEXT_WHITE
                textSize = width * .026f * 1.25f
                typeface = Typeface.SANS_SERIF
            }
            val translationRtl = com.quransunah.app.core.localeIsRtl(content.translationTag)
            val translation = android.text.StaticLayout.Builder.obtain(english, 0, english.length, en, maxWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(
                    if (translationRtl) android.text.TextDirectionHeuristics.RTL
                    else android.text.TextDirectionHeuristics.LTR,
                )
                .setLineSpacing(en.textSize * 0.28f, 1f)
                .build()
            canvas.save()
            canvas.translate(
                (width * options.ayahX - maxWidth / 2f).coerceIn(0f, (width - maxWidth).toFloat()),
                bodyTop + bodyHeight + ayahTextSize * 0.70f,
            )
            translation.draw(canvas)
            canvas.restore()
        }
        if (!glyphsOnly) drawBranding(canvas)
        return bitmap
    }

    private fun applyAyahPaint(paint: TextPaint, options: VideoMakerOptions) {
        if (options.tajweedText && options.font == 0) {
            paint.colorFilter = null
            paint.color = TEXT_WHITE
        } else {
            paint.color = TEXT_WHITE
            paint.colorFilter = PorterDuffColorFilter(TEXT_WHITE, PorterDuff.Mode.SRC_IN)
        }
    }

    private fun applyTextEffects(paint: TextPaint, options: VideoMakerOptions, width: Int) {
        when {
            options.glow -> paint.setShadowLayer(width * .014f, 0f, 0f, options.highlightColor)
            options.shadow -> paint.setShadowLayer(width * .012f, 0f, width * .006f, 0xCC000000.toInt())
        }
    }

    private data class MushafMeasure(val height: Float, val textSize: Float, val pad: Float = 0f)

    private suspend fun measureMushafBlock(
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        @Suppress("UNUSED_PARAMETER") hafs: Typeface,
        width: Int,
        height: Int,
        lockedTextSize: Float? = null,
        lineSpacing: Float = ShareMushafLayout.VIDEO_LINE_MULTIPLIER,
        fitWidthFraction: Float = 0.82f,
    ): MushafMeasure {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        suspend fun block(size: Float): MushafMeasure {
            val lineH = metricsLineHeight(lines, options, size, paint, lineSpacing)
            val pad = size * 0.28f
            return MushafMeasure(lines.size * lineH + pad * 2f, size, pad)
        }
        // Uniform size for the whole selection (widest-line fit). Do not resize per page.
        val size = lockedTextSize ?: sharedAyahTextSize(lines, options, width)
        return block(size)
    }

    private suspend fun metricsAscent(
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        textSize: Float,
        paint: TextPaint,
    ): Float {
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val fallback = if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
        var maxAscent = 0f
        val faces = linkedSetOf<Typeface>()
        for (line in lines) {
            for (word in line.words) {
                if (word.isMarker) continue
                val page = word.sourcePage.takeIf { it > 0 } ?: line.pageNumber
                val pageFace = if (options.font == 0) fontManager.loadPageTypeface(page, night = true) else null
                val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
                faces += face
            }
        }
        if (faces.isEmpty()) faces += fallback
        for (face in faces) {
            paint.typeface = face
            paint.textSize = textSize
            maxAscent = maxOf(maxAscent, -paint.fontMetrics.ascent)
        }
        return maxOf(maxAscent, textSize)
    }

    /** Line pitch from real QCF page-face metrics (ascent+descent, tashkeel). */

    private suspend fun metricsLineHeight(
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        textSize: Float,
        paint: TextPaint,
        fallbackMultiplier: Float,
    ): Float {
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val fallback = if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
        var maxSpan = 0f
        val faces = linkedSetOf<Typeface>()
        for (line in lines) {
            for (word in line.words) {
                if (word.isMarker) continue
                val page = word.sourcePage.takeIf { it > 0 } ?: line.pageNumber
                val pageFace = if (options.font == 0) fontManager.loadPageTypeface(page, night = true) else null
                val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
                faces += face
            }
        }
        if (faces.isEmpty()) faces += fallback
        for (face in faces) {
            paint.typeface = face
            paint.textSize = textSize
            val fm = paint.fontMetrics
            maxSpan = maxOf(maxSpan, fm.descent - fm.ascent)
        }
        // Also probe real glyph bounds — QCF tashkeel often exceeds fontMetrics.
        val bounds = android.graphics.Rect()
        for (line in lines) {
            for (word in line.words) {
                if (word.isMarker || word.qcf.isBlank()) continue
                val page = word.sourcePage.takeIf { it > 0 } ?: line.pageNumber
                val pageFace = if (options.font == 0) fontManager.loadPageTypeface(page, night = true) else null
                val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
                paint.typeface = face
                paint.textSize = textSize
                val shown = if (options.font == 0) visualPua(word.qcf) else word.uthmanic
                if (shown.isEmpty()) continue
                paint.getTextBounds(shown, 0, shown.length, bounds)
                maxSpan = maxOf(maxSpan, bounds.height().toFloat())
            }
        }
        val floor = textSize * fallbackMultiplier
        val leading = textSize * ShareMushafLayout.VIDEO_METRICS_LEADING
        return maxOf(maxSpan + leading, floor)
    }

    private suspend fun drawMushafBlock(
        canvas: Canvas,
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        hafs: Typeface,
        width: Int,
        top: Float,
        textSize: Float,
        tracking: Int,
        activeWord: Int?,
        activeAyah: Int?,
        activeProgress: Float,
        lineSpacing: Float = ShareMushafLayout.VIDEO_LINE_MULTIPLIER,
        blockPad: Float = 0f,
        drawHighlight: Boolean = true,
        wordBoxes: MutableList<ShareWordBox>? = null,
    ) {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.textSize = textSize
            textAlign = Paint.Align.LEFT
            applyAyahPaint(this, options)
            applyTextEffects(this, options, width)
        }
        val lineH = metricsLineHeight(lines, options, textSize, paint, lineSpacing)
        data class PlannedGlyph(
            val word: ShareMushafWord,
            val shown: String,
            val face: Typeface,
            val x: Float,
            val width: Float,
            val isMarker: Boolean,
            val bodyIndex: Int,
            val line: Int,
            val baseline: Float,
        )
        data class Piece(val word: ShareMushafWord, val shown: String, val width: Float, val marker: Boolean)
        val planned = ArrayList<PlannedGlyph>()
        val boxes = ArrayList<ShareWordBox>()
        var bodyIndex = 0
        lines.forEachIndexed { lineIndex, line ->
            val fallback = if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
            // Place baseline so the tallest QCF ascent on this block sits inside the line box.
            val baseline = top + blockPad + lineIndex * lineH + metricsAscent(lines, options, textSize, paint)
            val gap = ShareMushafLayout.wordGap(textSize, options.font == 0)
            val visible = line.words.filter { options.marker || !it.isMarker }
            val pieces = visible.map { word ->
                val page = word.sourcePage.takeIf { it > 0 } ?: line.pageNumber
                val pageFace = if (options.font == 0) fontManager.loadPageTypeface(page, night = true) else null
                val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
                paint.typeface = face
                if (word.isMarker) {
                    Piece(word, "", textSize * .90f * 1.05f, true) to face
                } else {
                    val glyph = if (options.font == 0) word.qcf.ifBlank { word.uthmanic } else word.uthmanic
                    val shown = if (options.font == 0) visualPua(glyph) else glyph
                    Piece(word, shown, paint.measureText(shown), false) to face
                }
            }
            val ink = pieces.fold(0f) { sum, (piece, _) -> sum + piece.width } +
                gap * (pieces.size - 1).coerceAtLeast(0)
            var cursor = (width + ink) / 2f
            pieces.forEachIndexed { index, (piece, face) ->
                cursor -= piece.width
                if (piece.marker) {
                    planned += PlannedGlyph(piece.word, "", hafs, cursor, piece.width, true, -1, lineIndex, baseline)
                } else {
                    planned += PlannedGlyph(piece.word, piece.shown, face, cursor, piece.width, false, bodyIndex, lineIndex, baseline)
                    boxes += ShareWordBox(
                        index = bodyIndex,
                        ayah = piece.word.ayah,
                        position = piece.word.position,
                        line = lineIndex,
                        left = cursor,
                        right = cursor + piece.width,
                        top = baseline - textSize,
                        bottom = baseline + textSize * .25f,
                        baseline = baseline,
                        descent = paint.fontMetrics.descent,
                    )
                    bodyIndex += 1
                }
                if (index < pieces.lastIndex) cursor -= gap
            }
        }
        wordBoxes?.addAll(boxes)
        val currentIndex = when {
            activeWord == null || activeAyah == null -> -1
            else -> ShareMushafLayout.globalWordIndex(lines, activeAyah, activeWord)
        }
        if (drawHighlight && currentIndex >= 0 && tracking == ShareMushafLayout.TRACKING_BAND) {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = options.highlightColor; alpha = 90 }
            boxes.groupBy { it.line }.values.forEach { lineBoxes ->
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                var bandTop = Float.POSITIVE_INFINITY
                var bandBottom = Float.NEGATIVE_INFINITY
                lineBoxes.forEach { box ->
                    val fraction = wordReciteFraction(box.ayah, box.position, activeAyah, activeWord, activeProgress)
                    if (fraction <= 0f) return@forEach
                    val edge = box.right
                    val start = edge - box.width * fraction
                    if (edge > right) right = edge
                    if (start < left) left = start
                    if (box.top < bandTop) bandTop = box.top
                    if (box.bottom > bandBottom) bandBottom = box.bottom
                }
                if (left < right) {
                    val radius = (bandBottom - bandTop) * 0.18f
                    canvas.drawRoundRect(android.graphics.RectF(left, bandTop, right, bandBottom), radius, radius, band)
                }
            }
        }
        if (drawHighlight && currentIndex >= 0 && tracking == ShareMushafLayout.TRACKING_UNDERLINE) {
            val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = options.highlightColor
                strokeWidth = width * .006f
                strokeCap = Paint.Cap.ROUND
            }
            ShareMushafLayout.cumulativeUnderlines(boxes, currentIndex, activeProgress).forEach { span ->
                canvas.drawLine(span.right, span.y, span.left, span.y, underline)
            }
        }
        planned.forEach { item ->
            if (item.isMarker) {
                HafsAyahMarker.draw(
                    canvas, item.face, item.word.ayah,
                    item.x + item.width / 2f, item.baseline - textSize * .25f, textSize * .90f, options.markerColor,
                )
            } else {
                paint.typeface = item.face
                val recited = isWordRecited(item.word.ayah, item.word.position, activeAyah, activeWord)
                if (tracking == ShareMushafLayout.TRACKING_CUMULATIVE && recited) {
                    val coloured = TextPaint(paint).apply {
                        colorFilter = PorterDuffColorFilter(options.highlightColor, PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawText(item.shown, item.x, item.baseline, coloured)
                } else {
                    canvas.drawText(item.shown, item.x, item.baseline, paint)
                }
            }
        }
    }

    private fun drawExportTracking(
        canvas: Canvas,
        boxes: List<ShareWordBox>,
        options: VideoMakerOptions,
        activeWord: Int?,
        activeAyah: Int,
        progress: Float,
        width: Int,
    ) {
        if (boxes.isEmpty() || activeWord == null) return
        val tracking = options.trackingMode
        if (tracking == ShareMushafLayout.TRACKING_BAND) {
            val band = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = options.highlightColor; alpha = 90 }
            boxes.groupBy { it.line }.values.forEach { lineBoxes ->
                var left = Float.POSITIVE_INFINITY
                var right = Float.NEGATIVE_INFINITY
                var bandTop = Float.POSITIVE_INFINITY
                var bandBottom = Float.NEGATIVE_INFINITY
                lineBoxes.forEach { box ->
                    val fraction = wordReciteFraction(box.ayah, box.position, activeAyah, activeWord, progress)
                    if (fraction <= 0f) return@forEach
                    val edge = box.right
                    val start = edge - box.width * fraction
                    if (edge > right) right = edge
                    if (start < left) left = start
                    if (box.top < bandTop) bandTop = box.top
                    if (box.bottom > bandBottom) bandBottom = box.bottom
                }
                if (left < right) {
                    val radius = (bandBottom - bandTop) * 0.18f
                    canvas.drawRoundRect(android.graphics.RectF(left, bandTop, right, bandBottom), radius, radius, band)
                }
            }
        } else if (tracking == ShareMushafLayout.TRACKING_UNDERLINE) {
            val currentIndex = boxes.indexOfFirst { it.ayah == activeAyah && it.position == activeWord }
                .let { found -> if (found >= 0) found else boxes.indexOfLast { it.ayah < activeAyah }.coerceAtLeast(0) }
            val underline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = options.highlightColor
                strokeWidth = width * .006f
                strokeCap = Paint.Cap.ROUND
            }
            ShareMushafLayout.cumulativeUnderlines(boxes, currentIndex, progress).forEach { span ->
                canvas.drawLine(span.right, span.y, span.left, span.y, underline)
            }
        }
    }

    private fun isWordRecited(ayah: Int, position: Int, activeAyah: Int?, activeWord: Int?): Boolean {
        if (activeAyah == null || activeWord == null) return false
        return ayah < activeAyah || (ayah == activeAyah && position < activeWord)
    }

    private fun wordReciteFraction(ayah: Int, position: Int, activeAyah: Int?, activeWord: Int?, progress: Float): Float {
        if (activeAyah == null || activeWord == null) return 0f
        return when {
            ayah < activeAyah || (ayah == activeAyah && position < activeWord) -> 1f
            ayah == activeAyah && position == activeWord -> progress.coerceIn(0f, 1f)
            else -> 0f
        }
    }

    private fun drawWrappedTracking(
        canvas: Canvas,
        body: android.text.Layout,
        words: List<String>,
        starts: List<Int>,
        activeWord: Int,
        activeProgress: Float,
        tracking: Int,
        options: VideoMakerOptions,
        width: Int,
        paint: TextPaint,
    ) {
        val trackingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = options.highlightColor
            strokeWidth = width * .006f
            strokeCap = Paint.Cap.ROUND
        }
        val filledLines = linkedMapOf<Int, android.graphics.RectF>()
        val underlineBoxes = ArrayList<ShareWordBox>()
        words.indices.forEach { index ->
            val complete = index < activeWord - 1
            val current = index == activeWord - 1
            if (!complete && !current) return@forEach
            val path = android.graphics.Path()
            val bounds = android.graphics.RectF()
            val line = body.getLineForOffset(starts[index])
            val next = starts.getOrNull(index + 1)
            val end = starts[index] + words[index].length +
                if (tracking == ShareMushafLayout.TRACKING_BAND && next != null && body.getLineForOffset(next) == line) 1 else 0
            body.getSelectionPath(starts[index], end, path)
            path.computeBounds(bounds, true)
            val fraction = if (complete) 1f else activeProgress.coerceIn(0f, 1f)
            if (tracking == ShareMushafLayout.TRACKING_BAND) {
                val filled = android.graphics.RectF(bounds.right - bounds.width() * fraction, bounds.top, bounds.right, bounds.bottom)
                filledLines[line]?.union(filled) ?: run { filledLines[line] = filled }
            } else {
                underlineBoxes += ShareWordBox(
                    index = index,
                    ayah = 0,
                    position = index + 1,
                    line = line,
                    left = bounds.left,
                    right = bounds.right,
                    top = bounds.top,
                    bottom = bounds.bottom,
                    baseline = body.getLineBaseline(line).toFloat(),
                    descent = paint.fontMetrics.descent + width * .008f,
                )
            }
        }
        if (tracking == ShareMushafLayout.TRACKING_BAND) {
            trackingPaint.alpha = 90
            filledLines.values.forEach { canvas.drawRoundRect(it, paint.textSize * .10f, paint.textSize * .10f, trackingPaint) }
        } else {
            val current = (activeWord - 1).coerceAtLeast(0)
            ShareMushafLayout.cumulativeUnderlines(underlineBoxes, current, activeProgress).forEach { span ->
                canvas.drawLine(span.right, span.y, span.left, span.y, trackingPaint)
            }
        }
    }

    private fun drawSurahTitle(
        canvas: Canvas,
        title: String,
        paint: TextPaint,
        bounds: Rect,
        centerX: Float,
        centerY: Float,
        frame: Int,
        width: Int,
    ) {
        if (title.isEmpty()) return
        val textW = paint.measureText(title)
        val textH = maxOf(bounds.height().toFloat(), paint.textSize)
        val padX = width * .03f
        val padY = textH * ShareMushafLayout.TITLE_PAD_Y_FACTOR
        val left = centerX - textW / 2f - padX
        val right = centerX + textW / 2f + padX
        val top = centerY - textH / 2f - padY
        val bottom = centerY + textH / 2f + padY
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AYAH_MARKER_ACCENT
            style = Paint.Style.STROKE
            strokeWidth = width * .0045f
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x66000000.toInt() }
        when (frame) {
            1 -> {
                val rect = android.graphics.RectF(left, top, right, bottom)
                canvas.drawRoundRect(rect, width * .012f, width * .012f, fill)
                canvas.drawRoundRect(rect, width * .012f, width * .012f, gold)
                val inset = android.graphics.RectF(left + width * .008f, top + width * .008f, right - width * .008f, bottom - width * .008f)
                gold.strokeWidth = width * .0022f
                canvas.drawRoundRect(inset, width * .008f, width * .008f, gold)
            }
            2 -> {
                val oval = android.graphics.RectF(left, top, right, bottom)
                canvas.drawOval(oval, fill)
                canvas.drawOval(oval, gold)
                gold.strokeWidth = width * .0022f
                canvas.drawOval(android.graphics.RectF(left + width * .01f, top + width * .01f, right - width * .01f, bottom - width * .01f), gold)
            }
            else -> {
                val path = plaquePath(left, top, right, bottom)
                canvas.drawPath(path, fill)
                canvas.drawPath(path, gold)
                gold.strokeWidth = width * .0022f
                canvas.drawPath(plaquePath(left + width * .01f, top + width * .01f, right - width * .01f, bottom - width * .01f), gold)
            }
        }
        canvas.drawText(title, centerX, centerY - (bounds.top + bounds.bottom) / 2f, paint)
    }

    private fun plaquePath(left: Float, top: Float, right: Float, bottom: Float): android.graphics.Path {
        val h = (bottom - top).coerceAtLeast(1f)
        val tip = h * 0.42f
        val mid = (top + bottom) / 2f
        return android.graphics.Path().apply {
            moveTo(left, mid)
            quadTo(left + tip * 0.35f, top, left + tip, top)
            lineTo(right - tip, top)
            quadTo(right - tip * 0.35f, top, right, mid)
            quadTo(right - tip * 0.35f, bottom, right - tip, bottom)
            lineTo(left + tip, bottom)
            quadTo(left + tip * 0.35f, bottom, left, mid)
            close()
        }
    }

    private fun drawVignette(canvas: Canvas) {
        val width = canvas.width.toFloat()
        val height = canvas.height.toFloat()
        val paint = Paint()
        paint.shader = RadialGradient(
            width / 2f,
            height / 2f,
            maxOf(width, height) * 0.72f,
            intArrayOf(0x00000000, 0x99000000.toInt()),
            floatArrayOf(0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawBranding(canvas: Canvas) {
        val width=canvas.width.toFloat();val height=canvas.height.toFloat()
        val stripHeight=height*.065f
        canvas.drawRect(0f,height-stripHeight,width,height,Paint().apply { color=0xAA111923.toInt() })
        val iconSize=stripHeight*.68f
        val label=promotionalWatermarkLabel()
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textSize=width*.028f; color=0xFFE3C98C.toInt(); textLocale=java.util.Locale.forLanguageTag("ar") }
        val textWidth=kotlin.math.ceil(paint.measureText(label).toDouble()).toInt()+2
        val text=android.text.StaticLayout.Builder.obtain(label,0,label.length,paint,textWidth)
            .setTextDirection(android.text.TextDirectionHeuristics.RTL).setIncludePad(false).build()
        val total=textWidth+iconSize+width*.02f;val left=(width-total)/2f;val cy=height-stripHeight/2f
        canvas.save();canvas.translate(left,cy-text.height/2f);text.draw(canvas);canvas.restore()
        val x=left+textWidth+width*.02f
        val destination=android.graphics.RectF(x,cy-iconSize/2f,x+iconSize,cy+iconSize/2f)
        val path=android.graphics.Path().apply { addRoundRect(destination,iconSize*.18f,iconSize*.18f,android.graphics.Path.Direction.CW) }
        canvas.save();canvas.clipPath(path)
        canvas.drawBitmap(brandingBitmap,coverSrcRect(brandingBitmap.width,brandingBitmap.height,iconSize.toInt(),iconSize.toInt()),destination,Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    }

    private suspend fun renderSlide(
        content: AyahShareContent,
        verse: AyahShareVerse,
        mode: SharePaintMode,
        backgroundAsset: String,
        canvasWidth: Int = SHARE_WIDTH,
        canvasHeight: Int = SHARE_HEIGHT,
    ): Bitmap {
        val titleFace = fontManager.loadSurahTitleTypeface()
            ?: fontManager.loadUthmanicTypeface()
            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)
        // Share art is always light text on a dark photo, so use the dark tajweed palette (white base layer).
        val pageFace = fontManager.loadPageTypeface(verse.pageNumber, night = true)
        val uthmanicFace = fontManager.loadUthmanicTypeface()
            ?: Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val bitmap = createBitmap(canvasWidth, canvasHeight)
        val canvas = Canvas(bitmap)
        drawCoverBackground(canvas, backgroundAsset)
        drawDarkOverlay(canvas)

        val surahLigature = fontManager.surahNameLigature(content.surah).orEmpty()
        val surahTitle = if (fontManager.canDrawLigature(surahLigature, titleFace)) {
            visualPua(surahLigature)
        } else {
            content.surahName.ifBlank {
                context.getString(R.string.surah_fallback, EasternArabic.format(content.surah))
            }
        }
        val showBasmalah = when (mode) {
            SharePaintMode.AYAH, SharePaintMode.INTRO -> false
            SharePaintMode.BASMALAH, SharePaintMode.FULL -> content.shouldShowBasmalah()
        }
        val basmalah = if (showBasmalah) fontManager.displayBasmalahLigature() else ""
        val qcfBody = verse.qcfText.trim()
        val markerRaw = verse.markerLigature?.takeIf { it.isNotBlank() }
        val pageFaceReady = pageFace != Typeface.DEFAULT && pageFace != uthmanicFace &&
            // QPC V4 page glyphs live in U+FB50..U+FEFF (older QCF builds used the PUA)
            qcfBody.any { it.code in 0xE000..0xF8FF || it.code in 0xFB50..0xFEFF } &&
            fontManager.canDrawLigature(qcfBody, pageFace) &&
            (markerRaw == null || fontManager.canDrawLigature(markerRaw, pageFace))
        val usePageFace = pageFaceReady
        val ayahSource = when {
            usePageFace -> qcfBody
            verse.uthmanicText.isNotBlank() -> verse.uthmanicText
            else -> qcfBody
        }
        val ayahFace = if (usePageFace) pageFace else uthmanicFace
        val markerLigature = if (usePageFace) markerRaw else null
        val numberLabel = formatShareAyahNumberLabel(verse.ayah)

        when (mode) {
            SharePaintMode.INTRO -> drawIntroCopy(canvas, surahTitle, "", titleFace)
            SharePaintMode.BASMALAH -> drawIntroCopy(canvas, "", basmalah, titleFace)
            SharePaintMode.AYAH -> drawAyahCopy(
                canvas,
                ayahSource,
                markerLigature,
                verse.ayah,
                numberLabel,
                ayahFace,
                uthmanicFace,
                reverseAyah = usePageFace,
            )
            SharePaintMode.FULL -> drawFullCopy(
                canvas,
                surahTitle,
                basmalah,
                ayahSource,
                markerLigature,
                verse.ayah,
                numberLabel,
                titleFace,
                ayahFace,
                uthmanicFace,
                reverseAyah = usePageFace,
            )
        }
        drawBranding(canvas)
        return bitmap
    }

    private fun drawCoverBackground(canvas: Canvas, fileName: String, alpha: Int = 255, scale: Float = 1f) {
        val width = canvas.width
        val height = canvas.height
        val cacheKey = "$fileName-$width-$height"
        val source = backgroundCache.get(cacheKey) ?: runCatching {
            context.assets.open(ShareBackgrounds.assetPath(fileName)).use { stream ->
                decodeSampledBitmap(stream, width, height)
            }
        }.getOrNull()
        if (source == null) {
            canvas.drawColor(0xFF1A140F.toInt())
            return
        }
        backgroundCache.put(cacheKey,source)
        val src = zoomedCoverSrc(source.width, source.height, width, height, scale)
        val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.alpha = alpha }
        canvas.drawBitmap(source, src, Rect(0, 0, width, height), filter)

    }

    private fun drawDarkOverlay(canvas: Canvas) {
        val width = canvas.width
        val height = canvas.height
        val paint = Paint()
        paint.shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(OVERLAY_TOP, OVERLAY_MID, OVERLAY_TOP),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    }

    private fun drawIntroCopy(
        canvas: Canvas,
        surahTitle: String,
        basmalah: String,
        titleFace: Typeface,
    ) {
        val width = canvas.width
        val height = canvas.height
        val maxWidth = width * 0.80f
        val surahPaint = ligaturePaint(titleFace, width * 0.13f, TEXT_WHITE)
        if (surahTitle.isNotEmpty()) fitWidth(surahPaint, surahTitle, maxWidth, 36f)
        val basmalahPaint = ligaturePaint(titleFace, width * 0.10f, BASMALAH_GOLD)
        if (basmalah.isNotEmpty()) fitWidth(basmalahPaint, basmalah, maxWidth, 32f)
        val gap = height * 0.016f
        val surahH = if (surahTitle.isEmpty()) 0f else lineHeight(surahPaint)
        val basmalahH = if (basmalah.isEmpty()) 0f else lineHeight(basmalahPaint)
        val block = surahH +
            if (basmalahH > 0f && surahH > 0f) gap + basmalahH else basmalahH
        var y = height * 0.46f - block / 2f
        if (surahTitle.isNotEmpty()) {
            drawCentered(canvas, surahTitle, y + surahH * 0.55f, surahPaint)
            y += surahH
        }
        if (basmalah.isNotEmpty()) {
            if (surahH > 0f) y += gap
            drawCentered(canvas, basmalah, y + basmalahH * 0.55f, basmalahPaint)
        }
    }

    private fun drawAyahCopy(
        canvas: Canvas,
        ayahSource: String,
        markerLigature: String?,
        ayahNumber: Int,
        numberLabel: String,
        ayahFace: Typeface,
        numberFace: Typeface,
        reverseAyah: Boolean,
    ) {
        val width = canvas.width
        val height = canvas.height
        val maxWidth = width * 0.80f
        val maxAyahHeight = height * 0.52f
        val wordCount = ayahSource.split(Regex("\\s+")).count { it.isNotBlank() }
        val ayahPaint = ligaturePaint(ayahFace, ayahFontSize(wordCount, width), TEXT_WHITE)
            .apply { colorFilter = PorterDuffColorFilter(TEXT_WHITE, PorterDuff.Mode.SRC_IN) } // single white tone, no COLR tajweed colours
        val lines = wrapFitted(ayahPaint, ayahSource, maxWidth, maxAyahHeight)
        val numberPaint = ligaturePaint(numberFace, width * 0.038f, TEXT_WHITE)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val gap = height * 0.014f
        val block = ayahH + gap + numberH
        var y = (height * 0.40f - block / 2f).coerceAtLeast(height * 0.14f)
        drawTextBackdrop(canvas, y, y + block)
        y = drawAyahTextLines(
            canvas = canvas,
            lines = lines,
            markerLigature = markerLigature,
            ayahNumber = ayahNumber,
            ayahPaint = ayahPaint,
            numberFace = numberFace,
            reverseAyah = reverseAyah,
            startY = y,
        )
        y += gap
        drawCentered(canvas, numberLabel, y + numberH * 0.55f, numberPaint)
    }

    private fun drawFullCopy(
        canvas: Canvas,
        surahTitle: String,
        basmalah: String,
        ayahSource: String,
        markerLigature: String?,
        ayahNumber: Int,
        numberLabel: String,
        titleFace: Typeface,
        ayahFace: Typeface,
        numberFace: Typeface,
        reverseAyah: Boolean,
    ) {
        val width = canvas.width
        val height = canvas.height
        val maxWidth = width * 0.80f
        val surahPaint = ligaturePaint(titleFace, width * 0.12f, TEXT_WHITE)
        fitWidth(surahPaint, surahTitle, maxWidth, 36f)
        val basmalahPaint = ligaturePaint(titleFace, width * 0.072f, BASMALAH_GOLD)
        if (basmalah.isNotEmpty()) fitWidth(basmalahPaint, basmalah, maxWidth, 30f)
        val wordCount = ayahSource.split(Regex("\\s+")).count { it.isNotBlank() }
        val ayahPaint = ligaturePaint(ayahFace, ayahFontSize(wordCount, width), TEXT_WHITE)
            .apply { colorFilter = PorterDuffColorFilter(TEXT_WHITE, PorterDuff.Mode.SRC_IN) } // single white tone, no COLR tajweed colours
        val numberPaint = ligaturePaint(numberFace, width * 0.036f, TEXT_WHITE)
        val maxAyahHeight = height * 0.42f
        val lines = wrapFitted(ayahPaint, ayahSource, maxWidth, maxAyahHeight)
        val sectionGap = height * 0.012f
        val surahH = lineHeight(surahPaint)
        val basmalahH = if (basmalah.isEmpty()) 0f else lineHeight(basmalahPaint)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val block = surahH +
            (if (basmalahH > 0f) sectionGap + basmalahH else 0f) +
            sectionGap + ayahH + sectionGap + numberH
        var y = (height * 0.40f - block / 2f).coerceIn(height * 0.08f, height * 0.78f - block)
        drawTextBackdrop(canvas, y, y + block)

        drawCentered(canvas, surahTitle, y + surahH * 0.55f, surahPaint)
        y += surahH
        if (basmalah.isNotEmpty()) {
            y += sectionGap
            drawCentered(canvas, basmalah, y + basmalahH * 0.55f, basmalahPaint)
            y += basmalahH
        }
        y += sectionGap
        y = drawAyahTextLines(
            canvas = canvas,
            lines = lines,
            markerLigature = markerLigature,
            ayahNumber = ayahNumber,
            ayahPaint = ayahPaint,
            numberFace = numberFace,
            reverseAyah = reverseAyah,
            startY = y,
        )
        y += sectionGap
        drawCentered(canvas, numberLabel, y + numberH * 0.55f, numberPaint)
    }

    /** Mirrors React `drawShareAyahTextLines`: Medina mark on the last line, else Eastern digits. */
    private fun drawAyahTextLines(
        canvas: Canvas,
        lines: List<String>,
        markerLigature: String?,
        ayahNumber: Int,
        ayahPaint: TextPaint,
        numberFace: Typeface,
        reverseAyah: Boolean,
        startY: Float,
    ): Float {
        val lineH = lineHeight(ayahPaint)
        var y = startY
        lines.forEachIndexed { index, line ->
            val baseline = y + lineH * 0.55f
            val isLast = index == lines.lastIndex
            when {
                isLast && !markerLigature.isNullOrBlank() && reverseAyah ->
                    drawAyahLineWithMarker(canvas, line, markerLigature, baseline, ayahPaint, ayahNumber, numberFace)
                isLast ->
                    drawAyahLineWithEndNumber(
                        canvas = canvas,
                        bodyText = if (reverseAyah) visualPua(line) else line,
                        ayahNumber = ayahNumber,
                        baseline = baseline,
                        bodyPaint = ayahPaint,
                        numberFace = numberFace,
                    )
                else -> {
                    val shown = if (reverseAyah) visualPua(line) else line
                    drawCentered(canvas, shown, baseline, ayahPaint)
                }
            }
            y += lineH
        }
        return y
    }

    private fun drawAyahLineWithMarker(
        canvas: Canvas,
        bodyText: String,
        markerLigature: String,
        baseline: Float,
        bodyPaint: TextPaint,
        ayahNumber: Int,
        numberFace: Typeface,
    ) {
        drawAyahLineWithEndNumber(canvas, visualPua(bodyText), ayahNumber, baseline, bodyPaint, numberFace)
    }

    private fun drawAyahLineWithEndNumber(
        canvas: Canvas,
        bodyText: String,
        ayahNumber: Int,
        baseline: Float,
        bodyPaint: TextPaint,
        numberFace: Typeface,
    ) {
        val bodyBounds = Rect()
        if (bodyText.isNotEmpty()) bodyPaint.getTextBounds(bodyText, 0, bodyText.length, bodyBounds)
        val height = maxOf(bodyPaint.textSize * .8f, bodyBounds.height().toFloat()) * .9f
        val markerWidth = height * 1.05f
        val gap = bodyPaint.textSize * .20f
        val bodyWidth = bodyPaint.measureText(bodyText)
        val total = bodyWidth + if (bodyText.isEmpty()) 0f else gap + markerWidth
        val right = canvas.width / 2f + total / 2f
        if (bodyText.isNotEmpty()) canvas.drawText(bodyText, right - bodyWidth / 2f, baseline, bodyPaint)
        val markerX = if (bodyText.isEmpty()) canvas.width / 2f else right - bodyWidth - gap - markerWidth / 2f
        val markerY = baseline + if (bodyBounds.isEmpty) -height * .35f else (bodyBounds.top + bodyBounds.bottom) / 2f
        HafsAyahMarker.draw(canvas, numberFace, ayahNumber, markerX, markerY, height)
    }
    /** Semi-transparent black box behind the text block: background -> overlay -> text. */
    private fun drawTextBackdrop(canvas: Canvas, top: Float, bottom: Float) {
        val padX = canvas.width * 0.05f
        val padY = canvas.height * 0.025f
        val rect = android.graphics.RectF(padX, top - padY, canvas.width - padX, bottom + padY)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TEXT_BACKDROP }
        val r = canvas.width * 0.04f
        canvas.drawRoundRect(rect, r, r, paint)
    }

    private fun ligaturePaint(typeface: Typeface, size: Float, color: Int): TextPaint {
        return TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = size
            this.color = color
            textAlign = Paint.Align.CENTER
            isLinearText = true
            setShadowLayer(10f, 0f, 3f, 0xCC000000.toInt())
        }
    }

    private fun drawCentered(
        canvas: Canvas,
        text: String,
        y: Float,
        paint: TextPaint,
    ) {
        canvas.drawText(text, canvas.width / 2f, y, paint)
    }

    /** Matches React `formatShareAyahNumberLabel`: Arabic label + Eastern digits. */
    private fun formatShareAyahNumberLabel(ayah: Int): String =
        "╪ó┘è╪⌐ ${EasternArabic.format(ayah)}"
    private fun wrapFitted(
        paint: TextPaint,
        text: String,
        maxWidth: Float,
        @Suppress("UNUSED_PARAMETER") maxHeight: Float,
    ): List<String> {
        if (text.isBlank()) return listOf(text)
        return wrapLines(paint, text, maxWidth)
    }


    private fun wrapLines(paint: TextPaint, text: String, maxWidth: Float): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return listOf(text)
        val lines = ArrayList<String>()
        var current = ""
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (paint.measureText(candidate) <= maxWidth) {
                current = candidate
            } else {
                if (current.isNotEmpty()) lines += current
                current = word
            }
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun fitWidth(paint: TextPaint, text: String, maxWidth: Float, minSize: Float) {
        while (paint.measureText(text) > maxWidth && paint.textSize > minSize) {
            paint.textSize *= 0.92f
        }
    }

    private fun lineHeight(paint: TextPaint): Float = paint.textSize * ShareMushafLayout.IMAGE_LINE_MULTIPLIER
    private suspend fun sharedAyahTextSize(
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        width: Int,
    ): Float {
        // One uniform size for the whole selection: the LARGEST size such that
        // every selected ayah reflows into ≤ VIDEO_AYAH_PAGE_MAX_LINES inside
        // VIDEO_WRAP_WIDTH. Short ayat wrap as a paragraph (mushaf breaks ignored).
        val maxWidth = width * ShareMushafLayout.VIDEO_WRAP_WIDTH
        val minSize = width * ShareMushafLayout.VIDEO_MIN_WIDTH_FRACTION
        val maxSize = width * ShareMushafLayout.VIDEO_FIXED_WIDTH_FRACTION *
            ShareMushafLayout.VIDEO_ARABIC_SIZE_FACTOR
        if (lines.isEmpty() || maxWidth <= 1f) return minSize
        val ayahs = ayahsInOrder(lines)
        val maxLines = ShareMushafLayout.VIDEO_AYAH_PAGE_MAX_LINES
        val height = (width * 16f / 9f).toInt().coerceAtLeast(width)
        val availableH = height * 0.50f
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        suspend fun allFit(size: Float): Boolean {
            for (ayah in ayahs) {
                val source = ayahSourceLines(lines, ayah)
                if (source.isEmpty()) continue
                val wrapped = reflowMushafLines(source, options, size, maxWidth)
                // Above min size every ayah must fit on one page (<= maxLines).
                // At min size, longer ayat may paginate.
                if (size > minSize * 1.01f && wrapped.size > maxLines) return false
                val pageLines = minOf(maxLines, maxOf(1, wrapped.size))
                val sample = if (wrapped.isEmpty()) source else wrapped.take(pageLines)
                val lineH = metricsLineHeight(
                    sample,
                    options,
                    size,
                    paint,
                    ShareMushafLayout.VIDEO_LINE_MULTIPLIER,
                )
                val pad = size * 0.28f
                if (pageLines * lineH + pad * 2f > availableH && size > minSize * 1.01f) {
                    return false
                }
            }
            return true
        }
        // If even maxSize fits every ayah in ≤5 lines, use it (large glyphs).
        if (allFit(maxSize)) return maxSize
        // Binary search largest size that still fits.
        var lo = minSize
        var hi = maxSize
        var best = minSize
        repeat(18) {
            val mid = (lo + hi) * 0.5f
            if (allFit(mid)) {
                best = mid
                lo = mid
            } else {
                hi = mid
            }
        }
        return best.coerceIn(minSize, maxSize)
    }



    /**
     * Joins the selected words into one paragraph. Mushaf line breaks are not
     * kept: a word or the next ayah continues on the same row until [maxWidth].
     */
    private suspend fun reflowMushafLines(
        lines: List<ShareMushafLine>,
        options: VideoMakerOptions,
        textSize: Float,
        maxWidth: Float,
    ): List<ShareMushafLine> {
        if (maxWidth <= 1f) return lines
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val fallback = if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
        data class Piece(val word: ShareMushafWord, val width: Float)
        val pieces = ArrayList<Piece>()
        for (line in lines) {
            val pageFace = if (options.font == 0) fontManager.loadPageTypeface(line.pageNumber, night = true) else null
            val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
            paint.textSize = textSize
            paint.typeface = face
            line.words.filter { options.marker || !it.isMarker }.forEach { word ->
                val width = if (word.isMarker) {
                    textSize * .90f * 1.05f
                } else {
                    val glyph = if (options.font == 0) word.qcf.ifBlank { word.uthmanic } else word.uthmanic
                    val shown = if (options.font == 0) visualPua(glyph) else glyph
                    paint.measureText(shown)
                }
                pieces += Piece(word.copy(sourcePage = line.pageNumber), width)
            }
        }
        if (pieces.isEmpty()) return emptyList()
        val gap = ShareMushafLayout.wordGap(textSize, options.font == 0)
        val out = ArrayList<ShareMushafLine>()
        val row = ArrayList<ShareMushafWord>()
        var ink = 0f
        var rowPage = pieces.first().word.sourcePage
        fun flush() {
            if (row.isEmpty()) return
            out += ShareMushafLine(pageNumber = rowPage, lineNumber = out.size + 1, words = row.toList())
            row.clear()
            ink = 0f
        }
        for (piece in pieces) {
            val next = if (row.isEmpty()) piece.width else ink + gap + piece.width
            if (row.isNotEmpty() && next > maxWidth) flush()
            if (row.isEmpty()) rowPage = piece.word.sourcePage
            row += piece.word
            ink = if (ink == 0f) piece.width else ink + gap + piece.width
        }
        flush()
        return out
    }

    private suspend fun lineInkWidth(line: ShareMushafLine, options: VideoMakerOptions, textSize: Float, paint: TextPaint): Float {
        val pageFace = if (options.font == 0) fontManager.loadPageTypeface(line.pageNumber, night = true) else null
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val fallback = if (options.font == 2) Typeface.create("serif", Typeface.NORMAL) else hafs
        val face = if (options.font == 0 && pageFace != null && pageFace != Typeface.DEFAULT) pageFace else fallback
        paint.textSize = textSize
        paint.typeface = face
        val gap = ShareMushafLayout.wordGap(textSize, options.font == 0)
        val visible = line.words.filter { options.marker || !it.isMarker }
        var ink = 0f
        visible.forEachIndexed { index, word ->
            ink += if (word.isMarker) textSize * .90f * 1.05f else {
                val glyph = if (options.font == 0) word.qcf.ifBlank { word.uthmanic } else word.uthmanic
                val shown = if (options.font == 0) visualPua(glyph) else glyph
                paint.measureText(shown)
            }
            if (index < visible.lastIndex) ink += gap
        }
        return ink
    }
    private fun ayahFontSize(@Suppress("UNUSED_PARAMETER") wordCount: Int, canvasWidth: Int): Float =
        canvasWidth * 0.058f


    // Legacy QCF PUA glyphs are bidi-neutral and must be pre-reversed; QPC V4 glyphs (U+FB50..U+FEFF)
    // are strong RTL, so the text stack already orders them right to left.
    private fun visualPua(text: String): String =
        if (text.any { it.code in 0xE000..0xF8FF }) text.reversed() else text

    private fun zoomedCoverSrc(srcW: Int, srcH: Int, dstW: Int, dstH: Int, scale: Float): Rect {
        val base = coverSrcRect(srcW, srcH, dstW, dstH)
        val zoom = scale.coerceAtLeast(1f)
        if (zoom <= 1.001f) return base
        val w = (base.width() / zoom).toInt().coerceAtLeast(1)
        val h = (base.height() / zoom).toInt().coerceAtLeast(1)
        val left = (base.centerX() - w / 2).coerceIn(base.left, base.right - w)
        val top = (base.centerY() - h / 2).coerceIn(base.top, base.bottom - h)
        return Rect(left, top, left + w, top + h)
    }

    private fun coverSrcRect(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Rect {
        val srcAspect = srcW.toFloat() / srcH
        val dstAspect = dstW.toFloat() / dstH
        return if (srcAspect > dstAspect) {
            val cropW = (srcH * dstAspect).toInt().coerceAtLeast(1)
            val left = ((srcW - cropW) / 2).coerceAtLeast(0)
            Rect(left, 0, (left + cropW).coerceAtMost(srcW), srcH)
        } else {
            val cropH = (srcW / dstAspect).toInt().coerceAtLeast(1)
            val top = ((srcH - cropH) / 2).coerceAtLeast(0)
            Rect(0, top, srcW, (top + cropH).coerceAtMost(srcH))
        }
    }

    private fun writeJpeg(bitmap: Bitmap, name: String, quality: Int = 92): File {
        val file = File(shareDir(), name)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        }
        return file
    }

    private fun shareDir(): File {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
    private fun resolveAyahAudio(
        reciter: AyahReciter,
        surah: Int,
        ayah: Int,
        timing: QulAyahTiming,
    ): File {
        val dir = File(shareDir(), "audio").apply { mkdirs() }
        val qulUrl = QulRecitations.ayahAudioUrl(reciter.id, surah, ayah)
        val url = when {
            QulRecitations.hasWordTimingCatalog(reciter.id) ->
                qulUrl ?: timing.audioUrl ?: timing.surahAudioUrl
            else ->
                timing.audioUrl ?: timing.surahAudioUrl ?: qulUrl
                    ?: runCatching { AudioUrls.ayahStreamUri(reciter, surah, ayah).toString() }.getOrNull()
        } ?: error("No ayah audio for this reciter")
        val name = if (timing.surahAudioUrl != null) "${reciter.id}-$surah.mp3" else "${reciter.id}-$surah-$ayah.mp3"
        val file = File(dir, name)
        if (!file.exists() || file.length() < 1024) downloadTo(url, file)
        return file
    }


    private fun downloadAyahAudio(
        reciter: AyahReciter,
        surah: Int,
        fromAyah: Int,
        toAyah: Int = fromAyah,
    ): List<File> {
        val dir = File(shareDir(), "audio")
        if (!dir.exists()) dir.mkdirs()
        return SurahAyahCounts.range(surah, fromAyah, surah, toAyah).map { (s, a) ->
            val file = File(dir, "${reciter.id}-$s-$a.mp3")
            if (!file.exists() || file.length() < 1024) {
                val uri = AudioUrls.ayahStreamUri(reciter, s, a)
                downloadTo(uri.toString(), file)
            }
            file
        }
    }

    private fun downloadTo(url: String, file: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }
        try {
            connection.inputStream.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
        if (file.length() < 256) error("empty audio")
    }

    private fun writeSilentWav(durationMs: Long): File {
        val sampleRate = 44_100
        val channels = 1
        val bits = 16
        val frames = (sampleRate * durationMs / 1000L).toInt().coerceAtLeast(sampleRate / 2)
        val dataBytes = frames * channels * (bits / 8)
        val file = File(shareDir(), "intro-silence.wav")
        file.outputStream().use { stream ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataBytes)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1)
            header.putShort(channels.toShort())
            header.putInt(sampleRate)
            header.putInt(sampleRate * channels * bits / 8)
            header.putShort((channels * bits / 8).toShort())
            header.putShort(bits.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataBytes)
            stream.write(header.array())
            stream.write(ByteArray(dataBytes))
        }
        return file
    }

    private suspend fun exportVideo(
        slides: List<File>,
        audioClips: List<ShareTimedClip>,
        output: File,
        frameDurations: List<Long>? = null,
        motion: Boolean = false,
        cleanAudio: Boolean = false,
        videoBitrate: Int = SHARE_VIDEO_BITRATE,
    ) {
        require(slides.isNotEmpty() && (frameDurations?.size == slides.size || slides.size == audioClips.size))
        val videoItems = slides.mapIndexed { index, file ->
            val durationUs = (frameDurations?.get(index) ?: audioClips[index].durationUs).coerceAtMost(MAX_SLIDE_US)
            // One encoded picture per slide. A fixed 30 fps would repeat long slides and stretch export time.
            val fps = ((1_000_000L + durationUs - 1) / durationUs).toInt().coerceIn(1, 30)
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                .setDurationUs(durationUs)
                .setFrameRate(fps)
                .build()
        }
        val audioItems = audioClips.map { clip ->
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(clip.file))).build()
        }
        val videoSequence = EditedMediaItemSequence.Builder(videoItems.first()).apply {
            videoItems.drop(1).forEach { addItem(it) }
        }.build()
        val audioSequence = EditedMediaItemSequence.Builder().apply {
            audioItems.forEach { addItem(it) }
        }.build()
        val composition = Composition.Builder(videoSequence, audioSequence)
            .setEffects(
                Effects(
                    if (cleanAudio) emptyList() else listOf(ShareEchoAudioProcessor()),
                    if (motion) listOf(androidx.media3.effect.MatrixTransformation { timeUs ->
                        android.graphics.Matrix().apply {
                            val scale = 1f + .025f * (1f - kotlin.math.cos(timeUs / 4_000_000.0).toFloat())
                            postScale(scale, scale)
                        }
                    }) else emptyList(),
                ),
            )
            .build()

        try {
            withTimeout(MAX_VIDEO_EXPORT_DURATION_MS) {
                withContext(Dispatchers.Main) {
                    suspendCancellableCoroutine { continuation ->
                        val encoderFactory = DefaultEncoderFactory.Builder(context)
                            .apply { if (cleanAudio) setRequestedAudioEncoderSettings(androidx.media3.transformer.AudioEncoderSettings.Builder().setBitrate(192_000).build()) }
                            .setRequestedVideoEncoderSettings(
                                VideoEncoderSettings.Builder()
                                    .setBitrate(videoBitrate)
                                    .build(),
                            )
                            .build()
                        val transformer = Transformer.Builder(context)
                            .apply { if (cleanAudio) setAudioMimeType(MimeTypes.AUDIO_AAC) }
                            .setVideoMimeType(MimeTypes.VIDEO_H264)
                            .setEncoderFactory(encoderFactory)
                            .addListener(object : Transformer.Listener {
                                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }

                                override fun onError(
                                    composition: Composition,
                                    exportResult: ExportResult,
                                    exportException: ExportException,
                                ) {
                                    if (continuation.isActive) continuation.resumeWithException(exportException)
                                }
                            })
                            .build()
                        continuation.invokeOnCancellation { transformer.cancel() }
                        transformer.start(composition, output.absolutePath)
                    }
                }
            }
            check(output.isFile && output.length() > 0L) { "Video export completed without a valid output file" }
        } catch (error: Exception) {
            output.delete()
            throw error
        }
    }

    private fun decodeSampledBitmap(stream: java.io.InputStream, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bytes = stream.readBytes()
        if (bytes.isEmpty()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, reqWidth, reqHeight)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var sample = 1
        if (height > reqHeight || width > reqWidth) {
            val halfH = height / 2
            val halfW = width / 2
            while (halfH / sample >= reqHeight && halfW / sample >= reqWidth) {
                sample *= 2
            }
        }
        return sample.coerceAtLeast(1)
    }

    private suspend fun saveToGalleryThenShare(
        file: File,
        mime: String,
        displayName: String,
        isVideo: Boolean,
    ) {
        val saved = if (AppPermissions.canWriteLegacyStorage(context)) {
            if (isVideo) {
                ShareGallerySaver.saveVideo(context, file, displayName)
            } else {
                ShareGallerySaver.saveImage(context, file, displayName)
            }
        } else {
            null
        }
        if (saved != null) {
            val message = if (isVideo) {
                context.getString(R.string.share_saved_video)
            } else {
                context.getString(R.string.share_saved_image)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            }
        }
        launchShare(file, mime, context.getString(R.string.share_ayah_title))
    }

    fun sharePlainText(text: String) {
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_via)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    private fun launchShare(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            clipData=android.content.ClipData.newRawUri("Quran video",uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.share_via)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}
