package com.quransunah.app.ui.mushaf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrainingRevealTest {
    private val words = listOf(
        TrainingWordRef(1, 2, 255, 1),
        TrainingWordRef(2, 2, 255, 2),
        TrainingWordRef(3, 2, 255, 3),
        TrainingWordRef(4, 2, 256, 1),
    )

    @Test
    fun nextWordRevealsOneWordThenTheNextVerse() {
        val ayah = 2 to 255
        val first = progressiveTrainingStep(TrainingRevealState(), ayah, words.filter { it.ayah == 255 })
        assertEquals(2 to 255, first?.selected)
        assertEquals(setOf(1), first?.revealedWordIds)
        assertTrue(first?.revealedAyahs.orEmpty().isEmpty())

        val second = progressiveTrainingStep(first!!, ayah, words.filter { it.ayah == 255 })
        assertEquals(setOf(1, 2), second?.revealedWordIds)

        val third = progressiveTrainingStep(second!!, ayah, words.filter { it.ayah == 255 })
        assertEquals(setOf(1, 2, 3), third?.revealedWordIds)
        assertTrue(2 to 255 !in third!!.revealedAyahs)
        assertEquals(null, progressiveTrainingStep(third, ayah, words.filter { it.ayah == 255 }))

        val following = progressiveTrainingStep(third, 2 to 256, words.filter { it.ayah == 256 })
        assertEquals(2 to 256, following?.selected)
        assertEquals(setOf(1, 2, 3, 4), following?.revealedWordIds)
        assertTrue(following?.revealedAyahs.orEmpty().isEmpty())
    }

    @Test
    fun tapTogglesOnlyTheSelectedVerse() {
        val shown = TrainingRevealState(revealedAyahs = setOf(2 to 255, 2 to 256))
        val hidden = toggleTrainingAyah(shown, 2 to 255, words)
        assertEquals(2 to 255, hidden.selected)
        assertFalse(2 to 255 in hidden.revealedAyahs)
        assertTrue(2 to 256 in hidden.revealedAyahs)

        val restored = toggleTrainingAyah(hidden, 2 to 255, words)
        assertTrue(2 to 255 in restored.revealedAyahs)
    }

    @Test
    fun partialVerseTapHidesThatVerseOnly() {
        val partial = TrainingRevealState(
            selected = 2 to 255,
            revealedWordIds = setOf(1, 2),
            revealedAyahs = setOf(2 to 256),
        )
        val hidden = toggleTrainingAyah(partial, 2 to 255, words)
        assertTrue(hidden.revealedWordIds.isEmpty())
        assertEquals(setOf(2 to 256), hidden.revealedAyahs)
    }

    @Test
    fun playingVerseIsRevealedAndStaysSelected() {
        val before = TrainingRevealState(
            selected = 2 to 255,
            revealedAyahs = setOf(2 to 255),
            revealedWordIds = setOf(9),
        )
        val playing = revealPlayingTrainingAyah(before, 2 to 256)
        assertEquals(2 to 256, playing.selected)
        assertEquals(setOf(2 to 255, 2 to 256), playing.revealedAyahs)
        assertEquals(setOf(9), playing.revealedWordIds)
    }

    @Test
    fun hiddenCheckKeepsMarkersAndRevealedWords() {
        assertFalse(
            trainingWordHidden(true, 2, 255, 8, true, emptySet(), emptySet()),
        )
        assertTrue(
            trainingWordHidden(false, 2, 255, 1, true, emptySet(), emptySet()),
        )
        assertFalse(
            trainingWordHidden(false, 2, 255, 1, true, emptySet(), setOf(1)),
        )
        assertFalse(
            trainingWordHidden(false, 2, 255, 1, true, setOf(2 to 255), emptySet()),
        )
    }
}
