package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.ReadingBookmark
import kotlinx.coroutines.flow.Flow

interface BookmarkRepository {
    fun observeBookmarks(): Flow<List<ReadingBookmark>>
    suspend fun get(id: String): ReadingBookmark?
    suspend fun isSaved(surah: Int, ayah: Int): Boolean
    suspend fun save(bookmark: ReadingBookmark)
    suspend fun delete(id: String)
}

typealias BookmarksRepository = BookmarkRepository
