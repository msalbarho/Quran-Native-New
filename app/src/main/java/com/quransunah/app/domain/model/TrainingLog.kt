package com.quransunah.app.domain.model

/** Last Training page and verse. Independent of memorization assessments. */
data class TrainingPosition(
    val surah: Int,
    val ayah: Int,
    val pageNumber: Int,
    val updatedAt: Long,
)

data class TrainingVerseRef(
    val surah: Int,
    val ayah: Int,
)

enum class TrainingAssessmentResult {
    MASTERED,
    LEARNING,
}

data class TrainingAssessmentEvent(
    val surah: Int,
    val ayah: Int,
    val result: TrainingAssessmentResult,
    val at: Long,
)

enum class TrainingSessionStatus {
    ACTIVE,
    FINISHED,
}

data class TrainingSession(
    val id: String,
    val mode: String,
    val startedAt: Long,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val lastSurah: Int,
    val lastAyah: Int,
    val pageNumber: Int,
    val status: TrainingSessionStatus,
    val practiced: List<TrainingVerseRef>,
    val assessments: List<TrainingAssessmentEvent>,
    val assessmentBlob: String,
    val assessmentsEditable: Boolean,
    val updatedAt: Long,
    val endedAt: Long?,
)

data class TrainingLogWrite(
    val finish: TrainingSession?,
    val save: TrainingSession,
)

/**
 * One active session per visit.
 * A visit starts at the first real practice action and ends when Training is
 * left, or when the next action arrives after [SESSION_GAP_MS].
 * Finished sessions are never rewritten except to mark them finished.
 */
object TrainingLog {
    const val MODE = "TRAINING"
    const val SESSION_GAP_MS = 12L * 60L * 60L * 1000L

    fun practice(
        active: TrainingSession?,
        surah: Int,
        ayah: Int,
        pageNumber: Int,
        now: Long,
    ): TrainingLogWrite {
        val continueSession = active != null &&
            active.status == TrainingSessionStatus.ACTIVE &&
            now - active.updatedAt <= SESSION_GAP_MS
        val finish = if (active != null && active.status == TrainingSessionStatus.ACTIVE && !continueSession) {
            active.copy(status = TrainingSessionStatus.FINISHED, endedAt = active.updatedAt)
        } else {
            null
        }
        val save = if (continueSession) {
            active.withVerse(surah, ayah, pageNumber, now)
        } else {
            newSession(surah, ayah, pageNumber, now)
        }
        return TrainingLogWrite(finish = finish, save = save)
    }

    fun assessment(
        active: TrainingSession?,
        surah: Int,
        ayah: Int,
        pageNumber: Int,
        result: TrainingAssessmentResult,
        now: Long,
    ): TrainingLogWrite {
        val stepped = practice(active, surah, ayah, pageNumber, now)
        val event = TrainingAssessmentEvent(surah, ayah, result, now)
        val save = if (stepped.save.assessmentsEditable) {
            stepped.save.copy(assessments = stepped.save.assessments + event)
        } else {
            stepped.save
        }
        return stepped.copy(save = save)
    }

    fun close(active: TrainingSession, endedAt: Long): TrainingSession =
        active.copy(status = TrainingSessionStatus.FINISHED, endedAt = endedAt)

    fun encodePracticed(verses: List<TrainingVerseRef>): String =
        verses.joinToString(",") { "${it.surah}:${it.ayah}" }

    fun decodePracticed(raw: String, fallbackSurah: Int): List<TrainingVerseRef> {
        val body = raw.trim().removePrefix("[").removeSuffix("]").trim()
        if (body.isEmpty()) return emptyList()
        return body.split(',').mapNotNull { part ->
            val token = part.trim().trim('"')
            if (token.isEmpty()) return@mapNotNull null
            val bits = token.split(':')
            if (bits.size == 2) {
                val surah = bits[0].toIntOrNull() ?: return@mapNotNull null
                val ayah = bits[1].toIntOrNull() ?: return@mapNotNull null
                TrainingVerseRef(surah, ayah)
            } else {
                val ayah = token.toIntOrNull() ?: return@mapNotNull null
                TrainingVerseRef(fallbackSurah, ayah)
            }
        }
    }

    fun encodeAssessments(events: List<TrainingAssessmentEvent>): String =
        events.joinToString("|") { "${it.surah}:${it.ayah}:${it.result.name}:${it.at}" }

    fun decodeAssessments(raw: String): Pair<List<TrainingAssessmentEvent>, Boolean> {
        val text = raw.trim()
        if (text.isEmpty() || text == "[]") return emptyList<TrainingAssessmentEvent>() to true
        val events = text.split('|').map { part ->
            val bits = part.split(':')
            if (bits.size != 4) return emptyList<TrainingAssessmentEvent>() to false
            val surah = bits[0].toIntOrNull() ?: return emptyList<TrainingAssessmentEvent>() to false
            val ayah = bits[1].toIntOrNull() ?: return emptyList<TrainingAssessmentEvent>() to false
            val result = runCatching { TrainingAssessmentResult.valueOf(bits[2]) }.getOrNull()
                ?: return emptyList<TrainingAssessmentEvent>() to false
            val at = bits[3].toLongOrNull() ?: return emptyList<TrainingAssessmentEvent>() to false
            TrainingAssessmentEvent(surah, ayah, result, at)
        }
        return events to true
    }

    private fun newSession(surah: Int, ayah: Int, pageNumber: Int, now: Long): TrainingSession {
        val verse = TrainingVerseRef(surah, ayah)
        return TrainingSession(
            id = "training:$now",
            mode = MODE,
            startedAt = now,
            startSurah = surah,
            startAyah = ayah,
            endSurah = surah,
            endAyah = ayah,
            lastSurah = surah,
            lastAyah = ayah,
            pageNumber = pageNumber,
            status = TrainingSessionStatus.ACTIVE,
            practiced = listOf(verse),
            assessments = emptyList(),
            assessmentBlob = "",
            assessmentsEditable = true,
            updatedAt = now,
            endedAt = null,
        )
    }

    private fun TrainingSession.withVerse(surah: Int, ayah: Int, pageNumber: Int, now: Long): TrainingSession {
        val verse = TrainingVerseRef(surah, ayah)
        val practiced = if (practiced.any { it == verse }) practiced else practiced + verse
        return copy(
            endSurah = surah,
            endAyah = ayah,
            lastSurah = surah,
            lastAyah = ayah,
            pageNumber = pageNumber,
            practiced = practiced,
            updatedAt = now,
        )
    }
}
