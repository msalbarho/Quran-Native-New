package com.quransunah.app.data.local.user.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memorization_plans",
    indices = [Index(value = ["updated_at"])],
)
data class MemorizationPlanEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "start_surah") val startSurah: Int,
    @ColumnInfo(name = "start_ayah") val startAyah: Int,
    @ColumnInfo(name = "end_surah") val endSurah: Int,
    @ColumnInfo(name = "end_ayah") val endAyah: Int,
    @ColumnInfo(name = "daily_target") val dailyTarget: Int,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "memorization_sessions",
    indices = [Index(value = ["plan_id", "started_at"])],
)
data class MemorizationSessionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "plan_id") val planId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "finished_at") val finishedAt: Long?,
    @ColumnInfo(name = "reviewed_count") val reviewedCount: Int,
    @ColumnInfo(name = "mastered_count") val masteredCount: Int,
)
