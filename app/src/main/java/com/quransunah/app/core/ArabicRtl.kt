package com.quransunah.app.core

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import java.util.Locale

object ArabicRtl {
    val locale: Locale = Locale.forLanguageTag("ar")

    /**
     * Applies Arabic locale + RTL without freezing orientation/size.
     *
     * [Context.createConfigurationContext] alone snapshots the whole
     * configuration at [attachBaseContext] time. With
     * `configChanges="orientation|screenSize|…"`, that snapshot stays
     * portrait forever and Compose's [LocalConfiguration] never flips to
     * landscape. This wrapper always merges locale into the *live* base
     * configuration so rotation still updates screen size and orientation.
     */
    fun wrap(base: Context): Context {
        Locale.setDefault(locale)
        return LocaleRtlContext(base)
    }

    fun applyLocale(config: Configuration) {
        config.setLocale(locale)
        config.setLayoutDirection(locale)
    }

    private class LocaleRtlContext(base: Context) : ContextWrapper(base) {
        @Volatile
        private var cachedResources: Resources? = null

        @Volatile
        private var cachedKey: Int = Int.MIN_VALUE

        override fun getResources(): Resources {
            val live = baseContext.resources.configuration
            val key = configurationKey(live)
            val hit = cachedResources
            if (hit != null && key == cachedKey) return hit
            val localized = Configuration(live)
            applyLocale(localized)
            val created = baseContext.createConfigurationContext(localized).resources
            cachedResources = created
            cachedKey = key
            return created
        }

        override fun createConfigurationContext(overrideConfiguration: Configuration): Context {
            val localized = Configuration(overrideConfiguration)
            applyLocale(localized)
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
