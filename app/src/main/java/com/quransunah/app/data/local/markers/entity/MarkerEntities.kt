package com.quransunah.app.data.local.markers.entity

import androidx.room.ColumnInfo

data class RubEntity(
    @ColumnInfo(name = "rub_number")
    val rubNumber: Int? = null,
    @ColumnInfo(name = "verses_count")
    val versesCount: Int? = null,
    @ColumnInfo(name = "first_verse_key")
    val firstVerseKey: String? = null,
    @ColumnInfo(name = "last_verse_key")
    val lastVerseKey: String? = null,
    @ColumnInfo(name = "verse_mapping")
    val verseMapping: String? = null,
    @ColumnInfo(name = "hizb_number")
    val hizbNumber: Int? = null,
    @ColumnInfo(name = "quarter_type_label")
    val quarterTypeLabel: String? = null,
    val label: String? = null,
)

data class SajdaEntity(
    @ColumnInfo(name = "sajdah_number")
    val sajdahNumber: Int? = null,
    @ColumnInfo(name = "verse_key")
    val verseKey: String? = null,
    @ColumnInfo(name = "sajdah_type")
    val sajdahType: String? = null,
)

data class JuzMarkerEntity(
    @ColumnInfo(name = "juz_number")
    val juzNumber: Int? = null,
    @ColumnInfo(name = "verses_count")
    val versesCount: Int? = null,
    @ColumnInfo(name = "first_verse_key")
    val firstVerseKey: String? = null,
    @ColumnInfo(name = "last_verse_key")
    val lastVerseKey: String? = null,
    @ColumnInfo(name = "verse_mapping")
    val verseMapping: String? = null,
)

data class HizbMarkerEntity(
    @ColumnInfo(name = "hizb_number")
    val hizbNumber: Int? = null,
    @ColumnInfo(name = "verses_count")
    val versesCount: Int? = null,
    @ColumnInfo(name = "first_verse_key")
    val firstVerseKey: String? = null,
    @ColumnInfo(name = "last_verse_key")
    val lastVerseKey: String? = null,
    @ColumnInfo(name = "verse_mapping")
    val verseMapping: String? = null,
)
