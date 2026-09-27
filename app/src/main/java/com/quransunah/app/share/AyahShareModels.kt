package com.quransunah.app.share

data class AyahShareVerse(
    val ayah: Int,
    val pageNumber: Int,
    val qcfText: String,
    val uthmanicText: String,
    /** QCF Medina ayah-end mark (and sajdah number glyph when applicable). */
    val markerLigature: String? = null,
)

data class AyahShareContent(
    val surah: Int,
    val surahName: String,
    val fromAyah: Int,
    val toAyah: Int,
    val verses: List<AyahShareVerse>,
) {
    fun shouldShowBasmalah(): Boolean = surah !in SURAHS_WITHOUT_BASMALAH

    fun shouldPrependIntro(): Boolean = true

    fun shouldPrependBasmalahRecitation(): Boolean = shouldShowBasmalah()

    companion object {
        /** Al-Fatiha (the opening verse is the basmalah) and At-Tawbah have no extra basmalah. */
        val SURAHS_WITHOUT_BASMALAH = setOf(1, 9)
    }
}
