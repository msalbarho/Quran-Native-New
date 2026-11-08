package com.quransunah.app.ui.training

import com.quransunah.app.recitation.core.HeardToken
import com.quransunah.app.recitation.core.LiveAlignment
import com.quransunah.app.recitation.core.WordStatus
import com.quransunah.app.ui.mushaf.MEDINA_PAGE_HEIGHT_OVER_WIDTH
import com.quransunah.app.ui.mushaf.TrainingWordMark
import com.quransunah.app.ui.mushaf.fittedMedinaPageBox
import com.quransunah.app.ui.mushaf.originalMedinaPageBox
import com.quransunah.app.ui.shell.trainingPageBottomInset
import com.quransunah.app.ui.shell.trainingPageTopInset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingSessionLogicTest {
    private val page = listOf(
        TrainingExpectedWord(1, 1, 1, 1, "بسم"),
        TrainingExpectedWord(2, 1, 1, 2, "الله"),
        TrainingExpectedWord(3, 1, 1, 3, "الرحمن"),
        TrainingExpectedWord(4, 1, 2, 1, "الحمد"),
        TrainingExpectedWord(5, 2, 1, 1, "الم"),
    )

    @Test
    fun fittedPageKeepsOneProportionOnEveryScreen() {
        val phone = fittedMedinaPageBox(360f, 640f)
        val small = fittedMedinaPageBox(320f, 480f)
        val wide = fittedMedinaPageBox(800f, 360f)
        listOf(phone, small, wide).forEach { box ->
            assertEquals(MEDINA_PAGE_HEIGHT_OVER_WIDTH, box.height / box.width, 0.001f)
            assertTrue(box.width <= 800f)
            assertTrue(box.height <= 640f || box == phone)
        }
        assertTrue(phone.width <= 360f && phone.height <= 640f)
        assertTrue(small.width <= 320f && small.height <= 480f)
        assertTrue(wide.width <= 800f && wide.height <= 360f)
        assertTrue(phone.height > 0f && small.width < small.height)
    }

    @Test
    fun originalSizeUsesTheSameProportionAndCanScroll() {
        val box = originalMedinaPageBox(360f)
        assertEquals(360f, box.width, 0.01f)
        assertEquals(360f * MEDINA_PAGE_HEIGHT_OVER_WIDTH, box.height, 0.01f)
        assertTrue(box.height > 480f)
    }

    @Test
    fun trainingDefaultsToOpenRecitation() {
        assertEquals(TrainingReciteScope.Open, TrainingReciteUi().scope)
    }

    @Test
    fun crossSurahRangeAndTypedAyahReferencesAreValidated() {
        assertFalse(trainingRangeValid(2, 1, 1, 7))
        assertTrue(trainingRangeValid(1, 1, 1, 7))
        assertTrue(trainingRangeValid(1, 7, 2, 2))
        assertEquals(2 to 5, parseTrainingAyahReference("2:5", 1))
        assertEquals(2 to 5, parseTrainingAyahReference("٢:٥", 1))
        assertEquals(1 to 5, parseTrainingAyahReference("5", 1))
        assertEquals(604, parseTrainingNumber("٦٠٤"))
        assertNull(parseTrainingAyahReference("2:", 1))
        assertNull(parseTrainingAyahReference("115:1", 1))
    }

    @Test
    fun noAlignmentProducesNoMarksOrFakeSummary() {
        assertTrue(assessRecitation(page, null).isEmpty())
        val mismatch = live(listOf(WordStatus.CORRECT), frozen = 0)
        assertTrue(assessRecitation(page, mismatch).isEmpty())
        val empty = summarizeRecitation(emptyList(), 0L)
        assertFalse(empty.fromSession)
    }

    @Test
    fun uncertainWrongIsNotShownAsAConfirmedError() {
        val marks = assessRecitation(page.take(2), live(listOf(WordStatus.WRONG, WordStatus.PENDING), frozen = -1))
        assertEquals(TrainingWordMark.Uncertain, marks[0].mark)
        assertEquals(TrainingWordMark.Unread, marks[1].mark)
        assertTrue(summarizeRecitation(marks, 1_000L).possibleErrors == 0)
        assertEquals(1, summarizeRecitation(marks, 1_000L).uncertain)
    }

    @Test
    fun frozenWrongIsAPossibleErrorAndMarkersAreNeverColoured() {
        val marks = assessRecitation(
            page.take(3),
            live(listOf(WordStatus.CORRECT, WordStatus.WRONG, WordStatus.MISSING), frozen = 2),
        )
        assertEquals(TrainingWordMark.Correct, marks[0].mark)
        assertEquals(TrainingWordMark.PossibleError, marks[1].mark)
        assertEquals(TrainingWordMark.Missing, marks[2].mark)
        assertNull(visibleTrainingMark(isAyahMarker = true, TrainingWordMark.PossibleError))
        assertEquals(TrainingWordMark.Correct, visibleTrainingMark(false, TrainingWordMark.Correct))
        assertEquals(setOf(1, 2, 3), recognizedWordIds(marks.associate { it.wordId to it.mark }))
    }

    @Test
    fun completedAyahRequiresEveryWordToBeHeardAndFinalized() {
        assertEquals(
            setOf(1 to 1),
            finalizedTrainingAyahs(page.take(3), live(List(3) { WordStatus.CORRECT }, frozen = 2)),
        )
        assertEquals(
            setOf(1 to 1, 1 to 2),
            finalizedTrainingAyahs(page, live(List(page.size) { WordStatus.CORRECT }, frozen = 3)),
        )
        assertTrue(
            finalizedTrainingAyahs(
                page.take(3),
                live(listOf(WordStatus.CORRECT, WordStatus.MISSING, WordStatus.CORRECT), frozen = 2),
            ).isEmpty(),
        )
        assertTrue(finalizedTrainingAyahs(page, null).isEmpty())
    }

    @Test
    fun summaryCountsComeOnlyFromRealMarks() {
        val marks = assessRecitation(
            page.take(4),
            live(
                listOf(WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.WRONG, WordStatus.PENDING),
                frozen = 2,
            ),
        )
        val summary = summarizeRecitation(marks, 12_500L)
        assertEquals(3, summary.evaluated)
        assertEquals(2, summary.correct)
        assertEquals(1, summary.possibleErrors)
        assertEquals(0, summary.uncertain)
        assertEquals(1, summary.unread)
        assertEquals(12_500L, summary.durationMs)
        assertEquals(listOf(3), summary.issues.map { it.wordId })
    }

    @Test
    fun chromeInsetsLeaveThePageClearOnSmallAndLargePhases() {
        assertTrue(trainingPageTopInset().value > 0f)
        assertTrue(trainingPageBottomInset(TrainingPhase.During, resultsOpen = false, singleAyahOpen = false).value < 90f)
        assertTrue(
            trainingPageBottomInset(TrainingPhase.After, resultsOpen = true, singleAyahOpen = false).value >
                trainingPageBottomInset(TrainingPhase.Before, resultsOpen = false, singleAyahOpen = false).value,
        )
        assertTrue(
            trainingPageBottomInset(TrainingPhase.Before, resultsOpen = false, singleAyahOpen = true).value >
                trainingPageBottomInset(TrainingPhase.Before, resultsOpen = false, singleAyahOpen = false).value,
        )
    }
}

private fun live(statuses: List<WordStatus>, frozen: Int) = LiveAlignment(
    statuses = statuses,
    heard = statuses.mapIndexed { index, status ->
        if (status == WordStatus.CORRECT || status == WordStatus.WRONG) "spoken-$index" else null
    },
    extras = emptyList(),
    cursor = 0,
    tokens = emptyList<HeardToken>(),
    finalizedUpTo = frozen,
)
