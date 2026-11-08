package com.quransunah.app.recitation.core

import com.quransunah.app.recitation.core.WordStatus.CORRECT
import com.quransunah.app.recitation.core.WordStatus.MISSING
import com.quransunah.app.recitation.core.WordStatus.PENDING
import com.quransunah.app.recitation.core.WordStatus.WRONG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementalAlignerTest {
    // Al-Fatiha 1:2-1:4
    private val expected = "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَـٰلَمِينَ ٱلرَّحْمَـٰنِ ٱلرَّحِيمِ مَـٰلِكِ يَوْمِ ٱلدِّينِ".split(" ")

    @Test fun wordByWordPartialsTurnGreenImmediately() {
        val a = IncrementalAligner(expected)
        var r = a.update("", "الحمد")
        // the word being spoken right now matches -> already green, cursor on the next word
        assertEquals(CORRECT, r.statuses[0]); assertEquals(1, r.cursor)
        r = a.update("", "الحمد لله")
        assertEquals(listOf(CORRECT, CORRECT, PENDING), r.statuses.take(3)); assertEquals(2, r.cursor)
        r = a.update("", "الحمد لله رب")
        assertEquals(CORRECT, r.statuses[2]); assertEquals(3, r.cursor)
    }

    @Test fun incompleteLastWordIsNotMarkedRedYet() {
        val a = IncrementalAligner(expected)
        val r = a.update("", "الحمد لله رب العا")   // last word still being spoken
        assertEquals(PENDING, r.statuses[3])
        assertEquals(3, r.cursor)
        val r2 = a.update("", "الحمد لله رب العالمين")
        assertEquals(CORRECT, r2.statuses[3])
    }

    @Test fun wrongWordTurnsRedOnceTheNextWordArrives() {
        val a = IncrementalAligner(expected)
        a.update("", "الحمد لله رب الناس")
        val r = a.update("", "الحمد لله رب الناس الرحمن")
        assertEquals(WRONG, r.statuses[3])
        assertEquals(CORRECT, r.statuses[4])
        assertEquals(5, r.cursor)
    }

    @Test fun skippedWordGreysOutAsSoonAsReciterMovesPast() {
        val a = IncrementalAligner(expected)
        val r = a.update("", "الحمد رب")
        assertEquals(listOf(CORRECT, MISSING, CORRECT), r.statuses.take(3))
        assertEquals(3, r.cursor)
    }

    @Test fun extraWordIsFlaggedInline() {
        val a = IncrementalAligner(expected)
        val r = a.update("الحمد لله لله رب", "")
        assertEquals(listOf(CORRECT, CORRECT, CORRECT), r.statuses.take(3))
        assertEquals(1, r.extras.size)
        // the repeated word is shown grey in the transcript (either copy may be the extra one)
        assertEquals(1, r.tokens.count { it.status == null })
        assertEquals(4, r.tokens.size)
    }

    @Test fun finalizedWordsAreFrozenAndPartialsBuildOnThem() {
        val a = IncrementalAligner(expected)
        a.update("الحمد لله رب العالمين", "")
        var r = a.update("الحمد لله رب العالمين", "الرحمن")
        assertEquals(CORRECT, r.statuses[4]); assertEquals(5, r.cursor)
        assertEquals(3, r.finalizedUpTo)
        // partial revised by the recognizer: tentative state is recomputed, frozen words untouched
        r = a.update("الحمد لله رب العالمين", "الرحيم")
        assertEquals(MISSING, r.statuses[4]); assertEquals(CORRECT, r.statuses[5])
        r = a.update("الحمد لله رب العالمين الرحمن الرحيم", "")
        assertEquals(listOf(CORRECT, CORRECT), r.statuses.subList(4, 6))
        assertTrue(r.statuses.subList(0, 4).all { it == CORRECT })
    }

    @Test fun reanchorsWhenReciterJumpsAhead() {
        val a = IncrementalAligner(expected)
        a.update("الحمد لله", "")
        val r = a.update("الحمد لله مالك يوم الدين", "")
        assertEquals(listOf(MISSING, MISSING, MISSING, MISSING), r.statuses.subList(2, 6))
        assertEquals(listOf(CORRECT, CORRECT, CORRECT), r.statuses.subList(6, 9))
        assertEquals(-1, r.cursor)
    }

    @Test fun longSessionCostStaysBounded() {
        val words = (1..2000).map { "كلمة$it" }
        val long = words.map { w -> w.replace(Regex("\\d"), "") + "ب".repeat(w.length % 5) + "ت".repeat(w.length % 3) }
        val a = IncrementalAligner(long)
        val sb = StringBuilder()
        val t0 = System.nanoTime()
        for (i in 0 until 300) {
            sb.append(long[i]).append(' ')
            a.update(sb.toString(), "")
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        assertTrue("300 incremental updates took $ms ms", ms < 3000)
    }

    @Test fun recognizerTranscriptResetIsHandled() {
        val a = IncrementalAligner(expected)
        a.update("الحمد لله", "")
        val r = a.update("", "رب")
        assertEquals(CORRECT, r.statuses[2])
    }
}
