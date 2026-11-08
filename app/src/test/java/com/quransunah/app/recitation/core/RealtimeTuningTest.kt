package com.quransunah.app.recitation.core

import com.quransunah.app.recitation.engine.ZipformerRecognizer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RealtimeTuningTest {
    private val samples = listOf(
        "بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ",
        "ٱلْحَمْدُ لِلَّهِ رَبِّ ٱلْعَٰلَمِينَ ں ۥ ۦ ٲ ـــ",
        "لل للل ل ل  ماا مم",
    )

    @Test
    fun incrementalNormalisationEqualsFull() {
        for (text in samples) {
            val inc = PhonemeNormalizer.Incremental()
            for (end in 0..text.length) {
                val prefix = text.substring(0, end)
                val a = inc.normalize(prefix)
                val b = PhonemeNormalizer.normalize(prefix)
                assertEquals(b.text, a.text)
                assertArrayEquals(b.rawIndex, a.rawIndex)
            }
            // non-prefix change falls back to full
            val other = samples.last()
            assertEquals(PhonemeNormalizer.normalize(other).text, inc.normalize(other).text)
        }
    }

    @Test
    fun pauseIsShortEnoughForLiveCorrection() {
        assertTrue(ZipformerRecognizer.PAUSE_MS <= 700L)
    }
}
