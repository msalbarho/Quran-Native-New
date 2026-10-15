package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.TrainingAssessmentResult
import com.quransunah.app.domain.model.TrainingPosition
import com.quransunah.app.domain.model.TrainingSession
import kotlinx.coroutines.flow.Flow

interface TrainingHistoryRepository {
    fun observePosition(): Flow<TrainingPosition?>

    fun observeSessions(): Flow<List<TrainingSession>>

    suspend fun currentPosition(): TrainingPosition?

    suspend fun latestSession(): TrainingSession?

    suspend fun recordPractice(surah: Int, ayah: Int, pageNumber: Int, now: Long)

    suspend fun recordAssessment(
        surah: Int,
        ayah: Int,
        pageNumber: Int,
        result: TrainingAssessmentResult,
        now: Long,
    )

    suspend fun rememberPosition(surah: Int, ayah: Int, pageNumber: Int, now: Long)

    suspend fun finishActiveSession(now: Long)
}
