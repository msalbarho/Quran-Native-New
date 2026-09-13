package com.quransunah.app.ui.index

import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.SurahInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuranIndexMatchTest {
    private val baqarah = SurahInfo(
        number = 2,
        nameArabic = "سُورَةُ البَقَرَةِ",
        numberOfAyahs = 286,
        revelationType = "Medinan",
        startPage = 2,
    )

    @Test
    fun surahQueryIgnoresTashkeelAndHamza() {
        assertTrue(matchesSurahQuery(baqarah, "البقرة"))
        assertTrue(matchesSurahQuery(baqarah, "بقره"))
        assertTrue(matchesSurahQuery(baqarah, "٢"))
    }

    @Test
    fun tawbahQueryMatchesPartialName() {
        val tawbah = SurahInfo(
            number = 9,
            nameArabic = "سُورَةُ التَّوْبَةِ",
            numberOfAyahs = 129,
            revelationType = "Medinan",
            startPage = 187,
        )
        assertTrue(matchesSurahQuery(tawbah, "التوبة"))
        assertTrue(matchesSurahQuery(tawbah, "توبه"))
        assertTrue(matchesSurahQuery(tawbah, "٩"))
    }

    @Test
    fun divisionJumpCarriesStartAyah() {
        val juz2 = DivisionInfo(
            id = 2,
            type = "juz",
            number = 2,
            name = "الجزء الثاني",
            pageNumber = 22,
            surah = 2,
            ayah = 142,
        )
        val jump = juz2.toIndexJump()
        assertEquals(22, jump.pageNumber)
        assertEquals(2, jump.surah)
        assertEquals(142, jump.ayah)
        assertTrue(jump.hasAyah)
    }
}
