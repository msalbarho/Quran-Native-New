package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import kotlin.math.abs
import kotlin.math.roundToInt
import com.quransunah.app.core.ArabicOrdinals
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.QuarterMarker
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.ThemeTokens
import kotlinx.coroutines.delay
import kotlin.math.max

/**
 * Text-mushaf (المصحف النصي): same printed-page word range as Medina mode,
 * reflowed as a centered continuous paragraph at the user Hafs size.
 */
@Composable
fun TextMushafPage(
    pageNumber: Int,
    lines: List<LineRecord>,
    fontManager: QcfFontManager?,
    fontSizeSp: Float,
    modifier: Modifier = Modifier,
    glyphColor: Color = Color(0xFF2B2420),
    highlightColor: Color = Color(0x246D5843),
    highlightWordId: Int? = null,
    highlightAyah: Pair<Int, Int>? = null,
    hideAyahText: Boolean = false,
    revealedAyahs: Set<Pair<Int, Int>> = emptySet(),
    chromeVisible: Boolean = true,
    surahsByNumber: Map<Int, SurahInfo> = emptyMap(),
    quarter: QuarterMarker? = null,
    sajda: SajdaMarker? = null,
    onWordTap: (WordRecord) -> Unit = {},
    onWordLongPress: (WordRecord) -> Unit = {},
    onEmptyTap: () -> Unit = {},
    onSurahNameLongPress: () -> Unit = {},
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val inspection = LocalInspectionMode.current
    var pageFace by remember(pageNumber, fontManager) {
        mutableStateOf(if (inspection) Typeface.DEFAULT else fontManager?.peekPageTypeface(pageNumber))
    }
    var titleFace by remember(fontManager) {
        mutableStateOf(fontManager?.peekSurahTitleTypeface())
    }
    var uthmanicFace by remember(fontManager) {
        mutableStateOf(fontManager?.peekUthmanicTypeface())
    }
    LaunchedEffect(pageNumber, fontManager, inspection) {
        if (inspection || fontManager == null) return@LaunchedEffect
        pageFace = fontManager.loadPageTypeface(pageNumber)
        if (titleFace == null) titleFace = fontManager.loadSurahTitleTypeface()
        if (uthmanicFace == null) uthmanicFace = fontManager.loadUthmanicTypeface()
        fontManager.preloadNeighbors(pageNumber)
    }

    val displayLines = remember(lines) { lines.filter { it.lineType != LineType.EMPTY } }
    val quarterLineNum = remember(displayLines, quarter) {
        findQuarterMarkerLineNumber(displayLines, quarter)
    }
    val sajdaLineNum = remember(displayLines, sajda) {
        findSajdaMarkerLineNumber(displayLines, sajda)
    }
    val quarterLines = remember(quarter) {
        ArabicOrdinals.quarterBadgeLines(quarter?.label.orEmpty())
    }
    var layoutSizeSp by remember { mutableFloatStateOf(fontSizeSp) }
    LaunchedEffect(fontSizeSp) {
        delay(48)
        layoutSizeSp = fontSizeSp
    }
    val fontSize = layoutSizeSp.sp
    val previewScale = if (layoutSizeSp > 0.01f) fontSizeSp / layoutSizeSp else 1f
    val wordGap = 4.dp
    val rowGap = 2.dp
    val markerColor = ThemeTokens.AyahMarker
    val bodyColor = if (night) Color.White else Color.Black
    val segments = remember(displayLines, quarterLineNum, sajdaLineNum) {
        buildTextMushafSegments(displayLines, quarterLineNum, sajdaLineNum)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(paper.pageBackground),
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = previewScale
                    scaleY = previewScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    clip = false
                }
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onEmptyTap,
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is TextMushafSegment.Surah -> {
                        val surah = segment.line.surahNumber ?: 1
                        val surahFrameModifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp, bottom = 4.dp)
                            .aspectRatio(SURAH_FRAME_ASPECT)
                            .heightIn(min = 38.dp)
                        SurahNameFrame(
                            surahNumber = surah,
                            surah = surahsByNumber[surah],
                            fontManager = fontManager,
                            titleTypeface = titleFace,
                            onTap = onEmptyTap,
                            onLongPress = onSurahNameLongPress,
                            modifier = surahFrameModifier,
                        )
                        // Pages 1–2: two blank lines under the frame.
                        if (pageNumber == 1 || pageNumber == 2) {
                            Spacer(modifier = Modifier.height((layoutSizeSp * 1.35f * 2).dp))
                        }
                    }
                    is TextMushafSegment.Basmala -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            QcfGlyphText(
                                ligature = fontManager?.displayBasmalahLigature().orEmpty(),
                                fallback = fontManager?.displayBasmalahLigature().orEmpty(),
                                fontManager = fontManager,
                                titleTypeface = titleFace,
                                color = ChromeTokens.BasmalaGold,
                                ligatureSize = fontSize,
                                fallbackSize = fontSize,
                                preferLigature = true,
                            )
                        }
                    }
                    is TextMushafSegment.Paragraph -> {
                        TextMushafParagraph(
                            words = segment.words,
                            hideAyahText = hideAyahText,
                            revealedAyahs = revealedAyahs,
                            pageNumber = pageNumber,
                            fontManager = fontManager,
                            pageFace = pageFace,
                            uthmanicFace = uthmanicFace,
                            fontSize = fontSize,
                            wordGap = wordGap,
                            rowGap = rowGap,
                            markerColor = markerColor,
                            bodyColor = bodyColor,
                            highlightColor = highlightColor,
                            accent = paper.accent,
                            highlightWordId = highlightWordId,
                            highlightAyah = highlightAyah,
                            hizbAnchorId = segment.hizbAnchorId,
                            sajdaAnchorId = segment.sajdaAnchorId,
                            quarterLines = quarterLines,
                            chromeVisible = chromeVisible,
                            onBadgeTap = onEmptyTap,
                            onTap = onWordTap,
                            onLongPress = onWordLongPress,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextMushafParagraph(
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    words: List<WordRecord>,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    pageFace: Typeface?,
    uthmanicFace: Typeface?,
    fontSize: TextUnit,
    wordGap: Dp,
    rowGap: Dp,
    markerColor: Color,
    bodyColor: Color,
    highlightColor: Color,
    accent: Color,
    highlightWordId: Int?,
    highlightAyah: Pair<Int, Int>?,
    hizbAnchorId: Int?,
    sajdaAnchorId: Int?,
    quarterLines: List<String>,
    chromeVisible: Boolean,
    onBadgeTap: () -> Unit,
    onTap: (WordRecord) -> Unit,
    onLongPress: (WordRecord) -> Unit,
) {
    val density = LocalDensity.current
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var hizbCenterY by remember { mutableStateOf<Float?>(null) }
    var sajdaCenterY by remember { mutableStateOf<Float?>(null) }

    fun reportAnchor(hizb: Boolean, sajda: Boolean, coords: LayoutCoordinates) {
        val box = container ?: return
        if (!coords.isAttached || !box.isAttached) return
        val topLeft = box.localPositionOf(coords, Offset.Zero)
        val centerY = topLeft.y + coords.size.height / 2f
        if (hizb && (hizbCenterY == null || abs((hizbCenterY ?: 0f) - centerY) > 0.5f)) {
            hizbCenterY = centerY
        }
        if (sajda && (sajdaCenterY == null || abs((sajdaCenterY ?: 0f) - centerY) > 0.5f)) {
            sajdaCenterY = centerY
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { container = it },
    ) {
        CenteredWordFlow(
            modifier = Modifier.fillMaxWidth(),
            horizontalGap = wordGap,
            verticalGap = rowGap,
        ) {
            TextMushafWordGroups(
                hideAyahText = hideAyahText,
                revealedAyahs = revealedAyahs,
                words = words,
                pageNumber = pageNumber,
                fontManager = fontManager,
                pageFace = pageFace,
                uthmanicFace = uthmanicFace,
                fontSize = fontSize,
                wordGap = wordGap,
                markerColor = markerColor,
                bodyColor = bodyColor,
                highlightColor = highlightColor,
                accent = accent,
                highlightWordId = highlightWordId,
                highlightAyah = highlightAyah,
                hizbAnchorId = hizbAnchorId,
                sajdaAnchorId = sajdaAnchorId,
                onAnchorPositioned = ::reportAnchor,
                onTap = onTap,
                onLongPress = onLongPress,
            )
        }
        Box(Modifier.matchParentSize()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.matchParentSize()) {
                    val hizbY = hizbCenterY
                val sajdaY = sajdaCenterY
                val pairCenterY =
                    if (hizbY != null &&
                        sajdaY != null &&
                        quarterLines.isNotEmpty() &&
                        abs(hizbY - sajdaY) < with(density) { 18.dp.toPx() }
                    ) {
                        (hizbY + sajdaY) / 2f
                    } else {
                        null
                    }
                if (pairCenterY != null) {
                    MushafPairedSideBadges(
                        quarterLines = quarterLines,
                        visible = chromeVisible,
                        onTap = onBadgeTap,
                        modifier = Modifier
                            .align(MushafSideBadgeAlign)
                            .zIndex(3f)
                            .offset {
                                IntOffset(
                                    0,
                                    (pairCenterY - with(density) { HizbBadgeHeight.toPx() / 2f }).roundToInt(),
                                )
                            },
                    )
                } else {
                    if (hizbY != null && quarterLines.isNotEmpty()) {
                        HizbFrameBadge(
                            lines = quarterLines,
                            visible = chromeVisible,
                            onTap = onBadgeTap,
                            modifier = Modifier
                                .align(MushafSideBadgeAlign)
                                .zIndex(3f)
                                .offset {
                                    IntOffset(
                                        0,
                                        (hizbY - with(density) { HizbBadgeHeight.toPx() / 2f }).roundToInt(),
                                    )
                                },
                        )
                    }
                    if (sajdaY != null) {
                        SajdahFrameBadge(
                            visible = chromeVisible,
                            onTap = onBadgeTap,
                            modifier = Modifier
                                .align(MushafSideBadgeAlign)
                                .zIndex(3f)
                                .offset {
                                    IntOffset(
                                        0,
                                        (sajdaY - with(density) { SajdahBadgeHeight.toPx() / 2f }).roundToInt(),
                                    )
                                },
                        )
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun CenteredWordFlow(
    modifier: Modifier = Modifier,
    horizontalGap: Dp,
    verticalGap: Dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val gapX = horizontalGap.roundToPx()
        val gapY = verticalGap.roundToPx()
        val maxWidth = constraints.maxWidth
        val placeables = measurables.map { measurable ->
            measurable.measure(
                constraints.copy(minWidth = 0, minHeight = 0, maxWidth = maxWidth),
            )
        }
        data class FlowRow(
            val items: List<Placeable>,
            val width: Int,
            val height: Int,
        )
        val rows = ArrayList<FlowRow>()
        val current = ArrayList<Placeable>()
        var rowWidth = 0
        var rowHeight = 0
        fun flush() {
            if (current.isEmpty()) return
            rows += FlowRow(current.toList(), rowWidth, rowHeight)
            current.clear()
            rowWidth = 0
            rowHeight = 0
        }
        for (placeable in placeables) {
            val extra = if (current.isEmpty()) 0 else gapX
            if (current.isNotEmpty() && rowWidth + extra + placeable.width > maxWidth) {
                flush()
            }
            current += placeable
            rowWidth += extra + placeable.width
            rowHeight = max(rowHeight, placeable.height)
        }
        flush()
        val totalHeight = if (rows.isEmpty()) {
            0
        } else {
            rows.sumOf { it.height } + gapY * (rows.lastIndex)
        }
        layout(maxWidth, totalHeight.coerceAtLeast(0)) {
            var y = 0
            val rtl = layoutDirection == LayoutDirection.Rtl
            for (row in rows) {
                val startX = ((maxWidth - row.width) / 2).coerceAtLeast(0)
                if (rtl) {
                    var x = startX + row.width
                    for (placeable in row.items) {
                        x -= placeable.width
                        placeable.place(x, y)
                        x -= gapX
                    }
                } else {
                    var x = startX
                    for (placeable in row.items) {
                        placeable.place(x, y)
                        x += placeable.width + gapX
                    }
                }
                y += row.height + gapY
            }
        }
    }
}

@Composable
private fun TextMushafWordGroups(
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    words: List<WordRecord>,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    pageFace: Typeface?,
    uthmanicFace: Typeface?,
    fontSize: TextUnit,
    wordGap: Dp,
    markerColor: Color,
    bodyColor: Color,
    highlightColor: Color,
    accent: Color,
    highlightWordId: Int?,
    highlightAyah: Pair<Int, Int>?,
    hizbAnchorId: Int?,
    sajdaAnchorId: Int?,
    onAnchorPositioned: (hizb: Boolean, sajda: Boolean, coords: LayoutCoordinates) -> Unit,
    onTap: (WordRecord) -> Unit,
    onLongPress: (WordRecord) -> Unit,
) {
    val groups = gluedWordGroups(words)
    groups.forEachIndexed { groupIndex, group ->
        val previousBefore = groups.getOrNull(groupIndex - 1)?.lastOrNull()
        val showHizb = hizbAnchorId != null && group.any { it.id == hizbAnchorId }
        val showSajda = sajdaAnchorId != null && group.any { it.id == sajdaAnchorId }
        Row(
            modifier = Modifier
                .wrapContentWidth()
                .then(
                    if (showHizb || showSajda) {
                        Modifier.onGloballyPositioned { coords ->
                            onAnchorPositioned(showHizb, showSajda, coords)
                        }
                    } else {
                        Modifier
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(wordGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            group.forEachIndexed { index, word ->
                    TextMushafWord(
                        hideAyahText = hideAyahText,
                        revealedAyahs = revealedAyahs,
                        word = word,
                    previous = group.getOrNull(index - 1) ?: previousBefore,
                    next = group.getOrNull(index + 1)
                        ?: groups.getOrNull(groupIndex + 1)?.firstOrNull(),
                    pageNumber = pageNumber,
                    fontManager = fontManager,
                    pageFace = pageFace,
                    uthmanicFace = uthmanicFace,
                    fontSize = fontSize,
                    glyphColor = if (word.isAyahMarker) markerColor else bodyColor,
                    highlightColor = highlightColor,
                    accent = accent,
                    selected = highlightWordId == word.id,
                    ayahHighlighted = highlightAyah != null &&
                        word.surah == highlightAyah.first &&
                        word.ayah == highlightAyah.second,
                    onTap = onTap,
                    onLongPress = onLongPress,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TextMushafWord(
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    word: WordRecord,
    previous: WordRecord?,
    next: WordRecord?,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    pageFace: Typeface?,
    uthmanicFace: Typeface?,
    fontSize: TextUnit,
    glyphColor: Color,
    highlightColor: Color,
    accent: Color,
    selected: Boolean,
    ayahHighlighted: Boolean,
    onTap: (WordRecord) -> Unit,
    onLongPress: (WordRecord) -> Unit,
) {
    val qcf = remember(word.qcfLigature, word.uthmanic, pageNumber, fontManager) {
        fontManager?.displayText(word, pageNumber).orEmpty().ifBlank { word.displayGlyph }
    }
    val overlay = remember(word.id, next?.id) { SajdahAyah.overlayLigature(word, next) }
    val number = remember(word.id, previous?.id) { SajdahAyah.numberLigature(word, previous) }
    val display = buildString {
        if (!overlay.isNullOrEmpty()) append(overlay)
        append(qcf.ifBlank { word.uthmanic })
    }
    val usePageFace = remember(pageFace, display, number, fontManager) {
        pageFace != null && display.isNotBlank() &&
            (fontManager == null || fontManager.canDrawLigature(display + number.orEmpty(), pageFace))
    }
    val pageFamily = remember(pageFace) { pageFace?.let { FontFamily(it) } }
    val uthmanicFamily = remember(uthmanicFace) { uthmanicFace?.let { FontFamily(it) } }
    val family = when {
        usePageFace && pageFamily != null -> pageFamily
        uthmanicFamily != null -> uthmanicFamily
        else -> FontFamily.Serif
    }
    val shown = if (usePageFace) display else word.uthmanic.ifBlank { display }
    val rubParts = remember(word.uthmanic, shown) { word.rubParts(shown) }
    val numberShown = when {
        number.isNullOrEmpty() -> null
        usePageFace -> number
        else -> null
    }
    val size = if (word.isAyahMarker) fontSize * 0.92f else fontSize
    val shape = RoundedCornerShape(6.dp)
    val paintColor = when {
        hideAyahText && !word.isAyahMarker && (word.surah to word.ayah) !in revealedAyahs -> Color.Transparent
        ayahHighlighted -> ChromeTokens.PlaybackAyahBlue
        word.isAyahMarker -> glyphColor
        else -> glyphColor
    }
    val wordModifier = Modifier
        .then(
            if (selected && !ayahHighlighted) {
                Modifier
                    .clip(shape)
                    .background(highlightColor)
                    .border(1.5.dp, accent.copy(alpha = 0.45f), shape)
            } else {
                Modifier
            },
        )
        .then(
            if (word.isAyahMarker) {
                Modifier
            } else {
                Modifier
                    .combinedClickable(
                        role = Role.Button,
                        onClick = { onTap(word) },
                        onLongClick = { onLongPress(word) },
                    )
                    .padding(horizontal = 2.dp, vertical = 1.dp)
            },
        )

    if (word.isAyahMarker) {
        Box(modifier = wordModifier, contentAlignment = Alignment.Center) {
            AyahMarkerGlyphContent(
                rubParts = rubParts,
                shown = shown,
                paintColor = paintColor,
                size = size,
                fontSize = fontSize,
                family = family,
                decoration = TextDecoration.None,
            )
        }
        return
    }

    Box(
        modifier = wordModifier,
        contentAlignment = Alignment.Center,
    ) {
        val hasMeaning = !word.meaning.isNullOrBlank() && !word.isAyahMarker
        val decoration = if (hasMeaning) TextDecoration.Underline else TextDecoration.None
        val glyph: @Composable () -> Unit = {
            if (rubParts == null) {
                Text(
                    text = shown,
                    color = paintColor,
                    fontSize = size,
                    lineHeight = fontSize * 1.75f,
                    fontFamily = family,
                    textAlign = TextAlign.Center,
                    textDecoration = decoration,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rubParts.markerLigature,
                        color = paintColor,
                        fontSize = size,
                        lineHeight = fontSize * 1.75f,
                        fontFamily = family,
                        textDecoration = decoration,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(2.dp))
                    if (rubParts.bodyLigature.isNotEmpty()) {
                        Text(
                            text = rubParts.bodyLigature,
                            color = paintColor,
                            fontSize = size,
                            lineHeight = fontSize * 1.75f,
                            fontFamily = family,
                            textDecoration = decoration,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
        if (numberShown.isNullOrEmpty()) {
            glyph()
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                glyph()
                Text(
                    text = numberShown,
                    color = paintColor,
                    fontSize = size,
                    lineHeight = fontSize * 1.75f,
                    fontFamily = family,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@Composable
private fun AyahMarkerGlyphContent(
    rubParts: RubElHizb.Parts?,
    shown: String,
    paintColor: Color,
    size: TextUnit,
    fontSize: TextUnit,
    family: FontFamily,
    decoration: TextDecoration,
) {
    if (rubParts == null) {
        Text(
            text = shown,
            color = paintColor,
            fontSize = size,
            lineHeight = fontSize * 1.75f,
            fontFamily = family,
            textAlign = TextAlign.Center,
            textDecoration = decoration,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rubParts.markerLigature,
                color = paintColor,
                fontSize = size,
                lineHeight = fontSize * 1.75f,
                fontFamily = family,
                textDecoration = decoration,
                maxLines = 1,
                softWrap = false,
            )
            Spacer(Modifier.width(2.dp))
            if (rubParts.bodyLigature.isNotEmpty()) {
                Text(
                    text = rubParts.bodyLigature,
                    color = paintColor,
                    fontSize = size,
                    lineHeight = fontSize * 1.75f,
                    fontFamily = family,
                    textDecoration = decoration,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private sealed class TextMushafSegment {
    data class Surah(val line: LineRecord) : TextMushafSegment()
    data class Basmala(val line: LineRecord) : TextMushafSegment()
    data class Paragraph(
        val words: List<WordRecord>,
        val hizbAnchorId: Int? = null,
        val sajdaAnchorId: Int? = null,
    ) : TextMushafSegment()
}

private fun buildTextMushafSegments(
    lines: List<LineRecord>,
    quarterLineNum: Int?,
    sajdaLineNum: Int?,
): List<TextMushafSegment> {
    val segments = ArrayList<TextMushafSegment>()
    val pending = ArrayList<WordRecord>()
    var hizbAnchorId: Int? = null
    var sajdaAnchorId: Int? = null
    fun flushParagraph() {
        if (pending.isEmpty()) return
        segments += TextMushafSegment.Paragraph(
            words = pending.toList(),
            hizbAnchorId = hizbAnchorId,
            sajdaAnchorId = sajdaAnchorId,
        )
        pending.clear()
        hizbAnchorId = null
        sajdaAnchorId = null
    }
    for (line in lines) {
        when (line.lineType) {
            LineType.SURAH_NAME -> {
                flushParagraph()
                segments += TextMushafSegment.Surah(line)
            }
            LineType.BASMALLAH -> {
                flushParagraph()
                segments += TextMushafSegment.Basmala(line)
            }
            LineType.AYAH -> {
                if (line.lineNum == quarterLineNum && hizbAnchorId == null) {
                    hizbAnchorId = line.words.firstOrNull()?.id
                }
                if (line.lineNum == sajdaLineNum && sajdaAnchorId == null) {
                    sajdaAnchorId = line.words.firstOrNull()?.id
                }
                pending += line.words
            }
            LineType.EMPTY -> Unit
        }
    }
    flushParagraph()
    return segments
}

private fun gluedWordGroups(words: List<WordRecord>): List<List<WordRecord>> {
    val groups = ArrayList<List<WordRecord>>(words.size)
    var index = 0
    while (index < words.size) {
        val word = words[index]
        val next = words.getOrNull(index + 1)
        if (next != null && next.isAyahMarker) {
            groups += listOf(word, next)
            index += 2
        } else {
            groups += listOf(word)
            index += 1
        }
    }
    return groups
}

@ArabicPreviews
@Composable
private fun TextMushafPagePreview() {
    PreviewTheme {
        TextMushafPage(
            pageNumber = 1,
            lines = PreviewFixtures.canvasLines,
            fontManager = null,
            fontSizeSp = 25f,
        )
    }
}
