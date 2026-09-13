package com.quransunah.app.ui.mushaf

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.shell.ThemeToggle
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val LigatureSize = 25.sp
private val FallbackSize = 15.sp

@Composable
fun PageHeaderBar(
    page: MushafPage?,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    night: Boolean,
    onSearch: () -> Unit,
    onThemeToggle: () -> Unit,
    onOpenSurahIndex: () -> Unit,
    onOpenJuzIndex: () -> Unit,
    surahNames: Map<Int, String> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val inspection = LocalInspectionMode.current
    val juzNumber = page?.juzNumber ?: 1
    val surahNumbers = remember(page) { HeaderGlyphFit.surahsOnPage(page) }
    val showSurah = pageNumber != 1 && surahNumbers.isNotEmpty()
    val combined = fontManager?.usesCombinedHeaderSurahGlyph(surahNumbers) == true
    val surahFallback = remember(surahNumbers, surahNames) {
        HeaderGlyphFit.headerFallbackLabel(surahNumbers, surahNames)
    }
    val juzFallback = stringResource(R.string.header_juz, EasternArabic.format(juzNumber))
    val surahLigature = if (inspection || fontManager == null) {
        ""
    } else {
        fontManager.headerSurahLigature(surahNumbers)
    }
    val juzLigature = if (inspection || fontManager == null) null else fontManager.juzHeaderLigature(juzNumber)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = ChromeTokens.HorizontalInset)
            .padding(top = ChromeTokens.HeaderTopGap, bottom = ChromeTokens.HeaderBottomGap),
    ) {
        val shape = RoundedCornerShape(ChromeTokens.Corner)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ChromeTokens.HeaderHeight)
                .shadow(6.dp, shape, clip = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(paper.chromeFill)
                    .border(1.6.dp, ChromeTokens.Gold, shape),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val tile = 18.dp.toPx()
                    val stroke = paper.decorPattern
                    var y = 0f
                    while (y < size.height + tile) {
                        var x = 0f
                        while (x < size.width + tile) {
                            drawHeaderGirih(Offset(x + tile / 2f, y + tile / 2f), tile * 0.36f, stroke)
                            x += tile
                        }
                        y += tile
                    }
                    drawRect(
                        color = ChromeTokens.GoldInner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showSurah) {
                    HeaderGlyph(
                        ligature = surahLigature,
                        fallback = surahFallback,
                        fontManager = fontManager,
                        color = if (night) Color.White else paper.textStrong,
                        contentDescription = stringResource(R.string.header_open_surah_index, surahFallback),
                        onClick = onOpenSurahIndex,
                        ltr = combined || surahNumbers.size > 1,
                    )
                }
                HeaderGlyph(
                    ligature = juzLigature.orEmpty(),
                    fallback = juzFallback,
                    fontManager = fontManager,
                    color = if (night) Color.White else paper.textStrong,
                    contentDescription = stringResource(R.string.header_open_juz_index, juzFallback),
                    onClick = onOpenJuzIndex,
                )
                Spacer(Modifier.weight(1f, fill = true))
                SearchChip(
                    night = night,
                    onClick = onSearch,
                )
                ThemeToggle(
                    night = night,
                    onToggle = onThemeToggle,
                    width = 30.dp,
                    height = 26.dp,
                )
            }
        }
    }
}

@Composable
private fun SearchChip(
    night: Boolean,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val label = stringResource(R.string.header_search_mushaf)
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier = Modifier
            .height(22.dp)
            .widthIn(min = 38.dp)
            .clip(shape)
            .background(
                if (night) Color.Black.copy(alpha = 0.28f)
                else Color(0xFFEDE6D4).copy(alpha = 0.72f),
            )
            .border(1.dp, if (night) Color.White.copy(alpha = 0.92f) else Color.Black, shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 6.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = if (night) paper.accent else paper.accentHover,
            modifier = Modifier.size(14.dp),
        )
    }
}

@Composable
private fun HeaderGlyph(
    ligature: String,
    fallback: String,
    fontManager: QcfFontManager?,
    color: Color,
    contentDescription: String,
    onClick: () -> Unit,
    ltr: Boolean = false,
) {
    val inspection = LocalInspectionMode.current
    var typeface by remember { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    LaunchedEffect(fontManager) {
        if (fontManager != null && !inspection) {
            typeface = fontManager.loadSurahTitleTypeface() ?: fontManager.loadUthmanicTypeface()
        }
    }
    val canDrawLigature = remember(ligature, typeface) {
        fontManager?.canDrawLigature(ligature, typeface) == true
    }
    val clickModifier = Modifier
        .semantics { this.contentDescription = contentDescription }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    if (canDrawLigature && typeface != null) {
        LigatureCanvas(
            text = ligature,
            typeface = typeface!!,
            color = color,
            ltr = ltr,
            modifier = clickModifier,
        )
    } else {
        Text(
            text = fallback,
            color = color,
            fontSize = FallbackSize,
            lineHeight = FallbackSize,
            fontFamily = FontFamily.Serif,
            textAlign = if (ltr) TextAlign.End else TextAlign.Start,
            style = TextStyle(
                textDirection = if (ltr) TextDirection.Ltr else TextDirection.Rtl,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Visible,
            modifier = clickModifier.wrapContentWidth(unbounded = true),
        )
    }
}

@Composable
private fun LigatureCanvas(
    text: String,
    typeface: Typeface,
    color: Color,
    ltr: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val paint = remember(typeface, color, density) {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = with(density) { LigatureSize.toPx() }
            this.color = color.toArgb()
            textAlign = Paint.Align.LEFT
        }
    }
    paint.color = color.toArgb()
    paint.textSize = with(density) { LigatureSize.toPx() }
    val bounds = Rect()
    paint.getTextBounds(text, 0, text.length, bounds)
    val advance = paint.measureText(text)
    val widthPx = HeaderGlyphFit.visualWidthPx(paint, text).coerceAtLeast(1f)
    val originX = HeaderGlyphFit.originXPx(
        advancePx = advance,
        inkLeftPx = bounds.left.toFloat(),
        inkRightPx = bounds.right.toFloat(),
        canvasWidthPx = widthPx,
        ltr = ltr,
    )
    Canvas(
        modifier = modifier
            .height(ChromeTokens.HeaderHeight)
            .requiredWidth(with(density) { widthPx.toDp() }),
    ) {
        val native = drawContext.canvas.nativeCanvas
        val fm = paint.fontMetrics
        val y = size.height / 2f - (fm.ascent + fm.descent) / 2f
        native.drawText(text, originX, y, paint)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHeaderGirih(
    center: Offset,
    outer: Float,
    color: Color,
) {
    val inner = outer * 0.41f
    val path = Path()
    for (i in 0 until 16) {
        val angle = (PI / 8.0) * i - PI / 2.0
        val radius = if (i % 2 == 0) outer else inner
        val x = center.x + (cos(angle) * radius).toFloat()
        val y = center.y + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = 0.7.dp.toPx()))
}

@ArabicPreviews
@Composable
private fun PageHeaderBarPreview() {
    PreviewTheme {
        PageHeaderBar(
            page = null,
            pageNumber = 2,
            fontManager = null,
            night = LocalNightMode.current,
            onSearch = {},
            onThemeToggle = {},
            onOpenSurahIndex = {},
            onOpenJuzIndex = {},
        )
    }
}
