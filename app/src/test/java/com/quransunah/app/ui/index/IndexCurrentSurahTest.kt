package com.quransunah.app.ui.index

import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.QuranWord
import org.junit.Assert.assertEquals
import org.junit.Test

class IndexCurrentSurahTest {
    @Test
    fun continuationPageUsesAyahSurah() {
        val page = pageOf(
            ayahLine(words = listOf(word(surah = 83, ayah = 1))),
            ayahLine(words = listOf(word(surah = 83, ayah = 2))),
        )
        assertEquals(83, page.primarySurahNumber())
    }

    @Test
    fun pageStartingNewSurahPrefersLaterSurah() {
        val page = pageOf(
            ayahLine(words = listOf(word(surah = 82, ayah = 19))),
            MushafLine(
                lineNumber = 2,
                lineType = LineType.SURAH_NAME,
                isCentered = true,
                surahNumber = 83,
                words = emptyList(),
            ),
            ayahLine(words = listOf(word(surah = 83, ayah = 1))),
        )
        assertEquals(83, page.primarySurahNumber())
    }

    private fun pageOf(vararg lines: MushafLine) = MushafPage(
        pageNumber = 587,
        lines = lines.toList(),
        juzNumber = 30,
        hizbNumber = 59,
    )

    private fun ayahLine(words: List<QuranWord>) = MushafLine(
        lineNumber = 1,
        lineType = LineType.AYAH,
        isCentered = false,
        surahNumber = words.firstOrNull()?.surah,
        words = words,
    )

    private fun word(surah: Int, ayah: Int) = QuranWord(
        id = surah * 1000 + ayah,
        surah = surah,
        ayah = ayah,
        wordIndex = 1,
        textLigature = "",
        textHafs = "ء",
    )
}
