package com.quransunah.app.data.local.user

import androidx.room.Database
import androidx.room.RoomDatabase
import com.quransunah.app.data.local.user.entity.AyahSearchEntity
import com.quransunah.app.data.local.user.entity.BookmarkEntity
import com.quransunah.app.data.local.user.entity.MemorizationEntity
import com.quransunah.app.data.local.user.entity.PageMetaEntity
import com.quransunah.app.data.local.user.entity.WordMeaningEntity

@Database(
    entities = [
        BookmarkEntity::class,
        MemorizationEntity::class,
        PageMetaEntity::class,
        AyahSearchEntity::class,
        WordMeaningEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun memorizationDao(): MemorizationDao
    abstract fun pageMetaDao(): PageMetaDao
    abstract fun searchDao(): SearchDao
    abstract fun wordMeaningDao(): WordMeaningDao
}
