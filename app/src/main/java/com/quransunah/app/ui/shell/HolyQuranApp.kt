package com.quransunah.app.ui.shell

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.data.catalog.TranslationRegistry
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.hydration.HydrationState
import com.quransunah.app.ui.bookmarks.BookmarksViewModel
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.index.QuranIndexViewModel
import com.quransunah.app.ui.index.primarySurahNumber
import com.quransunah.app.ui.listening.CompactPlaybackBar
import com.quransunah.app.ui.listening.CompactReciterChoice
import com.quransunah.app.ui.listening.ListeningScreen
import com.quransunah.app.ui.listening.ListeningViewModel
import com.quransunah.app.domain.model.QuarterMarker
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.ui.mushaf.LineRecord
import com.quransunah.app.ui.mushaf.MeaningsPage
import com.quransunah.app.ui.mushaf.MedinaPageFit
import com.quransunah.app.ui.mushaf.MedinaCanvasPage
import com.quransunah.app.ui.mushaf.PageHeaderBar
import com.quransunah.app.ui.mushaf.TranslationLanguageDialog
import com.quransunah.app.ui.mushaf.PageNumberBadge
import com.quransunah.app.ui.mushaf.PagePickerDialog
import com.quransunah.app.ui.mushaf.TextMushafPage
import com.quransunah.app.ui.mushaf.toLineRecords
import com.quransunah.app.ui.onboarding.OnboardingScreen
import com.quransunah.app.ui.search.SearchPane
import com.quransunah.app.ui.study.MeaningPopover
import com.quransunah.app.ui.study.StudyViewModel
import com.quransunah.app.recitation.ui.rememberMicPermissionRequest
import com.quransunah.app.ui.training.TrainingExpectedWord
import com.quransunah.app.ui.training.TrainingPhase
import com.quransunah.app.ui.training.TrainingRecitationViewModel
import com.quransunah.app.ui.training.TrainingReciteScope
import com.quransunah.app.ui.training.parseTrainingAyahReference
import com.quransunah.app.ui.training.recognizedWordIds
import com.quransunah.app.ui.training.trainingRangeValid
import com.quransunah.app.ui.study.WordSheet
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val SplashMinVisibleMs = 250L

@Composable
fun HolyQuranApp(viewModel: HolyQuranViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hydration by viewModel.hydration.collectAsStateWithLifecycle()
    var splashMinElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(SplashMinVisibleMs)
        splashMinElapsed = true
    }
    HolyQuranTheme(paletteId = settings.palette, nightMode = settings.nightMode) {
        when (val state = hydration) {
            is HydrationState.Failed -> {
                SideEffect { SplashFrameCache.release() }
                HydrationError(state.message) { viewModel.retryHydration() }
            }
            HydrationState.Pending -> BrandSplash(isPreparing = true)
            HydrationState.Ready -> if (splashMinElapsed) {
                SideEffect { SplashFrameCache.release() }
                ReadyShell(viewModel)
            } else {
                BrandSplash(isPreparing = true)
            }
        }
    }
}

@Composable
private fun BrandSplash(isPreparing: Boolean) {
    val splashBackground = colorResource(R.color.splash_background)
    val context = LocalContext.current
    val accent = LocalPaperColors.current.accent
    LaunchedEffect(accent) {
        withContext(Dispatchers.IO) {
            runCatching { prepareFloralArtwork(context, accent) }
        }
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view) {
            val window = (view.context as? Activity)?.window
            val restoreNavContrast = window != null &&
                Build.VERSION.SDK_INT >= 29 &&
                window.isNavigationBarContrastEnforced
            val restoreStatusContrast = window != null &&
                Build.VERSION.SDK_INT >= 29 &&
                window.isStatusBarContrastEnforced
            onDispose {
                if (window != null && Build.VERSION.SDK_INT >= 29) {
                    window.isNavigationBarContrastEnforced = restoreNavContrast
                    window.isStatusBarContrastEnforced = restoreStatusContrast
                }
            }
        }
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= 29) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            val controller = WindowCompat.getInsetsController(window, view)
            // Foliage and carpet sit behind the bars; white glyphs stay readable there.
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(splashBackground)
            .clipToBounds(),
    ) {
        SplashArtwork(Modifier.fillMaxSize())
        if (isPreparing) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
                    .background(splashBackground, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = Color(0xFFD4A017),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.hydration_preparing_title),
                    color = Color(0xFF3D342E),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.hydration_preparing_hint),
                    color = Color(0xFF6B5C52),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

internal object SplashFrameCache {
    private val lock = Any()
    private var frame: SplashFrame? = null

    fun warmup(context: android.content.Context) {
        obtain(context.applicationContext)
    }

    fun obtain(context: android.content.Context): SplashFrame? {
        synchronized(lock) {
            frame?.let { return it }
            val built = decode(context.applicationContext) ?: return null
            frame = built
            return built
        }
    }

    fun release() {
        synchronized(lock) {
            frame = null
        }
    }

    private fun decode(context: android.content.Context): SplashFrame? {
        val decoded = BitmapFactory.decodeResource(context.resources, R.drawable.splash) ?: return null
        val source = if (decoded.config == Bitmap.Config.ARGB_8888 && !decoded.isMutable) {
            decoded
        } else {
            decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
        }
        return SplashFrame(
            artwork = source.asImageBitmap(),
            top = splashEdgeWash(source, SplashEdge.Top),
            bottom = splashEdgeWash(source, SplashEdge.Bottom),
            left = splashEdgeWash(source, SplashEdge.Left),
            right = splashEdgeWash(source, SplashEdge.Right),
        )
    }
}

internal data class SplashFrame(
    val artwork: ImageBitmap,
    val top: ImageBitmap,
    val bottom: ImageBitmap,
    val left: ImageBitmap,
    val right: ImageBitmap,
)

@Composable
private fun SplashArtwork(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val frame = remember { SplashFrameCache.obtain(context) } ?: return
    val artwork = frame.artwork
    val topWash = frame.top
    val bottomWash = frame.bottom
    val leftWash = frame.left
    val rightWash = frame.right
    Canvas(modifier) {
        val scale = minOf(size.width / artwork.width, size.height / artwork.height)
        val drawW = (artwork.width * scale).roundToInt().coerceAtLeast(1)
        val drawH = (artwork.height * scale).roundToInt().coerceAtLeast(1)
        val left = ((size.width - drawW) / 2f).roundToInt()
        val top = ((size.height - drawH) / 2f).roundToInt()
        val overlap = 2
        if (left > 0) {
            drawImage(
                leftWash,
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(left + overlap, size.height.roundToInt().coerceAtLeast(1)),
                filterQuality = FilterQuality.Low,
            )
            drawImage(
                rightWash,
                dstOffset = IntOffset((left + drawW - overlap).coerceAtLeast(0), 0),
                dstSize = IntSize(
                    (size.width.roundToInt() - (left + drawW) + overlap).coerceAtLeast(1),
                    size.height.roundToInt().coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.Low,
            )
        }
        if (top > 0) {
            drawImage(
                topWash,
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize(size.width.roundToInt().coerceAtLeast(1), top + overlap),
                filterQuality = FilterQuality.Low,
            )
            drawImage(
                bottomWash,
                dstOffset = IntOffset(0, (top + drawH - overlap).coerceAtLeast(0)),
                dstSize = IntSize(
                    size.width.roundToInt().coerceAtLeast(1),
                    (size.height.roundToInt() - (top + drawH) + overlap).coerceAtLeast(1),
                ),
                filterQuality = FilterQuality.Low,
            )
        }
        drawImage(
            artwork,
            dstOffset = IntOffset(left, top),
            dstSize = IntSize(drawW, drawH),
            filterQuality = FilterQuality.High,
        )
    }
}

private enum class SplashEdge { Top, Bottom, Left, Right }

/** Soft continuation of one edge so the square artwork can fill taller or wider screens. */
private fun splashEdgeWash(source: Bitmap, edge: SplashEdge): ImageBitmap {
    val sw = source.width
    val sh = source.height
    val vertical = edge == SplashEdge.Top || edge == SplashEdge.Bottom
    val strip = if (vertical) {
        val stripH = (sh * 0.2f).toInt().coerceIn(1, sh)
        val y = if (edge == SplashEdge.Top) 0 else sh - stripH
        Bitmap.createBitmap(source, 0, y, sw, stripH)
    } else {
        val stripW = (sw * 0.2f).toInt().coerceIn(1, sw)
        val x = if (edge == SplashEdge.Left) 0 else sw - stripW
        Bitmap.createBitmap(source, x, 0, stripW, sh)
    }
    val outW = if (vertical) 96 else 36
    val outH = if (vertical) 36 else 96
    val wash = Bitmap.createScaledBitmap(strip, outW, outH, true)
    boxBlur(wash, radius = 6)
    val join = if (vertical) {
        val y = if (edge == SplashEdge.Top) 0 else sh - 1
        Bitmap.createScaledBitmap(Bitmap.createBitmap(source, 0, y, sw, 1), outW, 1, true)
    } else {
        val x = if (edge == SplashEdge.Left) 0 else sw - 1
        Bitmap.createScaledBitmap(Bitmap.createBitmap(source, x, 0, 1, sh), 1, outH, true)
    }
    blendWashJoin(wash, join, edge)
    return wash.asImageBitmap()
}

private fun blendWashJoin(wash: Bitmap, join: Bitmap, edge: SplashEdge) {
    val width = wash.width
    val height = wash.height
    val pixels = IntArray(width * height)
    wash.getPixels(pixels, 0, width, 0, 0, width, height)
    val blend = 12
    if (edge == SplashEdge.Top || edge == SplashEdge.Bottom) {
        val row = IntArray(width)
        join.getPixels(row, 0, width, 0, 0, width, 1)
        val steps = blend.coerceAtMost(height)
        for (step in 0 until steps) {
            val amount = 1f - step / steps.toFloat()
            val y = if (edge == SplashEdge.Top) height - 1 - step else step
            for (x in 0 until width) {
                val index = y * width + x
                pixels[index] = lerpArgb(pixels[index], row[x], amount)
            }
        }
    } else {
        val column = IntArray(height)
        join.getPixels(column, 0, 1, 0, 0, 1, height)
        val steps = blend.coerceAtMost(width)
        for (step in 0 until steps) {
            val amount = 1f - step / steps.toFloat()
            val x = if (edge == SplashEdge.Left) width - 1 - step else step
            for (y in 0 until height) {
                val index = y * width + x
                pixels[index] = lerpArgb(pixels[index], column[y], amount)
            }
        }
    }
    wash.setPixels(pixels, 0, width, 0, 0, width, height)
}

private fun boxBlur(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val width = bitmap.width
    val height = bitmap.height
    val src = IntArray(width * height)
    bitmap.getPixels(src, 0, width, 0, 0, width, height)
    val tmp = IntArray(src.size)
    blurHorizontal(src, tmp, width, height, radius)
    blurVertical(tmp, src, width, height, radius)
    bitmap.setPixels(src, 0, width, 0, 0, width, height)
}

private fun blurHorizontal(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val divisor = radius * 2 + 1
    for (y in 0 until height) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        val row = y * width
        for (offset in -radius..radius) {
            val color = src[row + offset.coerceIn(0, width - 1)]
            sumA += color ushr 24 and 0xFF
            sumR += color ushr 16 and 0xFF
            sumG += color ushr 8 and 0xFF
            sumB += color and 0xFF
        }
        for (x in 0 until width) {
            dst[row + x] = argb(sumA / divisor, sumR / divisor, sumG / divisor, sumB / divisor)
            val remove = src[row + (x - radius).coerceIn(0, width - 1)]
            val add = src[row + (x + radius + 1).coerceIn(0, width - 1)]
            sumA += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            sumR += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
            sumG += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
            sumB += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun blurVertical(src: IntArray, dst: IntArray, width: Int, height: Int, radius: Int) {
    val divisor = radius * 2 + 1
    for (x in 0 until width) {
        var sumA = 0
        var sumR = 0
        var sumG = 0
        var sumB = 0
        for (offset in -radius..radius) {
            val color = src[offset.coerceIn(0, height - 1) * width + x]
            sumA += color ushr 24 and 0xFF
            sumR += color ushr 16 and 0xFF
            sumG += color ushr 8 and 0xFF
            sumB += color and 0xFF
        }
        for (y in 0 until height) {
            dst[y * width + x] = argb(sumA / divisor, sumR / divisor, sumG / divisor, sumB / divisor)
            val remove = src[(y - radius).coerceIn(0, height - 1) * width + x]
            val add = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
            sumA += (add ushr 24 and 0xFF) - (remove ushr 24 and 0xFF)
            sumR += (add ushr 16 and 0xFF) - (remove ushr 16 and 0xFF)
            sumG += (add ushr 8 and 0xFF) - (remove ushr 8 and 0xFF)
            sumB += (add and 0xFF) - (remove and 0xFF)
        }
    }
}

private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
    return (alpha.coerceIn(0, 255) shl 24) or
        (red.coerceIn(0, 255) shl 16) or
        (green.coerceIn(0, 255) shl 8) or
        blue.coerceIn(0, 255)
}

private fun lerpArgb(from: Int, to: Int, amount: Float): Int {
    val t = amount.coerceIn(0f, 1f)
    fun channel(shift: Int): Int {
        val start = from ushr shift and 0xFF
        val end = to ushr shift and 0xFF
        return (start + (end - start) * t).toInt()
    }
    return argb(channel(24), channel(16), channel(8), channel(0))
}

@Composable
private fun HydrationError(message: String, onRetry: () -> Unit) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(paper.pageBackground)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = message, color = paper.textPrimary, textAlign = TextAlign.Center)
        Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.hydration_retry))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyShell(viewModel: HolyQuranViewModel) {
    val paper = LocalPaperColors.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val chrome by viewModel.chromeVisible.collectAsStateWithLifecycle()
    val headerQuarters by viewModel.headerQuarters.collectAsStateWithLifecycle()
    var tajweedEnabled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(true) }
    var tajweedGuideOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val surahsByNumber by viewModel.surahsByNumber.collectAsStateWithLifecycle()
    val pageNumber by viewModel.currentPage.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val meaningsByPage by viewModel.meaningsByPage.collectAsStateWithLifecycle()
    val selectedWord by viewModel.selectedWord.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val jumpHighlight by viewModel.jumpHighlight.collectAsStateWithLifecycle()
    val pendingPagerPage by viewModel.pendingPagerPage.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val indexTab by viewModel.indexTab.collectAsStateWithLifecycle()
    var firstWordHint by remember { mutableStateOf(false) }
    var trainingTextHidden by remember { mutableStateOf(true) }
    var revealedTrainingAyahs by remember { mutableStateOf(emptySet<Pair<Int, Int>>()) }
    var revealedTrainingWordIds by remember { mutableStateOf(emptySet<Int>()) }
    var selectedTrainingAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trainingSelectOpen by remember { mutableStateOf(false) }
    var trainingEngineOpen by remember { mutableStateOf(false) }
    var singleAyahMode by remember { mutableStateOf(false) }
    var trainingSurahPickerOpen by remember { mutableStateOf(false) }
    var singleAyahOriginPage by remember { mutableStateOf<Int?>(null) }
    var singleAyahOrigin by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var pendingTrainingWordAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trainingStartPending by remember { mutableStateOf(false) }
    var singleAyahText by remember { mutableStateOf("") }
    var resumeTrainingAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val listeningViewModel: ListeningViewModel = hiltViewModel()
    val studyViewModel: StudyViewModel = hiltViewModel()
    var trainingListenJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val stopTrainingListen: () -> Unit = {
        trainingListenJob?.cancel()
        trainingListenJob = null
        studyViewModel.audioPlayer.stop()
    }
    val trainingHistoryViewModel: com.quransunah.app.ui.training.TrainingHistoryViewModel = hiltViewModel()
    val recitationViewModel: TrainingRecitationViewModel = hiltViewModel()
    val recite by recitationViewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val indexViewModel: QuranIndexViewModel = hiltViewModel()
    val preferredAyah = jumpHighlight ?: selectedWord?.let { AyahRef(it.surah, it.ayah) }
    val currentLocationWord = remember(page) {
        page?.lines
            ?.asSequence()
            ?.flatMap { it.words.asSequence() }
            ?.firstOrNull { !it.isAyahMarker }
    }
    val currentSurahNumber = preferredAyah?.surah ?: currentLocationWord?.surah ?: 1
    val currentSurahName = surahsByNumber[currentSurahNumber]?.nameArabic
        ?: stringResource(R.string.surah_fallback, EasternArabic.format(currentSurahNumber))
    val currentAyah = preferredAyah?.ayah ?: currentLocationWord?.ayah ?: 1
    val currentPageAyahs = remember(page) {
        page?.lines.orEmpty()
            .flatMap { it.words }
            .filterNot { it.isAyahMarker }
            .map { it.surah to it.ayah }
            .toSet()
    }
    val trainingWords = remember(page) {
        page?.lines.orEmpty()
            .flatMap { it.words }
            .filterNot { it.isAyahMarker }
            .map { com.quransunah.app.ui.mushaf.TrainingWordRef(it.id, it.surah, it.ayah, it.wordIndex) }
    }
    val pageExpected = remember(page) {
        page?.lines.orEmpty()
            .flatMap { it.words }
            .filterNot { it.isAyahMarker }
            .map { word ->
                TrainingExpectedWord(word.id, word.surah, word.ayah, word.wordIndex, word.textHafs)
            }
    }
    val pageAyahs = remember(trainingWords) { trainingWords.map { it.surah to it.ayah }.distinct() }
    var rangeFrom by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var rangeTo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trainingFocusWordId by remember { mutableStateOf<Int?>(null) }
    val currentAyahKey = preferredAyah?.let { it.surah to it.ayah }
        ?: currentLocationWord?.let { it.surah to it.ayah }
    val selectedPageAyah = selectedTrainingAyah?.takeIf { it in pageAyahs }
        ?: currentAyahKey?.takeIf { it in pageAyahs }
        ?: currentLocationWord?.let { it.surah to it.ayah }?.takeIf { it in pageAyahs }
    val trainingCurrentAyah = selectedPageAyah ?: currentLocationWord?.let { it.surah to it.ayah }
    val trainingMarks = recite.marks
    val trainingRevealedWords = if (singleAyahMode) {
        val selected = selectedTrainingAyah ?: selectedPageAyah
        val visibleIds = trainingWords.filter { selected == (it.surah to it.ayah) }.map { it.id }.toSet()
        (revealedTrainingWordIds + recognizedWordIds(trainingMarks)).intersect(visibleIds)
    } else revealedTrainingWordIds + recognizedWordIds(trainingMarks)
    val trainingVisibleAyahs = if (singleAyahMode) {
        setOfNotNull(selectedTrainingAyah ?: selectedPageAyah)
    } else revealedTrainingAyahs + recite.completedAyahs
    val hideTrainingWords = tab == AppTab.Training &&
        (singleAyahMode || recite.phase == TrainingPhase.After || trainingTextHidden)
    val resultsOpen = recite.panelOpen && recite.summary != null
    val shownSummary = recite.summary
    val requestMic = rememberMicPermissionRequest { recitationViewModel.onMicDenied() }
    val trainingListenAyah = selectedTrainingAyah?.takeIf { it in currentPageAyahs } ?: trainingCurrentAyah
    val trainingScope = rememberCoroutineScope()
    var notedPlaybackAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trainingWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(page, preferredAyah) {
        bookmarksViewModel.bindContext(page, preferredAyah)
    }
    LaunchedEffect(singleAyahMode, selectedTrainingAyah, pageNumber) {
        if (!singleAyahMode) {
            singleAyahText = ""
        } else {
            val target = selectedTrainingAyah ?: currentAyahKey
            if (target != null) {
                singleAyahText = viewModel.trainingBodyWords(target.first, target.second)
                    .joinToString(" ") { it.textHafs }
            }
        }
    }
    LaunchedEffect(tab) {
        if (trainingWasOpen && tab != AppTab.Training) {
            trainingListenJob?.cancel()
            trainingListenJob = null
            trainingHistoryViewModel.finishSession()
            recitationViewModel.abandon()
            notedPlaybackAyah = null
        }
        trainingWasOpen = tab == AppTab.Training
        if (tab == AppTab.Training) recitationViewModel.warmUp(settings.recitationEngine)
        trainingTextHidden = true
        firstWordHint = false
        revealedTrainingAyahs = emptySet()
        revealedTrainingWordIds = emptySet()
        if (tab != AppTab.Training) {
            selectedTrainingAyah = null
            trainingSelectOpen = false
            trainingEngineOpen = false
            singleAyahMode = false
        }
    }
    LaunchedEffect(pageNumber, pageExpected, recite.completedAyahs, recite.phase, recite.scope) {
        val latestCompleted = recite.completedAyahs.lastOrNull() ?: return@LaunchedEffect
        if (latestCompleted in currentPageAyahs) selectedTrainingAyah = latestCompleted
        if (recite.phase == TrainingPhase.During && recite.scope == TrainingReciteScope.Open) {
            val completedPage = viewModel.trainingAyahPage(latestCompleted.first, latestCompleted.second)
            if (completedPage > pageNumber) viewModel.showAyahKeepingTab(completedPage)
        }
    }
    LaunchedEffect(pageNumber, pendingTrainingWordAyah, trainingWords) {
        val pending = pendingTrainingWordAyah ?: return@LaunchedEffect
        val word = trainingWords.firstOrNull { it.surah == pending.first && it.ayah == pending.second }
        if (word != null) {
            selectedTrainingAyah = pending
            revealedTrainingWordIds = revealedTrainingWordIds + word.id
            pendingTrainingWordAyah = null
        }
    }
    LaunchedEffect(tab, pageNumber, currentPageAyahs, playback.domain, playback.surah, playback.ayah, playback.isPlaying) {
        if (tab != AppTab.Training || !playback.isPlaying) return@LaunchedEffect
        if (playback.domain != PlaybackDomain.AYAH && playback.domain != PlaybackDomain.SURAH) return@LaunchedEffect
        val surah = playback.surah ?: return@LaunchedEffect
        val ayah = playback.ayah?.takeIf { it > 0 } ?: return@LaunchedEffect
        val key = surah to ayah
        if (key !in currentPageAyahs) {
            // Continuous listening moves the training page along with the reciter.
            if (trainingListenJob != null) {
                selectedTrainingAyah = key
                viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(surah, ayah))
            }
            return@LaunchedEffect
        }
        val next = com.quransunah.app.ui.mushaf.revealPlayingTrainingAyah(
            com.quransunah.app.ui.mushaf.TrainingRevealState(
                selected = selectedTrainingAyah,
                revealedAyahs = revealedTrainingAyahs,
                revealedWordIds = revealedTrainingWordIds,
            ),
            key,
        )
        selectedTrainingAyah = next.selected
        revealedTrainingAyahs = next.revealedAyahs
        if (notedPlaybackAyah != null && notedPlaybackAyah != key) {
            trainingScope.launch {
                val playbackPage = viewModel.trainingAyahPage(key.first, key.second)
                trainingHistoryViewModel.noteVerse(key.first, key.second, playbackPage)
            }
        }
        notedPlaybackAyah = key
    }
    LaunchedEffect(pageNumber, resumeTrainingAyah, currentPageAyahs) {
        val pending = resumeTrainingAyah ?: return@LaunchedEffect
        if (pending in currentPageAyahs) {
            val next = com.quransunah.app.ui.mushaf.revealPlayingTrainingAyah(
                com.quransunah.app.ui.mushaf.TrainingRevealState(
                    selected = selectedTrainingAyah,
                    revealedAyahs = revealedTrainingAyahs,
                    revealedWordIds = revealedTrainingWordIds,
                ),
                pending,
            )
            selectedTrainingAyah = next.selected
            revealedTrainingAyahs = next.revealedAyahs
            resumeTrainingAyah = null
        }
    }
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        var ctx = view.context
        val activity = generateSequence(ctx) { current ->
            (current as? android.content.ContextWrapper)?.baseContext
        }.filterIsInstance<Activity>().firstOrNull()
        val window = activity?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
val restoreBar = tab == AppTab.Reading || tab == AppTab.Training
    val navVisible = tab != AppTab.Home && tab != AppTab.Training &&
        (chrome || tab == AppTab.Listening)
    var pagePickerOpen by remember { mutableStateOf(false) }
    var meaningsMode by remember { mutableStateOf(false) }
    var translationPickerOpen by remember { mutableStateOf(false) }
    val appLanguageTag = ArabicRtl.currentTag(LocalContext.current)
    val highlight by viewModel.mushafHighlight.collectAsStateWithLifecycle()
    val onboardingDone by viewModel.onboardingDone.collectAsStateWithLifecycle()
    var manualGuide by remember { mutableStateOf(false) }
    var autoGuideDismissed by remember { mutableStateOf(false) }
    val showGuide = manualGuide || (onboardingDone == false && !autoGuideDismissed)

    val pagerState = rememberPagerState(
        initialPage = pageNumber - 1,
        pageCount = { AppConstants.TOTAL_PAGES },
    )
    BackHandler(
        enabled = !showGuide &&
            tab != AppTab.Home &&
            overlay == StudyOverlay.None &&
            !searchOpen &&
            picker == ShellPicker.None,
    ) {
        viewModel.selectTab(AppTab.Home)
    }
    LaunchedEffect(pagerState.currentPage, pendingPagerPage) {
        if (pendingPagerPage != null) return@LaunchedEffect
        viewModel.onUserPagerSettled(pagerState.currentPage + 1)
    }
    LaunchedEffect(pendingPagerPage) {
        val target = pendingPagerPage ?: return@LaunchedEffect
        pagerState.scrollToPage(target - 1)
        viewModel.consumePendingPagerPage()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(paper.pageBackground),
    ) {
        when (tab) {
            AppTab.Home -> HomeScreen(
                currentSurah = currentSurahName,
                currentSurahNumber = currentSurahNumber,
                currentAyah = currentAyah,
                onOpenReading = { viewModel.selectTab(AppTab.Reading) },
                onOpenTraining = { viewModel.selectTab(AppTab.Training) },
                onOpenListening = { viewModel.selectTab(AppTab.Listening) },
                onOpenIndex = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
                onOpenSavedPlaces = viewModel::openLastPositionPicker,
                onOpenSettings = viewModel::openSettingsPicker,
                wirdPlan = viewModel.wirdPlan.collectAsStateWithLifecycle().value,
                todayEpochDay = viewModel.todayEpochDay(),
                onStartWird = viewModel::openTodayWird,
                onSaveWird = viewModel::startWirdPlan,
                onRemoveWird = viewModel::clearWirdPlan,
                modifier = Modifier.fillMaxSize(),
            )
            AppTab.Reading, AppTab.Training -> {
                val pageContentDirection = LocalLayoutDirection.current
                val meaningsPagerDirection = if (tab == AppTab.Reading && meaningsMode) {
                    uiLayoutDirection()
                } else {
                    pageContentDirection
                }
                val playbackCursor = com.quransunah.app.ui.mushaf.rememberQulPlaybackCursor(playback)
                CompositionLocalProvider(
                    LocalLayoutDirection provides meaningsPagerDirection,
                    com.quransunah.app.ui.mushaf.LocalQulPlaybackCursor provides playbackCursor,
                    com.quransunah.app.ui.mushaf.LocalTrainingRevealedWordIds provides trainingRevealedWords,
                    com.quransunah.app.ui.mushaf.LocalTrainingWordMarks provides if (tab == AppTab.Training) trainingMarks else emptyMap(),
                    com.quransunah.app.ui.mushaf.LocalTrainingFocusWordId provides if (tab == AppTab.Training) trainingFocusWordId else null,
                    com.quransunah.app.ui.mushaf.LocalTrainingFirstWordHint provides (tab == AppTab.Training && firstWordHint),
                ) {
                HorizontalPager(
                        state = pagerState,
                        reverseLayout = false,
                        beyondViewportPageCount = if (tab == AppTab.Reading && meaningsMode) 1 else 0,
                        key = { it + 1 },
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(
                                bottom = if (tab == AppTab.Training) {
                                    trainingPageBottomInset(
                                        phase = recite.phase,
                                        resultsOpen = resultsOpen,
                                        singleAyahOpen = singleAyahMode,
                                    )
                                } else {
                                    ChromeTokens.MushafNavReserve
                                },
                            )
                            .clipToBounds(),
                    ) { index ->
                        CompositionLocalProvider(LocalLayoutDirection provides pageContentDirection) {
                        val pageNo = index + 1
                        LaunchedEffect(pageNo) {
                            viewModel.ensurePageLoaded(pageNo)
                        }
                        val pageData by remember(pageNo) {
                            viewModel.pageCache
                                .map { cache -> cache[pageNo] }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = cachedPage(viewModel, pageNo))
                        val lineRecords = remember(pageData) { pageData?.toLineRecords().orEmpty() }
                        val pageHighlightWord = highlight.wordId
                        val pageHighlightAyah = highlight.ayah?.let { it.surah to it.ayah }
                        val showMeanings = tab == AppTab.Reading && meaningsMode
                        Box(Modifier.fillMaxSize()) {
                        // Mounted on this pager page; the global toolbar covers it without moving the text.
                        if (tab == AppTab.Reading) {
                            PageHeaderBar(
                                page = pageData, pageNumber = pageNo, fontManager = viewModel.fontManager,
                                night = settings.nightMode, informationOnly = true,
                                hizbLabel = com.quransunah.app.ui.mushaf.localizedHeaderHizb(pageData, headerQuarters),
                                surahNames = surahsByNumber.mapValues { it.value.nameArabic },
                                onSearch = {}, onThemeToggle = {},
                                onOpenSurahIndex = {}, onOpenJuzIndex = {}, onOpenBookmarks = {},
                                modifier = Modifier.align(Alignment.TopCenter),
                            )
                        }
                        Box(Modifier.fillMaxSize().padding(top = if (tab == AppTab.Training) trainingPageTopInset() else ChromeTokens.MushafHeaderReserve)) {
                        if (showMeanings) {
                            val translationTag = settings.translationLanguage.orEmpty()
                            LaunchedEffect(pageData, translationTag) {
                                val loaded = pageData ?: return@LaunchedEffect
                                if (translationTag.isNotBlank()) {
                                    viewModel.loadPageMeanings(loaded, translationTag)
                                }
                            }
                            MeaningsPage(
                                rows = meaningsByPage[pageNo].orEmpty(),
                                pageNumber = pageNo,
                                translationTag = translationTag,
                                fontManager = viewModel.fontManager,
                                surahsByNumber = surahsByNumber,
                                onChangeTranslation = { translationPickerOpen = true },
                                onToggleChrome = { viewModel.toggleChrome() },
                                selected = pagerState.settledPage == index,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else if (settings.medinaMode) {
                            MedinaCanvasPage(
                                tajweedEnabled = tajweedEnabled,
                                pageNumber = pageData?.pageNumber ?: pageNo,
                                lines = lineRecords,
                                fontManager = viewModel.fontManager,
                                glyphColor = if (settings.nightMode) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    androidx.compose.ui.graphics.Color.Black
                                },
                                highlightColor = paper.ayahHighlight,
                                highlightWordId = if (playbackCursor != null) null else pageHighlightWord,
                                highlightAyah = if (playbackCursor != null) null else pageHighlightAyah,
                                hideAyahText = tab == AppTab.Training && hideTrainingWords,
                                revealedAyahs = trainingVisibleAyahs,
                                chromeVisible = chrome && tab != AppTab.Training,
                                surahsByNumber = surahsByNumber,
                                quarter = pageData?.quarter,
                                sajda = pageData?.sajda,
                                onWordTap = { word ->
                                    if (tab == AppTab.Training) {
                                        val next = com.quransunah.app.ui.mushaf.toggleTrainingAyah(
                                            com.quransunah.app.ui.mushaf.TrainingRevealState(
                                                selected = selectedTrainingAyah,
                                                revealedAyahs = revealedTrainingAyahs,
                                                revealedWordIds = revealedTrainingWordIds,
                                            ),
                                            word.surah to word.ayah,
                                            trainingWords,
                                        )
                                        selectedTrainingAyah = next.selected
                                        revealedTrainingAyahs = next.revealedAyahs
                                        revealedTrainingWordIds = next.revealedWordIds
                                        trainingHistoryViewModel.noteVerse(word.surah, word.ayah, pageNo)
                                    } else viewModel.onMushafWordTap(word)
                                },
                                onWordLongPress = { word -> viewModel.onWordLongPress(word) },
                                onEmptyTap = { if (tab != AppTab.Training) viewModel.toggleChrome() },
                                onSurahNameLongPress = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
                                pageFit = if (tab == AppTab.Training) MedinaPageFit.FitScreen else MedinaPageFit.Fill,
                                modifier = Modifier.fillMaxSize().clipToBounds(),
                            )
                        } else {
                            LiveTextMushafPage(
                                viewModel = viewModel,
                                pageNumber = pageData?.pageNumber ?: pageNo,
                                lines = lineRecords,
                                glyphColor = if (settings.nightMode) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    androidx.compose.ui.graphics.Color.Black
                                },
                                highlightColor = paper.ayahHighlight,
                                highlightWordId = pageHighlightWord,
                                highlightAyah = pageHighlightAyah,
                                hideAyahText = tab == AppTab.Training && hideTrainingWords,
                                revealedAyahs = trainingVisibleAyahs,
                                onRevealTrainingText = if (tab == AppTab.Training) { { word ->
                                    val next = com.quransunah.app.ui.mushaf.toggleTrainingAyah(
                                        com.quransunah.app.ui.mushaf.TrainingRevealState(
                                            selected = selectedTrainingAyah,
                                            revealedAyahs = revealedTrainingAyahs,
                                            revealedWordIds = revealedTrainingWordIds,
                                        ),
                                        word.surah to word.ayah,
                                        trainingWords,
                                    )
                                    selectedTrainingAyah = next.selected
                                    revealedTrainingAyahs = next.revealedAyahs
                                    revealedTrainingWordIds = next.revealedWordIds
                                    trainingHistoryViewModel.noteVerse(word.surah, word.ayah, pageNo)
                                } } else null,
                                chromeVisible = chrome && tab != AppTab.Training,
                                surahsByNumber = surahsByNumber,
                                quarter = pageData?.quarter,
                                sajda = pageData?.sajda,
                            )
                        }
                        }
                        }
                        }
                }
                }
            }
            AppTab.Listening -> ListeningScreen(
                nightMode = settings.nightMode,
                onThemeToggle = { viewModel.setNightMode(!settings.nightMode) },
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(bottom = 62.dp),
            )
        }

        if (chrome && tab == AppTab.Reading) {
            PageHeaderBar(
                tajweedEnabled = tajweedEnabled,
                onToggleTajweed = { tajweedEnabled = !tajweedEnabled },
                onTajweedGuide = { tajweedGuideOpen = true },
                page = page,
                pageNumber = pageNumber,
                fontManager = viewModel.fontManager,
                night = settings.nightMode,
                onSearch = viewModel::openSearch,
                onThemeToggle = { viewModel.setNightMode(!settings.nightMode) },
                onOpenSurahIndex = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
                onOpenJuzIndex = { viewModel.openIndexPicker(QuranIndexTab.Juz) },
                onOpenBookmarks = { studyViewModel.saveReadingPage(page, viewModel::openLastPositionPicker) },
                showDivisionLabels = ArabicRtl.currentTag(LocalContext.current) == "ar",
                meaningsSelected = meaningsMode,
                onSelectMeanings = { selectMeanings ->
                    if (!selectMeanings) {
                        meaningsMode = false
                    } else {
                        val stored = settings.translationLanguage
                        if (!stored.isNullOrBlank()) {
                            meaningsMode = true
                        } else {
                            val match = TranslationRegistry.forLanguage(appLanguageTag)
                            if (match != null) {
                                viewModel.setTranslationLanguage(match.languageTag)
                                meaningsMode = true
                            } else {
                                translationPickerOpen = true
                            }
                        }
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                surahNames = remember(surahsByNumber) {
                    surahsByNumber.mapValues { it.value.nameArabic }
                },
            )
        }

        if (tab == AppTab.Training && singleAyahMode) {
            val shownAyah = selectedTrainingAyah ?: selectedPageAyah ?: currentAyahKey
            if (shownAyah != null) TrainingSingleAyahPager(
                ayah = shownAyah,
                loadWords = viewModel::trainingAyahPageWords,
                fontManager = viewModel.fontManager,
                marks = trainingMarks,
                surahs = surahsByNumber,
                reciting = recite.phase == TrainingPhase.During,
                recitationDone = recite.summary != null,
                onAyahChange = { target ->
                    selectedTrainingAyah = target
                    trainingScope.launch { viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(target.first, target.second)) }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(top = 54.dp, bottom = 106.dp),
            )
        }

        if (tajweedGuideOpen) {
            com.quransunah.app.ui.mushaf.TajweedGuideDialog(onClose = { tajweedGuideOpen = false })
        }

        if (restoreBar && (chrome || (tab == AppTab.Training && !resultsOpen && recite.phase != TrainingPhase.During))) {
            ReadingPlaybackBar(
                viewModel = viewModel,
                listeningViewModel = listeningViewModel,
                studyViewModel = studyViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = if (tab == AppTab.Training) 118.dp else 66.dp),
            )
        }

        if (tab == AppTab.Training && recite.phase != TrainingPhase.During) {
            TrainingTopBar(
                onNextWord = {
                    val activeAyah = selectedTrainingAyah?.takeIf { it in currentPageAyahs } ?: trainingCurrentAyah
                    if (activeAyah != null) {
                        val next = com.quransunah.app.ui.mushaf.progressiveTrainingStep(
                            com.quransunah.app.ui.mushaf.TrainingRevealState(
                                selected = selectedTrainingAyah,
                                revealedAyahs = revealedTrainingAyahs,
                                revealedWordIds = revealedTrainingWordIds,
                            ),
                            activeAyah,
                            trainingWords,
                            firstWordHint,
                        )
                        if (next != null) {
                            selectedTrainingAyah = next.selected
                            revealedTrainingWordIds = next.revealedWordIds
                        } else {
                            SurahAyahCounts.next(activeAyah.first, activeAyah.second)?.let { target ->
                                selectedTrainingAyah = target
                                pendingTrainingWordAyah = target
                                trainingScope.launch { viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(target.first, target.second)) }
                            }
                        }
                    }
                },
                onHint = { firstWordHint = !firstWordHint },
                onRevealAll = {
                    trainingTextHidden = false
                    revealedTrainingAyahs = revealedTrainingAyahs + pageAyahs
                },
                onHideAll = {
                    trainingTextHidden = true
                    firstWordHint = false
                    revealedTrainingAyahs = emptySet()
                    revealedTrainingWordIds = emptySet()
                },
                onShowAyah = {
                    if (!singleAyahMode) {
                        singleAyahOriginPage = pageNumber
                        singleAyahOrigin = selectedTrainingAyah?.takeIf { it in currentPageAyahs } ?: trainingCurrentAyah
                        selectedTrainingAyah = singleAyahOrigin
                        singleAyahMode = true
                    }
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else if (tab == AppTab.Reading) {
            PageNumberBadge(
                pageNumber = pageNumber,
                onClick = { pagePickerOpen = true },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp),
            )
        }

        if (tab == AppTab.Training) {
            val studyUi by studyViewModel.uiState.collectAsStateWithLifecycle()
            val beginRecite: () -> Unit = {
                if (trainingListenJob != null) stopTrainingListen()
                if (!trainingStartPending && recite.phase != TrainingPhase.During) {
                    trainingStartPending = true
                    trainingScope.launch {
                        val start = trainingCurrentAyah ?: (1 to 1)
                        // «عرض الآية»: recite only the ayah on screen and stay in the view.
                        val viewAyah = if (singleAyahMode) selectedTrainingAyah ?: selectedPageAyah ?: currentAyahKey else null
                        val sourceWords = if (viewAyah != null) {
                            viewModel.trainingBodyWords(viewAyah.first, viewAyah.second)
                        } else when (recite.scope) {
                            TrainingReciteScope.Open -> viewModel.trainingWordsFromAyah(start.first, start.second)
                            TrainingReciteScope.Page -> pageExpected.map {
                                com.quransunah.app.domain.model.QuranWord(
                                    id = it.wordId, surah = it.surah, ayah = it.ayah, wordIndex = it.position,
                                    textLigature = "", textHafs = it.text,
                                )
                            }
                            TrainingReciteScope.Range -> {
                                val from = rangeFrom
                                val to = rangeTo
                                if (from == null || to == null || !trainingRangeValid(from.first, from.second, to.first, to.second)) {
                                    emptyList()
                                } else viewModel.trainingWordsBetweenAyahs(from, to)
                            }
                        }
                        val expected = sourceWords.filterNot { it.isAyahMarker }.map { word ->
                            TrainingExpectedWord(word.id, word.surah, word.ayah, word.wordIndex, word.textHafs)
                        }
                        trainingStartPending = false
                        if (expected.isNotEmpty()) {
                            trainingTextHidden = true
                            firstWordHint = false
                            revealedTrainingAyahs = emptySet()
                            revealedTrainingWordIds = emptySet()
                            if (viewAyah == null) singleAyahMode = false
                            recitationViewModel.arm(
                                expected,
                                stopAtCompletion = viewAyah != null || recite.scope != TrainingReciteScope.Open,
                            )
                            requestMic {
                                // free the audio focus/mic path before recording (public player API only)
                                if (viewModel.playback.value.isPlaying) viewModel.audioPlayer.pause()
                                recitationViewModel.start(settings.recitationEngine)
                            }
                        }
                    }
                }
            }
            val jumpToIssue: (com.quransunah.app.ui.training.TrainingWordAssessment) -> Unit = { issue ->
                trainingFocusWordId = issue.wordId
                selectedTrainingAyah = issue.surah to issue.ayah
                trainingScope.launch {
                    val target = viewModel.pageContainingWord(issue.surah, issue.ayah, issue.wordId)
                    viewModel.showAyahKeepingTab(target)
                }
            }
            val playTrainingAyah = {
                val key = trainingListenAyah ?: selectedPageAyah
                if (trainingListenJob != null) {
                    stopTrainingListen()
                } else if (key != null) {
                    val (surah, ayah) = key
                    val next = com.quransunah.app.ui.mushaf.revealPlayingTrainingAyah(
                        com.quransunah.app.ui.mushaf.TrainingRevealState(
                            selected = selectedTrainingAyah,
                            revealedAyahs = revealedTrainingAyahs,
                            revealedWordIds = revealedTrainingWordIds,
                        ),
                        surah to ayah,
                    )
                    selectedTrainingAyah = next.selected
                    revealedTrainingAyahs = next.revealedAyahs
                    trainingHistoryViewModel.noteVerse(surah, ayah, pageNumber)
                    val reciterId = studyUi.selectedReciterId
                    trainingListenJob?.cancel()
                    // Continuous «استماع»: play to the end of the surah, then carry on into the next one (stops after An-Nas).
                    trainingListenJob = trainingScope.launch {
                        studyViewModel.audioPlayer.setRepeatMode(SurahRepeatMode.OFF)
                        studyViewModel.audioPlayer.setPlaybackRate(settings.playbackRate)
                        var from: Pair<Int, Int>? = surah to ayah
                        while (from != null) {
                            val (s, a) = from
                            studyViewModel.audioPlayer.playAyahRange(reciterId, s, a, s, SurahAyahCounts.ayahCount(s))
                            withTimeoutOrNull(20_000) {
                                viewModel.playback.first { snap -> snap.isPlaying || !snap.errorMessage.isNullOrBlank() }
                            }
                            if (!viewModel.playback.value.errorMessage.isNullOrBlank()) break
                            var last: Pair<Int, Int>? = null
                            while (true) {
                                val snap = viewModel.playback.first { snap ->
                                    val ay = snap.ayah
                                    if (snap.surah != null && ay != null && ay > 0) last = snap.surah to ay
                                    !snap.isPlaying && !snap.isBuffering
                                }
                                kotlinx.coroutines.delay(700)
                                val now = viewModel.playback.value
                                if (!now.isPlaying && !now.isBuffering) break
                            }
                            from = last?.let { trainingContinuousNext(it.first, it.second) }
                        }
                        trainingListenJob = null
                    }
                }
            }
            when {
                recite.phase == TrainingPhase.During -> TrainingRecordingStrip(
                    elapsedMs = recite.elapsedMs,
                    onStop = recitationViewModel::stop,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                resultsOpen && shownSummary != null -> TrainingResultsPanel(
                    summary = shownSummary,
                    onRetake = beginRecite,
                    onReviewErrors = {
                        val first = shownSummary.issues.firstOrNull()
                        if (first != null) {
                            recitationViewModel.collapseResults()
                            jumpToIssue(first)
                        }
                    },
                    onClose = recitationViewModel::collapseResults,
                    onContinue = {
                        val completedAyah = recite.completedAyahs.lastOrNull() ?: selectedPageAyah
                        recitationViewModel.collapseResults()
                        if (completedAyah != null) {
                            val next = SurahAyahCounts.next(completedAyah.first, completedAyah.second)
                            val canAdvance = recite.scope != TrainingReciteScope.Range ||
                                (next != null && rangeTo?.let { SurahAyahCounts.ayahId(next.first, next.second) <= SurahAyahCounts.ayahId(it.first, it.second) } == true)
                            if (recite.scope == TrainingReciteScope.Page) {
                                selectedTrainingAyah = null
                                trainingTextHidden = true
                                trainingScope.launch {
                                    viewModel.showAyahKeepingTab((pageNumber + 1).coerceAtMost(AppConstants.TOTAL_PAGES))
                                }
                            } else if (next != null && canAdvance) {
                                selectedTrainingAyah = next
                                if (recite.scope == TrainingReciteScope.Range) rangeFrom = next
                                trainingTextHidden = true
                                trainingScope.launch {
                                    viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(next.first, next.second))
                                }
                            }
                        }
                    },
                    // In «عرض الآية» sit above both bottom slots (ayah navigation + bottom bar) so nothing covers it.
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .then(if (singleAyahMode) Modifier.padding(bottom = BottomNavBarHeight * 2 + 8.dp) else Modifier),
                )
                else -> TrainingBottomBar(
                    engine = settings.recitationEngine,
                    onStart = beginRecite,
                    onEngine = { trainingEngineOpen = true },
                    onListen = playTrainingAyah,
                    onSelect = { trainingSelectOpen = true },
                    onClose = {
                        if (trainingListenJob != null) stopTrainingListen()
                        recitationViewModel.abandon()
                        singleAyahMode = false
                        viewModel.selectTab(AppTab.Reading)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
        if (tab == AppTab.Training && singleAyahMode) {
            TrainingSingleAyahNavigation(
                onPrevious = {
                    val active = selectedTrainingAyah ?: selectedPageAyah ?: currentAyahKey ?: return@TrainingSingleAyahNavigation
                    SurahAyahCounts.previous(active.first, active.second)?.let { target ->
                        selectedTrainingAyah = target
                        trainingScope.launch { viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(target.first, target.second)) }
                    }
                },
                onNext = {
                    val active = selectedTrainingAyah ?: selectedPageAyah ?: currentAyahKey ?: return@TrainingSingleAyahNavigation
                    SurahAyahCounts.next(active.first, active.second)?.let { target ->
                        selectedTrainingAyah = target
                        trainingScope.launch { viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(target.first, target.second)) }
                    }
                },
                onClose = {
                    singleAyahMode = false
                    singleAyahOrigin?.let { selectedTrainingAyah = it }
                    val originPage = singleAyahOriginPage
                    if (originPage != null && originPage != pageNumber) viewModel.showAyahKeepingTab(originPage)
                    singleAyahOriginPage = null
                    singleAyahOrigin = null
                },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = BottomNavBarHeight + 4.dp),
            )
        }
        if (tab == AppTab.Training && trainingSurahPickerOpen) {
            // Same surah list as «الفهرس»; picking a surah moves the training page there without leaving training.
            IndexModal(onClose = { trainingSurahPickerOpen = false }) {
                com.quransunah.app.ui.index.QuranIndexPicker(
                    initialTab = com.quransunah.app.ui.index.QuranIndexTab.Surah,
                    pageNumber = pageNumber,
                    currentJuz = page?.juzNumber ?: 1,
                    currentHizb = page?.hizbNumber ?: 1,
                    currentSurah = page?.primarySurahNumber() ?: 1,
                    fontManager = viewModel.fontManager,
                    onClose = { trainingSurahPickerOpen = false },
                    onSelectPage = { jump ->
                        trainingSurahPickerOpen = false
                        selectedTrainingAyah = if (jump.hasAyah) jump.surah to jump.ayah else null
                        viewModel.showAyahKeepingTab(jump.pageNumber)
                    },
                    viewModel = indexViewModel,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
        if (tab == AppTab.Training && trainingSelectOpen) {
            TrainingSelectionDialog(
                scope = recite.scope,
                currentSurah = selectedTrainingAyah?.takeIf { it in currentPageAyahs }?.first ?: trainingCurrentAyah?.first ?: currentSurahNumber,
                currentAyah = selectedTrainingAyah?.takeIf { it in currentPageAyahs }?.second ?: trainingCurrentAyah?.second ?: currentAyah,
                currentPage = pageNumber,
                totalPages = AppConstants.TOTAL_PAGES,
                totalSurahs = AppConstants.SURAH_COUNT,
                onNavigatePage = { target ->
                    trainingSelectOpen = false
                    selectedTrainingAyah = null
                    viewModel.showAyahKeepingTab(target)
                },
                currentSurahName = surahsByNumber[selectedTrainingAyah?.takeIf { it in currentPageAyahs }?.first ?: trainingCurrentAyah?.first ?: currentSurahNumber]?.nameArabic.orEmpty(),
                onPickSurah = {
                    trainingSelectOpen = false
                    trainingSurahPickerOpen = true
                },
                onNavigateSurah = { target ->
                    trainingSelectOpen = false
                    selectedTrainingAyah = target to 1
                    trainingScope.launch {
                        val targetPage = surahsByNumber[target]?.startPage ?: viewModel.trainingAyahPage(target, 1)
                        viewModel.showAyahKeepingTab(targetPage)
                    }
                },
                onSelectScope = { selected ->
                    trainingSelectOpen = false
                    recitationViewModel.setScope(selected)
                },
                onApplyRange = { fromText, toText ->
                    val defaultSurah = trainingCurrentAyah?.first ?: currentSurahNumber
                    val from = parseTrainingAyahReference(fromText, defaultSurah)
                    val to = parseTrainingAyahReference(toText, defaultSurah)
                    if (from == null || to == null || !trainingRangeValid(from.first, from.second, to.first, to.second)) {
                        false
                    } else {
                        rangeFrom = from
                        rangeTo = to
                        selectedTrainingAyah = from
                        recitationViewModel.setScope(TrainingReciteScope.Range)
                        trainingSelectOpen = false
                        trainingScope.launch { viewModel.showAyahKeepingTab(viewModel.trainingAyahPage(from.first, from.second)) }
                        true
                    }
                },
                onDismiss = { trainingSelectOpen = false },
            )
        }
        if (tab == AppTab.Training && trainingEngineOpen) {
            TrainingEngineDialog(
                selected = settings.recitationEngine,
                available = recitationViewModel::engineAvailable,
                onSelect = { engine ->
                    recitationViewModel.setEngine(engine)
                    trainingEngineOpen = false
                },
                onDismiss = { trainingEngineOpen = false },
            )
        }
        AnimatedVisibility(
            visible = navVisible,
            enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { it / 3 },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            CurvedBottomNav(
                activeTab = tab,
                onTabChange = viewModel::selectTab,
                onIndexPress = viewModel::openIndexPicker,
                onTrainingPress = { viewModel.selectTab(AppTab.Training) },
                onSettingsPress = viewModel::openSettingsPicker,
            )
        }

        if (tab == AppTab.Reading && navVisible && !settings.readingHintDone) {
            ReadingWordHint(
                onDismiss = viewModel::markReadingHintDone,
                modifier = Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 8.dp, end = 8.dp, bottom = BottomNavBarHeight + 8.dp),
            )
        }
        if (overlay == StudyOverlay.Meaning && selectedWord != null) {
            MeaningPopover(word = selectedWord!!) { viewModel.closeStudyOverlays() }
        }
        if (overlay == StudyOverlay.WordSheet && selectedWord != null) {
            WordSheet(
                word = selectedWord!!,
                onDismiss = viewModel::closeStudyOverlays,
                onStepAyah = viewModel::openWordSheet,
                onAyahPlaybackStarted = viewModel::startFollowingPlayback,
                translationTag = if (meaningsMode) settings.translationLanguage else null,
                viewModel = studyViewModel,
            )
        }
        if (searchOpen) {
            val searchSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = viewModel::closeSearch,
                sheetState = searchSheet,
                containerColor = paper.pageBackground,
                contentColor = paper.textStrong,
            ) {
                SearchPane(
                    onSelectHit = viewModel::jumpToSearchHit,
                    showTitle = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .height(560.dp),
                )
            }
        }

        if (translationPickerOpen) {
            TranslationLanguageDialog(
                selectedTag = settings.translationLanguage,
                onSelect = { tag ->
                    viewModel.setTranslationLanguage(tag)
                    meaningsMode = true
                    translationPickerOpen = false
                },
                onDismiss = { translationPickerOpen = false },
            )
        }

        if (pagePickerOpen) {
            PagePickerDialog(
                pageNumber = pageNumber,
                onDismiss = { pagePickerOpen = false },
                onJump = viewModel::jumpToPageNumber,
            )
        }

        ShellPickerHost(
            picker = picker,
            indexTab = indexTab,
            pageNumber = pageNumber,
            currentJuz = page?.juzNumber ?: 1,
            currentHizb = page?.hizbNumber ?: 1,
            currentSurah = page?.primarySurahNumber() ?: 1,
            fontManager = viewModel.fontManager,
            preferredAyah = preferredAyah,
            indexViewModel = indexViewModel,
            onClose = viewModel::closePicker,
            onSelectIndexPage = viewModel::jumpToIndexPage,
            onJumpBookmark = { bookmark ->
                if (tab == AppTab.Training && picker == ShellPicker.Progress) {
                    viewModel.closePicker()
                    resumeTrainingAyah = bookmark.surah to bookmark.ayah
                    viewModel.showAyahKeepingTab(bookmark.pageNumber)
                } else {
                    viewModel.jumpToBookmark(bookmark)
                }
            },
            onStartReading = {
                viewModel.selectTab(AppTab.Reading)
            },
            onResumeTraining = { surah, ayah, page ->
                trainingScope.launch {
                    val resolved = if (page > 0) page else viewModel.trainingAyahPage(surah, ayah)
                    viewModel.closePicker()
                    resumeTrainingAyah = surah to ayah
                    viewModel.showAyahKeepingTab(resolved)
                    trainingHistoryViewModel.rememberPosition(surah, ayah, resolved)
                }
            },
            onOpenUserGuide = {
                viewModel.closePicker()
                manualGuide = true
            },
        )

        if (showGuide) {
            OnboardingScreen(
                onComplete = {
                    viewModel.completeOnboarding()
                    manualGuide = false
                    autoGuideDismissed = true
                },
                onDismiss = {
                    manualGuide = false
                    autoGuideDismissed = true
                },
            )
        }
    }
}

@Composable
private fun ReadingWordHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val paper = LocalPaperColors.current
    val direction = LocalLayoutDirection.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = modifier.fillMaxWidth()
                .background(androidx.compose.ui.graphics.lerp(paper.surface, paper.chromeFill, 0.15f), GoldCornerFrameShape(12.dp))
                .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, GoldCornerFrameShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.material3.IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Box(Modifier.size(26.dp).background(paper.chromeFill, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_close), stringResource(R.string.close),
                        tint = paper.textStrong, modifier = Modifier.size(17.dp))
                }
            }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Text(
                    text = stringResource(R.string.reading_word_hint),
                    color = paper.textStrong, fontSize = 13.sp, lineHeight = 18.sp,
                    textAlign = TextAlign.Start,
                    style = TextStyle(textDirection = androidx.compose.ui.text.style.TextDirection.Content),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ReadingPlaybackBar(
    viewModel: HolyQuranViewModel,
    listeningViewModel: ListeningViewModel,
    studyViewModel: StudyViewModel,
    modifier: Modifier = Modifier,
) {
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    if (playback.domain != PlaybackDomain.SURAH && playback.domain != PlaybackDomain.AYAH) return
    val listeningUi by listeningViewModel.uiState.collectAsStateWithLifecycle()
    val studyUi by studyViewModel.uiState.collectAsStateWithLifecycle()
    val languageTag = ArabicRtl.currentTag(LocalContext.current)
    val reciterChoices = when (playback.domain) {
        PlaybackDomain.SURAH -> listeningUi.reciters.map { reciter ->
            CompactReciterChoice(
                reciter.id.toString(),
                ReciterNames.display(languageTag, reciter.id, reciter.name),
            )
        }
        PlaybackDomain.AYAH -> studyUi.reciters.map { reciter ->
            CompactReciterChoice(
                reciter.id,
                ReciterNames.display(languageTag, reciter.id, reciter.name),
            )
        }
        else -> emptyList()
    }
    CompactPlaybackBar(
        snapshot = playback,
        fontManager = viewModel.fontManager,
        reciters = reciterChoices,
        selectedReciterId = playback.reciterId,
        onPlayPause = {
            if (playback.isPlaying) viewModel.audioPlayer.pause() else viewModel.audioPlayer.resume()
        },
        onPrevious = { viewModel.skipPlaybackVerse(-1) },
        onNext = { viewModel.skipPlaybackVerse(1) },
        onStop = { viewModel.audioPlayer.stop() },
        onRestore = { viewModel.selectTab(AppTab.Listening) },
        onSelectReciter = { reciterId ->
            when (playback.domain) {
                PlaybackDomain.SURAH -> {
                    listeningUi.reciters.firstOrNull { it.id.toString() == reciterId }
                        ?.let(listeningViewModel::switchSurahReciterAndPlay)
                }
                PlaybackDomain.AYAH -> studyViewModel.switchAyahReciterAndPlay(reciterId)
                else -> Unit
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun LiveTextMushafPage(
    viewModel: HolyQuranViewModel,
    pageNumber: Int,
    lines: List<LineRecord>,
    glyphColor: androidx.compose.ui.graphics.Color,
    highlightColor: androidx.compose.ui.graphics.Color,
    highlightWordId: Int?,
    highlightAyah: Pair<Int, Int>?,
    hideAyahText: Boolean,
    revealedAyahs: Set<Pair<Int, Int>>,
    onRevealTrainingText: ((com.quransunah.app.ui.mushaf.WordRecord) -> Unit)?,
    chromeVisible: Boolean,
    surahsByNumber: Map<Int, SurahInfo>,
    quarter: QuarterMarker?,
    sajda: SajdaMarker?,
) {
    val fontSizeSp by viewModel.textSizeSp.collectAsStateWithLifecycle()
    TextMushafPage(
        pageNumber = pageNumber,
        lines = lines,
        fontManager = viewModel.fontManager,
        fontSizeSp = fontSizeSp,
        glyphColor = glyphColor,
        highlightColor = highlightColor,
        highlightWordId = highlightWordId,
        highlightAyah = highlightAyah,
        hideAyahText = hideAyahText,
        revealedAyahs = revealedAyahs,
        chromeVisible = chromeVisible,
        surahsByNumber = surahsByNumber,
        quarter = quarter,
        sajda = sajda,
        onWordTap = { word -> onRevealTrainingText?.invoke(word) ?: viewModel.onMushafWordTap(word) },
        onWordLongPress = { word -> viewModel.onWordLongPress(word) },
        onEmptyTap = { viewModel.toggleChrome() },
        onSurahNameLongPress = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    )
}

private fun cachedPage(viewModel: HolyQuranViewModel, pageNo: Int) =
    viewModel.pageCache.value[pageNo]


