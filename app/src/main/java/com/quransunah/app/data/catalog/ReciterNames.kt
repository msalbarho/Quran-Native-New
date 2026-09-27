package com.quransunah.app.data.catalog

/**
 * Display names for the bundled reciters.
 * Arabic-script locales keep the catalog Arabic name.
 * Every other supported locale uses the explicit Latin name.
 * Names are not translated and are not produced by transliteration.
 */
object ReciterNames {
    private val arabicScriptLocales = setOf("ar", "ur", "fa", "fa-af", "ps", "ku")

    private val latinById = mapOf(
        "92" to "Yasser Al-Dosari",
        "5" to "Ahmed bin Ali Al-Ajmi",
        "123" to "Mishary Alafasy",
        "102" to "Maher Al-Muaiqly",
        "104" to "Mahmoud Khalil Al-Hussary",
        "husary" to "Mahmoud Khalil Al-Hussary",
        "62" to "Abdullah Awad Al-Juhani",
        "54" to "Abdul Rahman Al-Sudais",
        "31" to "Saud Al-Shuraim",
        "51" to "Abdul Basit Abdus Samad",
    )

    fun usesArabicScript(languageTag: String?): Boolean {
        val tag = languageTag?.lowercase() ?: return true
        if (tag in arabicScriptLocales) return true
        val language = tag.substringBefore('-')
        return language in arabicScriptLocales && language != "fa"
    }

    fun display(languageTag: String?, reciterId: String, arabicName: String): String {
        if (usesArabicScript(languageTag)) return arabicName
        return latinById[reciterId] ?: arabicName
    }

    fun display(languageTag: String?, reciterId: Int, arabicName: String): String =
        display(languageTag, reciterId.toString(), arabicName)
}
