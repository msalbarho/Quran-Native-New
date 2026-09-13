package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.index.revelationPlace
import com.quransunah.app.ui.shell.ChromeFrameDecor
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode

/** React `--mushaf-surah-frame-aspect` (3420×384). */
internal const val SURAH_FRAME_ASPECT = 3420f / 384f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SurahNameFrame(
    surahNumber: Int,
    surah: SurahInfo?,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val night = LocalNightMode.current
    val nameColor = if (night) Color.White else Color.Black
    val metaColor = if (night) Color.White else Color.Black
    val ayahs = surah?.numberOfAyahs?.let { count ->
        stringResource(R.string.surah_frame_ayahs_count, EasternArabic.format(count))
    }
    val revelation = when (revelationPlace(surah?.revelationType.orEmpty())) {
        "meccan" -> stringResource(R.string.index_makkah)
        "medinan" -> stringResource(R.string.index_madinah)
        else -> surah?.revelationType?.takeIf { it.isNotBlank() }
    }
    val label = stringResource(R.string.surah_frame_long_press)
    val metaFamily = LocalDisplayFontFamily.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = label }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
                onLongClick = onLongPress,
            ),
        contentAlignment = Alignment.Center,
    ) {
        val frameWidth = maxWidth
        val frameHeight = maxHeight
        ChromeFrameDecor(modifier = Modifier.fillMaxSize())

        if (ayahs != null || revelation != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = frameWidth * 0.11f),
                ) {
                    val metaSize = if (frameHeight < 40.dp) 10.sp else 12.sp
                    if (ayahs != null) {
                        Text(
                            text = ayahs,
                            color = metaColor,
                            fontFamily = metaFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = metaSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                    }
                    if (revelation != null) {
                        Text(
                            text = revelation,
                            color = metaColor,
                            fontFamily = metaFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = metaSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.CenterEnd),
                        )
                    }
                }
            }
        }

        QcfGlyphText(
            ligature = fontManager?.surahNameLigature(surahNumber).orEmpty(),
            fallback = stringResource(R.string.surah_fallback, EasternArabic.format(surahNumber)),
            fontManager = fontManager,
            titleTypeface = titleTypeface,
            color = nameColor,
            ligatureSize = if (frameHeight < 40.dp) (frameHeight.value * 0.62f).sp else 22.sp,
            fallbackSize = 14.sp,
            modifier = Modifier
                .widthIn(max = frameWidth * 0.52f)
                .padding(bottom = 2.dp),
        )
    }
}
