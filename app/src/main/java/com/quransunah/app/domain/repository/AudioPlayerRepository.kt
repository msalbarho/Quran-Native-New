package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SurahRepeatMode
import kotlinx.coroutines.flow.StateFlow

interface AudioPlayerRepository {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun warmup()

    fun prefetchSurahPlayback(reciterId: Int, moshafId: Int, surah: Int)

    suspend fun playSurah(
        reciterId: Int,
        moshafId: Int,
        surah: Int,
        repeatMode: SurahRepeatMode = SurahRepeatMode.OFF,
        startAyah: Int = 1,
    ): Result<Unit>

    suspend fun playAyah(
        reciterId: String,
        surah: Int,
        ayah: Int,
    ): Result<Unit>

    suspend fun playAyahRange(
        reciterId: String,
        startSurah: Int,
        startAyah: Int,
        endSurah: Int,
        endAyah: Int,
    ): Result<Unit>

    suspend fun playWord(
        wordId: Int,
        surah: Int,
        ayah: Int,
        wordPosition: Int,
    ): Result<Unit>

    fun pause()
    fun resume()
    fun stop()
    fun seekTo(positionMs: Long)
    suspend fun seekToAyah(surah: Int, ayah: Int)
    suspend fun seekToWord(wordId: Int, surah: Int, ayah: Int, wordPosition: Int)
    fun skipNext()
    fun skipPrevious()
    fun setRepeatMode(mode: SurahRepeatMode)
    fun setPlaybackRate(rate: Float)
}
