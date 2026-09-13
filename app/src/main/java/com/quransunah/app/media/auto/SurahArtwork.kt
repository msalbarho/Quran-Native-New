package com.quransunah.app.media.auto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.annotation.DrawableRes
import com.quransunah.app.core.AppLog

/**
 * Maps Quran surah numbers (1–114) to calligraphic name icons
 * (`ic_surah_001` … `ic_surah_114`) for Android Auto artwork.
 *
 * Uses compile-time [R.drawable] ids so mapping cannot silently fall back
 * to a generic icon when `getIdentifier` fails at runtime.
 */
object SurahArtwork {

    private const val TAG = "SurahArtwork"

    const val MIN_SURAH = 1
    const val MAX_SURAH = 114

    fun drawableName(surahNumber: Int): String =
        "ic_surah_%03d".format(java.util.Locale.US, surahNumber)

    @DrawableRes
    fun resourceId(context: Context, surahNumber: Int): Int {
        if (surahNumber !in MIN_SURAH..MAX_SURAH) return 0
        val compileTimeId = SurahArtworkIds.IDS[surahNumber - 1]
        if (compileTimeId != 0) return compileTimeId
        return context.resources.getIdentifier(
            drawableName(surahNumber),
            "drawable",
            context.packageName,
        )
    }

    /**
     * `android.resource://{packageName}/drawable/ic_surah_XXX`
     * (Android Auto / AAOS documented format).
     */
    fun uri(context: Context, surahNumber: Int): Uri? {
        if (surahNumber !in MIN_SURAH..MAX_SURAH) return null
        val resourceName = drawableName(surahNumber)
        val resId = resourceId(context, surahNumber)
        if (resId == 0) {
            AppLog.w(TAG, "missing drawable $resourceName pkg=${context.packageName}")
            return null
        }
        return AutoArtwork.typed(context, resId)
    }

    fun uriByResourceId(context: Context, surahNumber: Int): Uri? {
        val resId = resourceId(context, surahNumber)
        if (resId == 0) return null
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName)
            .appendPath(resId.toString())
            .build()
    }

    fun uriOrFallback(context: Context, surahNumber: Int?, fallback: Uri): Uri {
        if (surahNumber == null) return fallback
        val resId = resourceId(context, surahNumber)
        if (resId == 0) {
            AppLog.w(TAG, "surah=$surahNumber missing drawable — using fallback")
            return fallback
        }
        val byName = uri(context, surahNumber)
        if (byName != null) return byName
        return uriByResourceId(context, surahNumber) ?: fallback
    }
}
