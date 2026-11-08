package com.quransunah.app.share

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface

/** Native numbered ornaments already present in public/fonts/hafs.ttf:
 * U+FC00 = 1 through U+FD1D = 286. Each includes its own digit and decoration.
 * Exactly ONE glyph draw: no disc, overlaid digit, or individually offset layers.
 */
object HafsAyahMarker {
    fun glyph(number: Int): String {
        require(number in 1..286)
        return (0xFC00 + number - 1).toChar().toString()
    }
    fun draw(canvas: Canvas, face: Typeface, number: Int, centerX: Float, centerY: Float, height: Float, tint: Int = 0xFFD4B06A.toInt()) {
        val text = glyph(number)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face; textSize = height; color = tint; textAlign = Paint.Align.LEFT
        }
        val bounds = Rect()
        paint.getTextBounds(text, 0, text.length, bounds)
        check(!bounds.isEmpty) { "Native Hafs ayah marker unavailable" }
        paint.textSize *= height / bounds.height()
        paint.getTextBounds(text, 0, text.length, bounds)
        canvas.drawText(text, centerX - (bounds.left + bounds.right) / 2f,
            centerY - (bounds.top + bounds.bottom) / 2f, paint)
    }
    /** Nonbreaking RTL attachment; marker is a measured inline object, never a separate overlay. */
    fun appendInline(text: android.text.SpannableStringBuilder, face: Typeface, number: Int,
                     tint: Int, anchorWord: String, anchorFace: Typeface = face): Int {
        val start=text.length
        text.append("\u00A0\u200F\uFFFC\u200F")
        val markerOffset=start+2
        text.setSpan(InlineSpan(face,number,tint,anchorWord,anchorFace),markerOffset,markerOffset+1,
            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return markerOffset
    }

    private class InlineSpan(val face:Typeface,val number:Int,val tint:Int,val anchorWord:String,val anchorFace:Typeface)
        : android.text.style.ReplacementSpan() {
        private fun width(paint:Paint):Float {
            val p=Paint(paint).apply { typeface=face;textSize=paint.textSize*.90f }
            val b=Rect();p.getTextBounds(glyph(number),0,1,b)
            return p.textSize*b.width()/b.height().coerceAtLeast(1)
        }
        override fun getSize(paint:Paint,text:CharSequence,start:Int,end:Int,fm:Paint.FontMetricsInt?):Int =
            kotlin.math.ceil(width(paint).toDouble()).toInt()
        override fun draw(canvas:Canvas,text:CharSequence,start:Int,end:Int,x:Float,top:Int,y:Int,bottom:Int,paint:Paint) {
            val bounds=Rect();Paint(paint).apply { typeface=anchorFace }.getTextBounds(anchorWord,0,anchorWord.length,bounds)
            val centerY=y+(bounds.top+bounds.bottom)/2f
            HafsAyahMarker.draw(canvas,face,number,x+width(paint)/2f,centerY,paint.textSize*.90f,tint)
        }
    }
}
