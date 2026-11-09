package com.quransunah.app.share

import com.quransunah.app.core.SurahAyahCounts

/**
 * QUL (Tarteel) recitation ids that ship word-level ayah clocks.
 * Audio URLs follow the matching Tarteel CDN so highlights stay in sync.
 */
object QulRecitations {
    data class Spec(
        val recitationId: Int,
        /** Tarteel ayah-mp3 folder; null for surah-level recitations. */
        val ayahFolder: String? = null,
        /** Quran.com CDN reciter id when the QUL resource is surah-by-surah. */
        val qurancdnReciterId: Int? = null,
    ) {
        val isSurahLevel: Boolean get() = qurancdnReciterId != null
    }

    private val byReciterId = mapOf(
        "123" to Spec(18, ayahFolder = "alafasy"),
        "92" to Spec(26, ayahFolder = "yasserAlDosari"),
        "husary" to Spec(22, ayahFolder = "husaryMuallim"),
        "102" to Spec(13, ayahFolder = "maherAlMuaiqly"),
        "51" to Spec(15, ayahFolder = "abdulBasitMurattal"),
        "54" to Spec(16, ayahFolder = "abdulrahmanAlSudais"),
        "31" to Spec(25, ayahFolder = "saudAlShuraim"),
        "62" to Spec(162, qurancdnReciterId = 162),
    )

    fun spec(reciterId: String): Spec? = byReciterId[reciterId]

    fun reciterId(recitationId: Int): String? =
        byReciterId.entries.firstOrNull { it.value.recitationId == recitationId }?.key

    fun recitationId(reciterId: String): Int? = spec(reciterId)?.recitationId

    /** Ayah-level QUL catalogs only. Surah files can list a verse window with no words. */
    fun hasWordTimingCatalog(reciterId: String): Boolean = spec(reciterId)?.ayahFolder != null

    fun ayahAudioUrl(reciterId: String, surah: Int, ayah: Int): String? {
        val folder = spec(reciterId)?.ayahFolder ?: return null
        val name = "${SurahAyahCounts.pad3(surah)}${SurahAyahCounts.pad3(ayah)}.mp3"
        return "https://audio-cdn.tarteel.ai/quran/$folder/$name"
    }

    fun qurancdnChapterUrl(reciterId: Int, surah: Int): String =
        "https://api.qurancdn.com/api/qdc/audio/reciters/$reciterId/audio_files?chapter=$surah&segments=true"
}
