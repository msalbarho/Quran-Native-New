package com.quransunah.app.data.local.user

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.quransunah.app.data.local.user.entity.AyahSearchEntity
import com.quransunah.app.data.local.user.entity.BookmarkEntity
import com.quransunah.app.data.local.user.entity.PageMetaEntity
import com.quransunah.app.data.local.user.entity.WordMeaningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY saved_at DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY saved_at DESC")
    suspend fun getBookmarks(): List<BookmarkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT * FROM bookmarks WHERE id = :id LIMIT 1")
    suspend fun get(id: String): BookmarkEntity?
}

@Dao
interface PageMetaDao {
    @Query("SELECT * FROM page_meta WHERE page_number = :pageNumber")
    suspend fun get(pageNumber: Int): PageMetaEntity?

    @Query("SELECT COUNT(*) FROM page_meta")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PageMetaEntity>)
}

@Dao
interface SearchDao {
    @Query("SELECT COUNT(*) FROM ayah_search")
    suspend fun count(): Int

    @Query("DELETE FROM ayah_search")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AyahSearchEntity>)

    @Query(
        """
        SELECT * FROM ayah_search
        WHERE normalized LIKE :prefix || '%'
        LIMIT :limit
        """,
    )
    suspend fun prefixSearch(prefix: String, limit: Int = 40): List<AyahSearchEntity>

    @Query(
        """
        SELECT * FROM ayah_search
        WHERE normalized LIKE '%' || :needle || '%'
        ORDER BY surah, ayah
        LIMIT :limit
        """,
    )
    suspend fun containsSearch(needle: String, limit: Int = 40): List<AyahSearchEntity>

    @Query("SELECT * FROM ayah_search WHERE surah = :surah AND ayah = :ayah LIMIT 1")
    suspend fun getReference(surah: Int, ayah: Int): AyahSearchEntity?
}

@Dao
interface WordMeaningDao {
    @Query("SELECT COUNT(*) FROM word_meanings")
    suspend fun count(): Int

    @Query("SELECT * FROM word_meanings")
    suspend fun getAll(): List<WordMeaningEntity>

    @Query("SELECT meaning FROM word_meanings WHERE word_id = :wordId")
    suspend fun getMeaning(wordId: Int): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WordMeaningEntity>)
}
