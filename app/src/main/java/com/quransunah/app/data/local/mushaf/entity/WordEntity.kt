package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "words",
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["surah_id", "ayah_number"],
            childColumns = ["surah_id", "ayah_number"],
        ),
    ],
    indices = [
        Index(value = ["surah_id", "ayah_number", "word_position"], name = "idx_words_ayah_position"),
        Index(value = ["surah_id", "ayah_number"], name = "idx_words_surah_ayah"),
    ],
)
data class WordEntity(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Int?,
    @ColumnInfo(name = "surah_id")
    val surahId: Int,
    @ColumnInfo(name = "ayah_number")
    val ayahNumber: Int,
    @ColumnInfo(name = "word_position")
    val wordPosition: Int,
    @ColumnInfo(name = "text_hafs")
    val textHafs: String? = null,
    @ColumnInfo(name = "is_ayah_marker")
    val isAyahMarker: Int?,
    @ColumnInfo(name = "qpc_ligature")
    val qpcLigature: String? = null,
)
