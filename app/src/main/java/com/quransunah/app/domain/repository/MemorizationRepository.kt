package com.quransunah.app.domain.repository

import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import kotlinx.coroutines.flow.Flow

interface MemorizationRepository {
    fun observeItems(): Flow<List<MemorizationItem>>
    suspend fun get(id: String): MemorizationItem?
    suspend fun isTracked(surah: Int, ayah: Int): Boolean
    suspend fun track(item: MemorizationItem)
    suspend fun setState(id: String, state: MemorizationState, updatedAt: Long)
    suspend fun recordReview(id: String, updatedAt: Long)
    suspend fun untrack(id: String)
}
