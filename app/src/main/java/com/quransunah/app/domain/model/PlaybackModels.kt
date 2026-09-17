package com.quransunah.app.domain.model

enum class PlaybackDomain {
    IDLE,
    SURAH,
    AYAH,
    WORD,
}

enum class SurahRepeatMode {
    OFF,
    ONE,
    REMAINING,
}

enum class SleepTimerMode {
    OFF,
    MINUTES_15,
    MINUTES_30,
    MINUTES_60,
    END_OF_SURAH,
}

data class SleepTimerState(
    val mode: SleepTimerMode = SleepTimerMode.OFF,
    val endAtMs: Long = 0L,
)

enum class AudioQuality(val id: String) {
    HIGH("high"),
    SAVER("saver"),
    ;

    companion object {
        fun fromId(id: String): AudioQuality = entries.find { it.id == id } ?: HIGH
    }
}

data class PlaybackSnapshot(
    val domain: PlaybackDomain = PlaybackDomain.IDLE,
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val isBuffering: Boolean = false,
    val fromLocalCache: Boolean = false,
    val reciterName: String? = null,
    val reciterId: String? = null,
    val moshafId: Int? = null,
    val surah: Int? = null,
    val surahName: String? = null,
    val ayah: Int? = null,
    val wordId: Int? = null,
    val wordPosition: Int? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val repeatMode: SurahRepeatMode = SurahRepeatMode.OFF,
    val ayahId: Int? = null,
    val errorMessage: String? = null,
)

data class PlaybackProgress(
    val domain: PlaybackDomain = PlaybackDomain.IDLE,
    val surah: Int = 1,
    val ayah: Int = 0,
    val ayahId: Int = 0,
    val positionMs: Long = 0L,
    val moshafId: Int? = null,
    val reciterId: String? = null,
)
