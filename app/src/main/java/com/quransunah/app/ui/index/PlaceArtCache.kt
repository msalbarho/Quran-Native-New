package com.quransunah.app.ui.index

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.quransunah.app.R
import kotlin.math.roundToInt

/**
 * One faded Mecca/Madina glyph each, decoded once at 32dp. List rows then
 * paint the cached bitmap instead of allocating an offscreen GPU layer.
 */
internal object PlaceArtCache {
    private const val DIP = 32f
    private val lock = Any()
    @Volatile private var meccan: ImageBitmap? = null
    @Volatile private var medinan: ImageBitmap? = null

    fun warmup(context: Context) {
        bitmap(context, "meccan")
        bitmap(context, "medinan")
    }

    fun bitmap(context: Context, place: String?): ImageBitmap? {
        return when (place) {
            "meccan" -> meccan ?: synchronized(lock) {
                meccan ?: bake(context, R.drawable.mecca).also { meccan = it }
            }
            "medinan" -> medinan ?: synchronized(lock) {
                medinan ?: bake(context, R.drawable.madina).also { medinan = it }
            }
            else -> null
        }
    }

    fun trim() {
        synchronized(lock) {
            meccan = null
            medinan = null
        }
    }

    private fun bake(context: Context, resId: Int): ImageBitmap {
        val density = context.resources.displayMetrics.density
        val size = (DIP * density).roundToInt().coerceIn(24, 96)
        val src = BitmapFactory.decodeResource(
            context.resources,
            resId,
            BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 },
        )
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (src != null) {
            canvas.drawBitmap(src, null, android.graphics.Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
            if (!src.isRecycled) src.recycle()
        }
        val fade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f,
                0f,
                size.toFloat(),
                0f,
                intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(), 0x00000000),
                floatArrayOf(0f, 0.42f, 1f),
                Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), fade)
        return out.asImageBitmap()
    }
}
