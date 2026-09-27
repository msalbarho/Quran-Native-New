package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "ayahs",
    foreignKeys = [
        ForeignKey(
            entity = SurahEntity::class,
            parentColumns = ["id"],
            childColumns = ["surah_id"],
        ),
    ],
    indices = [
        Index(value = ["surah_id", "ayah_number"], name = "idx_ayahs_surah_ayah"),
        Index(value = ["surah_id", "ayah_number"], unique = true, name = "index_ayahs_surah_id_ayah_number"),
    ],
)
data class AyahEntity(
    @PrimaryKey
    @ColumnInfo(name = "global_ayah_index")
    val globalAyahIndex: Int?,
    @ColumnInfo(name = "surah_id")
    val surahId: Int,
    @ColumnInfo(name = "ayah_number")
    val ayahNumber: Int,
)
