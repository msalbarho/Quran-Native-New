package com.quransunah.app.ui.index

import com.quransunah.app.core.AppConstants
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafPage

/**
 * Resolves the surah the reader is currently in — same idea as React
 * `getSurahForPage`: prefer on-page ayah/surah markers, scanning from the
 * bottom so a page that ends one surah and starts the next lands on the
 * later surah.
 */
fun MushafPage.primarySurahNumber(): Int {
    lines.asReversed().forEach { line ->
        when (line.lineType) {
            LineType.SURAH_NAME -> line.surahNumber?.takeIf { it.isValidSurah() }?.let { return it }
            LineType.AYAH -> {
                line.words.asReversed().firstOrNull()?.surah?.takeIf { it.isValidSurah() }?.let { return it }
                line.surahNumber?.takeIf { it.isValidSurah() }?.let { return it }
            }
            else -> {
                line.words.asReversed().firstOrNull()?.surah?.takeIf { it.isValidSurah() }?.let { return it }
                line.surahNumber?.takeIf { it.isValidSurah() }?.let { return it }
            }
        }
    }
    lines.forEach { line ->
        line.surahNumber?.takeIf { it.isValidSurah() }?.let { return it }
        line.words.firstOrNull()?.surah?.takeIf { it.isValidSurah() }?.let { return it }
    }
    return 1
}

private fun Int.isValidSurah(): Boolean = this in 1..AppConstants.SURAH_COUNT
