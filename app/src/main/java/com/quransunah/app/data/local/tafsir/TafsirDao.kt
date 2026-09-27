package com.quransunah.app.data.local.tafsir

import androidx.room.Dao
import androidx.room.Query

@Dao
interface TafsirDao {
    @Query("SELECT text FROM tafsir_muyassar WHERE sura = :surah AND aya = :ayah LIMIT 1")
    suspend fun getMuyassar(surah: Int, ayah: Int): String?
}
