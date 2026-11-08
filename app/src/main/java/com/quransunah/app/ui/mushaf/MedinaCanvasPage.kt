package com.quransunah.app.ui.mushaf

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicOrdinals
import com.quransunah.app.core.HizbBadgeParts
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
    tajweedEnabled: Boolean = true,
    chromeVisible: Boolean = true,
    surahsByNumber: Map<Int, SurahInfo> = emptyMap(),
    quarter: QuarterMarker? = null,
    sajda: SajdaMarker? = null,
    onWordTap: (WordRecord) -> Unit = {},
    onWordLongPress: (WordRecord) -> Unit,
    onEmptyTap: () -> Unit = {},
    onSurahNameLongPress: () -> Unit = {},
    pageFit: MedinaPageFit = MedinaPageFit.Fill,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LocalPaperColors.current.mushafPaper)
            .clipToBounds(),
    ) {
        if (pageFit == MedinaPageFit.FitScreen) {
            val box = fittedMedinaPageBox(maxWidth.value, maxHeight.value)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                MedinaCanvasPageBody(
                    pageNumber = pageNumber,
                    lines = lines,
                    fontManager = fontManager,
                    glyphColor = glyphColor,
                    highlightColor = highlightColor,
                    highlightWordId = highlightWordId,
                    highlightAyah = highlightAyah,
                    hideAyahText = hideAyahText,
                    revealedAyahs = revealedAyahs,
                    tajweedEnabled = tajweedEnabled,
                    chromeVisible = chromeVisible,
                    surahsByNumber = surahsByNumber,
                    quarter = quarter,
                    sajda = sajda,
                    onWordTap = onWordTap,
                    onWordLongPress = onWordLongPress,
                    onEmptyTap = onEmptyTap,
                    onSurahNameLongPress = onSurahNameLongPress,
                    modifier = Modifier
                        .width(box.width.dp)
                        .height(box.height.dp)
                        .clipToBounds(),
                )
            }
            return@BoxWithConstraints
        }
        if (pageFit == MedinaPageFit.OriginalScroll) {
            val scrollState = rememberScrollState()
            LaunchedEffect(pageNumber) { scrollState.scrollTo(0) }
            val box = originalMedinaPageBox(maxWidth.value)
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
                    revealedAyahs = revealedAyahs,
                    tajweedEnabled = tajweedEnabled,
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
                        .height(box.height.dp)
                        .clipToBounds(),
                )
            }
            return@BoxWithConstraints
        }
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
                    revealedAyahs = revealedAyahs,
                    tajweedEnabled = tajweedEnabled,
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
                revealedAyahs = revealedAyahs,
                tajweedEnabled = tajweedEnabled,
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
    tajweedEnabled: Boolean,
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
    val nightPalette = LocalNightMode.current
    var pageTypeface by remember(pageNumber, inspection, nightPalette) {
        mutableStateOf(
            if (inspection) Typeface.DEFAULT else fontManager?.peekPageTypeface(pageNumber, nightPalette),
        )
    }
    var markerTypeface by remember(pageNumber, inspection) {
        mutableStateOf(if (inspection) Typeface.DEFAULT else fontManager?.peekPageTypeface(pageNumber, false))
    }
    var uthmanicTypeface by remember { mutableStateOf(fontManager?.peekUthmanicTypeface()) }
    var titleTypeface by remember { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var plan by remember { mutableStateOf<MedinaPagePlan?>(null) }

    LaunchedEffect(pageNumber, inspection, nightPalette) {
        if (inspection || fontManager == null) {
            markerTypeface = Typeface.DEFAULT
            pageTypeface = Typeface.DEFAULT
            uthmanicTypeface = Typeface.DEFAULT
            titleTypeface = Typeface.DEFAULT
            return@LaunchedEffect
        }
        fontManager.setNightPalette(nightPalette)
        // Only CPAL differs: marker outlines and all existing layout measurements stay unchanged.
        val originalMarkerFace = fontManager.loadPageTypeface(pageNumber, false)
        val bodyFace = fontManager.loadPageTypeface(pageNumber, nightPalette)
        markerTypeface = originalMarkerFace
        pageTypeface = bodyFace
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
            faceNumber = (fontManager?.faceNumberForPage(pageNumber) ?: pageNumber) * 2 + if (nightPalette) 1 else 0,
            titleReady = titleTypeface != null,
            firstWordId = firstWordId,
            lastWordId = lastWordId,
            lineFingerprint = linesFingerprint,
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

    val slotCount = AppConstants.LINES_PER_PAGE
    val slotHeightPx = if (canvasSize.height > 0) canvasSize.height / slotCount.toFloat() else 0f
    val firstWordHint = LocalTrainingFirstWordHint.current
    val revealedWordIds = LocalTrainingRevealedWordIds.current
    val wordMarks = LocalTrainingWordMarks.current
    val focusWordId = LocalTrainingFocusWordId.current

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
                        pagePaint.typeface = if (word.word.isAyahMarker) markerTypeface ?: pageTypeface else pageTypeface
                        val marker = word.marker
                        val background = ayahBackgroundGlyph(night)
                        if (background.isNotEmpty() && word.word.isAyahMarker && word.body.isNotEmpty() && pageTypeface != null) {
                            pagePaint.color = glyph
                            pagePaint.colorFilter = null
                            nativeCanvas.drawText(
                                background,
                                word.x,
                                word.baseline,
                                pagePaint,
                            )
                        }
                        val hidden = trainingWordHidden(
                            isAyahMarker = word.word.isAyahMarker,
                            surah = word.word.surah,
                            ayah = word.word.ayah,
                            wordId = word.word.id,
                            hideAyahText = hideAyahText,
                            revealedAyahs = revealedAyahs,
                            revealedWordIds = revealedWordIds,
                            wordPosition = word.word.position,
                            firstWordHint = firstWordHint,
                        )
                        val mark = if (word.word.isAyahMarker) null else wordMarks[word.word.id]
                        if (
                            focusWordId != null &&
                            focusWordId == word.word.id &&
                            !word.word.isAyahMarker &&
                            !hidden
                        ) {
                            pagePaint.style = Paint.Style.STROKE
                            pagePaint.strokeWidth = 2.2f
                            pagePaint.color = gold
                            pagePaint.colorFilter = null
                            nativeCanvas.drawRect(word.x, word.boxTop, word.x + word.width, word.boxBottom, pagePaint)
                            pagePaint.style = Paint.Style.FILL
                        }
                        pagePaint.applyGlyphColor(
                            monochrome = !tajweedEnabled,
                            preservePalette = word.word.isAyahMarker,
                            highlighted = highlighted && mark == null,
                            glyph = if (hidden) {
                                Color.Transparent.toArgb()
                            } else {
                                glyph
                            },
                            playbackBlue = playbackBlue,
                            tint = mark?.let { trainingMarkColor(it).toArgb() },
                        )
                        // COLR glyphs keep their palette colours whatever the paint colour, so a hidden
                        // (training) word is skipped instead of being drawn transparent.
                        if (word.body.isNotEmpty() && pagePaint.color != Color.Transparent.toArgb()) {
                            nativeCanvas.drawText(word.body, word.x, word.baseline, pagePaint)
                        }
                        if (!marker.isNullOrEmpty()) {
                            pagePaint.typeface = markerTypeface ?: pageTypeface
                            pagePaint.applyGlyphColor(
                                monochrome = !tajweedEnabled,
                                preservePalette = true,
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

        }
    }
}

internal fun Paint.applyGlyphColor(
    monochrome: Boolean,
    highlighted: Boolean,
    glyph: Int,
    playbackBlue: Int,
    preservePalette: Boolean = false,
    tint: Int? = null,
) {
    if (preservePalette) {
        color = glyph
        colorFilter = null
        return
    }
    when {
        glyph == Color.Transparent.toArgb() -> {
            color = glyph
            colorFilter = null
        }
        tint != null -> {
            color = tint
            colorFilter = android.graphics.PorterDuffColorFilter(tint, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        highlighted -> {
            // Tint every COLR layer (palette colours ignore Paint.color).
            color = playbackBlue
            colorFilter = android.graphics.PorterDuffColorFilter(playbackBlue, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        monochrome -> {
            color = glyph
            colorFilter = android.graphics.PorterDuffColorFilter(glyph, android.graphics.PorterDuff.Mode.SRC_IN)
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
