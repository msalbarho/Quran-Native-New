package com.quransunah.app.domain.model

import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts

/** A locally tracked ayah in the learner's memorization queue. */
data class MemorizationItem(
    val id: String,
    val surah: Int,
    val ayah: Int,
    val pageNumber: Int,
    val ayahText: String,
    val state: MemorizationState,
    val reviewCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
) {
    companion object {
        fun idFor(surah: Int, ayah: Int): String = "$surah:$ayah"
    }
}

enum class MemorizationState {
    LEARNING,
    MASTERED,
    ;

    companion object {
        fun fromStored(value: String): MemorizationState =
            entries.firstOrNull { it.name == value } ?: LEARNING
    }
}

data class MemorizationSummary(
    val total: Int = 0,
    val learning: Int = 0,
    val mastered: Int = 0,
) {
    val completionPercent: Int
        get() = if (total == 0) 0 else (mastered * 100 / total).coerceIn(0, 100)
}

fun List<MemorizationItem>.memorizationSummary(): MemorizationSummary = MemorizationSummary(
    total = size,
    learning = count { it.state == MemorizationState.LEARNING },
    mastered = count { it.state == MemorizationState.MASTERED },
)

fun MemorizationItem.hasValidReference(): Boolean =
    SurahAyahCounts.ayahId(surah, ayah) > 0 && pageNumber in 1..AppConstants.TOTAL_PAGES
