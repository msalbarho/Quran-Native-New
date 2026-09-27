package com.quransunah.app.media

import android.content.Context
import androidx.annotation.DrawableRes
import com.quransunah.app.R
import com.quransunah.app.media.auto.SurahArtworkIds
import java.util.Locale

/**
 * Single artwork map for Quran audio.
 * Lookup is by reciter id or surah number, never by display name.
 */
object AudioArtwork {
    @DrawableRes
    val notification: Int = R.drawable.ic_media_artwork

    @DrawableRes
    val surahFallback: Int = R.drawable.ic_auto_surah

    @DrawableRes
    val tabReciters: Int = R.drawable.ic_tab_reciters

    @DrawableRes
    val tabSurahs: Int = R.drawable.ic_tab_surahs

    @DrawableRes
    val launcher: Int = R.mipmap.ic_launcher

    private val reciters: Map<Int, Int> = mapOf(
        92 to R.drawable.reciter_dosari,
        5 to R.drawable.reciter_ajmi,
        123 to R.drawable.reciter_afasy,
        102 to R.drawable.reciter_maher,
        104 to R.drawable.reciter_hussary,
        62 to R.drawable.reciter_aljuhani,
        54 to R.drawable.reciter_sudais,
        31 to R.drawable.reciter_alshuraim,
        51 to R.drawable.reciter_basit,
    )

    @DrawableRes
    fun reciterDrawable(reciterId: Int): Int = reciters[reciterId] ?: 0

    @DrawableRes
    fun reciterDrawable(reciterId: String): Int =
        reciterId.toIntOrNull()?.let(::reciterDrawable) ?: 0

    fun surahDrawableName(surahNumber: Int): String =
        "ic_surah_%03d".format(Locale.US, surahNumber)

    @DrawableRes
    fun surahDrawable(context: Context, surahNumber: Int): Int {
        if (surahNumber !in 1..SurahArtworkIds.IDS.size) return 0
        val compiled = SurahArtworkIds.IDS[surahNumber - 1]
        if (compiled != 0) return compiled
        return context.resources.getIdentifier(
            surahDrawableName(surahNumber),
            "drawable",
            context.packageName,
        )
    }
}
