package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mushaf_pages",
    foreignKeys = [
        ForeignKey(entity = MushafEditionEntity::class, parentColumns = ["id"], childColumns = ["mushaf_id"]),
        ForeignKey(entity = WordEntity::class, parentColumns = ["word_id"], childColumns = ["first_word_index"]),
        ForeignKey(entity = WordEntity::class, parentColumns = ["word_id"], childColumns = ["last_word_index"]),
        ForeignKey(entity = SurahEntity::class, parentColumns = ["id"], childColumns = ["surah_id"]),
    ],
    indices = [
        Index(value = ["mushaf_id", "page_number", "line_number"], name = "idx_mushaf_pages_page"),
        Index(
            value = ["first_word_index", "last_word_index"],
            name = "idx_mushaf_pages_word_range",
        ),
    ],
)
data class MushafPageEntity(
    @PrimaryKey
    val id: Int? = null,
    @ColumnInfo(name = "mushaf_id")
    val mushafId: Int,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "line_number")
    val lineNumber: Int,
    @ColumnInfo(name = "line_type")
    val lineType: String?,
    @ColumnInfo(name = "is_centered")
    val isCentered: Int?,
    @ColumnInfo(name = "first_word_index")
    val firstWordIndex: Int? = null,
    @ColumnInfo(name = "last_word_index")
    val lastWordIndex: Int? = null,
    @ColumnInfo(name = "surah_id")
    val surahId: Int? = null,
)
