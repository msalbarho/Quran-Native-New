package com.quransunah.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quransunah.app.core.AppConstants
import com.quransunah.app.data.local.markers.MarkersDao
import com.quransunah.app.data.local.mushaf.MushafDao
import com.quransunah.app.data.local.quran.QuranDatabase
import com.quransunah.app.data.local.tafsir.TafsirDao
import com.quransunah.app.data.local.user.BookmarkDao
import com.quransunah.app.data.local.user.MemorizationDao
import com.quransunah.app.data.local.user.MemorizationPlanDao
import com.quransunah.app.data.local.user.PageMetaDao
import com.quransunah.app.data.local.user.SearchDao
import com.quransunah.app.data.local.user.UserDatabase
import com.quransunah.app.data.local.user.UserDatabaseMigrations
import com.quransunah.app.data.local.user.WordMeaningDao
import com.quransunah.app.data.packaged.PackagedStoreManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideQuranDatabase(
        @ApplicationContext context: Context,
        packaged: PackagedStoreManager,
    ): QuranDatabase {
        return packagedRoom(
            context,
            packaged,
            QuranDatabase::class.java,
            AppConstants.QURAN_DB_ASSET,
            AppConstants.QURAN_DB_FILE,
            AppConstants.QURAN_DB_VERSION,
        ) { builder ->
            builder.createFromAsset(
                AppConstants.QURAN_DB_ASSET,
                object : RoomDatabase.PrepackagedDatabaseCallback() {
                    override fun onOpenPrepackagedDatabase(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            "CREATE UNIQUE INDEX IF NOT EXISTS `index_ayahs_surah_id_ayah_number` " +
                                "ON `ayahs` (`surah_id`, `ayah_number`)",
                        )
                    }
                },
            )
        }
    }

    @Provides
    @Singleton
    fun provideUserDatabase(@ApplicationContext context: Context): UserDatabase {
        return Room.databaseBuilder(context, UserDatabase::class.java, AppConstants.USER_DB_FILE)
            .addMigrations(UserDatabaseMigrations.MIGRATION_1_2)
            .addMigrations(UserDatabaseMigrations.MIGRATION_2_3)
            .addMigrations(UserDatabaseMigrations.MIGRATION_3_4)
            .addMigrations(UserDatabaseMigrations.MIGRATION_4_5)
            .build()
    }

    @Provides
    fun provideMushafDao(db: QuranDatabase): MushafDao = db.mushafDao()

    @Provides
    fun provideMarkersDao(db: QuranDatabase): MarkersDao = db.markersDao()

    @Provides
    fun provideTafsirDao(db: QuranDatabase): TafsirDao = db.tafsirDao()

    @Provides
    fun provideBookmarkDao(db: UserDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideMemorizationDao(db: UserDatabase): MemorizationDao = db.memorizationDao()

    @Provides
    fun provideMemorizationPlanDao(db: UserDatabase): MemorizationPlanDao = db.memorizationPlanDao()

    @Provides
    fun providePageMetaDao(db: UserDatabase): PageMetaDao = db.pageMetaDao()

    @Provides
    fun provideSearchDao(db: UserDatabase): SearchDao = db.searchDao()

    @Provides
    fun provideWordMeaningDao(db: UserDatabase): WordMeaningDao = db.wordMeaningDao()

    @Provides
    @Singleton
    fun provideSettingsStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("holy_quran_settings")
        }
    }

    private fun <T : RoomDatabase> packagedRoom(
        context: Context,
        packaged: PackagedStoreManager,
        klass: Class<T>,
        assetPath: String,
        destName: String,
        version: String,
        configure: (RoomDatabase.Builder<T>) -> RoomDatabase.Builder<T>,
    ): T {
        packaged.invalidateStaleSync(assetPath, destName, version)
        val builder = Room.databaseBuilder(context, klass, destName)
            .createFromAsset(assetPath)
        return configure(builder).build()
    }
}
