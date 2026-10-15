package com.quransunah.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingLogTest {
    @Test
    fun practiceOpensOneSessionAndKeepsItAcrossVerses() {
        val first = TrainingLog.practice(null, 2, 255, 42, 1_000)
        assertNull(first.finish)
        assertEquals("training:1000", first.save.id)
        assertEquals(TrainingSessionStatus.ACTIVE, first.save.status)
        assertEquals(listOf(TrainingVerseRef(2, 255)), first.save.practiced)
        assertTrue(first.save.assessments.isEmpty())

        val second = TrainingLog.practice(first.save, 2, 256, 43, 2_000)
        assertNull(second.finish)
        assertEquals(first.save.id, second.save.id)
        assertEquals(2 to 255, second.save.startSurah to second.save.startAyah)
        assertEquals(2 to 256, second.save.lastSurah to second.save.lastAyah)
        assertEquals(43, second.save.pageNumber)
        assertEquals(
            listOf(TrainingVerseRef(2, 255), TrainingVerseRef(2, 256)),
            second.save.practiced,
        )

        val repeat = TrainingLog.practice(second.save, 2, 256, 43, 2_500)
        assertEquals(2, repeat.save.practiced.size)
    }

    @Test
    fun assessmentAppendsAndLeavesEarlierEventsUntouched() {
        val started = TrainingLog.practice(null, 2, 255, 42, 1_000).save
        val first = TrainingLog.assessment(
            started,
            2,
            255,
            42,
            TrainingAssessmentResult.LEARNING,
            1_500,
        )
        val again = TrainingLog.assessment(
            first.save,
            2,
            255,
            42,
            TrainingAssessmentResult.MASTERED,
            1_800,
        )
        assertEquals(2, again.save.assessments.size)
        assertEquals(TrainingAssessmentResult.LEARNING, again.save.assessments[0].result)
        assertEquals(1_500, again.save.assessments[0].at)
        assertEquals(TrainingAssessmentResult.MASTERED, again.save.assessments[1].result)

        val finished = TrainingLog.close(again.save, 3_000)
        assertEquals(again.save.assessments, finished.assessments)
        assertEquals(TrainingSessionStatus.FINISHED, finished.status)

        val later = TrainingLog.assessment(
            finished,
            2,
            255,
            42,
            TrainingAssessmentResult.LEARNING,
            90_000,
        )
        assertNull(later.finish)
        assertFalse(later.save.id == finished.id)
        assertEquals(1, later.save.assessments.size)
        assertEquals(TrainingAssessmentResult.LEARNING, later.save.assessments.single().result)
        assertEquals(2, finished.assessments.size)
    }

    @Test
    fun staleSessionIsClosedWithoutRewritingItsAssessments() {
        val started = TrainingLog.assessment(
            null,
            1,
            1,
            1,
            TrainingAssessmentResult.MASTERED,
            1_000,
        ).save
        val next = TrainingLog.practice(
            started,
            1,
            2,
            1,
            1_000 + TrainingLog.SESSION_GAP_MS + 1,
        )
        val closed = next.finish!!
        assertEquals(started.id, closed.id)
        assertEquals(TrainingSessionStatus.FINISHED, closed.status)
        assertEquals(started.assessments, closed.assessments)
        assertEquals(listOf(TrainingVerseRef(1, 1)), closed.practiced)
        assertEquals(listOf(TrainingVerseRef(1, 2)), next.save.practiced)
        assertTrue(next.save.assessments.isEmpty())
    }

    @Test
    fun legacyBlobsRoundTripWithoutDroppingOldRows() {
        assertEquals(
            listOf(TrainingVerseRef(2, 1), TrainingVerseRef(2, 7)),
            TrainingLog.decodePracticed("[1, 7]", fallbackSurah = 2),
        )
        val encoded = TrainingLog.encodePracticed(listOf(TrainingVerseRef(2, 255), TrainingVerseRef(3, 1)))
        assertEquals(listOf(TrainingVerseRef(2, 255), TrainingVerseRef(3, 1)), TrainingLog.decodePracticed(encoded, 1))

        val (empty, editable) = TrainingLog.decodeAssessments("[]")
        assertTrue(empty.isEmpty())
        assertTrue(editable)
        val (unknown, recognized) = TrainingLog.decodeAssessments("""{"kept":true}""")
        assertTrue(unknown.isEmpty())
        assertFalse(recognized)
    }
}
