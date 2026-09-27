package com.quransunah.app.data.local.mushaf

import com.quransunah.app.domain.model.LineType

/**
 * Same marker rule used when a mushaf page is assembled for the canvas.
 * A flagged row is a marker. A one-glyph eastern-digit row is also a marker.
 */
internal object AyahMarkerRule {
    fun isMarker(column: Int?, textHafs: String, ligature: String): Boolean {
        if (column == 1) return true
        return ligature.length == 1 && textHafs.isNotEmpty() && textHafs.all { it in '\u0660'..'\u0669' }
    }
}

/** Word ids attached to an ayah or basmallah line. Decorative lines carry none. */
internal fun mushafLineWordRange(
    lineTypeRaw: String,
    firstWordIndex: Int?,
    lastWordIndex: Int?,
): IntRange? {
    val lineType = LineType.fromRaw(lineTypeRaw)
    if (lineType != LineType.AYAH && lineType != LineType.BASMALLAH) return null
    val first = firstWordIndex ?: return null
    val last = lastWordIndex ?: return null
    return first..last
}
