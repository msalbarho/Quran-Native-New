package com.quransunah.app.recitation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Streaming decode → aligner glue for the Zipformer Quran engine (phonetic script as the model writes it). */
class PhonemeAlignerTest {
    // Al-Fatiha 1:2–1:5, canonical phonemes per mushaf word (waqf at verse ends)
    private val ph = listOf(
        "ءَلحَمدُ", "لِللَااهِ", "رَببِ", "لعَاالَمِۦۦۦۦن",
        "ءَررَحمَاانِ", "ررَحِۦۦۦۦم",
        "مَاالِكِ", "يَومِ", "ددِۦۦۦۦن",
        "ءِييَااكَ", "نَعبُدُ", "وَءِييَااكَ", "نَستَعِۦۦۦۦن",
    )
    private val txt = listOf(
        "ٱلْحَمْدُ", "لِلَّهِ", "رَبِّ", "ٱلْعَـٰلَمِينَ", "ٱلرَّحْمَـٰنِ", "ٱلرَّحِيمِ", "مَـٰلِكِ", "يَوْمِ", "ٱلدِّينِ",
        "إِيَّاكَ", "نَعْبُدُ", "وَإِيَّاكَ", "نَسْتَعِينُ",
    )
    private fun aligner(opt: Map<Int, List<String>> = emptyMap()) = PhonemeAligner(ph, txt, opt, correctThreshold = 0.7f)

    /** Feeds [full] the way the streaming model does: the transcript grows by a few symbols per chunk. */
    private fun stream(a: PhonemeAligner, full: String, step: Int = 3, each: (String, LiveAlignment) -> Unit = { _, _ -> }): LiveAlignment {
        var live = a.current
        var k = step
        while (k < full.length + step) { val t = full.take(minOf(k, full.length)); live = a.update(t); each(t, live); k += step }
        return live
    }

    @Test fun normalizerRemovesMaddLengthGhunnaAndQalqala() {
        val a = PhonemeNormalizer.normalize("نَستَعِۦۦۦۦن").text
        val b = PhonemeNormalizer.normalize("نَستَعِۦۦن").text
        assertEquals(a, b)
        assertEquals(PhonemeNormalizer.normalize("ءَںںںتُم").text, PhonemeNormalizer.normalize("ءَنتُم").text)
        assertEquals(PhonemeNormalizer.normalize("قَبڇلِ").text, PhonemeNormalizer.normalize("قَبلِ").text)
        assertEquals(PhonemeNormalizer.normalize("رَببِ").text, PhonemeNormalizer.normalize("رَبِ").text)
    }

    @Test fun correctRecitationIsAllGreenAndNeverRed() {
        val a = aligner()
        val live = stream(a, ph.joinToString("")) { _, l -> assertFalse("no red on correct recitation", l.statuses.contains(WordStatus.WRONG)) }
        assertTrue(live.statuses.all { it == WordStatus.CORRECT })
        val fin = a.finish()
        assertTrue(fin.statuses.all { it == WordStatus.CORRECT }); assertTrue(fin.extras.isEmpty())
    }

    @Test fun wordTurnsGreenAsSoonAsItsPhonemesAreHeard() {
        val a = aligner()
        val l = a.update(ph[0] + ph[1])
        assertEquals(WordStatus.CORRECT, l.statuses[0]); assertEquals(WordStatus.CORRECT, l.statuses[1])
        assertEquals(WordStatus.PENDING, l.statuses[2]); assertEquals(2, l.cursor)
    }

    @Test fun toleratesModelLimitsMaddLengthIkhfaQalqala() {
        val a = aligner()
        // madd held 2 instead of 4/6, extra ghunna, missing gemination – must not be red
        val heard = "ءَلحَمدُ" + "لِلَاهِ" + "رَبِ" + "لعَالَمِۦۦن" + "ءَرَحمَانِ" + "ررَحِۦۦم" + "مَالِكِ" + "يَومِ" + "دِۦن"
        stream(a, heard)
        val fin = a.finish()
        assertTrue(fin.statuses.take(9).all { it == WordStatus.CORRECT })
    }

    @Test fun wrongWordIsRedOnlyAfterTheReciterMovesOn() {
        val a = aligner()
        val before = ph.take(12).joinToString("")
        val wrong = "نَعبُدُ"           // said instead of نَستَعِين (last word of the passage)
        stream(a, before)
        val l1 = a.update(before + wrong)
        assertEquals("not judged while it may still be spoken", WordStatus.PENDING, l1.statuses[12])
        val l2 = a.update(before + wrong, pause = true)
        assertEquals("the last word is not hastily judged at a pause", WordStatus.PENDING, l2.statuses[12])
        assertEquals(WordStatus.WRONG, a.finish().statuses[12])
    }

    @Test fun wrongWordInTheMiddleIsFinalOnceLaterWordsMatch() {
        val a = aligner()
        val heard = ph.take(6).joinToString("") + "ءَحَدُ" /* instead of مَالِكِ */
        a.update(heard)
        assertEquals(WordStatus.PENDING, a.current.statuses[6])
        val l = a.update(heard + ph[7] + ph[8].take(3))
        assertEquals(WordStatus.WRONG, l.statuses[6]); assertEquals(WordStatus.CORRECT, l.statuses[7])
        assertTrue(l.finalizedUpTo >= 6)
    }

    @Test fun skippedWordBecomesGreyAfterMovingOn() {
        val a = aligner()
        val head = ph.take(2).joinToString("")       // ... لِلَّهِ  (skips رَبِّ)
        a.update(head)
        assertEquals(WordStatus.PENDING, a.current.statuses[2])
        val l = a.update(head + ph[3] + ph[4])
        assertEquals(WordStatus.MISSING, l.statuses[2])
        assertEquals(WordStatus.CORRECT, l.statuses[3])
    }

    @Test fun insertedWordIsAnExtraAndNeighboursStayGreen() {
        val a = aligner()
        val heard = ph.take(7).joinToString("") + "للَااهِ" + ph.drop(7).take(3).joinToString("")
        stream(a, heard)
        val fin = a.finish()
        assertEquals(1, fin.extras.size)
        assertEquals(6, fin.extras[0].afterExpectedIndex)
        assertTrue(fin.statuses.take(10).all { it == WordStatus.CORRECT })
        assertTrue(fin.tokens.any { it.status == null })
    }

    @Test fun istiadhaAndBasmalaBeforeThePassageAreNotExtras() {
        val a = aligner(mapOf(0 to listOf(ZipformerPhrases.ISTIADHA, ZipformerPhrases.BASMALA)))
        stream(a, ZipformerPhrases.ISTIADHA.replace(" ", "") + ZipformerPhrases.BASMALA.replace(" ", "") + ph.take(4).joinToString(""))
        val fin = a.finish()
        assertTrue(fin.extras.isEmpty())
        assertTrue(fin.statuses.take(4).all { it == WordStatus.CORRECT })
    }

    @Test fun repeatedTranscriptUpdatesAreStable() {
        val a = aligner()
        val t = ph.take(5).joinToString("")
        val l1 = a.update(t); val l2 = a.update(t); val l3 = a.update(t, pause = true)
        assertEquals(l1.statuses, l2.statuses); assertEquals(l1.statuses.take(4), l3.statuses.take(4))
    }

    @Test fun longSessionCostStaysBounded() {
        // 300 words recited correctly: per-update work is limited to the non-final tail
        val words = (0 until 300).map { ph[it % ph.size] }; val shown = (0 until 300).map { txt[it % txt.size] }
        val a = PhonemeAligner(words, shown)
        val full = words.joinToString("")
        val t0 = System.nanoTime()
        stream(a, full, step = 4)
        val ms = (System.nanoTime() - t0) / 1e6
        assertTrue(a.finish().statuses.all { it == WordStatus.CORRECT })
        assertTrue("took $ms ms", ms < 8000)
    }
}
