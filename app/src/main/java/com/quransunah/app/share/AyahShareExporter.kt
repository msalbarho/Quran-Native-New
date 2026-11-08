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
private const val TEXT_BACKDROP = 0x8C000000.toInt()
private const val AYAH_MARKER_FILL = 0xFF1B2433.toInt()
private const val AYAH_MARKER_ACCENT = 0xFFD4B06A.toInt()
private const val MAX_SLIDE_US = 280_000_000L
private const val INTRO_NAME_ONLY_MS = 2_200L

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

    internal fun promotionalWatermarkLabel():String = arabicBrandingName()+" - تلاوة واستماع"

    suspend fun shareImage(content: AyahShareContent) = withContext(Dispatchers.IO) {
        fontManager.ensureMaps()
        val verse = content.verses.firstOrNull() ?: return@withContext
        val bitmap = renderMaker(content,verse,VideoMakerOptions(),null,1080,1920,displayVerses=content.verses)
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
    suspend fun makerPreview(content: AyahShareContent, options: VideoMakerOptions, word: Int? = 1, progress: Float = .5f, elapsedUs: Long = 0L, totalUs: Long = 1L, onGeometry: ((MakerPreviewGeometry)->Unit)? = null): Bitmap =
        withContext(Dispatchers.IO) {
            fontManager.ensureMaps()
            renderMaker(content, content.verses.first(), options, word, 540, 960, progress, elapsedUs, totalUs, onGeometry=onGeometry)
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
            val source = downloadAyahAudio(reciter, content.surah, verse.ayah).first()
            val qulId = when (reciter.id) { "123" -> 18; "92" -> 26; else -> null }
            val originalSegments = qulId?.let { timingStore.load(content.surah, verse.ayah, it) }.orEmpty()
            if (qulId != null) check(originalSegments.isNotEmpty()) { "Word timings unavailable. Please retry with internet access." }
            // Word timestamps are highlight anchors, NOT safe audio crop boundaries.
            // Preserve the complete source ayah, including its real consonant attack and margins.
            val clip = ShareAudioClip.prepareMaker(source, File(shareDir(), "maker-${verse.ayah}.wav"),
                fromMs = 0L, toMs = null, fadeIn = false, fadeOut = false)
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
        var verseStartUs = 0L
        prepared.forEach { item ->
            makerRenderFrames(item.segments, item.clip.durationUs,
                options.trackingMode in listOf(0,2,4) || options.backgrounds.size > 1).forEach { interval ->
                val localUs = interval.startUs + interval.durationUs / 2
                val word = item.segments.firstOrNull { it.position == interval.word && localUs / 1000 in it.startMs until it.endMs }
                val progress = word?.let { ((localUs / 1000f - it.startMs) / (it.endMs - it.startMs).coerceAtLeast(1)).coerceIn(0f,1f) } ?: 0f
                val bitmap = renderMaker(content, item.verse, options, interval.word, 1080, 1920,
                    progress, verseStartUs + localUs, totalUs)
                frames += writeJpeg(bitmap, "maker-frame-${frames.size}.jpg")
                bitmap.recycle(); durations += interval.durationUs
            }
            verseStartUs += item.clip.durationUs
        }
        val output = File(shareDir(), "quran-${content.surah}-${content.fromAyah}-${content.toAyah}-${System.currentTimeMillis()}.mp4")
        val combined = ShareAudioClip.concatenateMaker(clips, File(shareDir(), "maker-selection.wav"))
        File(shareDir(), "last-maker-timeline.json").writeText(timeline.toString(2))
        val encoded = File(shareDir(), "maker-encoded.mp4")
        if (encoded.exists()) encoded.delete()
        exportVideo(frames, listOf(combined), encoded, durations, options.motion, cleanAudio = true)
        val encoderDelayUs = ShareAudioClip.makerEncoderDelayUs(encoded, combined.file)
        File(shareDir(), "last-maker-sync.txt").writeText("measuredVideoDelayUs=$encoderDelayUs\n")
        MakerAvSync.align(encoded, output, encoderDelayUs)
        output
    }

    suspend fun saveMakerVideo(file: File): Uri = withContext(Dispatchers.IO) {
        ShareGallerySaver.saveVideo(context, file, file.name) ?: error("Could not save video")
    }

    fun shareMakerVideo(file: File, tikTok: Boolean = false) {
        if (!tikTok) { launchShare(file, "video/mp4", "القرآن الكريم"); return }
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
        else launchShare(file, "video/mp4", "القرآن الكريم")
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

    private suspend fun renderMaker(content: AyahShareContent, verse: AyahShareVerse, options: VideoMakerOptions,
                                    activeWord: Int?, width: Int, height: Int, activeProgress: Float = 0f,
                                    elapsedUs: Long = 0L, totalUs: Long = 1L, displayVerses: List<AyahShareVerse> = listOf(verse), onGeometry: ((MakerPreviewGeometry)->Unit)? = null): Bitmap {
        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap)
        val backgrounds = options.backgrounds.ifEmpty { listOf(options.background) }
        val mix = makerBackgroundMix(elapsedUs,totalUs,backgrounds.size)
        drawCoverBackground(canvas, backgrounds[mix.from])
        if (mix.from != mix.to) drawCoverBackground(canvas, backgrounds[mix.to], (mix.blend*255).toInt())
        drawDarkOverlay(canvas)
        val hafs = fontManager.loadUthmanicTypeface() ?: Typeface.DEFAULT
        val pageFace = if (options.font == 0) fontManager.loadPageTypeface(verse.pageNumber, night = true) else null
        val qcf = options.font == 0 && verse.qcfText.any { it.code in 0xFB50..0xFEFF || it.code in 0xE000..0xF8FF } && pageFace != Typeface.DEFAULT
        val face = if (qcf) pageFace!! else if (options.font == 2) Typeface.create("serif",Typeface.NORMAL) else hafs
        val words = (if (qcf) verse.qcfText else verse.uthmanicText).trim().split(Regex("\\s+"))
        val starts = words.indices.map { index -> words.take(index).sumOf { it.length+1 } }
        val text = android.text.SpannableStringBuilder()
        displayVerses.forEachIndexed { index,item ->
            val itemPage=if(options.font==0)fontManager.loadPageTypeface(item.pageNumber,night=true)else null
            val itemQcf=options.font==0 && item.qcfText.any { it.code in 0xFB50..0xFEFF || it.code in 0xE000..0xF8FF } && itemPage!=Typeface.DEFAULT
            val itemFace=if(itemQcf)itemPage!! else if(options.font==2)Typeface.create("serif",Typeface.NORMAL)else hafs
            val itemWords=(if(itemQcf)item.qcfText else item.uthmanicText).trim().split(Regex("\\s+"))
            val begin=text.length
            text.append(itemWords.joinToString(" "))
            text.setSpan(object:android.text.style.MetricAffectingSpan() {
                override fun updateDrawState(tp:TextPaint){tp.typeface=itemFace;tp.color=TEXT_WHITE;tp.colorFilter=PorterDuffColorFilter(TEXT_WHITE,PorterDuff.Mode.SRC_IN)}
                override fun updateMeasureState(tp:TextPaint){tp.typeface=itemFace}
            },begin,text.length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if(options.marker)HafsAyahMarker.appendInline(text,hafs,item.ayah,options.markerColor,itemWords.last(),itemFace)
            if(index<displayVerses.lastIndex)text.append(" ")
        }
        if (activeWord != null) words.indices.forEach { index ->
            val recited = if (options.trackingMode == 1) index < activeWord else index == activeWord-1
            if (recited && options.trackingMode in listOf(1,3,4)) {
                text.setSpan(object : android.text.style.CharacterStyle() {
                    override fun updateDrawState(tp: TextPaint) {
                        tp.colorFilter = PorterDuffColorFilter(options.highlightColor,PorterDuff.Mode.SRC_IN)
                        if (options.trackingMode == 4) tp.setShadowLayer(width*.012f,0f,0f,options.highlightColor)
                    }
                },starts[index],starts[index]+words[index].length,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface=face; textSize=width*.080f*options.textScale; color=TEXT_WHITE
            colorFilter=PorterDuffColorFilter(TEXT_WHITE,PorterDuff.Mode.SRC_IN)
            if(options.glow) setShadowLayer(width*.014f,0f,0f,options.highlightColor)
        }
        val maxWidth=(width*.82f).toInt()
        fun layout()=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,maxWidth)
            .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).setTextDirection(android.text.TextDirectionHeuristics.RTL)
            .setLineSpacing(0f,1.45f).setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE).build()
        var body=layout()
        while(body.height>height*.48f && paint.textSize>width*.025f){paint.textSize*=.9f;body=layout()}
        val visibleWidth=(0 until body.lineCount).maxOf { body.getLineRight(it)-body.getLineLeft(it) }
        val bodyX=(width*options.ayahX).coerceIn(visibleWidth/2f,width-visibleWidth/2f)-maxWidth/2f
        val titleFace=if(options.showTitle)fontManager.loadSurahTitleTypeface() else null
        val title=if(options.showTitle)visualPua(fontManager.surahNameLigature(content.surah).orEmpty()) else ""
        val titlePaint=ligaturePaint(titleFace ?: hafs,width*.10f,AYAH_MARKER_ACCENT)
        fitWidth(titlePaint,title,width*.70f,20f)
        val titleBounds=Rect();if(title.isNotEmpty())titlePaint.getTextBounds(title,0,title.length,titleBounds)
        val geometry=makerVerticalPlacement(options.ayahY,options.titleY,body.height.toFloat()/height,
            titleBounds.height().toFloat()/height,options.showTitle,options.translation)
        onGeometry?.invoke(geometry)
        val bodyY=geometry.ayahCenterY*height-body.height/2f
        if(options.textBackdrop) drawTextBackdrop(canvas,bodyY,bodyY+body.height)
        canvas.save();canvas.translate(bodyX,bodyY)
        if(activeWord!=null && options.trackingMode in listOf(0,2)) {
            val trackingPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=options.highlightColor; strokeWidth=width*.006f;strokeCap=Paint.Cap.ROUND }
            val filledLines=linkedMapOf<Int,android.graphics.RectF>()
            words.indices.forEach { index ->
                val complete=index<activeWord-1
                if(complete || index==activeWord-1) {
                    val path=android.graphics.Path();val bounds=android.graphics.RectF()
                    val line=body.getLineForOffset(starts[index])
                    val next=starts.getOrNull(index+1)
                    val end=starts[index]+words[index].length+if(options.trackingMode==0 && next!=null && body.getLineForOffset(next)==line)1 else 0
                    body.getSelectionPath(starts[index],end,path);path.computeBounds(bounds,true)
                    val fraction=if(complete) 1f else activeProgress.coerceIn(0f,1f)
                    if(options.trackingMode==0) {
                        val filled=android.graphics.RectF(bounds.right-bounds.width()*fraction,bounds.top,bounds.right,bounds.bottom)
                        filledLines[line]?.union(filled) ?: run { filledLines[line]=filled }
                    } else if(!complete) {
                        val y=body.getLineBaseline(line)+paint.fontMetrics.descent+width*.008f
                        canvas.drawLine(bounds.right,y,bounds.right-bounds.width()*fraction,y,trackingPaint)
                    }
                }
            }
            if(options.trackingMode==0) {
                trackingPaint.alpha=90
                filledLines.values.forEach { canvas.drawRoundRect(it,paint.textSize*.10f,paint.textSize*.10f,trackingPaint) }
            }
        }
        body.draw(canvas);canvas.restore()
        if(options.showTitle)canvas.drawText(title,width*options.titleX,
            height*geometry.titleCenterY-(titleBounds.top+titleBounds.bottom)/2f,titlePaint)
        val english=displayVerses.mapNotNull { it.englishTranslation }.joinToString("\n")
        if(options.translation && english.isNotBlank()) {
            val en=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color=TEXT_WHITE;textSize=width*.026f }
            val translation=android.text.StaticLayout.Builder.obtain(english,0,english.length,en,maxWidth)
                .setAlignment(android.text.Layout.Alignment.ALIGN_CENTER).build()
            canvas.save();canvas.translate((width*options.ayahX-maxWidth/2f).coerceIn(0f,(width-maxWidth).toFloat()),bodyY+body.height+width*.075f);translation.draw(canvas);canvas.restore()
        }
        drawBranding(canvas)
        return bitmap
    }

    private fun drawBranding(canvas: Canvas) {
        val width=canvas.width.toFloat();val height=canvas.height.toFloat()
        val stripHeight=height*.065f
        canvas.drawRect(0f,height-stripHeight,width,height,Paint().apply { color=0xAA111923.toInt() })
        val iconSize=stripHeight*.68f
        val label=promotionalWatermarkLabel()
        val paint=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=Typeface.DEFAULT_BOLD;textSize=width*.028f;color=0xFFE3C98C.toInt() }
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

    private fun drawCoverBackground(canvas: Canvas, fileName: String, alpha: Int = 255) {
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
        val src = coverSrcRect(source.width, source.height, width, height)
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
            .apply { colorFilter = PorterDuffColorFilter(TEXT_WHITE, PorterDuff.Mode.SRC_IN) } // single white tone, no COLR tajweed colours
        val lines = wrapFitted(ayahPaint, ayahSource, maxWidth, maxAyahHeight)
        val numberPaint = ligaturePaint(numberFace, width * 0.038f, TEXT_WHITE)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val gap = height * 0.03f
        val block = ayahH + gap + numberH
        var y = (height * 0.48f - block / 2f).coerceAtLeast(height * 0.18f)
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
        val sectionGap = height * 0.028f
        val surahH = lineHeight(surahPaint)
        val basmalahH = if (basmalah.isEmpty()) 0f else lineHeight(basmalahPaint)
        val ayahH = lines.size * lineHeight(ayahPaint)
        val numberH = lineHeight(numberPaint)
        val block = surahH +
            (if (basmalahH > 0f) sectionGap + basmalahH else 0f) +
            sectionGap + ayahH + sectionGap + numberH
        var y = (height * 0.46f - block / 2f).coerceIn(height * 0.12f, height * 0.78f - block)
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

    // Legacy QCF PUA glyphs are bidi-neutral and must be pre-reversed; QPC V4 glyphs (U+FB50..U+FEFF)
    // are strong RTL, so the text stack already orders them right to left.
    private fun visualPua(text: String): String =
        if (text.any { it.code in 0xE000..0xF8FF }) text.reversed() else text

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
        frameDurations: List<Long>? = null,
        motion: Boolean = false,
        cleanAudio: Boolean = false,
    ) {
        require(slides.isNotEmpty() && (frameDurations?.size == slides.size || slides.size == audioClips.size))
        val videoItems = slides.mapIndexed { index, file ->
            val durationUs = (frameDurations?.get(index) ?: audioClips[index].durationUs).coerceAtMost(MAX_SLIDE_US)
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

        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .apply { if (cleanAudio) setRequestedAudioEncoderSettings(androidx.media3.transformer.AudioEncoderSettings.Builder().setBitrate(192_000).build()) }
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(SHARE_VIDEO_BITRATE)
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
