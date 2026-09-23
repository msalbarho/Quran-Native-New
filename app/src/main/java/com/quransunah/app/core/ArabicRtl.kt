package com.quransunah.app.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

/** Locale utilities for the UI. Quran text rendering remains Arabic-specific. */
object ArabicRtl {
    private const val PREFS_NAME = "holy_quran_locale"
    private const val LANGUAGE_KEY = "language_tag"
    private val supported = setOf("ar", "en", "nb")

    fun selectedLanguage(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(LANGUAGE_KEY, null)
            ?.lowercase()
            ?.takeIf { it in supported }

    fun setSelectedLanguage(context: Context, languageTag: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (languageTag == null) remove(LANGUAGE_KEY)
                else putString(LANGUAGE_KEY, languageTag.lowercase())
            }
            .apply()
    }

    fun wrap(base: Context): Context {
        val tag = selectedLanguage(base) ?: systemLanguage(base)
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        return LocaleContext(base, locale)
    }

    fun applyLocale(config: Configuration, locale: Locale) {
        config.setLocale(locale)
        // Keep the application's visual structure RTL for every UI language.
        config.setLayoutDirection(Locale.forLanguageTag("ar"))
    }

    private fun systemLanguage(context: Context): String {
        val system = context.resources.configuration.locales[0]
        return when (system.language) {
            "ar" -> "ar"
            "nb", "no" -> "nb"
            else -> "en"
        }
    }

    private class LocaleContext(base: Context, private val locale: Locale) : ContextWrapper(base) {
        @Volatile private var cachedResources: Resources? = null
        @Volatile private var cachedKey: Int = Int.MIN_VALUE

        override fun getResources(): Resources {
            val live = baseContext.resources.configuration
            val key = configurationKey(live)
            val hit = cachedResources
            if (hit != null && key == cachedKey) return hit
            val localized = Configuration(live)
            applyLocale(localized, locale)
            val created = baseContext.createConfigurationContext(localized).resources
            cachedResources = created
            cachedKey = key
            return created
        }

        override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
            val localized = Configuration(overrideConfiguration)
            applyLocale(localized, locale)
            return baseContext.createConfigurationContext(localized)
        }

        private fun configurationKey(config: Configuration): Int {
            var result = 17
            result = 31 * result + config.orientation
            result = 31 * result + config.screenWidthDp
            result = 31 * result + config.screenHeightDp
            result = 31 * result + config.smallestScreenWidthDp
            result = 31 * result + config.densityDpi
            result = 31 * result + config.uiMode
            result = 31 * result + config.screenLayout
            return result
        }
    }
}
