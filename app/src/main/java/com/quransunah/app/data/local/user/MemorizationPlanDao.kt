package com.quransunah.app.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quransunah.app.data.local.user.entity.MemorizationPlanEntity
import com.quransunah.app.data.local.user.entity.MemorizationSessionEntity
import com.quransunah.app.data.local.user.entity.MemorizationRecordingEntity
import com.quransunah.app.data.local.user.entity.RecitationAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemorizationPlanDao {
    @Query("SELECT * FROM memorization_plans ORDER BY updated_at DESC")
    fun observePlans(): Flow<List<MemorizationPlanEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlan(plan: MemorizationPlanEntity)

    @Query("SELECT * FROM memorization_plans WHERE id = :id LIMIT 1")
    suspend fun getPlan(id: String): MemorizationPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: MemorizationSessionEntity)

    @Query("UPDATE memorization_sessions SET finished_at = :finishedAt, reviewed_count = :reviewed, mastered_count = :mastered WHERE id = :id")
    suspend fun finishSession(id: String, finishedAt: Long, reviewed: Int, mastered: Int)

    @Query("SELECT * FROM memorization_sessions WHERE plan_id = :planId ORDER BY started_at DESC")
    fun observeSessions(planId: String): Flow<List<MemorizationSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecording(recording: MemorizationRecordingEntity)

    @Query("SELECT * FROM memorization_recordings WHERE session_id = :sessionId ORDER BY created_at DESC")
    fun observeRecordings(sessionId: String): Flow<List<MemorizationRecordingEntity>>

    @Query("DELETE FROM memorization_recordings WHERE id = :id")
    suspend fun deleteRecording(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: RecitationAttemptEntity)

    @Query("SELECT * FROM recitation_attempts WHERE session_id = :sessionId ORDER BY created_at DESC")
    fun observeAttempts(sessionId: String): Flow<List<RecitationAttemptEntity>>
}
