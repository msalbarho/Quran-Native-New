package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationPlan
import com.quransunah.app.domain.model.MemorizationSession
import com.quransunah.app.domain.model.MemorizationRecording
import com.quransunah.app.domain.model.MemorizationState
import kotlinx.coroutines.flow.Flow

interface MemorizationRepository {
    fun observeItems(): Flow<List<MemorizationItem>>
    suspend fun get(id: String): MemorizationItem?
    suspend fun isTracked(surah: Int, ayah: Int): Boolean
    suspend fun track(item: MemorizationItem)
    suspend fun setState(id: String, state: MemorizationState, updatedAt: Long)
    suspend fun recordReview(id: String, updatedAt: Long)
    suspend fun untrack(id: String)
    fun observePlans(): Flow<List<MemorizationPlan>>
    suspend fun savePlan(plan: MemorizationPlan)
    suspend fun startSession(planId: String, at: Long): String
    suspend fun finishSession(sessionId: String, reviewed: Int, mastered: Int, at: Long)
    fun observeSessions(planId: String): Flow<List<MemorizationSession>>
    suspend fun saveRecording(recording: MemorizationRecording)
    fun observeRecordings(sessionId: String): Flow<List<MemorizationRecording>>
    suspend fun deleteRecording(id: String)
}
