package com.quransunah.app.ui.shell

import com.quransunah.app.ui.mushaf.TrainingWordMark
import com.quransunah.app.ui.mushaf.trainingMarkColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrainingAyahWordTintTest {
    @Test
    fun liveColours() {
        assertNull(trainingAyahWordTint(null, false))
        assertNull(trainingAyahWordTint(TrainingWordMark.Unread, false))
        assertEquals(trainingMarkColor(TrainingWordMark.Correct), trainingAyahWordTint(TrainingWordMark.Correct, false))
        assertEquals(trainingMarkColor(TrainingWordMark.PossibleError), trainingAyahWordTint(TrainingWordMark.PossibleError, false))
    }

    @Test
    fun missingWordsTurnGreyWhenFinished() {
        assertEquals(trainingMarkColor(TrainingWordMark.Unread), trainingAyahWordTint(TrainingWordMark.Unread, true))
    }
}

class TrainingContinuousNextTest {
    @Test
    fun advancesToNextSurahOnlyAfterLastAyah() {
        org.junit.Assert.assertEquals(2 to 1, trainingContinuousNext(1, 7))
        org.junit.Assert.assertEquals(3 to 1, trainingContinuousNext(2, 286))
        assertNull(trainingContinuousNext(2, 100))
        assertNull(trainingContinuousNext(114, 6))
    }
}
