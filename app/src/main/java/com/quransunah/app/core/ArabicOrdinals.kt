package com.quransunah.app.core

enum class HizbQuarterKind { Full, Quarter, Half, ThreeQuarters }

data class HizbBadgeParts(
    val kind: HizbQuarterKind,
    val number: Int,
)

object ArabicOrdinals {
    private fun quranDigits(value: Int): String = EasternArabic.quranDigits(value)
    private val ones = arrayOf(
        "", "الأول", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع",
    )
    private val compound = arrayOf(
        "", "الحادي", "الثاني", "الثالث", "الرابع", "الخامس",
        "السادس", "السابع", "الثامن", "التاسع",
    )
    private val tens = arrayOf("", "", "العشرون", "الثلاثون", "الأربعون", "الخمسون", "الستون")

    /**
     * Popular opening-word names used in juz indexes (not the ordinal header ligatures).
     * Index 0 = juz 1 … index 29 = juz 30.
     */
    private val juzOpeningNames = arrayOf(
        "ألم",
        "سيقول",
        "تلك الرسل",
        "لن تنالوا",
        "والمحصنات",
        "لا يحب الله",
        "وإذا سمعوا",
        "ولو أننا",
        "قال الملأ",
        "واعلموا",
        "يعتذرون",
        "وما من دابة",
        "وما أبرئ",
        "ربما",
        "سبحان الذي",
        "قال ألم",
        "اقترب للناس",
        "قد أفلح",
        "وقال الذين",
        "أمن خلق",
        "اتل ما أوحي",
        "ومن يقنت",
        "وما لي",
        "فمن أظلم",
        "إليه يرد",
        "حم",
        "قال فما خطبكم",
        "قد سمع الله",
        "تبارك الذي",
        "عم يتساءلون",
    )

    fun masculine(value: Int): String {
        if (value < 1 || value > 60) return quranDigits(value)
        if (value == 10) return "العاشر"
        if (value < 10) return ones[value]
        if (value < 20) return "${compound[value - 10]} عشر"
        val unit = value % 10
        val ten = value / 10
        if (unit == 0) return tens[ten]
        return "${compound[unit]} و${tens[ten]}"
    }

    /** Ordinal label for the main page header: الجزء ١ … */
    fun juzLabel(juzNumber: Int): String = "الجزء ${quranDigits(juzNumber)}"

    /** Short opening name (ألم، عمّ، …) for the juz index only. */
    fun juzOpeningName(juzNumber: Int): String =
        juzOpeningNames.getOrNull(juzNumber - 1) ?: juzLabel(juzNumber)

    /** Index title: جزء عمّ، جزء ألم، … */
    fun juzIndexTitle(juzNumber: Int): String = "جزء ${juzOpeningName(juzNumber)}"

    fun hizbLabel(hizbNumber: Int): String = "الحزب ${masculine(hizbNumber)}"

    fun parseMasculine(text: String): Int? {
        val trimmed = text.trim()
        for (value in 1..60) {
            if (masculine(value) == trimmed) return value
        }
        return null
    }

    /**
     * Quarter kind and hizb number from the stored Arabic marker label.
     * Display text is localized separately; this only classifies the existing marker.
     */
    fun hizbBadgeParts(rawLabel: String): HizbBadgeParts? {
        val label = rawLabel.replace(Regex("\\s+"), " ").trim()
        if (label.isEmpty()) return null
        val match = Regex("الحزب\\s+(.+)$").find(label) ?: return null
        val hizbNumber = parseMasculine(match.groupValues[1].trim()) ?: return null
        val prefix = label.substring(0, match.range.first).trim()
        val kind = when (prefix) {
            "" -> HizbQuarterKind.Full
            "ربع" -> HizbQuarterKind.Quarter
            "نصف" -> HizbQuarterKind.Half
            "ثلاثة أرباع", "ثلاث أرباع" -> HizbQuarterKind.ThreeQuarters
            else -> return null
        }
        return HizbBadgeParts(kind, hizbNumber)
    }

    /** Split a page quarter label into stacked badge lines, matching React `formatPageQuarterBadgeLines`. */
    fun quarterBadgeLines(rawLabel: String): List<String> {
        val label = rawLabel.replace(Regex("\\s+"), " ").trim()
        if (label.isEmpty()) return emptyList()
        val match = Regex("الحزب\\s+(.+)$").find(label) ?: return listOf(label)
        val hizbNumber = parseMasculine(match.groupValues[1].trim()) ?: return listOf(label)
        val prefix = label.substring(0, match.range.first).trim()
        val lines = ArrayList<String>()
        when {
            prefix.isEmpty() -> lines += "الحزب"
            prefix == "ربع" -> {
                lines += "ربع"
                lines += "الحزب"
            }
            prefix == "نصف" -> {
                lines += "نصف"
                lines += "الحزب"
            }
            prefix == "ثلاثة أرباع" || prefix == "ثلاث أرباع" -> {
                lines += "ثلاث"
                lines += "أرباع"
                lines += "الحزب"
            }
            else -> {
                lines += prefix.split(' ')
                lines += "الحزب"
            }
        }
        lines += quranDigits(hizbNumber)
        return lines
    }
}
