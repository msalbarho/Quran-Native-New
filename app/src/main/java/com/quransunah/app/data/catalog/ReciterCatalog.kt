package com.quransunah.app.data.catalog

import android.content.Context
import com.quransunah.app.core.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class ReciterCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var unifiedReciters: List<UnifiedReciter>? = null

    @Volatile
    private var surahReciters: List<SurahReciter>? = null

    @Volatile
    private var ayahReciters: List<AyahReciter>? = null

    @Volatile
    private var moshafById: Map<Int, MoshafEdition>? = null

    fun unifiedReciters(): List<UnifiedReciter> {
        unifiedReciters?.let { return it }
        return synchronized(this) {
            unifiedReciters ?: loadUnifiedReciters().also { unifiedReciters = it }
        }
    }

    fun surahReciters(): List<SurahReciter> {
        surahReciters?.let { return it }
        return synchronized(this) {
            surahReciters ?: loadSurahRecitersFromUnified().also { loaded ->
                surahReciters = loaded
                moshafById = loaded.flatMap { reciter -> reciter.moshafs }.associateBy { it.id }
            }
        }
    }

    fun ayahReciters(): List<AyahReciter> {
        ayahReciters?.let { return it }
        return synchronized(this) {
            ayahReciters ?: loadAyahRecitersFromUnified().also { ayahReciters = it }
        }
    }

    fun surahReciter(id: Int): SurahReciter? = surahReciters().firstOrNull { it.id == id }

    fun ayahReciter(id: String): AyahReciter? {
        return ayahReciters().firstOrNull { it.id == id } ?: defaultAyahReciter().takeIf { it.id == id }
    }

    fun unifiedReciter(id: String): UnifiedReciter? = unifiedReciters().firstOrNull { it.id == id }

    fun moshaf(moshafId: Int): MoshafEdition? {
        surahReciters()
        return moshafById?.get(moshafId)
    }

    fun defaultSurahReciter(): SurahReciter? {
        return surahReciter(AppConstants.DEFAULT_SURAH_RECITER_ID) ?: surahReciters().firstOrNull()
    }

    fun reciterForMoshaf(moshafId: Int): SurahReciter? {
        return surahReciters().firstOrNull { reciter -> reciter.moshafs.any { it.id == moshafId } }
    }

    fun defaultAyahReciter(): AyahReciter {
        return ayahReciters().firstOrNull { it.id == AppConstants.DEFAULT_AYAH_RECITER_ID }
            ?: ayahReciters().firstOrNull()
            ?: FALLBACK_AYAH_RECITER
    }

    fun getAyahCapableReciters(): List<AyahReciter> {
        return ayahReciters().filter { it.supportsAyahPlayback }
    }

    private fun loadUnifiedReciters(): List<UnifiedReciter> {
        val parsed = runCatching {
            context.assets.open(AppConstants.RECITERS_ASSET).bufferedReader().use { reader ->
                json.decodeFromString<UnifiedRecitersFile>(reader.readText())
            }
        }.getOrElse { return emptyList() }
        return parsed.reciters.map { reciter ->
            UnifiedReciter(
                id = reciter.id,
                name = reciter.name,
                letter = reciter.letter,
                image = reciter.image,
                supportsSurahPlayback = reciter.supportsSurahPlayback,
                supportsAyahPlayback = reciter.supportsAyahPlayback,
                supportsAyahTiming = reciter.supportsAyahTiming,
                timingReciterId = reciter.timingReciterId,
                timingMoshafId = reciter.timingMoshafId,
                surahAudio = reciter.surahAudio?.let { audio ->
                    SurahAudioConfig(
                        moshafs = audio.moshafs.map { moshaf ->
                            SurahMoshafConfig(
                                id = moshaf.id,
                                name = moshaf.name,
                                server = moshaf.server.trimEnd('/'),
                                surahNumbers = parseSurahList(moshaf.surahNumbers),
                                rewayaId = moshaf.rewayaId ?: 0,
                            )
                        },
                    )
                },
                ayahAudio = reciter.ayahAudio?.let { audio ->
                    AyahAudioConfig(
                        type = audio.type,
                        baseUrl = audio.baseUrl.trimEnd('/'),
                    )
                },
            )
        }
    }

    private fun loadSurahRecitersFromUnified(): List<SurahReciter> {
        val unified = loadUnifiedReciters()
        return unified.filter { it.supportsSurahPlayback }.mapIndexed { index, reciter ->
            val surahAudio = reciter.surahAudio ?: return@mapIndexed null
            SurahReciter(
                id = reciter.id.toIntOrNull() ?: (index + 100),
                name = reciter.name,
                letter = reciter.letter,
                moshafs = surahAudio.moshafs.map { moshaf ->
                    MoshafEdition(
                        id = moshaf.id,
                        reciterId = reciter.id.toIntOrNull() ?: (index + 100),
                        reciterName = reciter.name,
                        name = moshaf.name,
                        server = moshaf.server,
                        surahNumbers = moshaf.surahNumbers,
                        rewayaId = moshaf.rewayaId,
                    )
                },
            )
        }.filterNotNull()
    }

    private fun loadAyahRecitersFromUnified(): List<AyahReciter> {
        val unified = loadUnifiedReciters()
        return unified.filter { it.supportsAyahPlayback }.map { reciter ->
            val ayahAudio = reciter.ayahAudio
            if (ayahAudio == null && !reciter.supportsAyahTiming) return@map null
            AyahReciter(
                id = reciter.id,
                name = reciter.name,
                type = if (reciter.supportsAyahTiming) {
                    AyahAudioType.MP3QURAN_TIMING
                } else {
                    ayahAudio!!.type
                },
                baseUrl = ayahAudio?.baseUrl.orEmpty(),
                supportsSurahPlayback = reciter.supportsSurahPlayback,
                supportsAyahPlayback = true,
                timingReciterId = reciter.timingReciterId,
                timingMoshafId = reciter.timingMoshafId,
            )
        }.filterNotNull().ifEmpty { listOf(FALLBACK_AYAH_RECITER) }
    }

    private fun parseSurahList(raw: String): Set<Int> {
        if (raw.isBlank()) return (1..AppConstants.SURAH_COUNT).toSet()
        return raw.split(',')
            .mapNotNull { token -> token.trim().toIntOrNull() }
            .filter { it in 1..AppConstants.SURAH_COUNT }
            .toSet()
    }

    companion object {
        val FALLBACK_AYAH_RECITER = AyahReciter(
            id = AppConstants.DEFAULT_AYAH_RECITER_ID,
            name = "الشيخ ياسر الدوسري",
            type = AyahAudioType.EQURAN,
            baseUrl = "https://cdn.equran.id/audio-partial/Yasser-Al-Dosari",
            supportsAyahPlayback = true,
        )
    }
}
