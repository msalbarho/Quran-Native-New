package com.quransunah.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorizationModelsTest {
    @Test
    fun summarySeparatesLearningAndMasteredAyahs() {
        val items = listOf(
            item(1, MemorizationState.LEARNING),
            item(2, MemorizationState.MASTERED),
            item(3, MemorizationState.MASTERED),
        )

        assertEquals(
            MemorizationSummary(total = 3, learning = 1, mastered = 2),
            items.memorizationSummary(),
        )
        assertEquals(66, items.memorizationSummary().completionPercent)
    }

    @Test
    fun emptySummaryHasZeroCompletion() {
        assertEquals(0, emptyList<MemorizationItem>().memorizationSummary().completionPercent)
    }

    @Test
    fun unrecognizedPersistedStateFallsBackToLearning() {
        assertEquals(MemorizationState.LEARNING, MemorizationState.fromStored("UNKNOWN"))
    }

    @Test
    fun referenceValidationRejectsInvalidAyahAndPage() {
        assertEquals(true, item(7, MemorizationState.LEARNING).hasValidReference())
        assertEquals(false, item(8, MemorizationState.LEARNING).hasValidReference())
        assertEquals(
            false,
            item(1, MemorizationState.LEARNING).copy(pageNumber = 605).hasValidReference(),
        )
    }

    private fun item(ayah: Int, state: MemorizationState) = MemorizationItem(
        id = MemorizationItem.idFor(1, ayah),
        surah = 1,
        ayah = ayah,
        pageNumber = 1,
        ayahText = "نص",
        state = state,
        reviewCount = 0,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
