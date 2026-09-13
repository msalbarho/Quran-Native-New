package com.quransunah.app.media.auto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoPresentationTest {
    @Test
    fun surahTitleUsesUnvocalizedLabel() {
        val title = AutoPresentation.surahTitle(1, "سُورَةُ الفَاتِحَةِ")
        assertTrue(title.contains("001. سورة الفاتحة"))
        assertFalse(title.contains("سورة 1"))
    }

    @Test
    fun surahLabelFallsBackToBundledNames() {
        assertEquals("سورة الفاتحة", AutoPresentation.surahLabel(1, null))
        assertEquals("سورة البقرة", AutoPresentation.surahLabel(2, ""))
        assertEquals("سورة الناس", AutoPresentation.surahLabel(114, null))
    }

    @Test
    fun bundledCatalogHasAllSurahs() {
        assertEquals(114, (1..114).count { SurahArabicNames.shortName(it) != null })
        assertEquals("الفاتحة", SurahArabicNames.shortName(1))
        assertEquals("سورة الفاتحة", SurahArabicNames.label(1))
    }
}
