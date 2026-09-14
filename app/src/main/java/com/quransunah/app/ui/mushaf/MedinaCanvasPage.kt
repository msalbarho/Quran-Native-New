package com.quransunah.app.ui.mushaf

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import com.quransunah.app.core.AppConstants
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
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Height ÷ width for Medina landscape fit-width pages.
 * Keeps the 15-line leaf portrait-proportioned so glyphs scale with screen
 * width instead of being crushed into the short landscape viewport.
 */
private const val MEDINA_LANDSCAPE_HEIGHT_OVER_WIDTH = 1.62f

/**
 * Medina 15-line page renderer. Glyphs are drawn with the QCF4 page face (tajweed
 * COLR) via the Android [Paint]/[android.graphics.Canvas] path so private-use
 * ligatures never fall back to a generic Arabic family.
 *
 * Word bounding boxes are recorded from a precomputed layout and used for short-tap
 * (seek while audio is playing, otherwise meaning gloss) and long-press
 * (~450 ms, cancelled after 10 px of movement) hit testing.
 *
 * Landscape only: fit-width + vertical scroll (portrait layout is unchanged).
 * Landscape is detected from layout bounds (`maxWidth > maxHeight`), not from
 * Configuration.orientation, which can stay stale when the activity handles
 * configChanges behind a locale ContextWrapper.
 */
@Composable
fun MedinaCanvasPage(
    pageNumber: Int,
    lines: List<LineRecord>,
    fontManager: QcfFontManager?,
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
    onWordLongPress: (WordRecord) -> Unit,
    onEmptyTap: () -> Unit = {},
    onSurahNameLongPress: () -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            val scrollState = rememberScrollState()
            LaunchedEffect(pageNumber) {
                scrollState.scrollTo(0)
            }
            val pageHeight = maxWidth * MEDINA_LANDSCAPE_HEIGHT_OVER_WIDTH
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                MedinaCanvasPageBody(
                    pageNumber = pageNumber,
                    lines = lines,
                    fontManager = fontManager,
                    glyphColor = glyphColor,
                    highlightColor = highlightColor,
                    highlightWordId = highlightWordId,
                    highlightAyah = highlightAyah,
                    hideAyahText = hideAyahText,
                    chromeVisible = chromeVisible,
                    surahsByNumber = surahsByNumber,
                    quarter = quarter,
                    sajda = sajda,
                    onWordTap = onWordTap,
                    onWordLongPress = onWordLongPress,
                    onEmptyTap = onEmptyTap,
                    onSurahNameLongPress = onSurahNameLongPress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(pageHeight)
                        .clipToBounds(),
                )
            }
        } else {
            MedinaCanvasPageBody(
                pageNumber = pageNumber,
                lines = lines,
                fontManager = fontManager,
                glyphColor = glyphColor,
                highlightColor = highlightColor,
                highlightWordId = highlightWordId,
                highlightAyah = highlightAyah,
                hideAyahText = hideAyahText,
                chromeVisible = chromeVisible,
                surahsByNumber = surahsByNumber,
                quarter = quarter,
                sajda = sajda,
                onWordTap = onWordTap,
                onWordLongPress = onWordLongPress,
                onEmptyTap = onEmptyTap,
                onSurahNameLongPress = onSurahNameLongPress,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MedinaCanvasPageBody(
    pageNumber: Int,
    lines: List<LineRecord>,
    fontManager: QcfFontManager?,
    glyphColor: Color,
    highlightColor: Color,
    highlightWordId: Int?,
    highlightAyah: Pair<Int, Int>?,
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    chromeVisible: Boolean,
    surahsByNumber: Map<Int, SurahInfo>,
    quarter: QuarterMarker?,
    sajda: SajdaMarker?,
    onWordTap: (WordRecord) -> Unit,
    onWordLongPress: (WordRecord) -> Unit,
    onEmptyTap: () -> Unit,
    onSurahNameLongPress: () -> Unit,
    modifier: Modifier,
) {
    val inspection = LocalInspectionMode.current
    var pageTypeface by remember(pageNumber, inspection) {
        mutableStateOf(
            if (inspection) Typeface.DEFAULT else fontManager?.peekPageTypeface(pageNumber),
        )
    }
    var uthmanicTypeface by remember { mutableStateOf(fontManager?.peekUthmanicTypeface()) }
    var titleTypeface by remember { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var plan by remember { mutableStateOf<MedinaPagePlan?>(null) }

    LaunchedEffect(pageNumber, inspection) {
        if (inspection || fontManager == null) {
            pageTypeface = Typeface.DEFAULT
            uthmanicTypeface = Typeface.DEFAULT
            titleTypeface = Typeface.DEFAULT
            return@LaunchedEffect
        }
        pageTypeface = fontManager.loadPageTypeface(pageNumber)
        if (titleTypeface == null) {
            titleTypeface = fontManager.loadSurahTitleTypeface()
        }
        if (uthmanicTypeface == null) {
            uthmanicTypeface = fontManager.peekUthmanicTypeface()
        }
        fontManager.preloadNeighbors(pageNumber)
    }

    val firstWordId = lines.firstNotNullOfOrNull { line -> line.words.firstOrNull()?.id } ?: 0
    val lastWordId = lines.asReversed().firstNotNullOfOrNull { line -> line.words.lastOrNull()?.id } ?: 0
    val lineCount = lines.size
    val meaningWordIds = remember(lines) {
        lines.asSequence()
            .flatMap { it.words.asSequence() }
            .filter { !it.isAyahMarker && !it.meaning.isNullOrBlank() }
            .map { it.id }
            .toHashSet()
    }

    val linesFingerprint = remember(lines) {
        lines.fold(0) { acc, line ->
            acc xor (line.lineNum * 31 + line.words.size)
        }
    }
    LaunchedEffect(
        pageNumber,
        canvasSize,
        pageTypeface,
        titleTypeface,
        uthmanicTypeface,
        firstWordId,
        lastWordId,
        lineCount,
        linesFingerprint,
        inspection,
    ) {
        val typeface = pageTypeface
        if (canvasSize.width <= 0 || canvasSize.height <= 0 || typeface == null || lines.isEmpty()) {
            plan = null
            return@LaunchedEffect
        }
        val key = MedinaPageLayoutStore.key(
            pageNumber = pageNumber,
            width = canvasSize.width,
            height = canvasSize.height,
            faceNumber = fontManager?.faceNumberForPage(pageNumber) ?: pageNumber,
            titleReady = titleTypeface != null,
            firstWordId = firstWordId,
            lastWordId = lastWordId,
            lineFingerprint = lineCount,
            extras = "sajdah-number-glyph",
        )
        MedinaPageLayoutStore.get(key)?.let {
            plan = it
            return@LaunchedEffect
        }
        val built = withContext(Dispatchers.Default) {
            buildMedinaPagePlan(
                pageNumber = pageNumber,
                lines = lines,
                width = canvasSize.width.toFloat(),
                height = canvasSize.height.toFloat(),
                pageTypeface = typeface,
                titleTypeface = titleTypeface,
                uthmanicTypeface = uthmanicTypeface,
                fontManager = fontManager,
                inspection = inspection,
            )
        }
        MedinaPageLayoutStore.put(key, built)
        plan = built
    }

    val layoutsState = rememberUpdatedState(plan?.wordLayouts.orEmpty())
    val density = LocalDensity.current
    val longPressMs = AppConstants.WORD_LONG_PRESS_MS
    val moveSlopPx = with(density) { AppConstants.WORD_LONG_PRESS_MOVE_SLOP_PX }
    val onWordTapState = rememberUpdatedState(onWordTap)
    val onWordLongPressState = rememberUpdatedState(onWordLongPress)
    val onEmptyTapState = rememberUpdatedState(onEmptyTap)

    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val context = LocalContext.current
    val markerBitmap = remember(context, night) {
        BitmapFactory.decodeResource(
            context.resources,
            ayahMarkerBackdropDrawable(night),
        )
    }
    val goldArgb = ChromeTokens.BasmalaGold.toArgb()
    val goldFilter = remember(goldArgb) {
        android.graphics.PorterDuffColorFilter(goldArgb, android.graphics.PorterDuff.Mode.SRC_IN)
    }
    val pagePaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            textAlign = Paint.Align.LEFT
            isLinearText = true
        }
    }

    val quarterLineIndex = remember(lines, quarter) {
        lineSlotIndex(lines, findQuarterMarkerLineNumber(lines, quarter))
    }
    val sajdaLineIndex = remember(lines, sajda) {
        lineSlotIndex(lines, findSajdaMarkerLineNumber(lines, sajda))
    }
    val quarterLines = remember(quarter) {
        ArabicOrdinals.quarterBadgeLines(quarter?.label.orEmpty())
    }
    val slotCount = AppConstants.LINES_PER_PAGE
    val slotHeightPx = if (canvasSize.height > 0) canvasSize.height / slotCount.toFloat() else 0f

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it }
                .pointerInput(pageNumber, lines, longPressMs, moveSlopPx) {
                    detectMedinaWordGestures(
                        longPressTimeoutMs = longPressMs,
                        moveSlopPx = moveSlopPx,
                        onTap = { offset ->
                            val hit = hitTest(layoutsState.value, offset)
                            if (hit != null) {
                                onWordTapState.value(hit.word)
                            } else {
                                onEmptyTapState.value()
                            }
                        },
                        onLongPress = { offset ->
                            val hit = hitTest(layoutsState.value, offset)
                            if (hit != null) {
                                onWordLongPressState.value(hit.word)
                            }
                        },
                    )
                },
        ) {
            val pagePlan = plan ?: return@Canvas
            val glyph = glyphColor.toArgb()
            val gold = goldArgb
            val playbackBlue = ChromeTokens.PlaybackAyahBlue.toArgb()
            val meaningLine = glyphColor.copy(alpha = 0.42f).toArgb()
            pagePaint.color = glyph
            pagePaint.colorFilter = null
            drawIntoCanvas { composeCanvas ->
                val nativeCanvas = composeCanvas.nativeCanvas
                for (slot in pagePlan.slots) {
                    val centered = slot.centered
                    if (centered != null) {
                        if (!centered.gold) continue
                        pagePaint.typeface = when (centered.kind) {
                            MedinaTypefaceKind.Page -> pageTypeface ?: Typeface.DEFAULT
                            MedinaTypefaceKind.Title -> titleTypeface ?: pageTypeface ?: Typeface.DEFAULT
                            MedinaTypefaceKind.Uthmanic ->
                                uthmanicTypeface ?: titleTypeface ?: pageTypeface ?: Typeface.DEFAULT
                        }
                        pagePaint.textSize = centered.textSize
                        pagePaint.color = gold
                        pagePaint.colorFilter = goldFilter
                        nativeCanvas.drawText(centered.text, centered.x, centered.baseline, pagePaint)
                        pagePaint.colorFilter = null
                        pagePaint.color = glyph
                        continue
                    }
                    pagePaint.typeface = pageTypeface ?: Typeface.DEFAULT
                    pagePaint.textSize = pagePlan.pageTextSize
                    pagePaint.color = glyph
                    pagePaint.colorFilter = null
                    for (word in slot.words) {
                        val ayahMatch = highlightAyah != null &&
                            word.word.surah == highlightAyah.first &&
                            word.word.ayah == highlightAyah.second
                        val wordMatch = highlightWordId != null && highlightWordId == word.word.id
                        val highlighted = ayahMatch || wordMatch
                        val marker = word.marker
                        if (word.word.isAyahMarker && word.body.isNotEmpty()) {
                            val badgeRect = ayahMarkerBadgeRect(
                                paint = pagePaint,
                                text = word.body,
                                x = word.x,
                                baseline = word.baseline,
                            )
                            nativeCanvas.drawAyahMarkerBackdrop(markerBitmap, badgeRect)
                        }
                        pagePaint.applyGlyphColor(
                            highlighted = highlighted,
                            glyph = if (hideAyahText && !word.word.isAyahMarker && (word.word.surah to word.word.ayah) !in revealedAyahs) Color.Transparent.toArgb() else glyph,
                            playbackBlue = playbackBlue,
                        )
                        if (word.body.isNotEmpty()) {
                            nativeCanvas.drawText(word.body, word.x, word.baseline, pagePaint)
                        }
                        if (!marker.isNullOrEmpty()) {
                            pagePaint.applyGlyphColor(
                                highlighted = highlighted,
                                glyph = glyph,
                                playbackBlue = playbackBlue,
                            )
                            nativeCanvas.drawText(marker, word.markerX, word.baseline, pagePaint)
                        }
                        pagePaint.colorFilter = null
                        if (!hideAyahText && !word.word.isAyahMarker && word.word.id in meaningWordIds) {
                            pagePaint.style = Paint.Style.STROKE
                            pagePaint.strokeWidth = 1.6f
                            pagePaint.color = if (highlighted) playbackBlue else meaningLine
                            val underlineY = word.boxBottom - (word.boxBottom - word.boxTop) * 0.08f
                            nativeCanvas.drawLine(
                                word.x,
                                underlineY,
                                word.x + word.width,
                                underlineY,
                                pagePaint,
                            )
                            pagePaint.style = Paint.Style.FILL
                            pagePaint.color = glyph
                        }
                    }
                }
            }
            pagePaint.color = glyph
        }

        if (slotHeightPx > 0f) {
            lines.forEachIndexed { index, line ->
                val surahNumber = line.surahNumber
                if (line.lineType == LineType.SURAH_NAME && surahNumber != null && surahNumber > 0) {
                    val top = with(density) { (index * slotHeightPx).toDp() }
                    val frameHeight = with(density) { slotHeightPx.toDp() }
                    SurahNameFrame(
                        surahNumber = surahNumber,
                        surah = surahsByNumber[surahNumber],
                        fontManager = fontManager,
                        titleTypeface = titleTypeface,
                        onTap = onEmptyTap,
                        onLongPress = onSurahNameLongPress,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = top)
                            .fillMaxWidth()
                            .padding(horizontal = ChromeTokens.HorizontalInset)
                            .height(frameHeight),
                    )
                }
            }
            val pairedLineIndex =
                if (quarterLineIndex != null &&
                    sajdaLineIndex != null &&
                    quarterLineIndex == sajdaLineIndex &&
                    quarterLines.isNotEmpty()
                ) {
                    quarterLineIndex
                } else {
                    null
                }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(Modifier.matchParentSize()) {
                    if (pairedLineIndex != null) {
                        val centerY = (pairedLineIndex + 0.5f) * slotHeightPx
                        MushafPairedSideBadges(
                            quarterLines = quarterLines,
                            visible = chromeVisible,
                            onTap = onEmptyTap,
                            modifier = Modifier
                                .align(MushafSideBadgeAlign)
                                .mushafSideBadgeAnchor(
                                    yOffsetPx = (centerY - with(density) { HizbBadgeHeight.toPx() / 2f }).roundToInt(),
                                    density = density,
                                ),
                        )
                    } else {
                        if (quarterLineIndex != null && quarterLines.isNotEmpty()) {
                            val centerY = (quarterLineIndex + 0.5f) * slotHeightPx
                            HizbFrameBadge(
                                lines = quarterLines,
                                visible = chromeVisible,
                                onTap = onEmptyTap,
                                modifier = Modifier
                                    .align(MushafSideBadgeAlign)
                                    .mushafSideBadgeAnchor(
                                        yOffsetPx = (centerY - with(density) { HizbBadgeHeight.toPx() / 2f }).roundToInt(),
                                        density = density,
                                    ),
                            )
                        }
                        if (sajdaLineIndex != null) {
                            val centerY = (sajdaLineIndex + 0.5f) * slotHeightPx
                            SajdahFrameBadge(
                                visible = chromeVisible,
                                onTap = onEmptyTap,
                                modifier = Modifier
                                    .align(MushafSideBadgeAlign)
                                    .mushafSideBadgeAnchor(
                                        yOffsetPx = (centerY - with(density) { SajdahBadgeHeight.toPx() / 2f }).roundToInt(),
                                        density = density,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Paint.applyGlyphColor(
    highlighted: Boolean,
    glyph: Int,
    playbackBlue: Int,
) {
    when {
        highlighted -> {
            color = playbackBlue
            colorFilter = null
        }
        else -> {
            color = glyph
            colorFilter = null
        }
    }
}

private fun hitTest(layouts: List<WordLayoutInfo>, offset: Offset): WordLayoutInfo? {
    val x = offset.x
    val y = offset.y
    for (index in layouts.indices.reversed()) {
        val layout = layouts[index]
        if (layout.contains(x, y)) return layout
    }
    return null
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectMedinaWordGestures(
    longPressTimeoutMs: Long,
    moveSlopPx: Float,
    onTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val downPosition = down.position

        val releasedBeforeTimeout = withTimeoutOrNull(longPressTimeoutMs) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.first()
                val travelled = (change.position - downPosition).getDistance()
                if (travelled > moveSlopPx) {
                    return@withTimeoutOrNull false
                }
                if (!change.pressed) {
                    change.consume()
                    return@withTimeoutOrNull true
                }
            }
        }

        when (releasedBeforeTimeout) {
            true -> onTap(downPosition)
            false -> Unit
            null -> {
                val stillDown = currentEvent.changes.firstOrNull()
                val travelled = stillDown?.let { (it.position - downPosition).getDistance() } ?: Float.MAX_VALUE
                if (stillDown != null && stillDown.pressed && travelled <= moveSlopPx) {
                    stillDown.consume()
                    onLongPress(downPosition)
                    waitForUpOrCancellation()
                }
            }
        }
    }
}

@ArabicPreviews
@Composable
private fun MedinaCanvasPagePreview() {
    PreviewTheme {
        val paper = LocalPaperColors.current
        MedinaCanvasPage(
            pageNumber = 1,
            lines = PreviewFixtures.canvasLines,
            fontManager = null,
            glyphColor = Color.Black,
            highlightColor = paper.ayahHighlight,
            highlightWordId = 2,
            onWordLongPress = {},
        )
    }
}
