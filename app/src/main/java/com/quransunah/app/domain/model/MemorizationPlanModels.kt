package com.quransunah.app.domain.model

import com.quransunah.app.core.SurahAyahCounts

/** A local plan describing the ayah range the learner wants to memorize. */
data class MemorizationPlan(
    val id: String,
    val name: String,
    val startSurah: Int,
    val startAyah: Int,
    val endSurah: Int,
    val endAyah: Int,
    val dailyTarget: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    val totalAyahs: Int get() = SurahAyahCounts.range(startSurah, startAyah, endSurah, endAyah).size

    companion object {
        fun idFor(startSurah: Int, startAyah: Int, endSurah: Int, endAyah: Int): String =
            "plan:$startSurah:$startAyah-$endSurah:$endAyah"
    }
}

data class MemorizationSession(
    val id: String,
    val planId: String,
    val startedAt: Long,
    val finishedAt: Long?,
    val reviewedCount: Int,
    val masteredCount: Int,
)

fun isValidAyahRange(plan: MemorizationPlan): Boolean =
    SurahAyahCounts.range(plan.startSurah, plan.startAyah, plan.endSurah, plan.endAyah).isNotEmpty() &&
        plan.dailyTarget in 1..plan.totalAyahs
