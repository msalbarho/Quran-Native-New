package com.quransunah.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicNormalizeTest {
    @Test
    fun foldDropsTashkeelAndUnifiesHamzaAlefs() {
        val vocalized = "بِسْمِ ٱللَّهِ"
        val typed = "بسم الله"
        assertEquals(
            ArabicNormalize.fold(typed, stripSpaces = true),
            ArabicNormalize.fold(vocalized, stripSpaces = true),
        )
        assertEquals("امنوا", ArabicNormalize.fold("آمَنُوا", stripSpaces = true))
        assertEquals("ابراهيم", ArabicNormalize.fold("إِبْرَاهِيمَ", stripSpaces = true))
        assertEquals("اصحاب", ArabicNormalize.fold("أَصْحَابِ", stripSpaces = true))
    }

    @Test
    fun foldUnifiesAlefMaqsuraAndYaa() {
        assertEquals(
            ArabicNormalize.fold("على", stripSpaces = true),
            ArabicNormalize.fold("علي", stripSpaces = true),
        )
        assertEquals(
            ArabicNormalize.fold("موسى", stripSpaces = true),
            ArabicNormalize.fold("موسي", stripSpaces = true),
        )
    }

    @Test
    fun foldIgnoresIsolatedHamzaAndTatweel() {
        assertEquals("قران", ArabicNormalize.fold("قُرْآن", stripSpaces = true))
        assertEquals("الله", ArabicNormalize.fold("الـلـه", stripSpaces = true))
    }

    @Test
    fun foldMapsTehMarbutaToHeh() {
        assertEquals("صلاه", ArabicNormalize.fold("صَلَاة", stripSpaces = true))
        assertEquals(
            ArabicNormalize.fold("صلاة", stripSpaces = true),
            ArabicNormalize.fold("صلاه", stripSpaces = true),
        )
    }

    @Test
    fun stripArticleRemovesAlPrefixAfterFold() {
        assertEquals("بقره", ArabicNormalize.stripArticle("البقرة"))
        assertEquals("بقره", ArabicNormalize.stripArticle("بقرة"))
    }
}

class SearchHighlightTest {
    @Test
    fun highlightFindsFoldedNeedleInsideVocalizedText() {
        val text = "اللَّهُ لَا إِلَٰهَ إِلَّا هُوَ"
        val range = com.quransunah.app.data.repository.SearchRepositoryImpl.highlightRange(text, "اله")
        requireNotNull(range)
        assertTrue(range.first < range.second)
        val slice = text.substring(range.first, range.second)
        assertTrue(ArabicNormalize.fold(slice, stripSpaces = true).contains("اله"))
    }

    @Test
    fun highlightMatchesHamzaVariants() {
        val text = "آمَنَ الرَّسُولُ"
        val range = com.quransunah.app.data.repository.SearchRepositoryImpl.highlightRange(
            text,
            ArabicNormalize.fold("امن", stripSpaces = true),
        )
        requireNotNull(range)
        assertEquals(0, range.first)
    }
}
