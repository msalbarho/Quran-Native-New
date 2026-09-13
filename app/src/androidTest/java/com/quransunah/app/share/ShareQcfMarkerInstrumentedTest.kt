package com.quransunah.app.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.text.TextPaint
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.quransunah.app.fonts.QcfFontManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ShareQcfMarkerInstrumentedTest {

    @Test
    fun pageFaceDrawsIkhlasMarkerGlyph() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val fonts = QcfFontManager(context)
        fonts.ensureMaps()
        val pageFace = fonts.loadPageTypeface(604)

        val body = listOf(0xF6F3, 0xF6F4, 0xF6F5, 0xF6F6).joinToString(" ") { it.toChar().toString() }
        val marker = 0xF6F7.toChar().toString()

        assertTrue("body ligatures must be drawable", fonts.canDrawLigature(body, pageFace))
        assertTrue("ayah marker must be drawable", fonts.canDrawLigature(marker, pageFace))
        assertTrue("page face must be QCF", pageFace != Typeface.DEFAULT)

        val bitmap = createBitmap(1080, 400)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(20, 14, 10))
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = pageFace
            textSize = 72f
            color = Color.rgb(247, 244, 238)
            textAlign = Paint.Align.CENTER
        }
        val markerPaint = TextPaint(bodyPaint).apply {
            color = Color.rgb(212, 176, 106)
            colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
        val gap = bodyPaint.textSize * 0.14f
        val bodyWidth = bodyPaint.measureText(body)
        val markerWidth = markerPaint.measureText(marker)
        val total = bodyWidth + gap + markerWidth
        val right = 540f + total / 2f
        canvas.drawText(body.reversed(), right - bodyWidth / 2f, 220f, bodyPaint)
        canvas.drawText(marker.reversed(), right - bodyWidth - gap - markerWidth / 2f, 220f, markerPaint)

        val out = File(context.cacheDir, "share-ikhlas-marker-smoke.png")
        out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        var goldish = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (c in pixels) {
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            if (r > 150 && g > 100 && b < 140 && r > b + 40) goldish++
        }
        assertTrue("expected gold marker pixels, file=$out goldish=$goldish", goldish > 80)
    }
}
