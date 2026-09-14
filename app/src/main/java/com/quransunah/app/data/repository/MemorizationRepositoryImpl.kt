package com.quransunah.app.data.repository

import com.quransunah.app.data.local.user.MemorizationDao
import com.quransunah.app.data.local.user.entity.MemorizationEntity
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.domain.model.hasValidReference
import com.quransunah.app.domain.repository.MemorizationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemorizationRepositoryImpl @Inject constructor(
    private val memorizationDao: MemorizationDao,
) : MemorizationRepository {
    override fun observeItems(): Flow<List<MemorizationItem>> =
        memorizationDao.observeItems().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): MemorizationItem? = memorizationDao.get(id)?.toDomain()

    override suspend fun isTracked(surah: Int, ayah: Int): Boolean =
        memorizationDao.get(MemorizationItem.idFor(surah, ayah)) != null

    override suspend fun track(item: MemorizationItem) {
        require(item.hasValidReference()) { "Invalid Quran reference for memorization" }
        require(item.ayahText.isNotBlank()) { "Memorization ayah text cannot be blank" }
        memorizationDao.upsert(item.toEntity())
    }

    override suspend fun setState(id: String, state: MemorizationState, updatedAt: Long) {
        memorizationDao.updateState(id, state.name, updatedAt)
    }

    override suspend fun recordReview(id: String, updatedAt: Long) {
        memorizationDao.incrementReviewCount(id, updatedAt)
    }

    override suspend fun untrack(id: String) {
        memorizationDao.delete(id)
    }

    private fun MemorizationEntity.toDomain() = MemorizationItem(
        id = id,
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        ayahText = ayahText,
        state = MemorizationState.fromStored(state),
        reviewCount = reviewCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MemorizationItem.toEntity() = MemorizationEntity(
        id = id,
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        ayahText = ayahText,
        state = state.name,
        reviewCount = reviewCount.coerceAtLeast(0),
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
