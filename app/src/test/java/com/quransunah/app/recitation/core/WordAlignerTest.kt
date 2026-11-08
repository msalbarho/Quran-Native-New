package com.quransunah.app.recitation.core

import com.quransunah.app.recitation.core.WordStatus.CORRECT
import com.quransunah.app.recitation.core.WordStatus.MISSING
import com.quransunah.app.recitation.core.WordStatus.PENDING
import com.quransunah.app.recitation.core.WordStatus.WRONG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordAlignerTest {
    private val fatiha2 = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ".split(" ")
    private val aligner = WordAligner()

    private fun words(s: String) = s.split(" ").filter { it.isNotBlank() }

    @Test fun perfectRecitation() {
        val r = aligner.align(fatiha2, words("الحمد لله رب العالمين"), live = false)
        assertEquals(listOf(CORRECT, CORRECT, CORRECT, CORRECT), r.statuses)
        assertTrue(r.extras.isEmpty())
        assertEquals(1f, r.accuracy, 0.001f)
    }

    @Test fun missingWordIsGrey() {
        val r = aligner.align(fatiha2, words("الحمد رب العالمين"), live = false)
        assertEquals(listOf(CORRECT, MISSING, CORRECT, CORRECT), r.statuses)
    }

    @Test fun wrongWordIsRed() {
        val r = aligner.align(fatiha2, words("الحمد لله رب الناس"), live = false)
        assertEquals(listOf(CORRECT, CORRECT, CORRECT, WRONG), r.statuses)
    }

    @Test fun mispronouncedWordIsRedButAligned() {
        val r = aligner.align(fatiha2, words("الهمد لله رب العالمين"), live = false)
        assertEquals(WRONG, r.statuses[0])
        assertEquals("الهمد", r.heard[0])
        assertEquals(CORRECT, r.statuses[3])
    }

    @Test fun extraWordIsReported() {
        val r = aligner.align(fatiha2, words("الحمد لله لله رب العالمين"), live = false)
        assertEquals(listOf(CORRECT, CORRECT, CORRECT, CORRECT), r.statuses)
        assertEquals(1, r.extras.size)
        assertEquals("لله", r.extras[0].text)
    }

    @Test fun liveModeKeepsTrailingWordsPending() {
        val r = aligner.align(fatiha2, words("الحمد لله"), live = true)
        assertEquals(listOf(CORRECT, CORRECT, PENDING, PENDING), r.statuses)
        assertEquals(1, r.lastCovered)
    }

    @Test fun finalModeMarksUnrecitedAsMissing() {
        val r = aligner.align(fatiha2, words("الحمد لله"), live = false)
        assertEquals(listOf(CORRECT, CORRECT, MISSING, MISSING), r.statuses)
    }

    @Test fun splitWordMerges() {
        val expected = words("يَـٰٓأَيُّهَا ٱلنَّاسُ ٱعْبُدُوا۟ رَبَّكُمُ")
        val r = aligner.align(expected, words("يا ايها الناس اعبدوا ربكم"), live = false)
        assertEquals(listOf(CORRECT, CORRECT, CORRECT, CORRECT), r.statuses)
        assertTrue(r.extras.isEmpty())
    }

    @Test fun emptyRecognition() {
        val r = aligner.align(fatiha2, emptyList(), live = true)
        assertTrue(r.statuses.all { it == PENDING })
        val f = aligner.align(fatiha2, emptyList(), live = false)
        assertTrue(f.statuses.all { it == MISSING })
    }

    @Test fun longPassageWithSkippedVerse() {
        val expected = words("قُلْ هُوَ ٱللَّهُ أَحَدٌ ٱللَّهُ ٱلصَّمَدُ لَمْ يَلِدْ وَلَمْ يُولَدْ وَلَمْ يَكُن لَّهُۥ كُفُوًا أَحَدٌۢ")
        val r = aligner.align(expected, words("قل هو الله احد لم يلد ولم يولد ولم يكن له كفوا احد"), live = false)
        assertEquals(MISSING, r.statuses[4])
        assertEquals(MISSING, r.statuses[5])
        assertEquals(expected.size - 2, r.correct)
    }

    @Test fun similarityBasics() {
        assertEquals(1f, WordAligner.similarity("كتاب", "كتاب"), 0f)
        assertTrue(WordAligner.similarity("الرحمان", "الرحمن") > 0.9f)
        assertTrue(WordAligner.similarity("الناس", "العالمين") < 0.5f)
        assertEquals(1, WordAligner.levenshtein("كتب", "كتاب"))
    }
}
