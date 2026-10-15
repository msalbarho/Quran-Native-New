package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.UiNumbers
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.index.revelationPlace
import com.quransunah.app.ui.shell.IslamicEdgePattern
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

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
    val paper = LocalPaperColors.current
    val nameColor = paper.textStrong
    val metaColor = paper.textStrong
    val ayahs = surah?.numberOfAyahs?.let { count ->
        stringResource(R.string.surah_frame_ayahs_count, UiNumbers.format(count, UiNumbers.resolvedLocale()))
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
        val shape = RoundedCornerShape(22.dp)
        val stroke = paper.accent.copy(alpha = if (night) 0.42f else 0.30f)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(paper.chromeFill)
                .border(1.dp, stroke, shape),
        ) {
            IslamicEdgePattern(Modifier.matchParentSize())
            MosqueEnd(alignStart = true, width = frameHeight * 1.32f, alpha = if (night) 0.50f else 0.40f)
            MosqueEnd(alignStart = false, width = frameHeight * 1.32f, alpha = if (night) 0.50f else 0.40f)
            Canvas(Modifier.fillMaxSize()) {
                val inset = 3.5.dp.toPx()
                drawRoundRect(
                    color = paper.accent.copy(alpha = if (night) 0.22f else 0.16f),
                    topLeft = Offset(inset, inset),
                    size = Size(
                        (size.width - inset * 2f).coerceAtLeast(0f),
                        (size.height - inset * 2f).coerceAtLeast(0f),
                    ),
                    cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    style = Stroke(width = 0.8.dp.toPx()),
                )
            }
        }

        if (ayahs != null || revelation != null) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = frameWidth * 0.11f),
                ) {
                    val metaSize = when {
                        frameHeight < 36.dp || frameWidth < 280.dp -> 10.sp
                        else -> 12.sp
                    }
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
            fallback = stringResource(R.string.surah_fallback, UiNumbers.format(surahNumber, UiNumbers.resolvedLocale())),
            fontManager = fontManager,
            titleTypeface = titleTypeface,
            color = nameColor,
            ligatureSize = if (frameHeight < 40.dp) (frameHeight.value * 0.62f).sp else 22.sp,
            fallbackSize = 14.sp,
            modifier = Modifier
                .widthIn(max = frameWidth * if (frameWidth < 300.dp) 0.44f else 0.50f)
                .padding(bottom = 2.dp),
        )
    }
}

/**
 * The WebP is one transparent illustration with a mosque at each end.
 * SrcIn keeps that alpha mask and replaces the beige pixels with the
 * active theme accent. Placement and aspect ratio stay unchanged.
 */
@Composable
private fun BoxScope.MosqueEnd(
    alignStart: Boolean,
    width: Dp,
    alpha: Float,
) {
    val paper = LocalPaperColors.current
    val sideWidth = if (width.value.isFinite()) width else 48.dp
    Box(
        modifier = Modifier
            .align(if (alignStart) Alignment.CenterStart else Alignment.CenterEnd)
            .width(sideWidth)
            .fillMaxHeight()
            .clipToBounds(),
    ) {
        Image(
            painter = painterResource(R.drawable.surah_mosque_silhouette),
            contentDescription = null,
            alignment = if (alignStart) Alignment.CenterStart else Alignment.CenterEnd,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.tint(paper.accent, BlendMode.SrcIn),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = if (alignStart) {
                            arrayOf(
                                0f to Color.Transparent,
                                0.62f to Color.Transparent,
                                1f to paper.chromeFill,
                            )
                        } else {
                            arrayOf(
                                0f to paper.chromeFill,
                                0.38f to Color.Transparent,
                                1f to Color.Transparent,
                            )
                        },
                    ),
                ),
        )
    }
}
