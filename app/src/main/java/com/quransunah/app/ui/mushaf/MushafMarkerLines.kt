package com.quransunah.app.ui.mushaf

import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.QuarterMarker
import com.quransunah.app.domain.model.SajdaMarker

fun findQuarterMarkerLineNumber(
    lines: List<LineRecord>,
    marker: QuarterMarker?,
): Int? {
    val rubLine = lines.firstOrNull { line ->
        line.words.any { word ->
            word.uthmanic.isNotEmpty() && word.uthmanic[0] == RubElHizb.MARK
        }
    }?.lineNum
    if (rubLine != null) return rubLine
    if (marker == null) return null
    return lines.firstOrNull { line ->
        line.lineType == LineType.AYAH &&
            line.words.any { word -> word.surah == marker.startSurah && word.ayah == marker.startAyah }
    }?.lineNum
}

fun findSajdaMarkerLineNumber(
    lines: List<LineRecord>,
    marker: SajdaMarker?,
): Int? {
    if (marker == null) return null
    return lines.lastOrNull { line ->
        line.lineType == LineType.AYAH &&
            line.words.any { word -> word.surah == marker.startSurah && word.ayah == marker.startAyah }
    }?.lineNum
}

fun lineSlotIndex(lines: List<LineRecord>, lineNum: Int?): Int? {
    if (lineNum == null) return null
    val index = lines.indexOfFirst { it.lineNum == lineNum }
    return index.takeIf { it >= 0 }
}
