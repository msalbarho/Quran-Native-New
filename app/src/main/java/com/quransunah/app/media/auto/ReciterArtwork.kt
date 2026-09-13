package com.quransunah.app.media.auto

import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import com.quransunah.app.R

/**
 * Maps the five main Auto-browse reciters to drawable avatar URIs
 * (`reciter_dosari`, `reciter_ajmi`, …).
 */
object ReciterArtwork {

    private val DRAWABLE_BY_RECITER_ID: Map<Int, Int> = mapOf(
        92 to R.drawable.reciter_dosari,
        5 to R.drawable.reciter_ajmi,
        123 to R.drawable.reciter_afasy,
        102 to R.drawable.reciter_maher,
        54 to R.drawable.reciter_sudais,
    )

    fun drawableName(reciterId: Int): String? = when (reciterId) {
        92 -> "reciter_dosari"
        5 -> "reciter_ajmi"
        123 -> "reciter_afasy"
        102 -> "reciter_maher"
        54 -> "reciter_sudais"
        else -> null
    }

    @DrawableRes
    fun resourceId(context: Context, reciterId: Int): Int {
        DRAWABLE_BY_RECITER_ID[reciterId]?.let { return it }
        val name = drawableName(reciterId) ?: return 0
        return context.resources.getIdentifier(name, "drawable", context.packageName)
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
