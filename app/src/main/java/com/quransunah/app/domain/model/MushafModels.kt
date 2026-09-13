package com.quransunah.app.domain.model

enum class LineType {
    SURAH_NAME,
    BASMALLAH,
    AYAH,
    EMPTY,
    ;

    companion object {
        fun fromRaw(raw: String): LineType = when (raw) {
            "surah_name" -> SURAH_NAME
            "basmallah", "basmalah" -> BASMALLAH
            "ayah" -> AYAH
            else -> EMPTY
        }
    }
}

data class QuranWord(
    val id: Int,
    val surah: Int,
    val ayah: Int,
    val wordIndex: Int,
    val textLigature: String,
    val textHafs: String,
    val isAyahMarker: Boolean = false,
    val meaning: String? = null,
)

data class MushafLine(
    val lineNumber: Int,
    val lineType: LineType,
    val isCentered: Boolean,
    val surahNumber: Int?,
    val words: List<QuranWord>,
)

data class MushafPage(
    val pageNumber: Int,
    val lines: List<MushafLine>,
    val juzNumber: Int,
    val hizbNumber: Int,
    val quarterLabel: String? = null,
    val quarter: QuarterMarker? = null,
    val sajda: SajdaMarker? = null,
)

data class SurahInfo(
    val number: Int,
    val nameArabic: String,
    val numberOfAyahs: Int,
    val revelationType: String,
    val startPage: Int,
)

data class DivisionInfo(
    val id: Int,
    val type: String,
    val number: Int,
    val name: String,
    val pageNumber: Int,
    val surah: Int,
    val ayah: Int,
    val parentNumber: Int = 0,
)

data class PageMeta(
    val pageNumber: Int,
    val juzNumber: Int,
    val hizbNumber: Int,
)

data class QuarterMarker(
    val pageNumber: Int,
    val label: String,
    val startSurah: Int,
    val startAyah: Int,
)

data class SajdaMarker(
    val pageNumber: Int,
    val startSurah: Int,
    val startAyah: Int,
    val required: Boolean,
    val number: Int = 0,
)

data class ReadingBookmark(
    val id: String,
    val surah: Int,
    val ayah: Int,
    val pageNumber: Int,
    val wordId: Int,
    val wordIndex: Int,
    val ayahText: String,
    val savedAt: Long,
) {
    companion object {
        fun idFor(surah: Int, ayah: Int): String = "$surah:$ayah"
    }
}

data class AyahSearchHit(
    val surah: Int,
    val ayah: Int,
    val pageNumber: Int,
    val juzNumber: Int = 0,
    val text: String,
    val surahName: String,
    val snippet: String,
    val highlightStart: Int = -1,
    val highlightEnd: Int = -1,
)

data class IndexJump(
    val pageNumber: Int,
    val surah: Int = 0,
    val ayah: Int = 0,
) {
    val hasAyah: Boolean get() = surah > 0 && ayah > 0
}

data class AyahRef(
    val surah: Int,
    val ayah: Int,
)
