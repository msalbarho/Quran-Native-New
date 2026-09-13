package com.quransunah.app.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.domain.model.AudioQuality
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackProgress
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.ui.theme.PaperPaletteId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class UserSettings(
    val lastPage: Int = 1,
    val lastTab: String = "reading",
    val palette: PaperPaletteId = PaperPaletteId.Beige,
    val nightMode: Boolean = false,
    val medinaMode: Boolean = true,
    val textSizeSp: Float = AppConstants.TEXT_MUSHAF_DEFAULT_SP,
    val onboardingDone: Boolean = false,
    val coachMarkDone: Boolean = false,
    val lastReciterId: Int = AppConstants.DEFAULT_SURAH_RECITER_ID,
    val lastListeningSurah: Int = 1,
    val lastRepeatMode: String = SurahRepeatMode.OFF.name,
    val lastAyahReciterId: String = AppConstants.DEFAULT_AYAH_RECITER_ID,
    val lastVideoReciterId: String = AppConstants.DEFAULT_AYAH_RECITER_ID,
    val keepScreenOn: Boolean = false,
    val tafsirSizeSp: Float = AppConstants.TAFSIR_DEFAULT_SP,
    val audioQuality: AudioQuality = AudioQuality.HIGH,
    val playbackRate: Float = AppConstants.DEFAULT_PLAYBACK_RATE,
)

@Singleton
class UserPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val liveTextSize = MutableStateFlow<Float?>(null)

    val settings: Flow<UserSettings> = dataStore.data.map { prefs ->
        UserSettings(
            lastPage = (prefs[Keys.LAST_PAGE] ?: 1).coerceIn(1, AppConstants.TOTAL_PAGES),
            lastTab = prefs[Keys.LAST_TAB] ?: "reading",
            palette = PaperPaletteId.fromId(prefs[Keys.PALETTE] ?: PaperPaletteId.Beige.id),
            nightMode = prefs[Keys.NIGHT] ?: false,
            medinaMode = prefs[Keys.MEDINA] ?: true,
            textSizeSp = storedTextSize(prefs),
            onboardingDone = prefs[Keys.ONBOARDING] ?: false,
            coachMarkDone = prefs[Keys.COACH] ?: false,
            lastReciterId = prefs[Keys.RECITER] ?: AppConstants.DEFAULT_SURAH_RECITER_ID,
            lastListeningSurah = (prefs[Keys.LISTENING_SURAH] ?: 1).coerceIn(1, AppConstants.SURAH_COUNT),
            lastRepeatMode = prefs[Keys.REPEAT] ?: SurahRepeatMode.OFF.name,
            lastAyahReciterId = prefs[Keys.AYAH_RECITER] ?: AppConstants.DEFAULT_AYAH_RECITER_ID,
            lastVideoReciterId = prefs[Keys.VIDEO_RECITER] ?: AppConstants.DEFAULT_AYAH_RECITER_ID,
            keepScreenOn = prefs[Keys.KEEP_SCREEN] ?: false,
            tafsirSizeSp = (prefs[Keys.TAFSIR_SIZE] ?: AppConstants.TAFSIR_DEFAULT_SP)
                .coerceIn(AppConstants.TAFSIR_MIN_SP, AppConstants.TAFSIR_MAX_SP),
            audioQuality = AudioQuality.fromId(prefs[Keys.AUDIO_QUALITY] ?: AudioQuality.HIGH.id),
            playbackRate = (prefs[Keys.PLAYBACK_RATE] ?: AppConstants.DEFAULT_PLAYBACK_RATE)
                .let { rate -> AppConstants.PLAYBACK_RATES.minBy { kotlin.math.abs(it - rate) } },
        )
    }.distinctUntilChanged()

    /** Live Hafs size for the text mushaf only — does not recompose the shell settings. */
    val textSizeSp: Flow<Float> = combine(dataStore.data, liveTextSize) { prefs, live ->
        (live ?: storedTextSize(prefs))
            .coerceIn(AppConstants.TEXT_MUSHAF_MIN_SP, AppConstants.TEXT_MUSHAF_MAX_SP)
    }.distinctUntilChanged()

    val playbackProgress: Flow<PlaybackProgress> = dataStore.data.map { prefs ->
        val domain = prefs[Keys.PLAYBACK_DOMAIN]
            ?.let { runCatching { PlaybackDomain.valueOf(it) }.getOrNull() }
            ?: PlaybackDomain.IDLE
        val surah = (prefs[Keys.PLAYBACK_SURAH] ?: prefs[Keys.LISTENING_SURAH] ?: 1)
            .coerceIn(1, AppConstants.SURAH_COUNT)
        val ayah = (prefs[Keys.PLAYBACK_AYAH] ?: 0).coerceAtLeast(0)
        val storedId = prefs[Keys.PLAYBACK_AYAH_ID] ?: 0
        PlaybackProgress(
            domain = domain,
            surah = surah,
            ayah = ayah,
            ayahId = storedId.takeIf { it > 0 } ?: SurahAyahCounts.ayahId(surah, ayah),
            positionMs = (prefs[Keys.PLAYBACK_POSITION_MS] ?: 0L).coerceAtLeast(0L),
            moshafId = prefs[Keys.PLAYBACK_MOSHAF_ID],
            reciterId = prefs[Keys.PLAYBACK_RECITER_ID],
        )
    }.distinctUntilChanged()

    suspend fun setLastPage(page: Int) {
        dataStore.edit { it[Keys.LAST_PAGE] = page.coerceIn(1, AppConstants.TOTAL_PAGES) }
    }

    suspend fun setLastTab(tab: String) {
        dataStore.edit { it[Keys.LAST_TAB] = tab }
    }

    suspend fun setPalette(id: PaperPaletteId) {
        dataStore.edit { it[Keys.PALETTE] = id.id }
    }

    suspend fun setNightMode(night: Boolean) {
        dataStore.edit { it[Keys.NIGHT] = night }
    }

    suspend fun setMedinaMode(medina: Boolean) {
        dataStore.edit { it[Keys.MEDINA] = medina }
    }

    fun previewTextSize(size: Float) {
        liveTextSize.value = size.coerceIn(
            AppConstants.TEXT_MUSHAF_MIN_SP,
            AppConstants.TEXT_MUSHAF_MAX_SP,
        )
    }

    suspend fun setTextSize(size: Float) {
        val clamped = snapTextSize(size)
        liveTextSize.value = clamped
        dataStore.edit {
            it[Keys.TEXT_SIZE] = clamped
        }
    }

    private fun storedTextSize(prefs: Preferences): Float =
        (prefs[Keys.TEXT_SIZE] ?: AppConstants.TEXT_MUSHAF_DEFAULT_SP)
            .coerceIn(AppConstants.TEXT_MUSHAF_MIN_SP, AppConstants.TEXT_MUSHAF_MAX_SP)

    private fun snapTextSize(size: Float): Float {
        val min = AppConstants.TEXT_MUSHAF_MIN_SP
        val max = AppConstants.TEXT_MUSHAF_MAX_SP
        val step = AppConstants.TEXT_MUSHAF_STEP_SP
        val snapped = min + kotlin.math.round((size - min) / step) * step
        return snapped.coerceIn(min, max)
    }

    suspend fun setLastReciterId(id: Int) {
        dataStore.edit { it[Keys.RECITER] = id }
    }

    suspend fun setLastListeningSurah(surah: Int) {
        dataStore.edit { it[Keys.LISTENING_SURAH] = surah.coerceIn(1, AppConstants.SURAH_COUNT) }
    }

    suspend fun setLastRepeatMode(mode: SurahRepeatMode) {
        dataStore.edit { it[Keys.REPEAT] = mode.name }
    }

    suspend fun setLastAyahReciterId(id: String) {
        dataStore.edit { it[Keys.AYAH_RECITER] = id }
    }

    suspend fun setLastVideoReciterId(id: String) {
        dataStore.edit { it[Keys.VIDEO_RECITER] = id }
    }

    suspend fun setKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.KEEP_SCREEN] = enabled }
    }

    suspend fun setTafsirSize(size: Float) {
        dataStore.edit {
            it[Keys.TAFSIR_SIZE] = size.coerceIn(AppConstants.TAFSIR_MIN_SP, AppConstants.TAFSIR_MAX_SP)
        }
    }

    suspend fun setAudioQuality(quality: AudioQuality) {
        dataStore.edit { it[Keys.AUDIO_QUALITY] = quality.id }
    }

    suspend fun setPlaybackRate(rate: Float) {
        val nearest = AppConstants.PLAYBACK_RATES.minBy { kotlin.math.abs(it - rate) }
        dataStore.edit { it[Keys.PLAYBACK_RATE] = nearest }
    }

    suspend fun getSearchIndexVersion(): Int =
        dataStore.data.first()[Keys.SEARCH_INDEX_VERSION] ?: 0

    suspend fun setSearchIndexVersion(version: Int) {
        dataStore.edit { it[Keys.SEARCH_INDEX_VERSION] = version }
    }

    suspend fun savePlaybackProgress(progress: PlaybackProgress) {
        dataStore.edit { prefs ->
            prefs[Keys.PLAYBACK_DOMAIN] = progress.domain.name
            prefs[Keys.PLAYBACK_SURAH] = progress.surah.coerceIn(1, AppConstants.SURAH_COUNT)
            prefs[Keys.PLAYBACK_AYAH] = progress.ayah.coerceAtLeast(0)
            prefs[Keys.PLAYBACK_AYAH_ID] = progress.ayahId.coerceAtLeast(0)
            prefs[Keys.PLAYBACK_POSITION_MS] = progress.positionMs.coerceAtLeast(0L)
            val moshafId = progress.moshafId
            if (moshafId != null) {
                prefs[Keys.PLAYBACK_MOSHAF_ID] = moshafId
            } else {
                prefs.remove(Keys.PLAYBACK_MOSHAF_ID)
            }
            val reciterId = progress.reciterId
            if (!reciterId.isNullOrBlank()) {
                prefs[Keys.PLAYBACK_RECITER_ID] = reciterId
            } else {
                prefs.remove(Keys.PLAYBACK_RECITER_ID)
            }
            if (progress.domain == PlaybackDomain.SURAH) {
                prefs[Keys.LISTENING_SURAH] = progress.surah.coerceIn(1, AppConstants.SURAH_COUNT)
                reciterId?.toIntOrNull()?.let { prefs[Keys.RECITER] = it }
            }
        }
    }

    private object Keys {
        val LAST_PAGE = intPreferencesKey("last_page")
        val LAST_TAB = stringPreferencesKey("last_tab")
        val PALETTE = stringPreferencesKey("palette")
        val NIGHT = booleanPreferencesKey("night_mode")
        val MEDINA = booleanPreferencesKey("medina_mode")
        val TEXT_SIZE = floatPreferencesKey("text_size_sp")
        val ONBOARDING = booleanPreferencesKey("onboarding_done")
        val COACH = booleanPreferencesKey("coach_mark_done")
        val RECITER = intPreferencesKey("last_reciter_id")
        val LISTENING_SURAH = intPreferencesKey("last_listening_surah")
        val REPEAT = stringPreferencesKey("last_repeat_mode")
        val AYAH_RECITER = stringPreferencesKey("last_ayah_reciter_id")
        val VIDEO_RECITER = stringPreferencesKey("last_video_reciter_id")
        val KEEP_SCREEN = booleanPreferencesKey("keep_screen_on")
        val TAFSIR_SIZE = floatPreferencesKey("tafsir_size_sp")
        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val PLAYBACK_RATE = floatPreferencesKey("playback_rate")
        val PLAYBACK_DOMAIN = stringPreferencesKey("playback_domain")
        val PLAYBACK_SURAH = intPreferencesKey("playback_surah")
        val PLAYBACK_AYAH = intPreferencesKey("playback_ayah")
        val PLAYBACK_AYAH_ID = intPreferencesKey("playback_ayah_id")
        val PLAYBACK_POSITION_MS = longPreferencesKey("playback_position_ms")
        val PLAYBACK_MOSHAF_ID = intPreferencesKey("playback_moshaf_id")
        val PLAYBACK_RECITER_ID = stringPreferencesKey("playback_reciter_id")
        val SEARCH_INDEX_VERSION = intPreferencesKey("search_index_version")
    }
}
