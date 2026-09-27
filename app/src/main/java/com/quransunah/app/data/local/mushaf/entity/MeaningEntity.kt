package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "meanings",
    foreignKeys = [
        ForeignKey(entity = AyahEntity::class, parentColumns = ["global_ayah_index"], childColumns = ["ayah_id"]),
    ],
    indices = [
        Index(value = ["ayah_id"], name = "idx_meanings_ayah"),
    ],
)
data class MeaningEntity(
    @PrimaryKey
    val id: Int? = null,
    @ColumnInfo(name = "ayah_id")
    val ayahId: Int,
    val word: String? = null,
    val text: String? = null,
)
