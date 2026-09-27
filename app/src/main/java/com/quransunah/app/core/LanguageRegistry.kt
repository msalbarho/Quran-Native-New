package com.quransunah.app.core

import java.util.Locale

/**
 * Single source of truth for supported UI languages.
 *
 * Keep this registry independent of Quran translation availability so the
 * UI language and the Quran translation language can be configured separately
 * in the future.
 */
object LanguageRegistry {
    data class Language(
        val tag: String,
        val nativeName: String,
        val displayName: String,
        /** Android res qualifier, e.g. values-fr or values-pt-rBR. */
        val resDir: String,
        /** Small country flag shown beside the language name. */
        val flag: String,
    )

    val entries: List<Language> = listOf(
        Language("ar", "العربية", "Arabic", "values-ar", "🇸🇦"),
        Language("en", "English", "English", "values", "🇺🇸"),
        Language("nb", "Norsk Bokmål", "Norwegian Bokmål", "values-nb", "🇳🇴"),
        Language("id", "Bahasa Indonesia", "Indonesian", "values-in", "🇮🇩"),
        Language("ur", "اردو", "Urdu", "values-ur", "🇵🇰"),
        Language("bn", "বাংলা", "Bengali", "values-bn", "🇧🇩"),
        Language("hi", "हिन्दी", "Hindi", "values-hi", "🇮🇳"),
        Language("tr", "Türkçe", "Turkish", "values-tr", "🇹🇷"),
        Language("fa", "فارسی", "Persian", "values-fa", "🇮🇷"),
        Language("fr", "Français", "French", "values-fr", "🇫🇷"),
        Language("ha", "Hausa", "Hausa", "values-ha", "🇳🇬"),
        Language("ms", "Bahasa Melayu", "Malay", "values-ms", "🇲🇾"),
        Language("ps", "پښتو", "Pashto", "values-ps", "🇦🇫"),
        Language("pa", "ਪੰਜਾਬੀ", "Punjabi", "values-pa", "🇮🇳"),
        Language("uz", "Oʻzbekcha", "Uzbek", "values-uz", "🇺🇿"),
        Language("ru", "Русский", "Russian", "values-ru", "🇷🇺"),
        Language("sw", "Kiswahili", "Swahili", "values-sw", "🇹🇿"),
        Language("so", "Soomaali", "Somali", "values-so", "🇸🇴"),
        Language("fa-AF", "دری", "Dari", "values-fa-rAF", "🇦🇫"),
        Language("ku", "Kurdish", "Kurdish", "values-ku", "🇮🇶"),
        Language("zgh", "ⵜⴰⵎⴰⵣⵓⵔⵜ", "Amazigh", "values-b+zgh", "🇲🇦"),
        Language("de", "Deutsch", "German", "values-de", "🇩🇪"),
    )

    val byTag: Map<String, Language> = entries.associateBy { it.tag.lowercase() }

    val tags: Set<String> = entries.map { it.tag.lowercase() }.toSet()

    val sortedEntries: List<Language> = entries.sortedWith(
        compareBy<Language> { entry ->
            when (entry.tag.lowercase()) {
                "ar" -> 0
                "en" -> 1
                "nb" -> 2
                else -> 3
            }
        }.thenBy { it.nativeName.lowercase() },
    )

    fun byTag(tag: String?): Language? = tag?.let { byTag[it.lowercase()] }

    fun displayLabel(tag: String?): String = when (tag) {
        null -> "System"
        else -> byTag(tag)?.nativeName ?: tag
    }

    fun localeFor(tag: String): Locale = Locale.forLanguageTag(tag)

    /** Returns the best matching supported language tag for a raw Android locale. */
    fun matchTag(locale: Locale): String? {
        val fullTag = locale.toLanguageTag().lowercase()
        if (fullTag in byTag) return fullTag
        val lang = locale.language.lowercase()
        if (lang in tags) return lang
        return when (lang) {
            "no" -> "nb"
            "nn" -> "nb"
            "in" -> "id"
            else -> null
        }
    }

    /** Fallback used when the device language is not supported. */
    fun defaultTag(): String = "en"
}
