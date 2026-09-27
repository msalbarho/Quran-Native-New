package com.quransunah.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.quransunah.app.core.AppConstants

/**
 * Body UI face (Noto Naskh): lists, settings, tafsir, bookmarks, form labels.
 * Not used for QCF mushaf, page-header ligatures, or index surah/juz names.
 */
val LocalAppFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

/**
 * Display UI face (Amiri): section titles, word-sheet headings, mushaf chrome meta
 * (مكية / عدد الآيات / أرباع الأحزاب). Never for ayah glyphs or header surah/juz.
 */
val LocalDisplayFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Default }

@Composable
fun rememberAppFontFamily(): FontFamily =
    rememberAssetFontFamily(
        regularPath = AppConstants.UI_BODY_FONT_REGULAR,
        boldPath = AppConstants.UI_BODY_FONT_BOLD,
    )

@Composable
fun rememberDisplayFontFamily(): FontFamily =
    rememberAssetFontFamily(
        regularPath = AppConstants.UI_DISPLAY_FONT_REGULAR,
        boldPath = AppConstants.UI_DISPLAY_FONT_BOLD,
    )

@Composable
private fun rememberAssetFontFamily(regularPath: String, boldPath: String): FontFamily {
    val assets = LocalContext.current.assets
    return remember(assets, regularPath, boldPath) {
        runCatching {
            FontFamily(
                Font(path = regularPath, assetManager = assets, weight = FontWeight.Normal),
                Font(path = boldPath, assetManager = assets, weight = FontWeight.Bold),
                Font(path = boldPath, assetManager = assets, weight = FontWeight.SemiBold),
                Font(path = boldPath, assetManager = assets, weight = FontWeight.ExtraBold),
            )
        }.getOrDefault(FontFamily.Serif)
    }
}
