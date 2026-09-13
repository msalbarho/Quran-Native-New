package com.quransunah.app.media.auto

import android.content.Context
import android.content.res.Configuration
import com.quransunah.app.core.ArabicRtl
import java.util.Locale

/**
 * Forces Arabic locale + RTL for Auto browse labels (ported from quran-app AutoRtl).
 * Independent of the phone UI language.
 */
object AutoRtl {
    private val ARABIC: Locale = Locale.forLanguageTag("ar")

    fun wrap(base: Context): Context = ArabicRtl.wrap(base)

    @Suppress("DEPRECATION")
    fun forceArabicRtl(context: Context) {
        Locale.setDefault(ARABIC)
        val config = Configuration(context.resources.configuration)
        config.setLocale(ARABIC)
        config.setLayoutDirection(ARABIC)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }
}
