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
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
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
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.data.catalog.TranslationRegistry
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.PlaybackDomain
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
import com.quransunah.app.ui.study.WordSheet
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

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
    var trainingNotice by remember { mutableStateOf<String?>(null) }
    var trainingMoreOpen by remember { mutableStateOf(false) }
    var resumeTrainingAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val listeningViewModel: ListeningViewModel = hiltViewModel()
    val studyViewModel: StudyViewModel = hiltViewModel()
    val memorizationViewModel: com.quransunah.app.ui.memorization.MemorizationViewModel = hiltViewModel()
    val trainingHistoryViewModel: com.quransunah.app.ui.training.TrainingHistoryViewModel = hiltViewModel()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val indexViewModel: QuranIndexViewModel = hiltViewModel()
    val preferredAyah = jumpHighlight ?: selectedWord?.let { AyahRef(it.surah, it.ayah) }
    val currentLocationWord = remember(page) {
        page?.lines
            ?.asSequence()
            ?.flatMap { it.words.asSequence() }
            ?.firstOrNull { !it.isAyahMarker }
    }
    val currentSurahNumber = currentLocationWord?.surah ?: 1
    val currentSurahName = currentLocationWord?.let { word ->
        surahsByNumber[word.surah]?.nameArabic
            ?: stringResource(R.string.surah_fallback, EasternArabic.format(word.surah))
    } ?: stringResource(R.string.surah_fallback, EasternArabic.format(1))
    val currentAyah = currentLocationWord?.ayah ?: 1
    val currentPageAyahs = remember(page) {
        page?.lines.orEmpty()
            .flatMap { it.words }
            .filterNot { it.isAyahMarker }
            .map { it.surah to it.ayah }
            .toSet()
    }
    val allTrainingAyahsRevealed = currentPageAyahs.isNotEmpty() &&
        currentPageAyahs.all { it in revealedTrainingAyahs }
    val anyTrainingAyahRevealed = revealedTrainingAyahs.isNotEmpty()
    val trainingWords = remember(page) {
        page?.lines.orEmpty()
            .flatMap { it.words }
            .filterNot { it.isAyahMarker }
            .map { com.quransunah.app.ui.mushaf.TrainingWordRef(it.id, it.surah, it.ayah, it.wordIndex) }
    }
    val trainingListenAyah = selectedTrainingAyah ?: currentLocationWord?.let { it.surah to it.ayah }
    val trainingScope = rememberCoroutineScope()
    val savedMastered = stringResource(R.string.training_saved_mastered)
    val savedReview = stringResource(R.string.training_saved_review)
    val noTrainingPosition = stringResource(R.string.training_no_position)
    var notedPlaybackAyah by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var trainingWasOpen by remember { mutableStateOf(false) }
    val trainingPlaybackOpen = tab == AppTab.Training &&
        (playback.domain == PlaybackDomain.AYAH || playback.domain == PlaybackDomain.SURAH)
    LaunchedEffect(page, preferredAyah) {
        bookmarksViewModel.bindContext(page, preferredAyah)
    }
    LaunchedEffect(tab) {
        if (trainingWasOpen && tab != AppTab.Training) {
            trainingHistoryViewModel.finishSession()
            notedPlaybackAyah = null
        }
        trainingWasOpen = tab == AppTab.Training
        trainingTextHidden = true
        firstWordHint = false
        revealedTrainingAyahs = emptySet()
        revealedTrainingWordIds = emptySet()
        if (tab != AppTab.Training) {
            selectedTrainingAyah = null
            trainingNotice = null
            trainingMoreOpen = false
        }
    }
    LaunchedEffect(trainingNotice) {
        if (trainingNotice != null) {
            delay(1600)
            trainingNotice = null
        }
    }
    LaunchedEffect(tab, pageNumber, currentPageAyahs, playback.domain, playback.surah, playback.ayah, playback.isPlaying) {
        if (tab != AppTab.Training || !playback.isPlaying) return@LaunchedEffect
        if (playback.domain != PlaybackDomain.AYAH && playback.domain != PlaybackDomain.SURAH) return@LaunchedEffect
        val surah = playback.surah ?: return@LaunchedEffect
        val ayah = playback.ayah?.takeIf { it > 0 } ?: return@LaunchedEffect
        val key = surah to ayah
        if (key !in currentPageAyahs) return@LaunchedEffect
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
    val restoreBar = tab == AppTab.Reading
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
                modifier = Modifier.fillMaxSize(),
            )
            AppTab.Reading, AppTab.Training -> {
                val pageContentDirection = LocalLayoutDirection.current
                val meaningsPagerDirection = if (tab == AppTab.Reading && meaningsMode) {
                    uiLayoutDirection()
                } else {
                    pageContentDirection
                }
                CompositionLocalProvider(
                    LocalLayoutDirection provides meaningsPagerDirection,
                    com.quransunah.app.ui.mushaf.LocalTrainingRevealedWordIds provides revealedTrainingWordIds,
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
                                top = ChromeTokens.MushafHeaderReserve,
                                bottom = ChromeTokens.MushafNavReserve +
                                    if (trainingPlaybackOpen) 60.dp else 0.dp,
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
                        val showMeanings = tab == AppTab.Reading && meaningsMode
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
                                pageNumber = pageData?.pageNumber ?: pageNo,
                                lines = lineRecords,
                                fontManager = viewModel.fontManager,
                                glyphColor = if (settings.nightMode) {
                                    androidx.compose.ui.graphics.Color.White
                                } else {
                                    androidx.compose.ui.graphics.Color.Black
                                },
                                highlightColor = paper.ayahHighlight,
                                highlightWordId = highlight.wordId,
                                highlightAyah = highlight.ayah?.let { it.surah to it.ayah },
                                hideAyahText = tab == AppTab.Training && trainingTextHidden,
                                revealedAyahs = revealedTrainingAyahs,
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
                                highlightWordId = highlight.wordId,
                                highlightAyah = highlight.ayah?.let { it.surah to it.ayah },
                                hideAyahText = tab == AppTab.Training && trainingTextHidden,
                                revealedAyahs = revealedTrainingAyahs,
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

        AnimatedVisibility(
            visible = chrome && tab == AppTab.Reading,
            enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                slideInVertically(tween(320, easing = FastOutSlowInEasing)) { -it / 3 },
            exit = fadeOut(tween(280, easing = FastOutSlowInEasing)) +
                slideOutVertically(tween(280, easing = FastOutSlowInEasing)) { -it / 3 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            PageHeaderBar(
                page = page,
                pageNumber = pageNumber,
                fontManager = viewModel.fontManager,
                night = settings.nightMode,
                onSearch = viewModel::openSearch,
                onThemeToggle = { viewModel.setNightMode(!settings.nightMode) },
                onOpenSurahIndex = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
                onOpenJuzIndex = { viewModel.openIndexPicker(QuranIndexTab.Juz) },
                onOpenBookmarks = viewModel::openLastPositionPicker,
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
                modifier = Modifier.statusBarsPadding(),
                surahNames = remember(surahsByNumber) {
                    surahsByNumber.mapValues { it.value.nameArabic }
                },
            )
        }

        if ((restoreBar && chrome) || tab == AppTab.Training) {
            ReadingPlaybackBar(
                viewModel = viewModel,
                listeningViewModel = listeningViewModel,
                studyViewModel = studyViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        start = 12.dp,
                        end = 12.dp,
                        bottom = if (tab == AppTab.Training) TrainingBottomBarHeight + 8.dp else 66.dp,
                    ),
            )
        }

        if (tab == AppTab.Training) {
            TrainingTopBar(
                onNextWord = {
                    val snapshot = com.quransunah.app.ui.mushaf.TrainingRevealState(
                        selected = selectedTrainingAyah,
                        revealedAyahs = revealedTrainingAyahs,
                        revealedWordIds = revealedTrainingWordIds,
                    )
                    val start = selectedTrainingAyah
                        ?: trainingWords.firstOrNull()?.let { it.surah to it.ayah }
                        ?: return@TrainingTopBar
                    trainingScope.launch {
                        var ayah: Pair<Int, Int>? = start
                        var state = snapshot
                        var guard = 0
                        while (ayah != null && guard < 6236) {
                            guard += 1
                            val words = viewModel.trainingBodyWords(ayah.first, ayah.second).map { word ->
                                com.quransunah.app.ui.mushaf.TrainingWordRef(
                                    word.id,
                                    word.surah,
                                    word.ayah,
                                    word.wordIndex,
                                )
                            }
                            val step = com.quransunah.app.ui.mushaf.progressiveTrainingStep(state, ayah, words, firstWordHint)
                            if (step != null) {
                                val revealedId = (step.revealedWordIds - state.revealedWordIds).first()
                                selectedTrainingAyah = step.selected
                                revealedTrainingWordIds = step.revealedWordIds
                                val targetPage = viewModel.pageContainingWord(ayah.first, ayah.second, revealedId)
                                if (targetPage != pageNumber) viewModel.showAyahKeepingTab(targetPage)
                                step.selected?.let { selected ->
                                    trainingHistoryViewModel.noteVerse(selected.first, selected.second, targetPage)
                                }
                                return@launch
                            }
                            ayah = com.quransunah.app.core.SurahAyahCounts.next(ayah.first, ayah.second)
                        }
                    }
                },
                onShowAll = {
                    firstWordHint = false
                    revealedTrainingAyahs = revealedTrainingAyahs + currentPageAyahs
                },
                onHideAll = {
                    firstWordHint = false
                    trainingTextHidden = true
                    val pageWordIds = trainingWords.map { it.id }.toSet()
                    revealedTrainingAyahs = revealedTrainingAyahs - currentPageAyahs
                    revealedTrainingWordIds = revealedTrainingWordIds - pageWordIds
                },
                showAllEnabled = !allTrainingAyahsRevealed,
                hideAllEnabled = firstWordHint || !trainingTextHidden || currentPageAyahs.any { it in revealedTrainingAyahs } ||
                    trainingWords.any { it.id in revealedTrainingWordIds },
                hintActive = firstWordHint,
                onToggleHint = {
                    firstWordHint = !firstWordHint
                    trainingTextHidden = true
                    revealedTrainingAyahs = emptySet()
                    revealedTrainingWordIds = emptySet()
                },
                onMore = { trainingMoreOpen = true },
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
            TrainingBottomBar(
                selectedAyah = selectedTrainingAyah?.second,
                canAssess = selectedTrainingAyah != null,
                notice = trainingNotice,
                onClose = {
                    revealedTrainingAyahs = emptySet()
                    revealedTrainingWordIds = emptySet()
                    selectedTrainingAyah = null
                    trainingTextHidden = true
                    viewModel.selectTab(AppTab.Reading)
                },
                onListen = {
                    trainingListenAyah?.let { (surah, ayah) ->
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
                        val reciterId = studyViewModel.uiState.value.selectedReciterId
                        trainingScope.launch {
                            studyViewModel.audioPlayer.playAyah(reciterId, surah, ayah)
                        }
                    }
                },
                onNeedsReview = {
                    val selected = selectedTrainingAyah ?: return@TrainingBottomBar
                    memorizationViewModel.markTrainingAyahForReview(selected.first, selected.second)
                    trainingHistoryViewModel.noteAssessment(selected.first, selected.second, pageNumber, mastered = false)
                    trainingNotice = savedReview
                },
                onMemorized = {
                    val selected = selectedTrainingAyah ?: return@TrainingBottomBar
                    memorizationViewModel.markTrainingAyahMastered(selected.first, selected.second)
                    trainingHistoryViewModel.noteAssessment(selected.first, selected.second, pageNumber, mastered = true)
                    trainingNotice = savedMastered
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        if (trainingMoreOpen) {
            TrainingMoreDialog(
                onResume = {
                    trainingMoreOpen = false
                    trainingScope.launch {
                        val position = trainingHistoryViewModel.resumeTarget()
                        if (position == null) {
                            trainingNotice = noTrainingPosition
                        } else {
                            resumeTrainingAyah = position.surah to position.ayah
                            viewModel.showAyahKeepingTab(position.pageNumber)
                        }
                    }
                },
                onHistory = {
                    trainingMoreOpen = false
                    viewModel.openTrainingHistory()
                },
                onProgress = {
                    trainingMoreOpen = false
                    viewModel.openProgressPicker()
                },
                onDismiss = { trainingMoreOpen = false },
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
        if (tab == AppTab.Training && !settings.trainingHintDone) {
            TrainingHintOverlay(onDismiss = viewModel::markTrainingHintDone)
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
                .background(androidx.compose.ui.graphics.lerp(paper.surface, paper.chromeFill, 0.15f), RoundedCornerShape(12.dp))
                .border(1.dp, paper.accent, RoundedCornerShape(12.dp))
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
private fun TrainingHintOverlay(onDismiss: () -> Unit) {
    val paper = LocalPaperColors.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color(0x990F172A))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .shadow(18.dp, RoundedCornerShape(22.dp))
                .clip(RoundedCornerShape(22.dp))
                .background(paper.pageBody)
                .clickable(onClick = {})
                .padding(horizontal = 22.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_visibility_off_eye),
                contentDescription = null,
                tint = paper.accent,
                modifier = Modifier.size(48.dp),
            )
            Text(
                text = stringResource(R.string.training_hint_title),
                color = paper.textStrong,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
            Text(
                text = stringResource(R.string.training_hint_subtitle),
                color = paper.textMuted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            TrainingHintStep(1, R.string.training_hint_step_read)
            TrainingHintStep(2, R.string.training_hint_step_recite)
            TrainingHintStep(3, R.string.training_hint_step_check)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.training_hint_skip),
                    color = paper.textMuted,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.training_hint_start),
                    color = paper.pageBody,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1.25f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(paper.accent)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun TrainingHintStep(number: Int, textRes: Int) {
    val paper = LocalPaperColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(paper.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = EasternArabic.format(number),
                color = paper.accent,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                fontSize = 14.sp,
            )
        }
        Text(
            text = stringResource(textRes),
            color = paper.textStrong,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
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
        onEmptyTap = { if (chromeVisible) viewModel.toggleChrome() },
        onSurahNameLongPress = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    )
}

private fun cachedPage(viewModel: HolyQuranViewModel, pageNo: Int) =
    viewModel.pageCache.value[pageNo]
