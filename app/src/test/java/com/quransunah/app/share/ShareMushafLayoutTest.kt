package com.quransunah.app.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test


class ShareMushafLayoutTest {
    private fun word(ayah: Int, position: Int, marker: Boolean = false) = ShareMushafWord(
        ayah = ayah,
        position = position,
        qcf = "g$ayah$position",
        uthmanic = "w$ayah$position",
        isMarker = marker,
    )

    @Test
    fun cutKeepsOriginalLineBreaksAndDropsOtherAyat() {
        val pages = listOf(
            SharePageLines(
                pageNumber = 1,
                lines = listOf(
                    ShareSourceLine(1, listOf(word(4, 1), word(4, 2), word(5, 1))),
                    ShareSourceLine(2, listOf(word(5, 2), word(5, 3, marker = true), word(6, 1))),
                    ShareSourceLine(3, listOf(word(6, 2), word(7, 1))),
                ),
            ),
        )
        val cut = ShareMushafLayout.cutLines(pages, fromAyah = 5, toAyah = 6)
        assertEquals(3, cut.size)
        assertEquals(listOf(5), cut[0].words.map { it.ayah }.distinct())
        assertEquals(listOf(5, 6), cut[1].words.map { it.ayah }.distinct())
        assertEquals(listOf(6), cut[2].words.map { it.ayah })
        assertEquals(1, cut[0].words.size)
        assertTrue(cut[1].words.any { it.isMarker })
        assertTrue(cut.none { line -> line.words.any { it.ayah == 4 || it.ayah == 7 } })
    }

    @Test
    fun wrappedArabicLinesUseTightMushafSpacing() {
        // v7: slightly roomier Arabic lines for 4–5-line whole-ayah pages; image share also roomier.
        assertTrue(ShareMushafLayout.ARABIC_LINE_MULTIPLIER <= 1.20f)
        assertTrue(ShareMushafLayout.ARABIC_LINE_MULTIPLIER >= 1.05f)
        assertTrue(ShareMushafLayout.IMAGE_LINE_MULTIPLIER <= 1.50f)
        assertTrue(ShareMushafLayout.IMAGE_LINE_MULTIPLIER >= 1.30f)
        assertTrue(ShareMushafLayout.PAGE_WORD_GAP < ShareMushafLayout.TEXT_WORD_GAP)
        assertTrue(ShareMushafLayout.TITLE_PAD_Y_FACTOR < 0.30f)
        val threeLines = 3 * ShareMushafLayout.arabicLineHeight(24f)
        val oldGappy = 3 * 24f * 1.45f
        assertTrue(threeLines < oldGappy)
        assertEquals(24f * ShareMushafLayout.ARABIC_LINE_MULTIPLIER, ShareMushafLayout.arabicLineHeight(24f), 0.001f)
        // v11/v12: tight glyph-bounds pitch (same as ARABIC_LINE_MULTIPLIER); fixed ayah size replaces 1.2x boost.
        assertEquals(1.12f, ShareMushafLayout.VIDEO_LINE_MULTIPLIER, 0.001f)
        assertEquals(1.0f, ShareMushafLayout.VIDEO_ARABIC_SIZE_FACTOR, 0.001f)
        assertEquals(0.06214f, ShareMushafLayout.VIDEO_AYAH_SIZE_FRACTION, 0.00001f)
        assertEquals(0.06f, ShareMushafLayout.VIDEO_METRICS_LEADING, 0.001f)
        assertTrue(ShareMushafLayout.VIDEO_WRAP_WIDTH < 1f)
    }

    @Test
    fun stackedCopyIsTitleThenAyatThenTranslationRoom() {
        val geometry = makerVerticalPlacement(.48f, .16f, .22f, .05f, true, true)
        assertTrue(geometry.titleCenterY < geometry.ayahCenterY)
        val titleBottom = geometry.titleCenterY + geometry.titleHeight / 2f
        val ayahTop = geometry.ayahCenterY - geometry.ayahHeight / 2f
        assertTrue(titleBottom <= ayahTop + 0.0001f)
        assertTrue(geometry.ayahCenterY + geometry.ayahHeight / 2f <= 0.8001f)
        assertTrue(ShareMushafLayout.TRANSLATION_GAP_WIDTH < 0.05f)
        assertTrue(ShareMushafLayout.TITLE_SIZE_FACTOR <= 0.61f)
    }

    @Test
    fun oneAyahNeverEnlargesAndMoreAyatShrink() {
        assertEquals(1f, ShareMushafLayout.fitScale(1, 1), 0f)
        assertTrue(ShareMushafLayout.fitScale(5, 8) < 1f)
        assertTrue(ShareMushafLayout.heightFitScale(1, 40f, 200f) == 1f)
        assertTrue(ShareMushafLayout.heightFitScale(10, 40f, 200f) < 1f)
    }

    @Test
    fun videoRangeStartsAtSelectedAyahAndKeepsTenAyahMaximum() {
        // Model a page containing ayahs 6–9: first, middle and last selections stay exact.
        assertEquals(6..15, ShareMushafLayout.initialAyahRange(6, 286, 10))
        assertEquals(8..17, ShareMushafLayout.initialAyahRange(8, 286, 10))
        assertEquals(9..18, ShareMushafLayout.initialAyahRange(9, 286, 10))
        assertEquals(10, ShareMushafLayout.initialAyahRange(8, 286, 10).count())
        assertEquals(1..10, ShareMushafLayout.initialAyahRange(1, 286, 10))
        assertEquals(286..286, ShareMushafLayout.initialAyahRange(286, 286, 10))
        assertEquals(8..8, ShareMushafLayout.initialAyahRange(8, 8, 10))
    }

    @Test
    fun imageRangeKeepsSelectedConsecutiveVersesWithinThreeVerseLimit() {
        assertEquals(8..10, ShareMushafLayout.imageShareRange(8, 10, 286))
        assertEquals(8..9, ShareMushafLayout.imageShareRange(8, 9, 286))
        assertEquals(286..286, ShareMushafLayout.imageShareRange(286, 300, 286))
        assertEquals(3, ShareMushafLayout.imageShareRange(8, 10, 286).count())
    }

    @Test
    fun imageMushafLinesKeepEverySelectedAyahAndExcludeAdjacentAyat() {
        val pages = listOf(
            SharePageLines(
                pageNumber = 1,
                lines = listOf(
                    ShareSourceLine(1, listOf(word(7, 1), word(8, 1))),
                    ShareSourceLine(2, listOf(word(8, 2), word(9, 1))),
                    ShareSourceLine(3, listOf(word(9, 2), word(10, 1), word(11, 1))),
                ),
            ),
        )
        val range = ShareMushafLayout.imageShareRange(8, 10, 286)
        val cut = ShareMushafLayout.cutLines(pages, range.first, range.last)
        assertEquals(listOf(8, 9, 10), cut.flatMap { line -> line.words.map { it.ayah } }.distinct())
        assertTrue(cut.none { line -> line.words.any { it.ayah == 7 || it.ayah == 11 } })
    }

    @Test
    fun underlineGrowsRtlThroughEveryRecitedWord() {
        val boxes = listOf(
            ShareWordBox(0, 1, 1, 0, 80f, 120f, 0f, 20f, 16f, 4f),
            ShareWordBox(1, 1, 2, 0, 40f, 75f, 0f, 20f, 16f, 4f),
            ShareWordBox(2, 1, 3, 0, 0f, 35f, 0f, 20f, 16f, 4f),
        )
        val first = ShareMushafLayout.cumulativeUnderlines(boxes, 0, 0.5f).single()
        assertEquals(120f, first.right, 0.01f)
        assertEquals(100f, first.left, 0.01f)
        val throughSecond = ShareMushafLayout.cumulativeUnderlines(boxes, 1, 1f).single()
        assertEquals(120f, throughSecond.right, 0.01f)
        assertEquals(40f, throughSecond.left, 0.01f)
        val all = ShareMushafLayout.cumulativeUnderlines(boxes, 2, 1f).single()
        assertEquals(120f, all.right, 0.01f)
        assertEquals(0f, all.left, 0.01f)
    }

    @Test
    fun qulCatalogCoversVerifiedAyahRecitersAndJuhani() {
        assertEquals(18, QulRecitations.recitationId("123"))
        assertEquals(26, QulRecitations.recitationId("92"))
        assertEquals(22, QulRecitations.recitationId("husary"))
        assertEquals(13, QulRecitations.recitationId("102"))
        assertEquals(15, QulRecitations.recitationId("51"))
        assertEquals(16, QulRecitations.recitationId("54"))
        assertEquals(25, QulRecitations.recitationId("31"))
        assertEquals(162, QulRecitations.recitationId("62"))
        assertTrue(QulRecitations.spec("62")!!.isSurahLevel)
        assertTrue(QulRecitations.ayahAudioUrl("123", 1, 1)!!.contains("alafasy/001001.mp3"))
        assertEquals(null, QulRecitations.ayahAudioUrl("5", 1, 1))
        assertEquals(null, QulRecitations.recitationId("5"))
        listOf("123", "92", "husary", "102", "51", "54", "31").forEach {
            assertTrue(QulRecitations.hasWordTimingCatalog(it))
        }
        assertTrue(!QulRecitations.hasWordTimingCatalog("5"))
        assertTrue(!QulRecitations.hasWordTimingCatalog("62"))
    }

    @Test
    fun kenBurnsOnlyZoomsIn() {
        assertEquals(1f, ShareMushafLayout.kenBurnsScale(0, 1_000_000), 0.001f)
        assertTrue(ShareMushafLayout.kenBurnsScale(1_000_000, 1_000_000) > 1f)
    }
}
