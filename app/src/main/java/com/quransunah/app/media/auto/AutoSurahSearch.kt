package com.quransunah.app.media.auto

import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicNormalize
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.local.mushaf.entity.SurahEntity

/** Ranks Auto search hits: exact surah number, then name (tashkeel-insensitive). */
object AutoSurahSearch {

    fun surahIds(query: String, surahs: Collection<SurahEntity>): List<Int> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val parsedNumber = parseSurahNumber(trimmed)
        val foldedQuery = foldName(trimmed)
        val digits = EasternArabic.westernDigits(trimmed)
        val hits = ArrayList<Pair<Int, Int>>(8)
        for (surah in surahs) {
            val surahId = surah.id ?: continue
            val rank = rank(surahId, surah.nameArabic, parsedNumber, foldedQuery, digits) ?: continue
            hits.add(surahId to rank)
        }
        hits.sortWith(compareBy({ it.second }, { it.first }))
        return hits.map { it.first }
    }

    internal fun parseSurahNumber(raw: String): Int? {
        val digits = EasternArabic.westernDigits(raw.trim())
        if (digits.isEmpty()) return null
        digits.toIntOrNull()?.takeIf { it in 1..AppConstants.SURAH_COUNT }?.let { return it }
        val separator = raw.indexOfFirst { it == ':' || it == ' ' || it == '،' || it == '/' }
        if (separator <= 0) return null
        val chapter = EasternArabic.westernDigits(raw.substring(0, separator)).toIntOrNull()
        return chapter?.takeIf { it in 1..AppConstants.SURAH_COUNT }
    }

    internal fun matchesName(nameArabic: String, foldedQuery: String): Boolean {
        if (foldedQuery.isEmpty()) return false
        val foldedName = foldName(nameArabic)
        if (foldedName.contains(foldedQuery)) return true
        val queryBare = ArabicNormalize.stripArticle(foldedQuery)
        val nameBare = ArabicNormalize.stripArticle(foldedName)
        return queryBare.isNotEmpty() && nameBare.contains(queryBare)
    }

    private fun rank(
        surahNumber: Int,
        nameArabic: String,
        parsedNumber: Int?,
        foldedQuery: String,
        digits: String,
    ): Int? {
        if (parsedNumber != null && surahNumber == parsedNumber) return 0
        if (digits.isNotEmpty() && surahNumber.toString() == digits) return 0
        if (digits.isNotEmpty() && surahNumber.toString().startsWith(digits)) return 1
        if (matchesName(nameArabic, foldedQuery)) return 2
        return null
    }

    private fun foldName(value: String): String {
        val stripped = value
            .replace(Regex("^سُورَةُ\\s+"), "")
            .replace(Regex("^سورة\\s+"), "")
        return ArabicNormalize.fold(stripped, stripSpaces = true)
    }
}
