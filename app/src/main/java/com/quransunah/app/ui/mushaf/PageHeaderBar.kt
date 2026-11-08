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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
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
import com.quransunah.app.core.UiNumbers
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.shell.IslamicEdgePattern
import com.quransunah.app.ui.shell.ThemeToggle
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

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
    onOpenBookmarks: () -> Unit,
    surahNames: Map<Int, String> = emptyMap(),
    showDivisionLabels: Boolean = true,
    meaningsSelected: Boolean = false,
    onSelectMeanings: (Boolean) -> Unit = {},
    informationOnly: Boolean = false,
    hizbLabel: String = "",
    tajweedEnabled: Boolean = true,
    onToggleTajweed: () -> Unit = {},
    onTajweedGuide: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val juzNumber = page?.juzNumber ?: 1
    val surahNumbers = remember(page) { listOfNotNull(HeaderGlyphFit.firstSurahOnPage(page)) }
    val languageTag = com.quransunah.app.core.ArabicRtl.currentTag(androidx.compose.ui.platform.LocalContext.current)
    val surahFallback = remember(surahNumbers, surahNames, languageTag) {
        val number = surahNumbers.firstOrNull()
        number?.let { com.quransunah.app.ui.index.SurahNames.localized(languageTag, it) }
            ?: HeaderGlyphFit.headerFallbackLabel(surahNumbers, surahNames)
    }
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val juzWords = remember(juzNumber, languageTag, resources) { headerNumberWords(juzNumber, languageTag, resources) }
    val juzFallback = stringResource(R.string.header_juz, juzWords)

    var headerFace by remember(fontManager) { mutableStateOf(fontManager?.peekQbsmlTypeface()) }
    var headerMapsReady by remember(fontManager) { mutableStateOf(false) }
    LaunchedEffect(fontManager, informationOnly) {
        if (informationOnly && fontManager != null) {
            fontManager.ensureMaps()
            headerFace = fontManager.loadQbsmlTypeface()
            headerMapsReady = true
        }
    }
    val surahSlice = if (headerMapsReady && fontManager != null) {
        surahNumbers.firstOrNull()?.let { number ->
            QbsmlHeaderSlices.forSurah(number, fontManager.headerSingleSurahLigature(number),
                fontManager::headerSurahLigature)
        }
    } else null
    val juzSlice = if (headerMapsReady) fontManager?.juzHeaderLigature(juzNumber)
        ?.takeIf { it.isNotEmpty() }?.let { HeaderGlyphSlice(it) } else null

    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = ChromeTokens.HorizontalInset)
                .padding(top = ChromeTokens.HeaderTopGap, bottom = ChromeTokens.HeaderBottomGap),
        ) {
            val shape = HorizontalIslamicPlaque
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChromeTokens.HeaderHeight),
            ) {
                SurahFrameBackground(Modifier.fillMaxSize())

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = if (informationOnly) 4.dp else 8.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = if (informationOnly) Arrangement.SpaceEvenly else Arrangement.spacedBy(6.dp),
                ) {
                    if (informationOnly) {
                        val face = headerFace
                        if (surahSlice != null && face != null) {
                            QbsmlHeaderLabel(surahSlice, face, surahFallback,
                                Modifier.weight(1f).testTag("mushaf-header-surah"))
                        } else HeaderInfoText(surahFallback, Modifier.weight(1f))
                        HeaderInfoText(hizbLabel, Modifier.weight(1.6f), fontScale = 0.875f)
                        if (page?.sajda != null) {
                            HeaderInfoText(stringResource(R.string.mushaf_sajdah), Modifier.weight(0.65f))
                        }
                        if (juzSlice != null && face != null) {
                            QbsmlHeaderLabel(juzSlice, face, juzFallback,
                                Modifier.weight(1f).testTag("mushaf-header-juz"))
                        } else HeaderInfoText(juzFallback, Modifier.weight(1f))
                    } else {
                        ReadingModeToggle(
                            modifier = Modifier.weight(1.25f),
                            meaningsSelected = meaningsSelected,
                            night = night,
                            onSelectMeanings = onSelectMeanings,
                        )
                        SearchChip(
                            modifier = Modifier.weight(1.25f),
                            night = night,
                            onClick = onSearch,
                        )
                        HeaderIconButton(
                            modifier = Modifier.weight(1f),
                            night = night,
                            contentDescription = stringResource(R.string.bookmarks_save_current),
                            onClick = onOpenBookmarks,
                        ) {
                            Icon(painterResource(R.drawable.ic_bookmark), null,
                                tint = paper.textStrong, modifier = Modifier.size(18.dp))
                        }
                        HeaderIconButton(
                            modifier = Modifier.weight(1f),
                            night = night,
                            contentDescription = stringResource(R.string.tajweed_colors),
                            onClick = onToggleTajweed,
                        ) {
                            Icon(imageVector = PaletteIcon, contentDescription = null, tint = paper.textStrong,
                                modifier = Modifier.size(18.dp).semantics { this.selected = tajweedEnabled })
                        }
                        HeaderIconButton(
                            modifier = Modifier.weight(1f),
                            night = night,
                            contentDescription = stringResource(R.string.tajweed_guide),
                            onClick = onTajweedGuide,
                        ) {
                            Icon(painterResource(R.drawable.ic_info_outline), null,
                                tint = paper.textStrong,
                                modifier = Modifier.size(18.dp).testTag("mushaf-header-info-icon"))
                        }
                        HeaderIconButton(
                            modifier = Modifier.weight(1f),
                            night = night,
                            contentDescription = stringResource(if (night) R.string.theme_to_light else R.string.theme_to_dark),
                            onClick = onThemeToggle,
                        ) {
                            ThemeToggle(night = night, onToggle = onThemeToggle,
                                modifier = Modifier.clearAndSetSemantics {}, width = 18.dp, height = 18.dp)
                        }
                    }

                }
            }
        }
    }

}

@Composable
private fun HeaderInfoText(text: String, modifier: Modifier, fontScale: Float = 1f) {
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val family = com.quransunah.app.ui.theme.LocalDisplayFontFamily.current
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val widthPx = constraints.maxWidth
        val fontSize = remember(text, widthPx, family, measurer, fontScale) {
            var size = 13f
            while (size > 6f && measurer.measure(
                androidx.compose.ui.text.AnnotatedString(text),
                style = TextStyle(fontSize = size.sp, fontFamily = family),
                maxLines = 1, softWrap = false,
            ).size.width > widthPx) size -= 0.25f
            (size * fontScale).sp
        }
        Text(text, color = LocalPaperColors.current.textStrong, fontSize = fontSize,
            fontFamily = family, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun ReadingModeToggle(
    meaningsSelected: Boolean,
    night: Boolean,
    onSelectMeanings: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(8.dp)
    val openMeanings = !meaningsSelected
    val label = stringResource(
        if (openMeanings) R.string.reading_mode_meanings else R.string.reading_mode_mushaf,
    )
    val iconTint = paper.textStrong
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.height(24.dp)
        .clip(shape)
        .background(headerChipFill(night))
        .border(1.dp, headerChipStroke(night), shape)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null,
            onClick = { onSelectMeanings(openMeanings) })
        .semantics { contentDescription = label },
    ) {
        val labelWidth = (constraints.maxWidth - with(density) { 26.dp.roundToPx() }).coerceAtLeast(1)
        val labelSize = remember(label, labelWidth, measurer) {
            var size = 11f
            while (size > 6f && measurer.measure(
                androidx.compose.ui.text.AnnotatedString(label), style = TextStyle(fontSize = size.sp),
                maxLines = 1, softWrap = false,
            ).size.width > labelWidth) size -= 0.25f
            size.sp
        }
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        ) {
            if (openMeanings) {
                Icon(
                    imageVector = MaterialTranslateIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_nav_quran),
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                color = iconTint,
                fontSize = labelSize,
                lineHeight = (labelSize.value + 2f).sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            )
        }
    }
}

/** Monochrome palette silhouette; its tint always follows the selected UI theme. */
private val PaletteIcon = ImageVector.Builder("Palette", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(pathData = PathParser().parsePathString(
        "M12,2C6.48,2 2,6.48 2,12s4.48,10 10,10h1.77c1.66,0 3,-1.34 3,-3 0,-0.73 -0.28,-1.41 -0.73,-1.94 -0.08,-0.1 -0.13,-0.22 -0.13,-0.36 0,-0.28 0.22,-0.5 0.5,-0.5H18c3.31,0 6,-2.69 6,-6C24,5.67 18.63,2 12,2zM6.5,13C5.67,13 5,12.33 5,11.5S5.67,10 6.5,10 8,10.67 8,11.5 7.33,13 6.5,13zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5 11,5.67 11,6.5 10.33,8 9.5,8zM14.5,8C13.67,8 13,7.33 13,6.5S13.67,5 14.5,5 16,5.67 16,6.5 15.33,8 14.5,8zM18.5,12C17.67,12 17,11.33 17,10.5S17.67,9 18.5,9 20,9.67 20,10.5 19.33,12 18.5,12z"
    ).toNodes(), fill = SolidColor(Color.Black), pathFillType = androidx.compose.ui.graphics.PathFillType.EvenOdd)
}.build()

/** Material Icons "Translate" (A / あ). Vector only; not a bitmap asset. */
private val MaterialTranslateIcon: ImageVector = ImageVector.Builder(
    name = "Translate",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    addPath(
        pathData = PathParser().parsePathString(
            "M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z",
        ).toNodes(),
        fill = SolidColor(Color.Black),
    )
}.build()

@Composable
private fun SearchChip(
    night: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val label = stringResource(R.string.header_search_mushaf)
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .height(24.dp)
            .clip(shape)
            .background(headerChipFill(night))
            .border(1.dp, headerChipStroke(night), shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = paper.textStrong,
            modifier = Modifier.size(18.dp),
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

@Composable
private fun headerChipFill(night: Boolean): Color {
    val paper = LocalPaperColors.current
    return paper.accent.copy(alpha = if (night) 0.16f else 0.08f)
}

@Composable
private fun headerChipStroke(night: Boolean): Color {
    val paper = LocalPaperColors.current
    return paper.accent.copy(alpha = if (night) 0.40f else 0.24f)
}

@Composable
private fun HeaderIconButton(
    night: Boolean,
    contentDescription: String?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(24.dp)
            .clip(shape)
            .background(headerChipFill(night))
            .border(1.dp, headerChipStroke(night), shape)
            .then(
                if (onClick != null && contentDescription != null) {
                    Modifier
                        .semantics { this.contentDescription = contentDescription }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
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
            onOpenBookmarks = {},
        )
    }
}
