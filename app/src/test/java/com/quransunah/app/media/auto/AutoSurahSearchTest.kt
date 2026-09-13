package com.quransunah.app.media.auto

import com.quransunah.app.data.local.mushaf.entity.SurahEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSurahSearchTest {
    private val catalog = listOf(
        SurahEntity(1, "سُورَةُ الفَاتِحَةِ", 7, "Meccan"),
        SurahEntity(2, "سُورَةُ البَقَرَةِ", 286, "Medinan"),
        SurahEntity(114, "سُورَةُ النَّاسِ", 6, "Meccan"),
    )

    @Test
    fun numberQueryJumpsToThatSurah() {
        assertEquals(listOf(114), AutoSurahSearch.surahIds("114", catalog))
        assertEquals(listOf(114), AutoSurahSearch.surahIds("١١٤", catalog))
        assertEquals(2, AutoSurahSearch.parseSurahNumber("2:255"))
        assertEquals(listOf(2), AutoSurahSearch.surahIds("2:255", catalog))
    }

    @Test
    fun nameQueryIgnoresTashkeel() {
        val people = AutoSurahSearch.surahIds("الناس", catalog)
        assertEquals(listOf(114), people)
        val cow = AutoSurahSearch.surahIds("بقرة", catalog)
        assertEquals(listOf(2), cow)
        assertTrue(AutoSurahSearch.matchesName("سُورَةُ البَقَرَةِ", "البقره"))
    }
}
