package com.quransunah.app.media.auto

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import com.quransunah.app.media.AudioArtwork

/**
 * URI adapter for reciter avatars. The id-to-drawable map lives in [AudioArtwork].
 */
object ReciterArtwork {

    @DrawableRes
    fun resourceId(context: Context, reciterId: Int): Int {
        val mapped = AudioArtwork.reciterDrawable(reciterId)
        if (mapped != 0) return mapped
        return context.resources.getIdentifier("", "drawable", context.packageName)
    }

    fun uri(context: Context, reciterId: Int): Uri? {
        val resId = resourceId(context, reciterId)
        if (resId == 0) return null
        return AutoArtwork.typed(context, resId)
    }

    fun uriOrFallback(context: Context, reciterId: Int, fallback: Uri): Uri {
        return uri(context, reciterId) ?: fallback
    }
}
