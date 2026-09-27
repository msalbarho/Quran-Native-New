package com.quransunah.app.data.repository

import android.database.sqlite.SQLiteDatabase
import com.quransunah.app.data.catalog.TranslationRegistry
import com.quransunah.app.data.packaged.PackagedStoreManager
import com.quransunah.app.domain.repository.TranslationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TranslationRepositoryImpl @Inject constructor(
    private val packaged: PackagedStoreManager,
) : TranslationRepository {
    private val mutex = Mutex()
    private val databases = mutableMapOf<String, SQLiteDatabase>()

    override suspend fun getAyahTranslation(locale: String, surah: Int, ayah: Int): String? {
        val entry = TranslationRegistry.forLanguage(locale) ?: return null
        val db = database(entry)
        db.query(
            TABLE,
            arrayOf(COLUMN_TEXT),
            "sura = ? AND ayah = ?",
            arrayOf(surah.toString(), ayah.toString()),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.getString(0)
        }
    }

    private suspend fun database(entry: TranslationRegistry.Translation): SQLiteDatabase = mutex.withLock {
        val key = entry.languageTag.lowercase()
        databases[key]?.takeIf { it.isOpen }?.let { return it }
        val file = packaged.ensureCopied(
            entry.assetPath,
            "translation_${entry.languageTag}.sqlite",
            ASSET_VERSION,
        )
        SQLiteDatabase.openDatabase(
            file.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).also { databases[key] = it }
    }

    private companion object {
        const val ASSET_VERSION = "1"
        const val TABLE = "translation"
        const val COLUMN_TEXT = "text"
    }
}
