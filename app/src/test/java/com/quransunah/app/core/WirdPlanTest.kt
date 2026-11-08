package com.quransunah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WirdPlanTest {
    private fun assertCovers(parts: List<IntRange>) {
        assertEquals(1, parts.first().first)
        assertEquals(604, parts.last().last)
        parts.zipWithNext { a, b -> assertEquals(a.last + 1, b.first) }
        assertTrue(parts.all { !it.isEmpty() })
    }

    @Test fun thirtyDaysSplitsEvenly() {
        val parts = WirdPlan.split(30)
        assertEquals(30, parts.size)
        assertCovers(parts)
        val sizes = parts.map { it.count() }
        assertTrue(sizes.max() - sizes.min() <= 1)
        assertEquals(604, sizes.sum())
    }

    @Test fun presetsAndCustomCoverAllPages() {
        listOf(1, 7, 15, 29, 60, 100, 603, 604).forEach { d ->
            val parts = WirdPlan.split(d)
            assertEquals(d, parts.size)
            assertCovers(parts)
        }
    }

    @Test fun daysAreClamped() {
        assertEquals(1, WirdPlan.split(0).size)
        assertEquals(604, WirdPlan.split(5000).size)
    }

    @Test fun reachingLastPageCompletesPortion() {
        val s = WirdPlan.State(days = 30, completed = 0, startEpochDay = 100)
        val end = s.current!!.last
        assertEquals(0, WirdPlan.completedAfterVisit(s, end - 1))
        assertEquals(1, WirdPlan.completedAfterVisit(s, end))
        assertEquals(1, WirdPlan.completedAfterVisit(s, end + 1))
        assertEquals(0, WirdPlan.completedAfterVisit(s, 500))
    }

    @Test fun scheduleBehindAndAhead() {
        val s = WirdPlan.State(days = 30, completed = 2, startEpochDay = 100)
        assertEquals(3, s.scheduleDelta(105))
        assertEquals(0, s.scheduleDelta(101))
        assertEquals(-1, s.scheduleDelta(100))
        assertEquals(25, s.daysLeft(105))
        assertFalse(s.todayDone(105))
        assertTrue(s.todayDone(101))
    }

    @Test fun finishedPlanStopsAdvancing() {
        val s = WirdPlan.State(days = 15, completed = 15, startEpochDay = 0)
        assertTrue(s.finished)
        assertEquals(15, WirdPlan.completedAfterVisit(s, 604))
        assertEquals(1f, s.progress)
    }
}
