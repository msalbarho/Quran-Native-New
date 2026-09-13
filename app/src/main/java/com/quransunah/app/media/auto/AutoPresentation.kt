package com.quransunah.app.media.auto

import androidx.core.text.BidiFormatter
import java.util.Locale

object AutoPresentation {
    private const val RLM = "\u200F"
    private const val RLI = "\u2067"
    private const val PDI = "\u2069"
    private val arabic: Locale = Locale.forLanguageTag("ar")
    private val bidi: BidiFormatter by lazy { BidiFormatter.getInstance(arabic) }
    /** Arabic combining marks (tashkeel) + tatweel. */
    private val tashkeel = Regex("[\\u064B-\\u065F\\u0670\\u0640]")

    fun rtlLabel(text: String): String = bidi.unicodeWrap(text)

    /**
     * Auto / notification title: `001. سورة الفاتحة` (unvocalized, stable).
     */
    fun surahTitle(surahNumber: Int, nameArabic: String): String {
        val number = String.format(Locale.US, "%03d", surahNumber)
        return "$RLI$number. ${surahLabel(surahNumber, nameArabic)}$PDI"
    }

    /**
     * Display label `سورة الفاتحة`. Prefers a cleaned DB name, then the bundled
     * short-name table, and never emits `سورة 1` when a known name exists.
     */
    fun surahLabel(surahNumber: Int, nameArabic: String?): String {
        if (!nameArabic.isNullOrBlank()) {
            val cleaned = cleanSurahLabel(nameArabic)
            val body = cleaned.removePrefix("سورة").trim()
            if (body.isNotBlank()) return "سورة $body"
        }
        SurahArabicNames.label(surahNumber)?.let { return it }
        val short = SurahArabicNames.shortName(surahNumber)
        if (short != null) return "سورة $short"
        return "سورة $surahNumber"
    }

    fun cleanSurahLabel(nameArabic: String): String {
        val stripped = nameArabic.replace(tashkeel, "").trim()
        val body = stripped.removePrefix("سورة").trim().ifBlank { stripped }
        return if (body.startsWith("سورة")) body else "سورة $body"
    }

    fun surahSubtitle(revelationType: String, ayahCount: Int): String {
        val revelation = revelationLabel(revelationType)
        val verses = "${String.format(Locale.US, "%d", ayahCount)} ${ayahWord(ayahCount)}"
        return "$RLM$revelation • ${RLI}$verses$PDI"
    }

    fun revelationLabel(raw: String): String {
        val normalized = raw.trim().lowercase(Locale.ROOT)
        return when {
            normalized.contains("medin") || normalized.contains("مدن") -> "مدنية"
            normalized.contains("mecc") || normalized.contains("makk") || normalized.contains("مك") -> "مكية"
            raw.isBlank() -> "—"
            else -> raw
        }
    }

    fun ayahWord(count: Int): String {
        val mod100 = count % 100
        return when {
            count == 2 || mod100 == 2 -> "آيتان"
            mod100 in 3..10 -> "آيات"
            else -> "آية"
        }
    }
}
