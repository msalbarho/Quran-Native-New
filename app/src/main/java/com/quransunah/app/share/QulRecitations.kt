package com.quransunah.app.share

import com.quransunah.app.core.SurahAyahCounts

/**
 * QUL (Tarteel) / KFGQPC recitation ids that ship word-level ayah clocks.
 * Audio URLs follow the matching CDN so highlights stay in sync.
 */
object QulRecitations {
    data class Spec(
        val recitationId: Int,
        /** Tarteel ayah-mp3 folder; null for surah-level recitations. */
        val ayahFolder: String? = null,
        /** Quran.com CDN reciter id when the QUL resource is surah-by-surah. */
        val qurancdnReciterId: Int? = null,
        /**
         * King Fahd Complex (KFGQPC) Maher Hafs Mojawwad surah server
         * (mp3quran mirror of official download.qurancomplex.gov.sa muaiqly-08/hafs).
         */
        val kfcSurahServer: String? = null,
        /** mp3quran ayat_timing read id paired with [kfcSurahServer]. */
        val kfcTimingReadId: Int? = null,
        /** Asset folder under assets/ with per-surah KFC word clocks. */
        val kfcTimingAssetDir: String? = null,
    ) {
        val isSurahLevel: Boolean
            get() = qurancdnReciterId != null || kfcSurahServer != null
        val isKfc: Boolean get() = kfcSurahServer != null
    }

    private val byReciterId = mapOf(
        "123" to Spec(18, ayahFolder = "alafasy"),
        "92" to Spec(26, ayahFolder = "yasserAlDosari"),
        "husary" to Spec(22, ayahFolder = "husaryMuallim"),
        "102" to Spec(
            recitationId = 13,
            kfcSurahServer = "https://server12.mp3quran.net/maher/Almusshaf-Al-Mojawwad",
            kfcTimingReadId = 133,
            kfcTimingAssetDir = "kfc-maher-timings",
        ),
        "51" to Spec(15, ayahFolder = "abdulBasitMurattal"),
        "54" to Spec(16, ayahFolder = "abdulrahmanAlSudais"),
        "31" to Spec(25, ayahFolder = "saudAlShuraim"),
        "62" to Spec(162, qurancdnReciterId = 162),
    )

    fun spec(reciterId: String): Spec? = byReciterId[reciterId]

    fun reciterId(recitationId: Int): String? =
        byReciterId.entries.firstOrNull { it.value.recitationId == recitationId }?.key

    fun recitationId(reciterId: String): Int? = spec(reciterId)?.recitationId

    /** Ayah-level QUL catalogs or bundled KFC Maher word clocks. */
    fun hasWordTimingCatalog(reciterId: String): Boolean {
        val s = spec(reciterId) ?: return false
        return s.ayahFolder != null || s.kfcTimingAssetDir != null
    }

    fun ayahAudioUrl(reciterId: String, surah: Int, ayah: Int): String? {
        val folder = spec(reciterId)?.ayahFolder ?: return null
        val name = "${SurahAyahCounts.pad3(surah)}${SurahAyahCounts.pad3(ayah)}.mp3"
        return "https://audio-cdn.tarteel.ai/quran/$folder/$name"
    }

    fun kfcSurahAudioUrl(reciterId: String, surah: Int): String? {
        val server = spec(reciterId)?.kfcSurahServer ?: return null
        return "${server.trimEnd('/')}/${SurahAyahCounts.pad3(surah)}.mp3"
    }

    fun qurancdnChapterUrl(reciterId: Int, surah: Int): String =
        "https://api.qurancdn.com/api/qdc/audio/reciters/$reciterId/audio_files?chapter=$surah&segments=true"

    /** Attribution for King Fahd Complex Maher audio when used in credits/about. */
    const val KFGQPC_MAHER_ATTRIBUTION =
        "Maher Al-Muaiqly audio: King Fahd Complex for the Printing of the Holy Qur'an (KFGQPC)"
}
