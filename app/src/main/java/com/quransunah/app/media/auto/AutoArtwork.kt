package com.quransunah.app.media.auto

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.quransunah.app.R

object AutoArtwork {

    fun launcher(context: Context): Uri =
        named(context, "ic_launcher", "mipmap") ?: typed(context, R.mipmap.ic_launcher)

    fun tabReciters(context: Context): Uri = typed(context, R.drawable.ic_tab_reciters)

    fun tabSurahs(context: Context): Uri = typed(context, R.drawable.ic_tab_surahs)

    fun reciter(context: Context, reciterId: Int): Uri =
        ReciterArtwork.uriOrFallback(context, reciterId, launcher(context))

    fun surah(context: Context, surahNumber: Int): Uri =
        SurahArtwork.uriOrFallback(context, surahNumber, typed(context, R.drawable.ic_auto_surah))

    /** `android.resource://{pkg}/{type}/{name}` — Auto-documented artwork URI. */
    fun typed(context: Context, resId: Int): Uri {
        val resources = context.resources
        return Uri.Builder()
            .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(resources.getResourcePackageName(resId))
            .appendPath(resources.getResourceTypeName(resId))
            .appendPath(resources.getResourceEntryName(resId))
            .build()
    }

    fun named(context: Context, entryName: String, type: String): Uri? {
        val resId = context.resources.getIdentifier(entryName, type, context.packageName)
        if (resId == 0) return null
        return typed(context, resId)
    }
}
