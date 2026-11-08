package com.quransunah.app.share

import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.fonts.QpcV4Glyphs
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** App-only exporter smoke test; launches no activity and never invokes a share intent. */
@RunWith(AndroidJUnit4::class)
class VideoMakerExportInstrumentedTest {
    @Test fun fatihaCleanVideoAndWordTint() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val glyphs = QpcV4Glyphs(context)
        val fonts = QcfFontManager(context, glyphs)
        val exporter = AyahShareExporter(context, fonts, ReciterCatalog(context))
        fun qcf(ids: IntRange) = ids.mapNotNull { glyphs.forWord(it) }.joinToString(" ")
        val content = AyahShareContent(1, "سُورَةُ الفَاتِحَةِ", 1, 3, listOf(
            AyahShareVerse(1, 1, qcf(1..4), "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", englishTranslation = "In the name of Allah, the Most Compassionate, the Most Merciful."),
            AyahShareVerse(2, 1, qcf(6..9), "الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ", englishTranslation = "All praise is for Allah, Lord of all worlds."),
            AyahShareVerse(3, 1, qcf(11..12), "الرَّحْمَٰنِ الرَّحِيمِ", englishTranslation = "The Most Compassionate, the Most Merciful.")
        ))
        val options = VideoMakerOptions(translation = true, backgrounds = ShareBackgrounds.FILES.take(3))
        val evidence = File(context.getExternalFilesDir(null), "vm-inline-v3").apply { mkdirs() }
        val nativeFace = fonts.loadUthmanicTypeface()!!
        val nativePaint = android.graphics.Paint().apply { typeface = nativeFace }
        for (number in 1..286) assertTrue("native Hafs marker $number missing", nativePaint.hasGlyph(HafsAyahMarker.glyph(number)))
        for (number in listOf(1,7,99,286)) {
            val marker = exporter.markerPreview(number)
            File(evidence, "marker-a-$number.png").outputStream().use { marker.compress(Bitmap.CompressFormat.PNG,100,it) }
            marker.recycle()
        }
        val sharedImage = exporter.shareImagePreview(content)
        File(evidence, "marker-share-image.png").outputStream().use { sharedImage.compress(Bitmap.CompressFormat.PNG,100,it) }
        var bluePixels=0
        for(y in 450 until 1450)for(x in 0 until sharedImage.width){
            val c=sharedImage.getPixel(x,y);val r=android.graphics.Color.red(c);val g=android.graphics.Color.green(c);val b=android.graphics.Color.blue(c)
            if(b>r+35 && b>g+15 && b>100)bluePixels++
        }
        assertEquals("white text must persist after each inline marker",0,bluePixels)
        sharedImage.recycle()
        val modeNames=listOf("progress","cumulative","underline","current","glow")
        modeNames.forEachIndexed { mode,name ->
            for(frame in 0..8) {
                val image=exporter.makerPreview(content,options.copy(trackingMode=mode),frame/3+1,(frame%3)/2f)
                File(evidence,"mode-$name-${frame.toString().padStart(3,'0')}.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
                image.recycle()
            }
        }
        for((name,colour) in listOf("gold" to 0xFFD4B06A.toInt(),"white" to -1,"black" to 0xFF000000.toInt(),"red" to 0xFFE53935.toInt(),"yellow" to 0xFFFFEB3B.toInt(),"green" to 0xFF4CAF50.toInt())) {
            val image=exporter.markerPreview(286,colour)
            File(evidence,"marker-colour-$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        }
        val dragged=exporter.makerPreview(content,options.copy(ayahX=.6f,ayahY=.56f,titleX=.56f,titleY=.25f),2,.6f)
        File(evidence,"mode-dragged.png").outputStream().use { dragged.compress(Bitmap.CompressFormat.PNG,100,it) };dragged.recycle()
        for((frame,time) in listOf(9800000L,10000000L,10200000L).withIndex()) {
            val image=exporter.makerPreview(content,options,2,.5f,time,30000000L)
            File(evidence,"background-crossfade-$frame.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) };image.recycle()
        }
        val styledImage=exporter.makeMakerImage(content,options)
        styledImage.copyTo(File(evidence,"marker-branded-image.jpg"),overwrite=true)
        val a = exporter.makerPreview(content, options, 1)
        val b = exporter.makerPreview(content, options, 2)
        var different = 0
        for (y in 300 until 650) for (x in 0 until a.width) if (a.getPixel(x,y) != b.getPixel(x,y)) different++
        assertTrue("word tint must move between adjacent words, changed pixels=$different", different > 20)
        a.compress(Bitmap.CompressFormat.PNG, 100, File(evidence, "word1.png").outputStream())
        b.compress(Bitmap.CompressFormat.PNG, 100, File(evidence, "word2.png").outputStream())
        a.recycle(); b.recycle()
        val report = StringBuilder("wordTintChangedPixels=$different\n")
        for (reciter in (InstrumentationRegistry.getArguments().getString("makerReciter")?.let { listOf(it) } ?: listOf("123", "92"))) {
            val file = exporter.makeVideo(content, reciter, options)
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                val format = (0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                    .first { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                assertEquals("audio/mp4a-latm", format.getString(MediaFormat.KEY_MIME))
                assertEquals(44100, format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                assertEquals(2, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
                report.append("reciter=$reciter audio=$format file=${file.name}\n")
            } finally { extractor.release() }
            report.append("reciter=$reciter " + File(context.cacheDir, "share/last-maker-sync.txt").readText())
            val timeline = File(context.cacheDir, "share/last-maker-timeline.json")
            timeline.copyTo(File(evidence, "timeline-$reciter-v3.json"), overwrite = true)
            File(context.cacheDir, "share/maker-selection.wav").copyTo(File(evidence, "selection-$reciter-v3.wav"), overwrite = true)
            val saved = exporter.saveMakerVideo(file)
            report.append("saved=$saved\n")
            File(evidence, "export-$reciter-v3.mp4").outputStream().use { dest -> file.inputStream().use { it.copyTo(dest) } }
        }
        File(evidence, "report-v3.txt").writeText(report.toString())
    }
    @Test fun inlineMarkerWrappingAndArabicProjectBranding() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val fonts=QcfFontManager(context,QpcV4Glyphs(context));fonts.ensureMaps()
        val face=fonts.loadUthmanicTypeface()!!
        val exporter=AyahShareExporter(context,fonts,ReciterCatalog(context))
        assertEquals("القرآن الكريم",exporter.arabicBrandingName())
        assertEquals("القرآن الكريم - تلاوة واستماع",exporter.promotionalWatermarkLabel())
        val icon=context.assets.open("share-brand/icon.jpg").use { it.readBytes() }
        val digest=java.security.MessageDigest.getInstance("SHA-256").digest(icon).joinToString(""){"%02x".format(it)}
        assertEquals("22504a6f8b1e2090757a6d93b2e116f679837cbebf389e4a07c6fd4a035a1df5",digest)
        val verses=listOf("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ","الْحَمْدُ لِلَّهِ رَبِّ الْعَالَمِينَ","الرَّحْمَٰنِ الرَّحِيمِ")
        for(width in listOf(190,230,310)) {
            val text=android.text.SpannableStringBuilder()
            val markers=mutableListOf<Pair<Int,Int>>()
            verses.forEachIndexed { index,verse ->
                val start=text.length; text.append(verse)
                val lastWord=verse.substringAfterLast(' ')
                val lastWordStart=start+verse.lastIndexOf(' ')+1
                val marker=HafsAyahMarker.appendInline(text,face,index+1,0xFFD4B06A.toInt(),lastWord)
                markers+=lastWordStart to marker
                if(index<verses.lastIndex)text.append(" ")
            }
            val paint=android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { typeface=face;textSize=43f }
            val layout=android.text.StaticLayout.Builder.obtain(text,0,text.length,paint,width)
                .setTextDirection(android.text.TextDirectionHeuristics.RTL).setAlignment(android.text.Layout.Alignment.ALIGN_CENTER)
                .setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE).setLineSpacing(0f,1.45f).build()
            assertTrue("must exercise multi-line layout",layout.lineCount>1)
            markers.forEach { (lastWord,marker) ->
                assertEquals("marker must stay with its final word at width $width",layout.getLineForOffset(lastWord),layout.getLineForOffset(marker))
                val markerPath=android.graphics.Path();val markerBounds=android.graphics.RectF()
                layout.getSelectionPath(marker,marker+1,markerPath);markerPath.computeBounds(markerBounds,true)
                val wordPath=android.graphics.Path();val wordBounds=android.graphics.RectF()
                layout.getSelectionPath(lastWord,marker-2,wordPath);wordPath.computeBounds(wordBounds,true)
                assertTrue("RTL end marker must be LEFT of the last word",markerBounds.right<=wordBounds.left+1f)
                assertTrue("marker outside layout",markerBounds.left>=-1f && markerBounds.right<=width+1f)
            }
        }
    }
}
