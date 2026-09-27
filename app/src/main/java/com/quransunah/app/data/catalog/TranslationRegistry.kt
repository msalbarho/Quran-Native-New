package com.quransunah.app.data.catalog

/**
 * Packaged Quran translations. Arabic has no entry: the mushaf text is native content.
 *
 * Asset paths are relative to `assets/` and match the prepared SQLite filenames.
 */
object TranslationRegistry {
    data class Translation(
        val languageTag: String,
        val assetPath: String,
    )

    val entries: List<Translation> = listOf(
        Translation("en", "translations/en.sqlite"),
        Translation("nb", "translations/nb.sqlite"),
        Translation("id", "translations/id.sqlite"),
        Translation("ur", "translations/ur.sqlite"),
        Translation("bn", "translations/bn.sqlite"),
        Translation("hi", "translations/hi.sqlite"),
        Translation("tr", "translations/tr.sqlite"),
        Translation("fa", "translations/fa.sqlite"),
        Translation("fr", "translations/fr.sqlite"),
        Translation("ha", "translations/ha.sqlite"),
        Translation("ms", "translations/ms.sqlite"),
        Translation("ps", "translations/ps.sqlite"),
        Translation("pa", "translations/pa.sqlite"),
        Translation("uz", "translations/uz.sqlite"),
        Translation("ru", "translations/ru.sqlite"),
        Translation("sw", "translations/sw.sqlite"),
        Translation("so", "translations/so.sqlite"),
        Translation("fa-AF", "translations/fa-AF.sqlite"),
        Translation("ku", "translations/ku.sqlite"),
        Translation("zgh", "translations/zgh.sqlite"),
        Translation("de", "translations/de.sqlite"),
    )

    val byTag: Map<String, Translation> = entries.associateBy { it.languageTag.lowercase() }

    fun forLanguage(tag: String?): Translation? = tag?.let { byTag[it.lowercase()] }
}
