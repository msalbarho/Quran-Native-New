package com.quransunah.app.data.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnifiedRecitersFile(
    val reciters: List<UnifiedReciterJson> = emptyList(),
)

@Serializable
data class UnifiedReciterJson(
    val id: String,
    val name: String,
    val letter: String? = null,
    val image: String? = null,
    val supportsSurahPlayback: Boolean,
    val supportsAyahPlayback: Boolean,
    val supportsAyahTiming: Boolean = false,
    val timingReciterId: Int? = null,
    val timingMoshafId: Int? = null,
    val surahAudio: SurahAudioJson? = null,
    val ayahAudio: AyahAudioJson? = null,
)

@Serializable
data class SurahAudioJson(
    val moshafs: List<MoshafJson> = emptyList(),
)

@Serializable
data class MoshafJson(
    val id: Int,
    val name: String,
    @SerialName("rewayaId") val rewayaId: Int? = null,
    val server: String,
    val surahNumbers: String,
)

@Serializable
data class AyahAudioJson(
    val type: AyahAudioType,
    val baseUrl: String,
)

@Serializable
enum class AyahAudioType {
    @SerialName("equran")
    EQURAN,

    @SerialName("islamic-network")
    ISLAMIC_NETWORK,

    @SerialName("mp3quran-timing")
    MP3QURAN_TIMING,
}

data class SurahReciter(
    val id: Int,
    val name: String,
    val letter: String?,
    val moshafs: List<MoshafEdition>,
) {
    fun preferredMoshaf(): MoshafEdition? {
        val fullHafs = moshafs.firstOrNull { it.rewayaId == 1 && it.surahNumbers.size >= 114 }
        if (fullHafs != null) return fullHafs
        return moshafs.maxByOrNull { it.surahNumbers.size } ?: moshafs.firstOrNull()
    }

    fun moshaf(id: Int): MoshafEdition? = moshafs.firstOrNull { it.id == id }
}

data class MoshafEdition(
    val id: Int,
    val reciterId: Int,
    val reciterName: String,
    val name: String,
    val server: String,
    val surahNumbers: Set<Int>,
    val rewayaId: Int = 0,
) {
    fun contains(surah: Int): Boolean = surahNumbers.contains(surah)
}

data class AyahReciter(
    val id: String,
    val name: String,
    val type: AyahAudioType,
    val baseUrl: String,
    val supportsSurahPlayback: Boolean = false,
    val supportsAyahPlayback: Boolean = true,
    val timingReciterId: Int? = null,
    val timingMoshafId: Int? = null,
)

data class UnifiedReciter(
    val id: String,
    val name: String,
    val letter: String? = null,
    val image: String? = null,
    val supportsSurahPlayback: Boolean,
    val supportsAyahPlayback: Boolean,
    val supportsAyahTiming: Boolean = false,
    val timingReciterId: Int? = null,
    val timingMoshafId: Int? = null,
    val surahAudio: SurahAudioConfig? = null,
    val ayahAudio: AyahAudioConfig? = null,
) {
    companion object {
        fun fromSurahReciter(reciter: SurahReciter): UnifiedReciter {
            return UnifiedReciter(
                id = reciter.id.toString(),
                name = reciter.name,
                letter = reciter.letter,
                supportsSurahPlayback = true,
                supportsAyahPlayback = false,
                surahAudio = SurahAudioConfig(
                    moshafs = reciter.moshafs.map { moshaf ->
                        SurahMoshafConfig(
                            id = moshaf.id,
                            name = moshaf.name,
                            server = moshaf.server,
                            surahNumbers = moshaf.surahNumbers,
                            rewayaId = moshaf.rewayaId,
                        )
                    },
                ),
            )
        }

        fun fromAyahReciter(reciter: AyahReciter): UnifiedReciter {
            return UnifiedReciter(
                id = reciter.id,
                name = reciter.name,
                supportsSurahPlayback = reciter.supportsSurahPlayback,
                supportsAyahPlayback = true,
                ayahAudio = AyahAudioConfig(
                    type = reciter.type,
                    baseUrl = reciter.baseUrl,
                ),
            )
        }
    }
}

data class SurahAudioConfig(
    val moshafs: List<SurahMoshafConfig>,
)

data class SurahMoshafConfig(
    val id: Int,
    val name: String,
    val server: String,
    val surahNumbers: Set<Int>,
    val rewayaId: Int = 0,
)

data class AyahAudioConfig(
    val type: AyahAudioType,
    val baseUrl: String,
)
