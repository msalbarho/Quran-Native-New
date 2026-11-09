package com.quransunah.app.share

import org.junit.Assert.*
import org.junit.Test

class VideoMakerTimingTest {
    private val words = listOf(WordSegment(1, 100, 500), WordSegment(2, 700, 1200))
    @Test fun qulFourTupleUsesTheWordPositionNotTheLeadingIndex() {
        val words = listOf(
            wordSegmentFromQulRow(listOf(0, 1, 0, 560)),
            wordSegmentFromQulRow(listOf(1, 2, 760, 1200)),
            wordSegmentFromQulRow(listOf(2, 3, 2000, 2360)),
            wordSegmentFromQulRow(listOf(3, 4, 2680, 6120)),
        )
        assertEquals(listOf(1, 2, 3, 4), words.map { it!!.position })
        assertEquals(0L, words[0]!!.startMs)
        assertEquals(560L, words[0]!!.endMs)
        assertEquals(2, wordSegmentFromQulRow(listOf(2, 760, 1200))!!.position)
    }
    @Test fun wordLookupHonoursBoundariesAndSilence() {
        assertNull(words.wordAt(0))
        assertEquals(1, words.wordAt(100))
        assertEquals(1, words.wordAt(499))
        assertNull(words.wordAt(500))
        assertNull(words.wordAt(699))
        assertEquals(2, words.wordAt(700))
        assertNull(words.wordAt(1200))
    }
    @Test fun frameIntervalsCoverSilenceAndTailExactly() {
        val frames = words.videoFrames(1_500_000)
        assertEquals(1_500_000L, frames.sumOf { it.durationUs })
        assertNull(frames.first().word)
        assertNull(frames.last().word)
        assertTrue(frames.all { it.durationUs > 0 })
        frames.zipWithNext().forEach { (a,b) -> assertEquals(a.startUs + a.durationUs, b.startUs) }
    }
    @Test fun emptyTimingsKeepOneCompleteAyahFrame() {
        assertEquals(listOf(WordVideoFrame(0, 3000000, null)), emptyList<WordSegment>().videoFrames(3000000))
    }
    @Test fun segmentsBeyondAudioAreClipped() {
        val frames = words.videoFrames(900000)
        assertEquals(900000L, frames.sumOf { it.durationUs })
        assertEquals(2, frames.last().word)
    }
    @Test fun cursorNeverBlinksInsideInterWordGaps() {
        val cursor = words.cursorSegments(1500000)
        assertEquals(1, cursor.wordAt(0))
        assertEquals(1, cursor.wordAt(699))
        assertEquals(2, cursor.wordAt(700))
        assertEquals(2, cursor.wordAt(1499))
    }
    @Test fun lateFirstWordAnchorCannotRemoveItsLeadingSyllable() {
        val cursor = listOf(WordSegment(1,880,1240), WordSegment(2,1520,6840)).cursorSegments(6852063)
        assertEquals(0L, cursor.first().startMs)
        assertEquals(1, cursor.wordAt(400))
        assertEquals(2, cursor.wordAt(1520))
    }
    @Test fun fadesOnlyAffectSelectionEdgesNotAyahJoins() {
        val total = 44100L * 20
        assertEquals(0f, makerEdgeFadeGain(0,total), 0f)
        assertEquals(0f, makerEdgeFadeGain(total-1,total), 0f)
        assertEquals(1f, makerEdgeFadeGain(13230,total), 0f)
        assertEquals(1f, makerEdgeFadeGain(44100L*3,total), 0f)
        assertEquals(1f, makerEdgeFadeGain(44100L*9,total), 0f)
        assertEquals(.5f, makerEdgeFadeGain(6615,total), .0001f)
    }
    @Test fun nativeNumberGlyphRangeIsCompleteAndSingleCodepoint() {
        assertEquals(0xFC00, HafsAyahMarker.glyph(1).first().code)
        assertEquals(0xFD1D, HafsAyahMarker.glyph(286).first().code)
        assertTrue((1..286).all { HafsAyahMarker.glyph(it).length == 1 })
    }
    @Test fun editorStartsWithReadableVideoDefaults() {
        val options = VideoMakerOptions()
        assertTrue(options.marker)
        assertTrue(options.showTitle)
        assertFalse(options.translation)
        assertEquals(0, options.trackingMode)
        assertEquals(0xFFD4B06A.toInt(), options.markerColor)
        assertTrue(options.ayahY in .2f.. .34f)
        assertTrue(options.titleY < options.ayahY)
        assertTrue(options.ayahY - options.titleY < 0.22f)
        assertTrue(ShareBackgrounds.FILES.contains(options.background))
        assertEquals(7, ShareMushafLayout.HIGHLIGHT_COLOURS.size)
        assertEquals(3, ShareMushafLayout.MARKER_COLOURS.size)
        assertEquals(22f, ShareMushafLayout.scaleToFontSize(options.textScale), 0.01f)
        assertEquals(1f, ShareMushafLayout.fontSizeToScale(22f), 0.01f)
        assertTrue(ShareMushafLayout.fontSizeToScale(29f) <= 29f / 22f + 0.001f)
        assertFalse(makerNeedsSmoothFrames(options.copy(trackingMode = 1, backgrounds = listOf(options.background))))
        assertTrue(makerNeedsSmoothFrames(options.copy(trackingMode = 2)))
    }
    @org.junit.Test fun smoothRenderIntervalsAreContinuousAndNeverSubFrame() {
        val frames=makerRenderFrames(listOf(WordSegment(1,0,200),WordSegment(2,200,600)),600000L,true,100_000L)
        org.junit.Assert.assertEquals(600000L,frames.sumOf { it.durationUs })
        org.junit.Assert.assertTrue(frames.all { it.durationUs in 100_000..200_000 })
        org.junit.Assert.assertEquals(0L,frames.first().startUs)
        org.junit.Assert.assertEquals(100_000L, makerExportFrameUs(2_000_000L, true))
        val longStep = makerExportFrameUs(120_000_000L, true)
        org.junit.Assert.assertTrue(120_000_000L / longStep <= 480)
    }
    @org.junit.Test fun backgroundsDivideEquallyAndCrossfadeAtBoundary() {
        org.junit.Assert.assertEquals(MakerBackgroundMix(0,0,0f),makerBackgroundMix(0,30000000,3))
        org.junit.Assert.assertEquals(MakerBackgroundMix(0,1,.5f),makerBackgroundMix(10000000,30000000,3))
        org.junit.Assert.assertEquals(MakerBackgroundMix(1,2,.5f),makerBackgroundMix(20000000,30000000,3))
        org.junit.Assert.assertEquals(MakerBackgroundMix(2,2,0f),makerBackgroundMix(30000000,30000000,3))
    }
    @org.junit.Test fun draggedBlocksNeverOverlapOrReachWatermark() {
        for(height in listOf(.08f,.25f,.48f))for(a in listOf(-1f,.3f,.5f,2f))for(t in listOf(-1f,.5f,2f)) {
            val p=makerVerticalPlacement(a,t,height,.06f,true,true)
            org.junit.Assert.assertTrue(p.ayahCenterY-p.titleCenterY>=makerBlockSeparation(height,.06f)-.0001f)
            org.junit.Assert.assertTrue(p.ayahCenterY+height/2f<=.8001f)
            org.junit.Assert.assertTrue(p.titleCenterY-.03f>=.0499f)
        }
    }
}
