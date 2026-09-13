package com.quransunah.app.data.repository

import com.quransunah.app.core.AppConstants
import com.quransunah.app.data.local.quran.QuranDatabase
import com.quransunah.app.data.packaged.PackagedStoreManager
import com.quransunah.app.domain.repository.TafsirRepository
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TafsirRepositoryImpl @Inject constructor(
    private val databaseProvider: Provider<QuranDatabase>,
    private val packaged: PackagedStoreManager,
) : TafsirRepository {
    private val mutex = Mutex()
    @Volatile private var database: QuranDatabase? = null

    override suspend fun getAyahTafsir(surah: Int, ayah: Int): String? = mutex.withLock {
        val db = database ?: databaseProvider.get().also { database = it }
        val text = db.tafsirDao().getMuyassar(surah, ayah)
        packaged.markCurrent(
            AppConstants.QURAN_DB_ASSET,
            AppConstants.QURAN_DB_FILE,
            AppConstants.QURAN_DB_VERSION,
        )
        text
    }

    override fun release() {
        database?.close()
        database = null
    }
}
