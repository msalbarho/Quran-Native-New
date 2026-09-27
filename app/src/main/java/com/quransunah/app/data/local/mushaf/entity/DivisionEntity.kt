package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "divisions",
    foreignKeys = [
        ForeignKey(entity = AyahEntity::class, parentColumns = ["global_ayah_index"], childColumns = ["ayah_id"]),
        ForeignKey(entity = MushafPageEntity::class, parentColumns = ["id"], childColumns = ["page_id"]),
        ForeignKey(entity = DivisionEntity::class, parentColumns = ["id"], childColumns = ["parent_id"]),
    ],
    indices = [
        Index(value = ["ayah_id"], name = "idx_divisions_ayah"),
        Index(value = ["parent_id"], name = "idx_divisions_parent"),
        Index(value = ["division_type", "division_number"], name = "idx_divisions_type_number"),
        Index(value = ["start_surah", "start_ayah"], name = "idx_divisions_start"),
    ],
)
data class DivisionEntity(
    @PrimaryKey
    val id: Int?,
    @ColumnInfo(name = "division_type")
    val divisionType: String,
    @ColumnInfo(name = "division_type_id")
    val divisionTypeId: Int?,
    @ColumnInfo(name = "division_number")
    val divisionNumber: Int? = null,
    @ColumnInfo(name = "start_surah")
    val startSurah: Int?,
    @ColumnInfo(name = "start_ayah")
    val startAyah: Int?,
    @ColumnInfo(name = "end_surah")
    val endSurah: Int? = null,
    @ColumnInfo(name = "end_ayah")
    val endAyah: Int? = null,
    val name: String? = null,
    @ColumnInfo(name = "quarter_number")
    val quarterNumber: Int? = null,
    @ColumnInfo(name = "quarter_type_label")
    val quarterTypeLabel: String? = null,
    @ColumnInfo(name = "page_id")
    val pageId: Int? = null,
    @ColumnInfo(name = "end_page_number")
    val endPageNumber: Int? = null,
    @ColumnInfo(name = "ayah_id")
    val ayahId: Int? = null,
    val mark: String? = null,
    @ColumnInfo(name = "hex_str")
    val hexStr: String? = null,
    @ColumnInfo(name = "parent_id")
    val parentId: Int? = null,
    @ColumnInfo(name = "verse_count")
    val verseCount: Int? = null,
    @ColumnInfo(name = "marker_number")
    val markerNumber: Int? = null,
    @ColumnInfo(name = "marker_label")
    val markerLabel: String? = null,
    @ColumnInfo(name = "marker_hizb_number")
    val markerHizbNumber: Int? = null,
    @ColumnInfo(name = "marker_quarter_type_label")
    val markerQuarterTypeLabel: String? = null,
)
