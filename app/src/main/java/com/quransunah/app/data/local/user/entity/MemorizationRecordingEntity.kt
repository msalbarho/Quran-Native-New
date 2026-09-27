package com.quransunah.app.data.local.user.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memorization_recordings",
    indices = [Index(value = ["session_id", "created_at"])],
)
data class MemorizationRecordingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
)
