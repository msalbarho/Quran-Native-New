package com.quransunah.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MemorizationPlanModelsTest {
    @Test
    fun validPlanCalculatesRangeAndRejectsOversizedDailyTarget() {
        val plan = MemorizationPlan(
            id = MemorizationPlan.idFor(1, 1, 1, 7),
            name = "الفاتحة",
            startSurah = 1,
            startAyah = 1,
            endSurah = 1,
            endAyah = 7,
            dailyTarget = 2,
            createdAt = 1,
            updatedAt = 1,
        )
        assertEquals(7, plan.totalAyahs)
        assertTrue(isValidAyahRange(plan))
        assertEquals(false, isValidAyahRange(plan.copy(dailyTarget = 8)))
    }

    @Test
    fun planIdIsStableForSameRange() {
        assertEquals(
            MemorizationPlan.idFor(2, 1, 2, 5),
            MemorizationPlan.idFor(2, 1, 2, 5),
        )
    }
}
