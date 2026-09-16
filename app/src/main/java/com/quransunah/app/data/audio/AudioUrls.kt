package com.quransunah.app.data.audio

import android.net.Uri
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.catalog.AyahAudioType
import com.quransunah.app.data.catalog.AyahReciter
import com.quransunah.app.data.catalog.MoshafEdition
import com.quransunah.app.domain.model.AudioQuality

object AudioUrls {
    fun surahStreamUri(moshaf: MoshafEdition, surah: Int): Uri {
        val file = "${SurahAyahCounts.pad3(surah)}.mp3"
        return Uri.parse("${moshaf.server}/$file")
    }

    fun ayahStreamUri(
        reciter: AyahReciter,
        surah: Int,
        ayah: Int,
        quality: AudioQuality = AudioQuality.HIGH,
    ): Uri {
        val path = when (reciter.type) {
            AyahAudioType.ISLAMIC_NETWORK ->
                "${SurahAyahCounts.globalAyahIndex(surah, ayah)}.mp3"
            AyahAudioType.EQURAN ->
                "${SurahAyahCounts.pad3(surah)}${SurahAyahCounts.pad3(ayah)}.mp3"
            AyahAudioType.MP3QURAN_TIMING ->
                error("MP3Quran timing reciters use full-surah playback")
        }
        val base = when {
            quality == AudioQuality.SAVER && reciter.baseUrl.contains("/audio/128/") ->
                reciter.baseUrl.replace("/audio/128/", "/audio/64/")
            else -> reciter.baseUrl
        }
        return Uri.parse("$base/$path")
    }

    fun wordCdnUri(surah: Int, ayah: Int, wordPosition: Int): Uri {
        val name = listOf(surah, ayah, wordPosition).joinToString("_") { SurahAyahCounts.pad3(it) }
        return Uri.parse("${AppConstants.WORD_AUDIO_CDN}/wbw/$name.mp3")
    }

    fun wordAssetPath(wordId: Int): String {
        return "${AppConstants.WORD_AUDIO_ASSET_DIR}/${SurahAyahCounts.pad5(wordId)}.mp3"
    }

    fun wordAssetUri(wordId: Int): Uri {
        return Uri.parse("asset:///${wordAssetPath(wordId)}")
    }
}
