package com.quransunah.app.data.repository

import com.quransunah.app.data.local.user.BookmarkDao
import com.quransunah.app.data.local.user.entity.BookmarkEntity
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.domain.repository.BookmarkRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class BookmarkRepositoryImpl @Inject constructor(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {
    override fun observeBookmarks(): Flow<List<ReadingBookmark>> =
        bookmarkDao.observeBookmarks().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): ReadingBookmark? =
        bookmarkDao.get(id)?.toDomain()

    override suspend fun isSaved(surah: Int, ayah: Int): Boolean =
        bookmarkDao.get(ReadingBookmark.idFor(surah, ayah)) != null

    override suspend fun save(bookmark: ReadingBookmark) {
        bookmarkDao.upsert(bookmark.toEntity())
    }

    override suspend fun delete(id: String) {
        bookmarkDao.delete(id)
    }

    private fun BookmarkEntity.toDomain() = ReadingBookmark(
        id = id,
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        wordId = wordId,
        wordIndex = wordIndex,
        ayahText = ayahText,
        savedAt = savedAt,
    )

    private fun ReadingBookmark.toEntity() = BookmarkEntity(
        id = id,
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        wordId = wordId,
        wordIndex = wordIndex,
        ayahText = ayahText,
        savedAt = savedAt,
    )
}
