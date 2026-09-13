package com.quransunah.app.core

object AppConstants {
    fun qcf4FaceAsset(faceNumber: Int): String =
        "fonts/qcf4/QCF4_Hafs_%02d.ttf".format(java.util.Locale.US, faceNumber.coerceIn(1, 47))

    const val TOTAL_PAGES = 604
    const val LINES_PER_PAGE = 15
    const val SURAH_COUNT = 114
    /** Side inset as a fraction of page width (was 0.045; reduced for a wider text column). */
    const val MUSHAF_HORIZONTAL_INSET = 0.028f
    const val HYDRATION_TIMEOUT_MS = 20_000L
    const val PAGE_FONT_CACHE_SIZE = 8
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

    const val USER_DB_FILE = "holy_quran_user.db"

    const val QCF4_FONT_DIR = "fonts"
    const val QCF4_FACE_DIR = "fonts/qcf4"
    const val QCF4_FONT_MAP_ASSET = "json/qcf4-font-map.json"
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
