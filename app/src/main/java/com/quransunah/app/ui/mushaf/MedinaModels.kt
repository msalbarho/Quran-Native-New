package com.quransunah.app.ui.mushaf

import android.graphics.RectF
import com.quransunah.app.core.AppConstants
import com.quransunah.app.data.local.mushaf.entity.WordEntity
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.QuranWord

/**
 * Word-level token for the Medina canvas. Field names follow the packaged
 * `words` table (`word_id`, `surah_id`, `ayah_number`, `word_position`,
 * `text_hafs`, `qpc_ligature`, `is_ayah_marker`).
 */
data class WordRecord(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    val position: Int,
    val uthmanic: String,
    val qcfLigature: String,
    val isAyahMarker: Boolean,
    val meaning: String? = null,
) {
    /** Glyph painted on a QCF4 page; falls back to Hafs if the ligature is empty. */
    val displayGlyph: String
        get() = qcfLigature.ifBlank { uthmanic }

    fun rubParts(displayLigature: String = displayGlyph): RubElHizb.Parts? =
        RubElHizb.parts(uthmanic, displayLigature)
}

data class LineRecord(
    val lineNum: Int,
    val lineType: LineType,
    val centered: Boolean,
    val words: List<WordRecord>,
    val surahNumber: Int? = null,
)

/** Word identity plus the canvas-space bounding box recorded during the last paint. */
data class WordLayoutInfo(
    val word: WordRecord,
    val bounds: RectF,
    val lineNum: Int,
) {
    fun contains(x: Float, y: Float): Boolean = bounds.contains(x, y)
}

fun WordEntity.toWordRecord(meaning: String? = null): WordRecord = WordRecord(
    id = wordId ?: error("word row has null word_id"),
    surah = surahId,
    ayah = ayahNumber,
    position = wordPosition,
    uthmanic = textHafs.orEmpty(),
    qcfLigature = qpcLigature.orEmpty(),
    isAyahMarker = isAyahMarker == 1,
    meaning = meaning,
)

fun QuranWord.toWordRecord(): WordRecord = WordRecord(
    id = id,
    surah = surah,
    ayah = ayah,
    position = wordIndex,
    uthmanic = textHafs,
    qcfLigature = textLigature,
    isAyahMarker = isAyahMarker,
    meaning = meaning,
)

fun MushafLine.toLineRecord(): LineRecord = LineRecord(
    lineNum = lineNumber,
    lineType = lineType,
    centered = isCentered || lineType == LineType.SURAH_NAME || lineType == LineType.BASMALLAH,
    words = words.map { it.toWordRecord() },
    surahNumber = surahNumber,
)

fun MushafPage.toLineRecords(): List<LineRecord> {
    val mapped = lines.map { it.toLineRecord() }
    if (mapped.size >= AppConstants.LINES_PER_PAGE) {
        return mapped.take(AppConstants.LINES_PER_PAGE)
    }
    val padded = mapped.toMutableList()
    var nextLineNum = (mapped.maxOfOrNull { it.lineNum } ?: 0) + 1
    while (padded.size < AppConstants.LINES_PER_PAGE) {
        padded += LineRecord(
            lineNum = nextLineNum,
            lineType = LineType.EMPTY,
            centered = false,
            words = emptyList(),
            surahNumber = null,
        )
        nextLineNum += 1
    }
    return padded
}
