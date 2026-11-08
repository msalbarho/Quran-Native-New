package com.quransunah.app.recitation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

class AyahModeTest {

    /** Real Uthmani words of a verse from the app's bundled data (assets/quran/words.tsvz). */
    private data class W(val verseId: Int, val position: Int, val isEnd: Boolean, val text: String)
    private val words: List<W> by lazy {
        val f = listOf("src/test/resources/recitation/words.tsvz", "app/src/test/resources/recitation/words.tsvz").map(::File).first { it.exists() }
        GZIPInputStream(f.inputStream()).bufferedReader().lineSequence().map { it.split('\t') }
            .takeWhile { it[0].toInt() <= 8 }
            .map { W(it[0].toInt(), it[1].toInt(), it[2] == "e", it[6]) }.toList()
    }
    private fun expected(verseId: Int) = words.filter { it.verseId == verseId && !it.isEnd }.map { it.text }

    /** Feeds a transcript the way a recognizer does (word-by-word partials, then a final result) and evaluates on Stop. */
    private fun reciteVerse(verseId: Int, spoken: String): VerseEvaluation {
        val aligner = IncrementalAligner(expected(verseId))
        val ws = spoken.split(' ')
        var live: LiveAlignment? = null
        for (k in 1..ws.size) {
            val (c, p) = OpeningStripper.strip("", ws.take(k).joinToString(" "), QuranStructure.basmalaBefore(verseId))
            live = aligner.update(c, p)
        }
        val (c, p) = OpeningStripper.strip(spoken, "", QuranStructure.basmalaBefore(verseId))
        live = aligner.update(c, p)
        return VerseEvaluator.evaluate(verseId, expected(verseId), live)
    }

    private val fatihaSpoken = listOf(
        "بسم الله الرحمن الرحيم",
        "الحمد لله رب العالمين",
        "الرحمن الرحيم",
        "مالك يوم الدين",
        "اياك نعبد واياك نستعين",
        "اهدنا الصراط المستقيم",
        "صراط الذين انعمت عليهم غير المغضوب عليهم ولا الضالين",
    )

    @Test fun structureIsConsistent() {
        assertEquals(6236, QuranStructure.VERSE_COUNT)
        assertEquals(1 to 1, QuranStructure.ref(1))
        assertEquals(1 to 7, QuranStructure.ref(7))
        assertEquals(2 to 1, QuranStructure.ref(8))
        assertEquals(114 to 6, QuranStructure.ref(6236))
        for (id in listOf(1, 7, 8, 293, 294, 1235, 1236, 6230, 6236)) { val (s, a) = QuranStructure.ref(id); assertEquals(id, QuranStructure.verseId(s, a)) }
    }

    @Test fun basmalaAndSurahBoundaries() {
        assertFalse("Al-Fatiha's basmala is verse 1 itself", QuranStructure.basmalaBefore(1))
        assertTrue(QuranStructure.basmalaBefore(8))                                  // Al-Baqarah 2:1
        assertFalse(QuranStructure.basmalaBefore(QuranStructure.verseId(9, 1)))      // At-Tawbah
        assertTrue(QuranStructure.basmalaBefore(QuranStructure.verseId(10, 1)))
        assertEquals(8, AyahNavigator.next(7)); assertEquals(7, AyahNavigator.previous(8))
        assertTrue(AyahNavigator.startsNewSurah(8))
        assertNull(AyahNavigator.next(6236)); assertNull(AyahNavigator.previous(1))
    }

    @Test fun fatihaDataHasBasmalaAsVerseOne() {
        assertEquals(4, expected(1).size)
        assertEquals("بِسْمِ", expected(1)[0])
        assertEquals(listOf(4, 4, 2, 3, 4, 3, 9), (1..7).map { expected(it).size })
    }

    @Test fun fullFatihaRunAyahByAyah() {
        var s = AyahFlowState(1)
        for (v in 1..7) {
            assertEquals(v, s.verseId)
            s = AyahFlow.start(s); assertEquals(AyahPhase.RECORDING, s.phase)
            s = AyahFlow.stopRequested(s); assertEquals(AyahPhase.FINALIZING, s.phase)
            val e = reciteVerse(v, fatihaSpoken[v - 1])
            assertTrue("verse $v: ${e.mistakes}", e.passed)
            assertEquals(1f, e.accuracy, 0.001f)
            s = AyahFlow.evaluated(s, e)
            assertTrue(AyahFlow.shouldAutoAdvance(s, autoAdvance = true))
            assertFalse(AyahFlow.shouldAutoAdvance(s, autoAdvance = false))
            s = AyahFlow.next(s)
        }
        assertEquals(8, s.verseId)                         // continues into Al-Baqarah
        assertTrue(QuranStructure.basmalaBefore(s.verseId))
        assertEquals(7, s.history.size)
        assertEquals(1f, s.averageAccuracy, 0.001f)
    }

    @Test fun mistakesInVerseSevenAreListedAndRetryWorks() {
        var s = AyahFlow.jump(AyahFlowState(1), 7)
        s = AyahFlow.stopRequested(AyahFlow.start(s))
        // "المغضوبين" mispronounced, "ولا" skipped, an extra word inserted
        val e = reciteVerse(7, "صراط الذين انعمت عليهم غير المغضوبين عليهم الضالين امين")
        assertFalse(e.passed)
        assertTrue(e.mistakes.any { it.type == MistakeType.WRONG && it.wordIndex == 5 })
        assertTrue(e.mistakes.any { it.type == MistakeType.MISSING && it.wordIndex == 7 })
        assertTrue(e.mistakes.any { it.type == MistakeType.EXTRA && it.heard == "امين" })
        s = AyahFlow.evaluated(s, e)
        assertFalse("failed verse never auto-advances", AyahFlow.shouldAutoAdvance(s, true))
        s = AyahFlow.retry(s)
        assertEquals(7, s.verseId); assertEquals(AyahPhase.READY, s.phase); assertNull(s.evaluation)
        s = AyahFlow.evaluated(AyahFlow.stopRequested(AyahFlow.start(s)), reciteVerse(7, fatihaSpoken[6]))
        assertTrue(s.evaluation!!.passed)
        assertEquals(1f, s.history[7]!!.accuracy, 0.001f)
    }

    @Test fun stoppingEarlyMarksTheRestMissing() {
        val e = reciteVerse(2, "الحمد لله")
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.MISSING, WordStatus.MISSING), e.result.statuses)
        assertEquals(0.5f, e.accuracy, 0.001f)
        assertFalse(e.passed)
    }

    @Test fun nothingHeardIsNotSavedToHistory() {
        val e = VerseEvaluator.evaluate(2, expected(2), null)
        assertFalse(e.heardAnything); assertFalse(e.passed)
        val s = AyahFlow.evaluated(AyahFlow.stopRequested(AyahFlow.start(AyahFlowState(2))), e)
        assertTrue(s.history.isEmpty())
    }

    @Test fun openingIsStrippedBeforeFirstVerseOfSurah() {
        // Al-Baqarah 2:1 "الم": reciter says isti'adha + basmala first
        val e = reciteVerse(8, "اعوذ بالله من الشيطان الرجيم بسم الله الرحمن الرحيم الم")
        assertTrue(e.mistakes.toString(), e.passed)
        // while still saying the basmala nothing is judged yet
        assertEquals("" to "", OpeningStripper.strip("", "بسم الله الرحمن", stripBasmala = true))
        // Al-Fatiha: the basmala IS the verse and must not be stripped
        assertEquals("" to "بسم الله", OpeningStripper.strip("", "بسم الله", stripBasmala = false))
        // committed/partial split is preserved
        assertEquals("الحمد" to "لله", OpeningStripper.strip("اعوذ بالله من الشيطان الرجيم الحمد", "لله", stripBasmala = false))
    }

    @Test fun backAndForthNavigation() {
        var s = AyahFlowState(8)
        s = AyahFlow.previous(s); assertEquals(7, s.verseId)
        s = AyahFlow.previous(AyahFlow.jump(s, 1)); assertEquals(1, s.verseId)
        s = AyahFlow.next(AyahFlow.jump(s, 6236)); assertTrue(s.finishedQuran); assertEquals(6236, s.verseId)
    }

    @Test fun hideRuleKeepsBismillahAndFirstWordCues() {
        val visible = words.filter { it.verseId <= 7 && !it.isEnd && HideRule.visibleWhenHidden(it.verseId, it.position, it.isEnd) }.map { it.text }
        val n = { l: List<String> -> l.map { ArabicNormalizer.skeleton(ArabicNormalizer.normalize(it)) } }
        assertEquals(n(listOf("بسم", "الله", "الحمد", "الرحمن", "مالك", "اياك", "اهدنا", "صراط")), n(visible))
        assertTrue(HideRule.visibleWhenHidden(2, 4, isEnd = true))
    }
}
