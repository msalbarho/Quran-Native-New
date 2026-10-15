package com.quransunah.app.data.local.user.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Column order matches the legacy `training_log` table, with new columns appended.
 * Reordering fields would make the migration fail schema validation.
 */
@Entity(
    tableName = "training_log",
    indices = [Index(value = ["started_at"])],
)
data class TrainingLogEntity(
    @PrimaryKey val id: String,
    val mode: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    val surah: Int,
    @ColumnInfo(name = "start_ayah") val startAyah: Int,
    @ColumnInfo(name = "end_ayah") val endAyah: Int,
    @ColumnInfo(name = "last_ayah") val lastAyah: Int,
    val status: String,
    @ColumnInfo(name = "practiced_ayahs") val practicedAyahs: String,
    val assessments: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "page_number", defaultValue = "0") val pageNumber: Int,
    @ColumnInfo(name = "end_surah", defaultValue = "0") val endSurah: Int,
    @ColumnInfo(name = "last_surah", defaultValue = "0") val lastSurah: Int,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
)

@Entity(tableName = "training_position")
data class TrainingPositionEntity(
    @PrimaryKey val id: Int,
    val surah: Int,
    val ayah: Int,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)
