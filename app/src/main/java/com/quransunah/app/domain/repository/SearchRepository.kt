package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.AyahSearchHit

interface SearchRepository {
    suspend fun ensureIndex()
    suspend fun search(query: String): List<AyahSearchHit>
}
