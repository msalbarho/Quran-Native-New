package com.quransunah.app.media

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.SurahRepeatMode

object PlaybackMediaIds {
    const val ROOT = "root"
    const val RECITERS = "reciters"
    const val SURAHS = "surahs"

    const val EXTRA_DOMAIN = "playback_domain"
    const val EXTRA_SURAH = "playback_surah"
    const val EXTRA_SURAH_NAME = "playback_surah_name"
    const val EXTRA_AYAH = "playback_ayah"
    const val EXTRA_AYAH_ID = "playback_ayah_id"
    const val EXTRA_WORD_ID = "playback_word_id"
    const val EXTRA_WORD_POSITION = "playback_word_position"
    const val EXTRA_RECITER_NAME = "playback_reciter_name"
    const val EXTRA_RECITER_ID = "playback_reciter_id"
    const val EXTRA_MOSHAF_ID = "playback_moshaf_id"
    const val EXTRA_FROM_LOCAL = "playback_from_local"
    const val EXTRA_REPEAT = "playback_repeat"

    private const val RECITER_PREFIX = "reciter:"
    private const val BROWSE_SURAHS_PREFIX = "browse:surahs:"
    private const val BROWSE_RECITER_PREFIX = "browse:reciter:"

    data class SurahPlayRequest(
        val surah: Int,
        val moshafId: Int? = null,
        val reciterId: Int? = null,
    )

    fun surah(moshafId: Int, surah: Int): String = "surah:$moshafId:$surah"

    fun ayah(reciterId: String, surah: Int, ayah: Int): String = "ayah:$reciterId:$surah:$ayah"

    fun word(wordId: Int, surah: Int, ayah: Int, position: Int): String =
        "word:$wordId:$surah:$ayah:$position"

    fun reciter(reciterId: Int): String = "$RECITER_PREFIX$reciterId"

    fun browseFlatSurah(surah: Int): String = "$BROWSE_SURAHS_PREFIX$surah"

    fun browseReciterSurah(reciterId: Int, surah: Int): String =
        "$BROWSE_RECITER_PREFIX$reciterId:surah:$surah"

    fun parseSurah(mediaId: String?): Pair<Int, Int>? {
        val parts = mediaId?.split(':') ?: return null
        if (parts.size != 3 || parts[0] != "surah") return null
        val moshafId = parts[1].toIntOrNull() ?: return null
        val surah = parts[2].toIntOrNull() ?: return null
        return moshafId to surah
    }

    fun parseAyah(mediaId: String?): Triple<String, Int, Int>? {
        val parts = mediaId?.split(':') ?: return null
        if (parts.size != 4 || parts[0] != "ayah") return null
        val reciterId = parts[1].ifBlank { return null }
        val surah = parts[2].toIntOrNull() ?: return null
        val ayah = parts[3].toIntOrNull() ?: return null
        return Triple(reciterId, surah, ayah)
    }

    fun parseReciter(mediaId: String?): Int? {
        if (mediaId.isNullOrBlank() || !mediaId.startsWith(RECITER_PREFIX)) return null
        if (mediaId.contains(":surah:")) return null
        return mediaId.removePrefix(RECITER_PREFIX).toIntOrNull()
    }

    fun parsePlayRequest(mediaId: String?): SurahPlayRequest? {
        if (mediaId.isNullOrBlank()) return null
        parseBrowseRequest(mediaId)?.let { return it }
        parseSurah(mediaId)?.let { (moshafId, surah) ->
            return SurahPlayRequest(surah = surah, moshafId = moshafId)
        }
        parseReciter(mediaId)?.let { reciterId ->
            return SurahPlayRequest(surah = 1, reciterId = reciterId)
        }
        return null
    }

    fun parseBrowseRequest(mediaId: String?): SurahPlayRequest? {
        if (mediaId.isNullOrBlank()) return null
        if (mediaId.startsWith(BROWSE_SURAHS_PREFIX)) {
            val surah = mediaId.removePrefix(BROWSE_SURAHS_PREFIX).toIntOrNull() ?: return null
            return SurahPlayRequest(surah = surah)
        }
        if (mediaId.startsWith(BROWSE_RECITER_PREFIX) && mediaId.contains(":surah:")) {
            val rest = mediaId.removePrefix(BROWSE_RECITER_PREFIX)
            val parts = rest.split(":surah:")
            if (parts.size == 2) {
                val reciterId = parts[0].toIntOrNull() ?: return null
                val surah = parts[1].toIntOrNull() ?: return null
                return SurahPlayRequest(surah = surah, reciterId = reciterId)
            }
        }
        return null
    }

    fun extras(
        domain: PlaybackDomain,
        reciterName: String,
        reciterId: String,
        surah: Int,
        surahName: String,
        ayah: Int? = null,
        wordId: Int? = null,
        wordPosition: Int? = null,
        moshafId: Int? = null,
        fromLocal: Boolean = false,
        repeatMode: SurahRepeatMode = SurahRepeatMode.OFF,
    ): Bundle {
        return Bundle().apply {
            putString(EXTRA_DOMAIN, domain.name)
            putString(EXTRA_RECITER_NAME, reciterName)
            putString(EXTRA_RECITER_ID, reciterId)
            putInt(EXTRA_SURAH, surah)
            putString(EXTRA_SURAH_NAME, surahName)
            ayah?.let {
                putInt(EXTRA_AYAH, it)
                val ayahId = SurahAyahCounts.ayahId(surah, it)
                if (ayahId > 0) putInt(EXTRA_AYAH_ID, ayahId)
            }
            wordId?.let { putInt(EXTRA_WORD_ID, it) }
            wordPosition?.let { putInt(EXTRA_WORD_POSITION, it) }
            moshafId?.let { putInt(EXTRA_MOSHAF_ID, it) }
            putBoolean(EXTRA_FROM_LOCAL, fromLocal)
            putString(EXTRA_REPEAT, repeatMode.name)
        }
    }

    fun mediaItem(
        mediaId: String,
        uri: Uri,
        title: String,
        artist: String,
        subtitle: String?,
        extras: Bundle,
        artwork: Uri? = null,
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setSubtitle(subtitle)
            .setAlbumTitle(artist)
            .setIsPlayable(true)
            .setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(artwork)
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .build()
    }
}
