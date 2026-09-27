package com.quransunah.app.core

import android.text.TextUtils
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * Layout direction of the selected app UI language.
 * The rest of the app stays on the global direction set in [ArabicRtl.applyLocale].
 */
@Composable
@ReadOnlyComposable
fun uiLayoutDirection(): LayoutDirection {
    val tag = ArabicRtl.currentTag(LocalContext.current)
    val rtl = TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(tag)) ==
        View.LAYOUT_DIRECTION_RTL
    return if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
}
