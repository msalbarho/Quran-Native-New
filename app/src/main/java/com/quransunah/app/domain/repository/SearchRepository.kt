package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.AyahSearchHit

interface SearchRepository {
    suspend fun ensureIndex()
    suspend fun search(query: String): List<AyahSearchHit>

    /**
     * Text matches only, ordered by surah then ayah.
     * Plain numeric references stay on [search] so callers can avoid them.
     */
    suspend fun searchVerses(query: String, limit: Int, offset: Int): List<AyahSearchHit>
}
