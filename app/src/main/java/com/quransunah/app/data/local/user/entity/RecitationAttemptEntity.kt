package com.quransunah.app.data.local.user.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "recitation_attempts",
    indices = [Index(value = ["session_id", "created_at"])],
)
data class RecitationAttemptEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "session_id") val sessionId: String,
    @ColumnInfo(name = "recording_id") val recordingId: String?,
    @ColumnInfo(name = "score_percent") val scorePercent: Int,
    @ColumnInfo(name = "missing_count") val missingCount: Int,
    @ColumnInfo(name = "extra_count") val extraCount: Int,
    @ColumnInfo(name = "different_count") val differentCount: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
