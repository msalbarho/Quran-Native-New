package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "surahs")
data class SurahEntity(
    @PrimaryKey
    val id: Int?,
    @ColumnInfo(name = "name_arabic")
    val nameArabic: String,
    @ColumnInfo(name = "number_of_ayahs")
    val numberOfAyahs: Int,
    @ColumnInfo(name = "revelation_type")
    val revelationType: String?,
)
