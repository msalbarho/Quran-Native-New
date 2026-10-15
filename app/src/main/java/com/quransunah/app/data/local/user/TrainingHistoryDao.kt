package com.quransunah.app.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quransunah.app.data.local.user.entity.TrainingLogEntity
import com.quransunah.app.data.local.user.entity.TrainingPositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrainingHistoryDao {
    @Query("SELECT * FROM training_position WHERE id = 1")
    fun observePosition(): Flow<TrainingPositionEntity?>

    @Query("SELECT * FROM training_position WHERE id = 1")
    suspend fun position(): TrainingPositionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPosition(row: TrainingPositionEntity)

    @Query("SELECT * FROM training_log WHERE status = 'ACTIVE' ORDER BY updated_at DESC LIMIT 1")
    suspend fun activeSession(): TrainingLogEntity?

    @Query("SELECT * FROM training_log ORDER BY started_at DESC LIMIT 1")
    suspend fun latestSession(): TrainingLogEntity?

    @Query("SELECT * FROM training_log ORDER BY started_at DESC")
    fun observeSessions(): Flow<List<TrainingLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(row: TrainingLogEntity)

    @Query(
        """
        UPDATE training_log
        SET status = 'FINISHED', ended_at = :endedAt
        WHERE id = :id AND status = 'ACTIVE'
        """,
    )
    suspend fun finishSession(id: String, endedAt: Long)
}
