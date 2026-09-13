package com.quransunah.app.data.local.tafsir.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import com.quransunah.app.data.local.mushaf.entity.AyahEntity

@Entity(
    tableName = "tafsir_muyassar",
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["surah_id", "ayah_number"],
            childColumns = ["sura", "aya"],
        ),
    ],
    indices = [
        Index(value = ["sura", "aya"], name = "idx_tafsir_sura_aya"),
    ],
)
data class ArabicMuyassarEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val sura: Int,
    val aya: Int,
    val text: String?,
)

@Entity(
    tableName = "quran_text",
    foreignKeys = [
        ForeignKey(
            entity = AyahEntity::class,
            parentColumns = ["surah_id", "ayah_number"],
            childColumns = ["sura", "aya"],
        ),
    ],
    indices = [
        Index(value = ["sura", "aya"], name = "idx_quran_text_sura_aya"),
    ],
)
data class QuranTextEntity(
    @PrimaryKey
    @ColumnInfo(name = "index")
    val index: Int? = null,
    val sura: Int,
    val aya: Int,
    val text: String?,
)
