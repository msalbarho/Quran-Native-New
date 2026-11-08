package com.quransunah.app.recitation.core

import com.quransunah.app.ui.mushaf.TrainingWordMark
import com.quransunah.app.ui.mushaf.trainingMarkColor
import com.quransunah.app.ui.training.TrainingExpectedWord
import com.quransunah.app.ui.training.assessRecitation
import com.quransunah.app.ui.training.summarizeRecitation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Al-Fatiha 1:1–1:5 as the user tested it on the device:
 * ayah 1 with one wrong word (الكريم instead of الرحيم), ayah 2 skipped completely, ayah 3 correct,
 * ayah 4 with a missing word (مالك الدين), one inserted extra word, then ayah 5.
 */
class FatihaSkipAyahTest {
    // ayah number of every expected word
    private val ayahOf = listOf(1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 4, 4, 4, 5, 5, 5, 5)
    private val ph = listOf(
        "بِسمِ", "للَااهِ", "ررَحمَاانِ", "ررَحِۦۦۦۦم",
        "ءَلحَمدُ", "لِللَااهِ", "رَببِ", "لعَاالَمِۦۦۦۦن",
        "ءَررَحمَاانِ", "ررَحِۦۦۦۦم",
        "مَاالِكِ", "يَومِ", "ددِۦۦۦۦن",
        "ءِييَااكَ", "نَعبُدُ", "وَءِييَااكَ", "نَستَعِۦۦۦۦن",
    )
    private val txt = listOf(
        "بِسْمِ", "ٱللَّهِ", "ٱلرَّحْمَـٰنِ", "ٱلرَّحِيمِ",
        "ٱلْحَمْدُ", "لِلَّهِ", "رَبِّ", "ٱلْعَـٰلَمِينَ",
        "ٱلرَّحْمَـٰنِ", "ٱلرَّحِيمِ",
        "مَـٰلِكِ", "يَوْمِ", "ٱلدِّينِ",
        "إِيَّاكَ", "نَعْبُدُ", "وَإِيَّاكَ", "نَسْتَعِينُ",
    )
    private val ayah2 = 4..7

    private fun stream(a: PhonemeAligner, full: String, step: Int = 3, each: (String, LiveAlignment) -> Unit = { _, _ -> }): LiveAlignment {
        var live = a.current; var k = step
        while (k < full.length + step) { val t = full.take(minOf(k, full.length)); live = a.update(t); each(t, live); k += step }
        return live
    }

    private val expectedWords = txt.indices.map { TrainingExpectedWord(100 + it, 1, ayahOf[it], it + 1, txt[it]) }

    @Test fun zipformerSkippedAyahIsMissingLiveAndInTheReport() {
        val a = PhonemeAligner(ph, txt, correctThreshold = 0.7f)
        val ayah1 = ph[0] + ph[1] + ph[2] + "كَرِۦۦۦۦم"          // الكريم instead of الرحيم
        val ayah3 = ph[8] + ph[9]
        val ayah4 = ph[10] + ph[12]                              // مالك الدين (يوم missing)
        val extra = "كَتَبَ"                                      // inserted word
        val ayah5 = ph.subList(13, 17).joinToString("")

        // live: as soon as the reciter is clearly into ayah 3/4, the whole of ayah 2 is already missing
        val liveAt = (ayah1 + ayah3 + ph[10]).length
        var checkedLive = false
        stream(a, ayah1 + ayah3 + ayah4 + extra + ayah5) { t, live ->
            if (!checkedLive && t.length >= liveAt) {
                checkedLive = true
                ayah2.forEach { assertEquals("1:2 word $it live", WordStatus.MISSING, live.statuses[it]) }
                assertEquals(WordStatus.CORRECT, live.statuses[8]); assertEquals(WordStatus.CORRECT, live.statuses[9])
                val liveMarks = assessRecitation(expectedWords, live)
                ayah2.forEach { assertEquals(TrainingWordMark.Missing, liveMarks[it].mark) }
            }
        }
        assertTrue(checkedLive)
        val fin = a.finish()
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.CORRECT, WordStatus.WRONG), fin.statuses.subList(0, 4))
        ayah2.forEach { assertEquals(WordStatus.MISSING, fin.statuses[it]) }
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT), fin.statuses.subList(8, 10))
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.MISSING, WordStatus.CORRECT), fin.statuses.subList(10, 13))
        assertTrue(fin.statuses.subList(13, 17).all { it == WordStatus.CORRECT })
        assertEquals(1, fin.extras.size)

        val marks = assessRecitation(expectedWords, fin)
        val summary = summarizeRecitation(marks, 30_000L)
        assertEquals(TrainingWordMark.PossibleError, marks[3].mark)
        ayah2.forEach { assertEquals(TrainingWordMark.Missing, marks[it].mark) }
        assertEquals(TrainingWordMark.Missing, marks[11].mark)
        assertEquals(trainingMarkColor(TrainingWordMark.PossibleError), trainingMarkColor(TrainingWordMark.Missing)) // red
        assertEquals(1, summary.possibleErrors)
        assertEquals(5, summary.missing)
        assertTrue(summary.issues.map { it.wordId }.containsAll((ayah2.map { 100 + it }) + (100 + 11) + (100 + 3)))
    }

    @Test fun zipformerNormalPaceNeverInventsMissingOrWrong() {
        val a = PhonemeAligner(ph, txt, correctThreshold = 0.7f)
        stream(a, ph.joinToString(""), step = 2) { _, l ->
            assertFalse("no missing at normal pace", l.statuses.contains(WordStatus.MISSING))
            assertFalse("no red at normal pace", l.statuses.contains(WordStatus.WRONG))
        }
        val fin = a.finish()
        assertTrue(fin.statuses.all { it == WordStatus.CORRECT }); assertTrue(fin.extras.isEmpty())
    }

    @Test fun wordEnginesSkippedAyahIsMissing() {
        // Whisper / Android recognizer path (word aligner)
        val a = IncrementalAligner(txt)
        a.update("", "بسم الله الرحمن الكريم")
        var live = a.update("بسم الله الرحمن الكريم", "الرحمن الرحيم مالك")
        ayah2.forEach { assertEquals("1:2 word $it live", WordStatus.MISSING, live.statuses[it]) }
        live = a.update("بسم الله الرحمن الكريم الرحمن الرحيم مالك الدين كتب اياك نعبد واياك نستعين", "")
        assertEquals(WordStatus.WRONG, live.statuses[3])
        ayah2.forEach { assertEquals(WordStatus.MISSING, live.statuses[it]) }
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.CORRECT), live.statuses.subList(8, 10))
        assertEquals(listOf(WordStatus.CORRECT, WordStatus.MISSING, WordStatus.CORRECT), live.statuses.subList(10, 13))
        assertTrue(live.statuses.subList(13, 17).all { it == WordStatus.CORRECT })
        assertEquals(1, live.extras.size)
        val summary = summarizeRecitation(assessRecitation(expectedWords, live), 20_000L)
        assertEquals(5, summary.missing); assertEquals(1, summary.possibleErrors)
    }

    @Test fun wordEnginesNormalPaceNeverInventsMissing() {
        val a = IncrementalAligner(txt)
        val words = listOf("بسم", "الله", "الرحمن", "الرحيم", "الحمد", "لله", "رب", "العالمين", "الرحمن", "الرحيم", "مالك", "يوم", "الدين", "اياك", "نعبد", "واياك", "نستعين")
        val sb = StringBuilder()
        words.forEach { w ->
            val l = a.update(sb.toString().trim(), w)
            assertFalse(l.statuses.contains(WordStatus.MISSING)); assertFalse(l.statuses.contains(WordStatus.WRONG))
            sb.append(w).append(' ')
        }
        assertTrue(a.update(sb.toString().trim(), "").statuses.all { it == WordStatus.CORRECT })
    }
}
