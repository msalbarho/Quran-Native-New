package com.quransunah.app.media.auto

import android.content.Context
import com.quransunah.app.core.ArabicRtl

/**
 * Applies the user's selected UI locale to Android Auto browse labels.
 */
object AutoRtl {
    fun wrap(base: Context): Context = ArabicRtl.wrap(base)

    fun forceArabicRtl(context: Context) {
        // Locale is already applied by attachBaseContext; avoid changing the
        // process-wide locale after the service has been created.
    }
}
