package com.quransunah.app.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.Typeface
import android.provider.Settings
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.LanguageRegistry
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.data.catalog.ReciterCatalog
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.index.SurahNames
import com.quransunah.app.ui.mushaf.SajdahAyah
import com.quransunah.app.ui.mushaf.ayahBackgroundGlyph
import com.quransunah.app.ui.mushaf.ayahMarkerGlyphSize
import com.quransunah.app.ui.mushaf.ayahMarkerTextStyle
import com.quransunah.app.ui.mushaf.toWordRecord
import com.quransunah.app.ui.shell.ChromeFrameDecor
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperPalettes
import com.quransunah.app.ui.theme.ThemeTokens
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PAGE_COUNT = 5
private val CardShape = RoundedCornerShape(18.dp)
private val InnerShape = RoundedCornerShape(15.dp)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val direction = uiLayoutDirection()

    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch {
                pagerState.animateScrollToPage(
                    pagerState.currentPage - 1,
                    animationSpec = tween(420, easing = FastOutSlowInEasing),
                )
            }
        } else {
            onDismiss()
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(paper.pageBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            HorizontalPager(
                state = pagerState,
                reverseLayout = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { page ->
                GuidePage(page = page)
            }
            GuideProgress(
                page = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 14.dp),
            )
            GuideControls(
                isLast = pagerState.currentPage == PAGE_COUNT - 1,
                onSkip = onComplete,
                onNext = {
                    if (pagerState.currentPage == PAGE_COUNT - 1) {
                        onComplete()
                    } else {
                        scope.launch {
                            pagerState.animateScrollToPage(
                                pagerState.currentPage + 1,
                                animationSpec = tween(420, easing = FastOutSlowInEasing),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun GuidePage(page: Int) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (page) {
                0 -> WelcomeVisual(Modifier.fillMaxWidth().height(210.dp))
                1 -> ReadingVisual(Modifier.fillMaxWidth())
                2 -> WordToolsVisual(Modifier.fillMaxWidth())
                3 -> ListenMeaningsVisual(Modifier.fillMaxWidth())
                else -> ComfortVisual(Modifier.fillMaxWidth())
            }
        }
        when (page) {
            0 -> GuideCopy(
                title = stringResource(R.string.guide_welcome_title),
                body = stringResource(R.string.guide_welcome_body),
            )
            1 -> GuideCopy(
                title = stringResource(R.string.guide_reading_title),
                body = stringResource(R.string.guide_reading_body),
            )
            2 -> GuideCopy(
                title = stringResource(R.string.guide_word_title),
                body = stringResource(R.string.guide_word_body),
            )
            3 -> GuideCopy(
                title = stringResource(R.string.guide_listen_title),
                body = stringResource(R.string.guide_listen_body),
            )
            else -> {
                val paper = LocalPaperColors.current
                Text(
                    text = stringResource(R.string.guide_comfort_title),
                    color = paper.textStrong,
                    fontFamily = LocalDisplayFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 34.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                SectionCopy(
                    title = stringResource(R.string.guide_text_title),
                    body = stringResource(R.string.guide_text_body),
                )
                Spacer(Modifier.height(12.dp))
                SectionCopy(
                    title = stringResource(R.string.guide_madinah_title),
                    body = stringResource(R.string.guide_madinah_body),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun GuideCopy(title: String, body: String) {
    val paper = LocalPaperColors.current
    Text(
        text = title,
        color = paper.textStrong,
        fontFamily = LocalDisplayFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 34.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = body,
        color = paper.textPrimary,
        fontFamily = LocalAppFontFamily.current,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionCopy(title: String, body: String) {
    val paper = LocalPaperColors.current
    Text(
        text = title,
        color = paper.textStrong,
        fontFamily = LocalDisplayFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = body,
        color = paper.textPrimary,
        fontFamily = LocalAppFontFamily.current,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WelcomeVisual(modifier: Modifier = Modifier) {
    GuideFrame(modifier = modifier, decorated = true) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WelcomePillar(
                icon = R.drawable.ic_nav_quran,
                label = stringResource(R.string.tab_reading),
                modifier = Modifier.weight(1f),
            )
            WelcomePillar(
                icon = R.drawable.ic_headset,
                label = stringResource(R.string.tab_listening),
                modifier = Modifier.weight(1f),
            )
            WelcomePillar(
                icon = R.drawable.ic_format_list_bulleted,
                label = stringResource(R.string.reading_mode_meanings),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun WelcomePillar(icon: Int, label: String, modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(paper.pageBody.copy(alpha = 0.92f))
                .border(1.2.dp, ChromeTokens.Gold, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = paper.textStrong,
                modifier = Modifier.size(26.dp),
            )
        }
        Text(
            text = label,
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReadingVisual(modifier: Modifier = Modifier) {
    val painter = painterResource(R.drawable.guide_fatiha)
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        1f
    }
    GuideFrame(modifier = modifier, fill = false) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Image(
                painter = painter,
                contentDescription = stringResource(R.string.guide_reading_title),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio),
            )
            SwipeGestureCue(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .height(44.dp),
            )
        }
    }
}

@Composable
private fun WordToolsVisual(modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val body = LocalAppFontFamily.current
    val night = LocalNightMode.current
    val onAccent = if (night) PaperPalettes.CharcoalBlack else Color.White
    GuideFrame(modifier = modifier, fill = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            MalikAyahPreview(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.word_play_word),
                    color = paper.textMuted,
                    fontFamily = body,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 10.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PreviewCircle(
                            size = 32.dp,
                            background = paper.pageBody,
                            border = paper.tone500,
                            contentDescription = stringResource(R.string.word_next_word),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_previous),
                                contentDescription = null,
                                tint = paper.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        PreviewCircle(
                            size = 40.dp,
                            background = paper.accent,
                            border = Color.Transparent,
                            contentDescription = stringResource(R.string.word_play_word),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_play_arrow),
                                contentDescription = null,
                                tint = onAccent,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.size(6.dp))
                        PreviewCircle(
                            size = 32.dp,
                            background = paper.pageBody,
                            border = paper.tone500,
                            contentDescription = stringResource(R.string.word_prev_word),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_skip_next),
                                contentDescription = null,
                                tint = paper.accent,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PreviewOutlineButton(
                    label = stringResource(R.string.word_tafsir),
                    modifier = Modifier.weight(1f),
                )
                PreviewOutlineButton(
                    label = stringResource(R.string.word_save_ayah),
                    modifier = Modifier.weight(1f),
                )
            }
            PreviewReciterField()
            PreviewFillButton(label = stringResource(R.string.word_play_ayah))
            PreviewFillButton(label = stringResource(R.string.word_share_text))
        }
    }
}

private const val GUIDE_FATIHA_SURAH = 1
private const val GUIDE_FATIHA_AYAH = 4

private const val GUIDE_HAND_TIP_X = 375f / 883f
private const val GUIDE_HAND_TIP_Y = 230f / 1300f

@Composable
private fun SwipeGestureCue(modifier: Modifier = Modifier) {
    val hand = painterResource(R.drawable.ic_guide_touch)
    val handTint = LocalPaperColors.current.accent
    val transition = rememberInfiniteTransition(label = "guideSwipe")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3400
                0f at 0
                1f at 1100 using FastOutSlowInEasing
                1f at 1650
                0f at 2750 using FastOutSlowInEasing
                0f at 3400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "guideSwipeTravel",
    )
    val lean by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 3400
                1f at 0
                1f at 1650
                -1f at 1700
                -1f at 3400
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "guideSwipeLean",
    )
    Canvas(modifier) {
        val handHeight = 32.dp.toPx().coerceAtMost(size.height - 2.dp.toPx())
        val y = (handHeight * GUIDE_HAND_TIP_Y + 1.dp.toPx())
            .coerceIn(1.dp.toPx(), (size.height - handHeight * (1f - GUIDE_HAND_TIP_Y) - 1.dp.toPx()).coerceAtLeast(1.dp.toPx()))
        val inset = 18.dp.toPx()
        val start = inset
        val end = size.width - inset
        drawLine(
            color = ChromeTokens.Gold.copy(alpha = 0.35f),
            start = Offset(start, y),
            end = Offset(end, y),
            strokeWidth = 1.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val x = start + (end - start) * travel
        drawGuideHandImage(
            painter = hand,
            fingertip = Offset(x, y),
            height = handHeight,
            lean = lean,
            tint = handTint,
        )
    }
}

@Composable
private fun MalikAyahPreview(modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val density = LocalDensity.current
    val context = LocalContext.current
    val hand = painterResource(R.drawable.ic_guide_touch)
    val handTint = paper.accent
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingMushafEntryPoint::class.java,
        )
    }
    val fontManager = remember(entryPoint) { entryPoint.qcfFontManager() }
    var words by remember { mutableStateOf<List<QuranWord>>(emptyList()) }
    var pageNumber by remember { mutableIntStateOf(1) }
    var pageFace by remember { mutableStateOf<Typeface?>(null) }
    var stripCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var malikBounds by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(entryPoint, fontManager) {
        val repository = entryPoint.mushafRepository()
        val loaded = repository.getAyahWords(GUIDE_FATIHA_SURAH, GUIDE_FATIHA_AYAH)
        val page = repository.getPageForAyah(GUIDE_FATIHA_SURAH, GUIDE_FATIHA_AYAH).coerceAtLeast(1)
        pageNumber = page
        pageFace = fontManager.loadPageTypeface(page)
        words = loaded
    }
    val malikId = words.firstOrNull { !it.isAyahMarker }?.id
    val transition = rememberInfiniteTransition(label = "guidePress")
    val cycle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "guidePressCycle",
    )
    val pressed = when {
        cycle < 0.16f -> cycle / 0.16f
        cycle < 0.62f -> 1f
        cycle < 0.78f -> 1f - (cycle - 0.62f) / 0.16f
        else -> 0f
    }
    val ripple = if (cycle in 0.16f..0.62f) {
        ((cycle - 0.16f) / 0.46f).let { raw ->
            val pulse = raw * 2f
            if (pulse <= 1f) pulse else pulse - 1f
        }
    } else {
        0f
    }
    val glyphColor = if (night) Color.White else Color.Black
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .onPlaced { stripCoords = it },
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                words.forEachIndexed { index, word ->
                    GuideMushafGlyph(
                        word = word,
                        next = words.getOrNull(index + 1),
                        pageNumber = pageNumber,
                        fontManager = fontManager,
                        pageFace = pageFace,
                        glyphSize = 26.sp,
                        bodyColor = glyphColor,
                        markerColor = ThemeTokens.AyahMarker,
                        modifier = if (word.id == malikId) {
                            Modifier.onPlaced { coords ->
                                val strip = stripCoords
                                if (strip == null || !strip.isAttached || !coords.isAttached) return@onPlaced
                                val origin = strip.localPositionOf(coords, Offset.Zero)
                                val nextBounds = Rect(
                                    origin.x,
                                    origin.y,
                                    origin.x + coords.size.width,
                                    origin.y + coords.size.height,
                                )
                                if (nextBounds != malikBounds) malikBounds = nextBounds
                            }
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }
        if (malikBounds.width > 0f && malikBounds.height > 0f) {
            val markX = with(density) { malikBounds.left.toDp() }
            val markY = with(density) { malikBounds.top.toDp() }
            val markWidth = with(density) { malikBounds.width.toDp() }
            val markHeight = with(density) { malikBounds.height.toDp() }
            Box(
                modifier = Modifier
                    .absoluteOffset(x = markX, y = markY)
                    .size(markWidth, markHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(paper.ayahHighlight.copy(alpha = 0.34f + 0.16f * pressed))
                    .border(
                        width = 1.5.dp,
                        color = paper.accent.copy(alpha = 0.35f + 0.35f * pressed),
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
            Canvas(Modifier.matchParentSize()) {
                val center = Offset(
                    x = malikBounds.center.x,
                    y = malikBounds.top + malikBounds.height * 0.42f,
                )
                if (pressed > 0.05f && ripple > 0f) {
                    drawCircle(
                        color = ChromeTokens.Gold.copy(alpha = (1f - ripple) * 0.28f * pressed),
                        radius = (10.dp.toPx() + ripple * 18.dp.toPx()),
                        center = center,
                        style = Stroke(width = 1.4.dp.toPx()),
                    )
                }
                val pressY = malikBounds.top + malikBounds.height * 0.72f
                val restY = (pressY + 7.dp.toPx()).coerceAtMost(size.height - 2.dp.toPx())
                drawGuideHandImage(
                    painter = hand,
                    fingertip = Offset(center.x, restY + (pressY - restY) * pressed),
                    height = 30.dp.toPx(),
                    lean = 0f,
                    tint = handTint,
                )
            }
        }
    }
}

@Composable
private fun GuideMushafGlyph(
    word: QuranWord,
    next: QuranWord?,
    pageNumber: Int,
    fontManager: QcfFontManager,
    pageFace: Typeface?,
    glyphSize: TextUnit,
    bodyColor: Color,
    markerColor: Color,
    modifier: Modifier = Modifier,
) {
    val night = LocalNightMode.current
    val remapped = if (word.textLigature.isBlank()) {
        word.textLigature
    } else {
        fontManager.remapLigature(word.textLigature, pageNumber)
    }
    val record = word.toWordRecord()
    val overlay = SajdahAyah.overlayLigature(record, next?.toWordRecord())
    val display = buildString {
        if (!overlay.isNullOrEmpty()) append(overlay)
        append(remapped.ifBlank { word.textHafs })
    }
    val family = when {
        pageFace != null && remapped.isNotBlank() -> FontFamily(pageFace)
        else -> FontFamily.Serif
    }
    val ayahBackground = if (pageFace != null && remapped.isNotBlank()) {
        ayahBackgroundGlyph(night)
    } else {
        ""
    }
    if (word.isAyahMarker) {
        Text(
            text = ayahBackground + display,
            color = markerColor,
            style = ayahMarkerTextStyle(ayahMarkerGlyphSize(glyphSize)),
            fontFamily = family,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = modifier,
        )
    } else {
        Text(
            text = display,
            color = bodyColor,
            fontSize = glyphSize,
            lineHeight = glyphSize * 1.6f,
            fontFamily = family,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = modifier.padding(horizontal = 2.dp),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGuideHandImage(
    painter: androidx.compose.ui.graphics.painter.Painter,
    fingertip: Offset,
    height: Float,
    lean: Float,
    tint: Color,
) {
    val dstH = height.coerceAtLeast(1f)
    val intrinsic = painter.intrinsicSize
    val aspect = if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        883f / 1300f
    }
    val dstW = dstH * aspect
    val topLeft = Offset(
        fingertip.x - dstW * GUIDE_HAND_TIP_X,
        fingertip.y - dstH * GUIDE_HAND_TIP_Y,
    )
    rotate(degrees = lean * 14f, pivot = fingertip) {
        translate(left = topLeft.x, top = topLeft.y) {
            with(painter) {
                draw(
                    size = Size(dstW, dstH),
                    colorFilter = ColorFilter.tint(tint),
                )
            }
        }
    }
}

@Composable
private fun PreviewCircle(
    size: Dp,
    background: Color,
    border: Color,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(
                if (border == Color.Transparent) Modifier else Modifier.border(1.dp, border, CircleShape),
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

@Composable
private fun PreviewOutlineButton(label: String, modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .heightIn(min = 36.dp)
            .clip(shape)
            .border(1.dp, paper.tone500, shape)
            .background(paper.pageBody)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewReciterField() {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, paper.tone500, shape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = stringResource(R.string.audio_reciter),
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(R.string.word_choose_reader),
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PreviewFillButton(label: String) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(shape)
            .background(paper.accent)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (night) PaperPalettes.CharcoalBlack else Color.White,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListenMeaningsVisual(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardingMushafEntryPoint::class.java,
        )
    }
    val fontManager = remember(entryPoint) { entryPoint.qcfFontManager() }
    var words by remember { mutableStateOf<List<QuranWord>>(emptyList()) }
    var pageNumber by remember { mutableIntStateOf(1) }
    var pageFace by remember { mutableStateOf<Typeface?>(null) }
    var surahTitle by remember { mutableStateOf("") }
    var reciterName by remember { mutableStateOf("") }
    LaunchedEffect(entryPoint, fontManager, context) {
        runCatching {
            val repository = entryPoint.mushafRepository()
            val tag = ArabicRtl.currentTag(context)
            val surah = repository.getSurahs().firstOrNull { it.number == GUIDE_FATIHA_SURAH }
            val arabicName = surah?.nameArabic.orEmpty()
                .removePrefix("سُورَةُ ")
                .removePrefix("سورة ")
            val reciter = withContext(Dispatchers.IO) {
                entryPoint.reciterCatalog().defaultSurahReciter()
            }
            val loaded = repository.getAyahWords(GUIDE_FATIHA_SURAH, 1)
            val page = repository.getPageForAyah(GUIDE_FATIHA_SURAH, 1).coerceAtLeast(1)
            surahTitle = SurahNames.displayName(tag, GUIDE_FATIHA_SURAH, arabicName)
            reciterName = ReciterNames.display(tag, reciter?.id ?: 0, reciter?.name.orEmpty())
            pageNumber = page
            pageFace = fontManager.loadPageTypeface(page)
            words = loaded
        }
    }
    GuideFrame(modifier = modifier, fill = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuideListeningPreview(
                surahTitle = surahTitle,
                reciterName = reciterName,
            )
            GuideMeaningsPreview(
                words = words,
                pageNumber = pageNumber,
                pageFace = pageFace,
                fontManager = fontManager,
            )
        }
    }
}

@Composable
private fun GuideListeningPreview(
    surahTitle: String,
    reciterName: String,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val player = if (night) {
        paper.pageBackground
    } else {
        androidx.compose.ui.graphics.lerp(paper.textStrong, paper.accent, 0.42f)
    }
    val titleColor = if (night) paper.textStrong else paper.tone100
    val mutedColor = if (night) paper.textMuted else paper.tone400
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previousIcon = if (rtl) R.drawable.ic_skip_next else R.drawable.ic_skip_previous
    val nextIcon = if (rtl) R.drawable.ic_skip_previous else R.drawable.ic_skip_next
    val shape = RoundedCornerShape(16.dp)
    val scrimTop = if (night) 0.72f else 0.52f
    val scrimMid = if (night) 0.58f else 0.34f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(player),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.home_listening),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = scrimTop),
                            0.42f to Color.Black.copy(alpha = scrimMid),
                            1f to player,
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (surahTitle.isNotEmpty()) {
                    Text(
                        text = surahTitle,
                        color = titleColor,
                        fontFamily = LocalAppFontFamily.current,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                if (reciterName.isNotEmpty()) {
                    Text(
                        text = reciterName,
                        color = mutedColor,
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(paper.accent.copy(alpha = 0.28f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.22f)
                        .height(4.dp)
                        .background(paper.accent),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GuidePlayerCircle(
                    size = 28.dp,
                    background = Color.White.copy(alpha = 0.08f),
                    border = paper.accent.copy(alpha = 0.45f),
                    icon = previousIcon,
                    iconTint = titleColor,
                    iconSize = 14.dp,
                    contentDescription = stringResource(R.string.audio_prev_surah),
                )
                GuidePlayerCircle(
                    size = 36.dp,
                    background = paper.accent,
                    border = Color.Transparent,
                    icon = R.drawable.ic_play_arrow,
                    iconTint = paper.pageBackground,
                    iconSize = 18.dp,
                    contentDescription = stringResource(R.string.audio_play),
                )
                GuidePlayerCircle(
                    size = 28.dp,
                    background = Color.White.copy(alpha = 0.08f),
                    border = paper.accent.copy(alpha = 0.45f),
                    icon = nextIcon,
                    iconTint = titleColor,
                    iconSize = 14.dp,
                    contentDescription = stringResource(R.string.audio_next_surah),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                GuideRepeatChip(
                    icon = R.drawable.ic_repeat,
                    label = stringResource(R.string.audio_repeat_all),
                    selected = false,
                    muted = mutedColor,
                    modifier = Modifier.weight(1f),
                )
                GuideRepeatChip(
                    icon = R.drawable.ic_repeat_one,
                    label = stringResource(R.string.audio_repeat_one),
                    selected = false,
                    muted = mutedColor,
                    modifier = Modifier.weight(1f),
                )
                GuideRepeatChip(
                    icon = R.drawable.ic_play_once,
                    label = stringResource(R.string.audio_repeat_off),
                    selected = true,
                    muted = mutedColor,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun GuidePlayerCircle(
    size: Dp,
    background: Color,
    border: Color,
    icon: Int,
    iconTint: Color,
    iconSize: Dp,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(
                if (border == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.border(1.dp, border, CircleShape)
                },
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun GuideRepeatChip(
    icon: Int,
    label: String,
    selected: Boolean,
    muted: Color,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val mark = if (selected) paper.pageBackground else muted
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(shape)
            .background(if (selected) paper.accent else Color.White.copy(alpha = 0.05f))
            .border(
                width = 1.dp,
                color = if (selected) paper.accent else muted.copy(alpha = 0.45f),
                shape = shape,
            )
            .padding(horizontal = 4.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = mark,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            color = mark,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuideMeaningsPreview(
    words: List<QuranWord>,
    pageNumber: Int,
    pageFace: Typeface?,
    fontManager: QcfFontManager,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val card = if (night) {
        androidx.compose.ui.graphics.lerp(paper.surface, paper.accent, 0.16f)
    } else {
        androidx.compose.ui.graphics.lerp(paper.pageBackground, paper.accent, 0.08f)
    }
    val glyphColor = paper.textStrong
    val languageName = LanguageRegistry.byTag("en")?.nativeName ?: "English"
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(R.string.translation_selector, languageName),
            color = paper.accentHover,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth(),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(card)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    words.forEachIndexed { index, word ->
                        GuideMushafGlyph(
                            word = word,
                            next = words.getOrNull(index + 1),
                            pageNumber = pageNumber,
                            fontManager = fontManager,
                            pageFace = pageFace,
                            glyphSize = 16.sp,
                            bodyColor = glyphColor,
                            markerColor = glyphColor,
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.guide_meaning_sample),
                color = paper.textMuted,
                fontFamily = LocalAppFontFamily.current,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ComfortVisual(modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val display = LocalDisplayFontFamily.current
    var sizeStep by remember { mutableFloatStateOf(0.62f) }
    val sampleSp = 15f + sizeStep * 15f
    GuideFrame(modifier = modifier, fill = false) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_pages_text),
                color = paper.textMuted,
                fontFamily = LocalAppFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_font_small),
                    color = paper.textStrong,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                )
                Slider(
                    value = sizeStep,
                    onValueChange = { sizeStep = it },
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = paper.accent,
                        activeTrackColor = paper.accent,
                        inactiveTrackColor = paper.tone500,
                    ),
                )
                Text(
                    text = stringResource(R.string.settings_font_large),
                    color = paper.textStrong,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(64.dp),
                )
            }
            Text(
                text = stringResource(R.string.guide_preview_bismillah),
                color = paper.textStrong,
                fontFamily = display,
                fontSize = sampleSp.sp,
                lineHeight = (sampleSp * 1.45f).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_pages_madina),
                color = paper.textMuted,
                fontFamily = LocalAppFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            MadinahRotationDemo(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun MadinahRotationDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val transition = rememberInfiniteTransition(label = "madinahRotate")
    val animated by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5600
                0f at 0
                0f at 1000
                1f at 2100 using FastOutSlowInEasing
                1f at 3600
                0f at 4700 using FastOutSlowInEasing
                0f at 5600
            },
        ),
        label = "turn",
    )
    val turn = if (motionEnabled) animated else 0f
    Box(modifier = modifier.height(118.dp)) {
        RotationCue(
            turn = turn,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 2.dp)
                .size(28.dp),
        )
        MushafPhone(
            turn = turn,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun RotationCue(turn: Float, modifier: Modifier = Modifier) {
    val motion = sin(turn * PI).toFloat().coerceIn(0f, 1f)
    Canvas(modifier.graphicsLayer { alpha = motion }) {
        if (motion < 0.02f) return@Canvas
        val gold = ChromeTokens.Gold
        val inset = 3.dp.toPx()
        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
        val arcTopLeft = Offset(inset, inset)
        rotate(degrees = -turn * 70f, pivot = center) {
            drawArc(
                color = gold,
                startAngle = 210f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = arcTopLeft,
                size = arcSize,
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round),
            )
            val radius = arcSize.minDimension / 2f
            val end = 310.0 * PI / 180.0
            val tip = Offset(
                x = center.x + radius * cos(end).toFloat(),
                y = center.y + radius * sin(end).toFloat(),
            )
            val tangentX = -sin(end).toFloat()
            val tangentY = cos(end).toFloat()
            val normalX = cos(end).toFloat()
            val normalY = sin(end).toFloat()
            val head = 4.4.dp.toPx()
            val path = Path().apply {
                moveTo(
                    tip.x - tangentX * head + normalX * head * 0.42f,
                    tip.y - tangentY * head + normalY * head * 0.42f,
                )
                lineTo(tip.x, tip.y)
                lineTo(
                    tip.x - tangentX * head - normalX * head * 0.42f,
                    tip.y - tangentY * head - normalY * head * 0.42f,
                )
            }
            drawPath(path, color = gold, style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

@Composable
private fun MushafPhone(turn: Float, modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val painter = painterResource(R.drawable.guide_fatiha)
    val width = lerp(48.dp, 148.dp, turn)
    val height = lerp(84.dp, 80.dp, turn)
    val shell = RoundedCornerShape(11.dp)
    val screen = RoundedCornerShape(6.dp)
    val crop = ((turn - 0.72f) / 0.18f).coerceIn(0f, 1f)
    val tilt = sin(turn * PI).toFloat() * -6f
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .graphicsLayer { rotationZ = tilt }
            .clip(shell)
            .background(paper.pageBody)
            .border(1.4.dp, ChromeTokens.Gold, shell)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(screen)
                .background(paper.pageBody)
                .border(0.6.dp, ChromeTokens.GoldInner, screen),
        ) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 1f - crop },
            )
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = crop },
            )
        }
    }
}

@Composable
private fun GuideFrame(
    modifier: Modifier = Modifier,
    decorated: Boolean = false,
    fill: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val paper = LocalPaperColors.current
    Box(
        modifier = modifier
            .clip(CardShape)
            .background(paper.pageBody)
            .border(1.4.dp, ChromeTokens.Gold, CardShape)
            .padding(3.dp)
            .clip(InnerShape)
            .border(0.8.dp, ChromeTokens.GoldInner, InnerShape),
    ) {
        if (decorated) {
            ChromeFrameDecor(Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier
                .then(if (fill) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
    }
}

@Composable
private fun GuideProgress(page: Int, modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            val active = index == page
            val width by animateDpAsState(
                targetValue = if (active) 22.dp else 6.dp,
                animationSpec = tween(280),
                label = "dot",
            )
            val description = stringResource(R.string.onboarding_page_cd, index + 1, PAGE_COUNT)
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(if (active) ChromeTokens.Gold else paper.tone500.copy(alpha = 0.7f))
                    .semantics {
                        if (active) contentDescription = description
                    },
            )
        }
    }
}

@Composable
private fun GuideControls(
    isLast: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.onboarding_skip),
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button, onClick = onSkip)
                .padding(horizontal = 8.dp, vertical = 10.dp),
        )
        Text(
            text = stringResource(if (isLast) R.string.onboarding_start else R.string.onboarding_next),
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(shape)
                .background(paper.chromeFill)
                .border(1.2.dp, ChromeTokens.Gold, shape)
                .clickable(role = Role.Button, onClick = onNext)
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface OnboardingMushafEntryPoint {
    fun mushafRepository(): MushafRepository
    fun qcfFontManager(): QcfFontManager
    fun reciterCatalog(): ReciterCatalog
}
