package com.quransunah.app.core

import com.quransunah.app.BuildConfig

object AppConstants {
    /** Coloured QPC V4 tajweed page font (one per Madinah page, `public/fonts/qcf4/pN.ttf`). */
    fun qcf4PageAsset(pageNumber: Int): String =
        "fonts/qcf4/p${pageNumber.coerceIn(1, TOTAL_PAGES)}.ttf"

    const val TOTAL_PAGES = 604
    const val LINES_PER_PAGE = 15
    const val SURAH_COUNT = 114
    /** Side inset as a fraction of page width (was 0.045; reduced for a wider text column). */
    const val MUSHAF_HORIZONTAL_INSET = 0.028f
    const val HYDRATION_TIMEOUT_MS = 20_000L
    /** Page Typefaces kept in memory: current page ±[PAGE_FONT_PRELOAD_RADIUS] plus slack. */
    const val PAGE_FONT_CACHE_SIZE = 10
    const val PAGE_FONT_PRELOAD_RADIUS = 3
    const val PAGE_DATA_CACHE_SIZE = 16
    const val PAGE_CACHE_RADIUS = 4
    const val PAGE_SWIPE_COMMIT_PX = 50f
    const val PAGE_SWIPE_SLOP_PX = 8f
    const val PAGE_SETTLE_MS = 280
    const val WORD_LONG_PRESS_MS = 450L
    const val WORD_LONG_PRESS_MOVE_SLOP_PX = 10f
    const val TEXT_MUSHAF_MIN_SP = 22.7f
    const val TEXT_MUSHAF_MAX_SP = 34f
    const val TEXT_MUSHAF_DEFAULT_SP = 25f
    const val TEXT_MUSHAF_STEP_SP = 0.1f
    const val TAFSIR_MIN_SP = 14f
    const val TAFSIR_MAX_SP = 28f
    const val TAFSIR_DEFAULT_SP = 17f
    const val TAFSIR_STEP_SP = 0.5f
    const val DEFAULT_PLAYBACK_RATE = 1f
    val PLAYBACK_RATES = floatArrayOf(0.75f, 1f, 1.25f, 1.5f)
    const val SHARE_AYAH_RANGE_MAX = 10
    const val SEARCH_RESULT_LIMIT = 40
    const val SEARCH_MIN_QUERY_LENGTH = 2
    const val SEARCH_INDEX_VERSION = 2
    const val AYAH_JUMP_HIGHLIGHT_MS = 2_800L

    const val QURAN_DB_ASSET = "databases/quran_unified.db"
    const val QURAN_DB_FILE = "quran_unified.db"
    const val QURAN_DB_VERSION = "1"

    /**
     * Identity stored beside the installed Quran database.
     * Includes the bundled file hash so an updated asset replaces a previous copy.
     */
    fun quranDatabaseIdentity(): String =
        "$QURAN_DB_VERSION:${BuildConfig.QURAN_DB_CONTENT_HASH}:${BuildConfig.QURAN_DB_CONTENT_BYTES}"

    const val USER_DB_FILE = "holy_quran_user.db"

    const val QCF4_FONT_DIR = "fonts"
    /** Coloured QPC V4 page fonts live under assets/fonts/qcf4/pN.ttf (see qcf4PageAsset). */
    const val QBSML_FONT = "fonts/qbsml.ttf"
    const val JUZ_NAME_FONT = "fonts/juz.ttf"
    /** Body / lists / tafsir UI (Noto Naskh). */
    const val UI_BODY_FONT_REGULAR = "fonts/NotoNaskhArabic-Regular.ttf"
    const val UI_BODY_FONT_BOLD = "fonts/NotoNaskhArabic-Bold.ttf"
    /** Titles / chrome meta (Amiri). Not for QCF mushaf or header ligatures. */
    const val UI_DISPLAY_FONT_REGULAR = "fonts/Amiri-Regular.ttf"
    const val UI_DISPLAY_FONT_BOLD = "fonts/Amiri-Bold.ttf"
    const val UI_FONT_REGULAR = UI_BODY_FONT_REGULAR
    const val UI_FONT_BOLD = UI_BODY_FONT_BOLD
    const val SURAH_TITLE_FALLBACK_FONT = "fonts/ksa.ttf"
    const val UTHMANIC_HAFS_FILE = "fonts/hafs.ttf"
    const val SURAH_TITLE_FONT = QBSML_FONT

    const val RECITERS_ASSET = "json/reciters.json"
    const val WORD_AUDIO_ASSET_DIR = "audio/words"
    const val WORD_AUDIO_CDN = "https://audio.qurancdn.com"
    const val SURAH_AUDIO_CACHE_DIR = "audio"
    const val AYAT_TIMING_CACHE_DIR = "audio/timings"
    const val AYAT_TIMING_URL = "https://mp3quran.net/api/v3/ayat_timing"
    const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "quran_playback"
    const val DEFAULT_SURAH_RECITER_ID = 92
    const val DEFAULT_AYAH_RECITER_ID = "92"
    const val EXTRA_FROM_ANDROID_AUTO = "from_android_auto"
}
