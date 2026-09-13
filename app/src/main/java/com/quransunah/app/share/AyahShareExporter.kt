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
import kotlinx.coroutines.withContext

private const val SHARE_WIDTH = 1080
private const val SHARE_HEIGHT = 1920
/** Higher canvas for share-video export (React uses 1080×1920 @ 8 Mbps). */
private const val SHARE_VIDEO_WIDTH = 1440
private const val SHARE_VIDEO_HEIGHT = 2560
private const val SHARE_VIDEO_BITRATE = 8_000_000
private const val OVERLAY_TOP = 0x99000000.toInt()
private const val OVERLAY_MID = 0x66000000.toInt()
private const val TEXT_WHITE = 0xFFF7F4EE.toInt()
private const val BASMALAH_GOLD = 0xFFC5A059.toInt()
/** Accent for Medina-style ayah-end marks on dark share slides. */
private const val AYAH_MARKER_ACCENT = 0xFFD4B06A.toInt()
private const val MAX_SLIDE_US = 280_000_000L
private const val INTRO_NAME_ONLY_MS = 2_200L

private enum class SharePaintMode {
    FULL,
    INTRO,
    BASMALAH,
    AYAH,
}

@OptIn(UnstableApi::class)
@Singleton
class AyahShareExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fontManager: QcfFontManager,
    private val reciterCatalog: ReciterCatalog,
) {
    suspend fun shareImage(content: AyahShareContent) = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val verse = content.verses.firstOrNull() ?: return@withContext
        val bitmap = renderSlide(
            content = content,
            verse = verse,
            mode = SharePaintMode.FULL,
            backgroundAsset = ShareBackgrounds.pick(),
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
        val pageFace = fontManager.loadPageTypeface(verse.pageNumber)
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
        val pageFaceReady = pageFace != Typeface.DEFAULT &&
            qcfBody.any { it.code in 0xE000..0xF8FF } &&
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
        return bitmap
    }

    private fun drawCoverBackground(canvas: Canvas, fileName: String) {
        val width = canvas.width
        val height = canvas.height
        val source = runCatching {
            context.assets.open(ShareBackgrounds.assetPath(fileName)).use { stream ->
                decodeSampledBitmap(stream, width, height)
            }
        }.getOrNull()
        if (source == null) {
            canvas.drawColor(0xFF1A140F.toInt())
            return
        }
        val src = coverSrcRect(source.width, source.height, width, height)
        val filter = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, src, Rect(0, 0, width, height), filter)
        source.recycle()
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
        val gap = height * 0.04f
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
        val lines = wrapFitted(ayahPaint, ayahSource, maxWidth, maxAyahHeight)
        val numberPaint = ligaturePaint(numberFace, width * 0.038f, TEXT_WHITE)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val gap = height * 0.03f
        val block = ayahH + gap + numberH
        var y = (height * 0.48f - block / 2f).coerceAtLeast(height * 0.18f)
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
        val numberPaint = ligaturePaint(numberFace, width * 0.036f, TEXT_WHITE)
        val maxAyahHeight = height * 0.42f
        val lines = wrapFitted(ayahPaint, ayahSource, maxWidth, maxAyahHeight)
        val sectionGap = height * 0.028f
        val surahH = lineHeight(surahPaint)
        val basmalahH = if (basmalah.isEmpty()) 0f else lineHeight(basmalahPaint)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val block = surahH +
            (if (basmalahH > 0f) sectionGap + basmalahH else 0f) +
            sectionGap + ayahH + sectionGap + numberH
        var y = (height * 0.46f - block / 2f).coerceIn(height * 0.12f, height * 0.78f - block)

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
                    drawAyahLineWithMarker(canvas, line, markerLigature, baseline, ayahPaint)
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
    ) {
        if (bodyText.isBlank()) {
            val markerPaint = markerPaint(bodyPaint)
            canvas.drawText(visualPua(markerLigature), canvas.width / 2f, baseline, markerPaint)
            return
        }
        val gap = bodyPaint.textSize * 0.14f
        val bodyWidth = bodyPaint.measureText(bodyText)
        val markerPaint = markerPaint(bodyPaint)
        val markerWidth = markerPaint.measureText(markerLigature)
        val totalWidth = bodyWidth + gap + markerWidth
        val rightEdge = canvas.width / 2f + totalWidth / 2f
        val bodyCenterX = rightEdge - bodyWidth / 2f
        val markerCenterX = rightEdge - bodyWidth - gap - markerWidth / 2f
        canvas.drawText(visualPua(bodyText), bodyCenterX, baseline, bodyPaint)
        canvas.drawText(visualPua(markerLigature), markerCenterX, baseline, markerPaint)
    }

    private fun drawAyahLineWithEndNumber(
        canvas: Canvas,
        bodyText: String,
        ayahNumber: Int,
        baseline: Float,
        bodyPaint: TextPaint,
        numberFace: Typeface,
    ) {
        val endNumber = EasternArabic.format(ayahNumber)
        val numberPaint = ligaturePaint(numberFace, bodyPaint.textSize * 0.88f, AYAH_MARKER_ACCENT)
        val gap = bodyPaint.textSize * 0.14f
        val bodyWidth = bodyPaint.measureText(bodyText)
        val numberWidth = numberPaint.measureText(endNumber)
        val totalWidth = bodyWidth + gap + numberWidth
        val rightEdge = canvas.width / 2f + totalWidth / 2f
        val bodyCenterX = rightEdge - bodyWidth / 2f
        val numberCenterX = rightEdge - bodyWidth - gap - numberWidth / 2f
        canvas.drawText(bodyText, bodyCenterX, baseline, bodyPaint)
        canvas.drawText(endNumber, numberCenterX, baseline, numberPaint)
    }

    private fun markerPaint(base: TextPaint): TextPaint {
        return TextPaint(base).apply {
            color = AYAH_MARKER_ACCENT
            colorFilter = PorterDuffColorFilter(AYAH_MARKER_ACCENT, PorterDuff.Mode.SRC_IN)
        }
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
        "آية ${EasternArabic.format(ayah)}"

    private fun wrapFitted(
        paint: TextPaint,
        text: String,
        maxWidth: Float,
        maxHeight: Float,
    ): List<String> {
        if (text.isBlank()) return listOf(text)
        var size = paint.textSize
        var lines = wrapLines(paint, text, maxWidth)
        while (lines.size * lineHeight(paint) > maxHeight && size > 28f) {
            size *= 0.92f
            paint.textSize = size
            lines = wrapLines(paint, text, maxWidth)
        }
        return lines
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

    private fun lineHeight(paint: TextPaint): Float = paint.textSize * 1.72f

    private fun ayahFontSize(wordCount: Int, canvasWidth: Int): Float = when {
        wordCount <= 8 -> canvasWidth * 0.072f
        wordCount <= 16 -> canvasWidth * 0.058f
        wordCount <= 28 -> canvasWidth * 0.048f
        else -> canvasWidth * 0.040f
    }

    private fun visualPua(text: String): String = text.reversed()

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

    private fun writeJpeg(bitmap: Bitmap, name: String): File {
        val file = File(shareDir(), name)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
        }
        return file
    }

    private fun shareDir(): File {
        val dir = File(context.cacheDir, "share")
        if (!dir.exists()) dir.mkdirs()
        return dir
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
    ) {
        require(slides.isNotEmpty() && slides.size == audioClips.size)
        val videoItems = slides.mapIndexed { index, file ->
            val durationUs = audioClips[index].durationUs.coerceAtMost(MAX_SLIDE_US)
            EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(file)))
                .setDurationUs(durationUs)
                .setFrameRate(30)
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
                    listOf(ShareEchoAudioProcessor()),
                    emptyList(),
                ),
            )
            .build()

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(SHARE_VIDEO_BITRATE)
                            .build(),
                    )
                    .build()
                val transformer = Transformer.Builder(context)
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

    private fun launchShare(file: File, mime: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
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
