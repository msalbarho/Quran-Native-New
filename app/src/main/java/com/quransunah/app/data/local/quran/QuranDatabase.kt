package com.quransunah.app.data.local.quran

import androidx.room.Database
import androidx.room.RoomDatabase
import com.quransunah.app.data.local.mushaf.MushafDao
import com.quransunah.app.data.local.mushaf.entity.DivisionEntity
import com.quransunah.app.data.local.mushaf.entity.AyahEntity
import com.quransunah.app.data.local.mushaf.entity.MeaningEntity
import com.quransunah.app.data.local.mushaf.entity.MushafEditionEntity
import com.quransunah.app.data.local.mushaf.entity.MushafPageEntity
import com.quransunah.app.data.local.mushaf.entity.SurahEntity
import com.quransunah.app.data.local.mushaf.entity.WordEntity
import com.quransunah.app.data.local.markers.MarkersDao
import com.quransunah.app.data.local.tafsir.TafsirDao
import com.quransunah.app.data.local.tafsir.entity.ArabicMuyassarEntity
import com.quransunah.app.data.local.tafsir.entity.QuranTextEntity

@Database(
    entities = [
        WordEntity::class,
        AyahEntity::class,
        MushafPageEntity::class,
        SurahEntity::class,
        DivisionEntity::class,
        MushafEditionEntity::class,
        MeaningEntity::class,
        ArabicMuyassarEntity::class,
        QuranTextEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class QuranDatabase : RoomDatabase() {
    abstract fun mushafDao(): MushafDao
    abstract fun markersDao(): MarkersDao
    abstract fun tafsirDao(): TafsirDao
}