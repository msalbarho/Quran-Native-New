package com.quransunah.app.data.local.mushaf.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mushaf_editions")
data class MushafEditionEntity(
    @PrimaryKey
    val id: Int?,
    val name: String?,
    @ColumnInfo(name = "number_of_pages")
    val numberOfPages: Int?,
    @ColumnInfo(name = "lines_per_page")
    val linesPerPage: Int?,
    @ColumnInfo(name = "font_name")
    val fontName: String?,
)
