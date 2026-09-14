package com.quransunah.app.data.repository

import com.quransunah.app.data.local.user.MemorizationDao
import com.quransunah.app.data.local.user.MemorizationPlanDao
import com.quransunah.app.data.local.user.entity.MemorizationEntity
import com.quransunah.app.data.local.user.entity.MemorizationPlanEntity
import com.quransunah.app.data.local.user.entity.MemorizationSessionEntity
import com.quransunah.app.data.local.user.entity.MemorizationRecordingEntity
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.domain.model.MemorizationPlan
import com.quransunah.app.domain.model.MemorizationSession
import com.quransunah.app.domain.model.MemorizationRecording
import com.quransunah.app.domain.model.isValidAyahRange
import com.quransunah.app.domain.model.hasValidReference
import com.quransunah.app.domain.repository.MemorizationRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class MemorizationRepositoryImpl @Inject constructor(
    private val memorizationDao: MemorizationDao,
    private val planDao: MemorizationPlanDao,
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

    override fun observePlans(): Flow<List<MemorizationPlan>> = planDao.observePlans().map { rows -> rows.map { it.toDomain() } }

    override suspend fun savePlan(plan: MemorizationPlan) {
        require(isValidAyahRange(plan)) { "Invalid memorization plan range or daily target" }
        planDao.upsertPlan(plan.toEntity())
    }

    override suspend fun startSession(planId: String, at: Long): String {
        require(planDao.getPlan(planId) != null) { "Unknown memorization plan" }
        val id = "$planId:session:$at"
        planDao.insertSession(MemorizationSessionEntity(id, planId, at, null, 0, 0))
        return id
    }

    override suspend fun finishSession(sessionId: String, reviewed: Int, mastered: Int, at: Long) {
        planDao.finishSession(sessionId, at, reviewed.coerceAtLeast(0), mastered.coerceAtLeast(0))
    }

    override fun observeSessions(planId: String): Flow<List<MemorizationSession>> =
        planDao.observeSessions(planId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun saveRecording(recording: MemorizationRecording) {
        require(planDao.getPlan(recording.sessionId.substringBefore(":session:")) != null) { "Recording must belong to a known plan session" }
        require(recording.filePath.isNotBlank() && recording.durationMs >= 0) { "Invalid recording metadata" }
        planDao.insertRecording(
            MemorizationRecordingEntity(recording.id, recording.sessionId, recording.filePath, recording.createdAt, recording.durationMs),
        )
    }

    override fun observeRecordings(sessionId: String): Flow<List<MemorizationRecording>> =
        planDao.observeRecordings(sessionId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun deleteRecording(id: String) = planDao.deleteRecording(id)

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

    private fun MemorizationPlanEntity.toDomain() = MemorizationPlan(
        id, name, startSurah, startAyah, endSurah, endAyah, dailyTarget, createdAt, updatedAt,
    )

    private fun MemorizationPlan.toEntity() = MemorizationPlanEntity(
        id, name, startSurah, startAyah, endSurah, endAyah, dailyTarget, createdAt, updatedAt,
    )

    private fun MemorizationSessionEntity.toDomain() = MemorizationSession(
        id, planId, startedAt, finishedAt, reviewedCount, masteredCount,
    )

    private fun MemorizationRecordingEntity.toDomain() = MemorizationRecording(
        id, sessionId, filePath, createdAt, durationMs,
    )
}
