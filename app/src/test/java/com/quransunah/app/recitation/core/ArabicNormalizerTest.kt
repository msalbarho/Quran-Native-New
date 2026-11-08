package com.quransunah.app.recitation.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicNormalizerTest {
    private fun n(s: String) = ArabicNormalizer.normalize(s)

    @Test fun stripsHarakatAndShadda() {
        assertEquals("بسم الله الرحمان الرحيم", n("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ ٱلرَّحِيمِ"))
    }

    @Test fun unifiesAlefForms() {
        assertEquals("ا ا ا ا", n("أ إ آ ٱ"))
        assertEquals("انزلنا", n("أَنزَلْنَا"))
    }

    @Test fun taMarbutaAndAlefMaqsura() {
        assertEquals("رحمه", n("رَحْمَةٌ"))
        assertEquals("علي", n("عَلَىٰ"))
        assertEquals("موسي", n("مُوسَىٰ"))
    }

    @Test fun hamzaOnCarriers() {
        assertEquals("يومنون", n("يُؤْمِنُونَ"))
        assertEquals("ايها", n("أَيُّهَا"))
        assertEquals("بير", n("بِئْرٍ"))
    }

    @Test fun uthmaniSmallLettersAndMarksRemoved() {
        // small waw / small yeh / waqf signs / rub el hizb
        assertEquals("انه", n("إِنَّهُۥ"))
        assertEquals("فيه", n("فِيهِۦ ۛ"))
        assertEquals("ذالك الكتاب لا ريب", n("ذَٰلِكَ ٱلْكِتَـٰبُ لَا رَيْبَ ۛ"))
        assertEquals("كفروا", n("كَفَرُوا۟"))
    }

    @Test fun daggerAlefAfterWawBecomesAlef() {
        assertEquals("الصلاه", n("ٱلصَّلَوٰةَ"))
        assertEquals("الزكاه", n("ٱلزَّكَوٰةَ"))
    }

    @Test fun tatweelAndPunctuation() {
        assertEquals("ياايها", n("يَـٰٓأَيُّهَا"))
        assertEquals("العالمين", n("ٱلْعَـٰلَمِينَ"))
        assertEquals("قال نعم", n("قال، نعم."))
        assertEquals("", n("١٢٣ ۞"))
    }

    @Test fun skeletonIgnoresAlefAndHamza() {
        assertEquals(ArabicNormalizer.skeleton(n("ٱلرَّحْمَٰنِ")), ArabicNormalizer.skeleton(n("الرحمن")))
        assertTrue(WordAligner.similarity(n("ٱلسَّمَٰوَٰتِ"), n("السماوات")) >= 0.9f)
        assertTrue(WordAligner.similarity(n("ٱلصَّلَوٰةَ"), n("الصلاة")) >= 0.9f)
    }

    @Test fun recognizerSpellingMatchesUthmani() {
        val pairs = listOf(
            "ٱلْحَمْدُ" to "الحمد", "لِلَّهِ" to "لله", "رَبِّ" to "رب", "ٱلْعَـٰلَمِينَ" to "العالمين",
            "مَـٰلِكِ" to "مالك", "ٱلصِّرَٰطَ" to "الصراط", "ٱلْمُسْتَقِيمَ" to "المستقيم",
            "ٱلضَّآلِّينَ" to "الضالين", "إِيَّاكَ" to "اياك",
        )
        for ((u, r) in pairs) {
            val sim = WordAligner.similarity(n(u), n(r))
            assertTrue("$u vs $r sim=$sim", sim >= 0.8f)
        }
    }
}
