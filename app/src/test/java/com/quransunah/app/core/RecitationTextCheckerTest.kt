package com.quransunah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecitationTextCheckerTest {
    @Test
    fun normalizesTashkeelAndHamzaVariants() {
        val result = RecitationTextChecker.compare("إِنَّ اللَّهَ", "ان الله")
        assertEquals(100, result.scorePercent)
        assertTrue(result.differences.isEmpty())
    }

    @Test
    fun reportsMissingExtraAndDifferentWords() {
        val missing = RecitationTextChecker.compare("قل هو الله أحد", "قل هو الله")
        assertEquals(1, missing.differences.count { it.type == WordDifferenceType.MISSING })
        val extra = RecitationTextChecker.compare("قل هو الله", "قل هو الله أحد")
        assertEquals(1, extra.differences.count { it.type == WordDifferenceType.EXTRA })
        val different = RecitationTextChecker.compare("الحمد لله", "الحمد للناس")
        assertEquals(1, different.differences.count { it.type == WordDifferenceType.DIFFERENT })
    }
}
