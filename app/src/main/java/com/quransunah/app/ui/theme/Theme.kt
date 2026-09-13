package com.quransunah.app.ui.theme

import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

val LocalPaperColors = staticCompositionLocalOf { PaperPalettes.resolve(PaperPaletteId.Beige, false) }
val LocalNightMode = staticCompositionLocalOf { false }
val LocalPaperPaletteId = staticCompositionLocalOf { PaperPaletteId.Beige }

private fun appTypography(bodyFont: FontFamily, displayFont: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 34.sp),
    displayMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    displaySmall = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    headlineSmall = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleLarge = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = TextStyle(fontFamily = displayFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 22.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = bodyFont,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = bodyFont, fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
)

@Composable
fun HolyQuranTheme(
    paletteId: PaperPaletteId = PaperPaletteId.Beige,
    nightMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val paper = PaperPalettes.resolve(paletteId, nightMode)
    val bodyFont = rememberAppFontFamily()
    val displayFont = rememberDisplayFontFamily()
    val scheme: ColorScheme = if (nightMode) {
        darkColorScheme(
            primary = paper.accent,
            secondary = paper.accentSoft,
            background = paper.pageBackground,
            surface = paper.surface,
            surfaceContainerLow = paper.pageBody,
            surfaceContainerHigh = paper.tone400,
            onPrimary = Color(0xFF1C1B1F),
            onSecondary = paper.textStrong,
            onBackground = paper.textPrimary,
            onSurface = paper.textStrong,
            onSurfaceVariant = paper.textMuted,
        )
    } else {
        lightColorScheme(
            primary = paper.accent,
            secondary = paper.accentSoft,
            background = paper.pageBackground,
            surface = paper.surface,
            surfaceContainerLow = paper.pageBody,
            surfaceContainerHigh = paper.tone300,
            onPrimary = Color.White,
            onSecondary = paper.textStrong,
            onBackground = paper.textPrimary,
            onSurface = paper.textStrong,
            onSurfaceVariant = paper.textMuted,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = paper.pageBackground.toArgb()
            window.navigationBarColor = paper.pageBackground.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !nightMode
            controller.isAppearanceLightNavigationBars = !nightMode
        }
    }

    CompositionLocalProvider(
        LocalPaperColors provides paper,
        LocalNightMode provides nightMode,
        LocalPaperPaletteId provides paletteId,
        LocalAppFontFamily provides bodyFont,
        LocalDisplayFontFamily provides displayFont,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = appTypography(bodyFont = bodyFont, displayFont = displayFont),
            content = content,
        )
    }
}
