package com.quransunah.app.data.repository

import com.quransunah.app.data.local.user.TrainingHistoryDao
import com.quransunah.app.data.local.user.entity.TrainingLogEntity
import com.quransunah.app.data.local.user.entity.TrainingPositionEntity
import com.quransunah.app.domain.model.TrainingAssessmentResult
import com.quransunah.app.domain.model.TrainingLog
import com.quransunah.app.domain.model.TrainingPosition
import com.quransunah.app.domain.model.TrainingSession
import com.quransunah.app.domain.model.TrainingSessionStatus
import com.quransunah.app.domain.repository.TrainingHistoryRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class TrainingHistoryRepositoryImpl @Inject constructor(
    private val dao: TrainingHistoryDao,
) : TrainingHistoryRepository {
    private val gate = Mutex()

    override fun observePosition(): Flow<TrainingPosition?> =
        dao.observePosition().map { row -> row?.toDomain() }

    override fun observeSessions(): Flow<List<TrainingSession>> =
        dao.observeSessions().map { rows -> rows.map { it.toDomain() } }

    override suspend fun currentPosition(): TrainingPosition? = dao.position()?.toDomain()

    override suspend fun latestSession(): TrainingSession? = dao.latestSession()?.toDomain()

    override suspend fun recordPractice(surah: Int, ayah: Int, pageNumber: Int, now: Long) {
        gate.withLock {
            val write = TrainingLog.practice(dao.activeSession()?.toDomain(), surah, ayah, pageNumber, now)
            write.finish?.let { dao.finishSession(it.id, it.endedAt ?: it.updatedAt) }
            dao.upsertSession(write.save.toEntity())
            dao.upsertPosition(positionRow(surah, ayah, pageNumber, now))
        }
    }

    override suspend fun recordAssessment(
        surah: Int,
        ayah: Int,
        pageNumber: Int,
        result: TrainingAssessmentResult,
        now: Long,
    ) {
        gate.withLock {
            val write = TrainingLog.assessment(
                dao.activeSession()?.toDomain(),
                surah,
                ayah,
                pageNumber,
                result,
                now,
            )
            write.finish?.let { dao.finishSession(it.id, it.endedAt ?: it.updatedAt) }
            dao.upsertSession(write.save.toEntity())
            dao.upsertPosition(positionRow(surah, ayah, pageNumber, now))
        }
    }

    override suspend fun rememberPosition(surah: Int, ayah: Int, pageNumber: Int, now: Long) {
        gate.withLock {
            dao.upsertPosition(positionRow(surah, ayah, pageNumber, now))
        }
    }

    override suspend fun finishActiveSession(now: Long) {
        gate.withLock {
            val active = dao.activeSession() ?: return@withLock
            dao.finishSession(active.id, now)
        }
    }

    private fun positionRow(surah: Int, ayah: Int, pageNumber: Int, now: Long) = TrainingPositionEntity(
        id = POSITION_ID,
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        updatedAt = now,
    )

    private fun TrainingPositionEntity.toDomain() = TrainingPosition(
        surah = surah,
        ayah = ayah,
        pageNumber = pageNumber,
        updatedAt = updatedAt,
    )

    private fun TrainingLogEntity.toDomain(): TrainingSession {
        val endSurahValue = if (endSurah > 0) endSurah else surah
        val lastSurahValue = if (lastSurah > 0) lastSurah else surah
        val (events, editable) = TrainingLog.decodeAssessments(assessments)
        val statusValue = runCatching { TrainingSessionStatus.valueOf(status) }
            .getOrDefault(TrainingSessionStatus.FINISHED)
        return TrainingSession(
            id = id,
            mode = mode,
            startedAt = startedAt,
            startSurah = surah,
            startAyah = startAyah,
            endSurah = endSurahValue,
            endAyah = endAyah,
            lastSurah = lastSurahValue,
            lastAyah = lastAyah,
            pageNumber = pageNumber,
            status = statusValue,
            practiced = TrainingLog.decodePracticed(practicedAyahs, surah),
            assessments = if (editable) events else emptyList(),
            assessmentBlob = assessments,
            assessmentsEditable = editable,
            updatedAt = updatedAt,
            endedAt = endedAt,
        )
    }

    private fun TrainingSession.toEntity(): TrainingLogEntity {
        val assessmentsText = if (assessmentsEditable) {
            TrainingLog.encodeAssessments(assessments)
        } else {
            assessmentBlob
        }
        return TrainingLogEntity(
            id = id,
            mode = mode,
            startedAt = startedAt,
            surah = startSurah,
            startAyah = startAyah,
            endAyah = endAyah,
            lastAyah = lastAyah,
            status = status.name,
            practicedAyahs = TrainingLog.encodePracticed(practiced),
            assessments = assessmentsText,
            updatedAt = updatedAt,
            pageNumber = pageNumber,
            endSurah = endSurah,
            lastSurah = lastSurah,
            endedAt = endedAt,
        )
    }

    private companion object {
        const val POSITION_ID = 1
    }
}
