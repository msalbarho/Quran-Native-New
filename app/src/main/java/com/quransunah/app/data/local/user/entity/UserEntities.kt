package com.quransunah.app.data.local.user.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "bookmarks",
    indices = [
        Index(value = ["surah", "ayah"]),
        Index(value = ["saved_at"]),
    ],
)
data class BookmarkEntity(
    @PrimaryKey
    val id: String,
    val surah: Int,
    val ayah: Int,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "word_id")
    val wordId: Int,
    @ColumnInfo(name = "word_index")
    val wordIndex: Int,
    @ColumnInfo(name = "ayah_text")
    val ayahText: String,
    @ColumnInfo(name = "saved_at")
    val savedAt: Long,
)

@Entity(tableName = "page_meta")
data class PageMetaEntity(
    @PrimaryKey
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "juz_number")
    val juzNumber: Int,
    @ColumnInfo(name = "hizb_number")
    val hizbNumber: Int,
)

@Entity(
    tableName = "ayah_search",
    indices = [
        Index(value = ["normalized"]),
        Index(value = ["surah", "ayah"], unique = true),
    ],
)
data class AyahSearchEntity(
    @PrimaryKey
    @ColumnInfo(name = "ayah_key")
    val ayahKey: String,
    val surah: Int,
    val ayah: Int,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    @ColumnInfo(name = "text_hafs")
    val textHafs: String,
    val normalized: String,
)

@Entity(tableName = "word_meanings")
data class WordMeaningEntity(
    @PrimaryKey
    @ColumnInfo(name = "word_id")
    val wordId: Int,
    val meaning: String,
)
