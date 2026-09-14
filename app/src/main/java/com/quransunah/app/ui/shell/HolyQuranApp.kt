package com.quransunah.app.ui.shell

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
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
import com.quransunah.app.ui.mushaf.MedinaCanvasPage
import com.quransunah.app.ui.mushaf.PageHeaderBar
import com.quransunah.app.ui.mushaf.PageNumberBadge
import com.quransunah.app.ui.mushaf.PagePickerDialog
import com.quransunah.app.ui.mushaf.TextMushafPage
import com.quransunah.app.ui.mushaf.toLineRecords
import com.quransunah.app.ui.memorization.MemorizationViewModel
import com.quransunah.app.ui.search.SearchPane
import com.quransunah.app.ui.study.MeaningPopover
import com.quransunah.app.ui.study.StudyViewModel
import com.quransunah.app.ui.study.WordSheet
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private const val BRAND_SPLASH_MIN_MS = 1_250L

@Composable
fun HolyQuranApp(viewModel: HolyQuranViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val hydration by viewModel.hydration.collectAsStateWithLifecycle()
    var brandMinElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(BRAND_SPLASH_MIN_MS)
        brandMinElapsed = true
    }
    HolyQuranTheme(paletteId = settings.palette, nightMode = settings.nightMode) {
        when (val state = hydration) {
            is HydrationState.Failed -> HydrationError(state.message) { viewModel.retryHydration() }
            HydrationState.Pending, HydrationState.Ready -> {
                val showBrandSplash = state is HydrationState.Pending || !brandMinElapsed
                if (showBrandSplash) {
                    BrandSplash(isPreparing = state is HydrationState.Pending)
                } else {
                    ReadyShell(viewModel)
                }
            }
        }
    }
}

@Composable
private fun BrandSplash(isPreparing: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black)
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.splash),
            contentDescription = null,
            modifier = Modifier.fillMaxHeight(),
            contentScale = ContentScale.FillHeight,
        )
        if (isPreparing) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(
                    color = androidx.compose.ui.graphics.Color(0xFFD4A017),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    text = stringResource(R.string.hydration_preparing_title),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Text(
                    text = stringResource(R.string.hydration_preparing_hint),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.82f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
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
    val textSizeSp by viewModel.textSizeSp.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val selectedWord by viewModel.selectedWord.collectAsStateWithLifecycle()
    val overlay by viewModel.overlay.collectAsStateWithLifecycle()
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val jumpHighlight by viewModel.jumpHighlight.collectAsStateWithLifecycle()
    val pendingPagerPage by viewModel.pendingPagerPage.collectAsStateWithLifecycle()
    val picker by viewModel.picker.collectAsStateWithLifecycle()
    val indexTab by viewModel.indexTab.collectAsStateWithLifecycle()
    var trainingTextHidden by remember { mutableStateOf(true) }
    var revealedTrainingAyahs by remember(pageNumber) { mutableStateOf(emptySet<Pair<Int, Int>>()) }
    var revealedTrainingAyah by remember(pageNumber) { mutableStateOf<Pair<Int, Int>?>(null) }
    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val listeningViewModel: ListeningViewModel = hiltViewModel()
    val studyViewModel: StudyViewModel = hiltViewModel()
    val memorizationViewModel: MemorizationViewModel = hiltViewModel()
    val indexViewModel: QuranIndexViewModel = hiltViewModel()
    val preferredAyah = jumpHighlight ?: selectedWord?.let { AyahRef(it.surah, it.ayah) }
    val currentLocationWord = remember(page) {
        page?.lines
            ?.asSequence()
            ?.flatMap { it.words.asSequence() }
            ?.firstOrNull { !it.isAyahMarker }
    }
    val currentSurahName = currentLocationWord?.let { word ->
        surahsByNumber[word.surah]?.nameArabic
            ?: stringResource(R.string.surah_fallback, EasternArabic.format(word.surah))
    } ?: stringResource(R.string.surah_fallback, EasternArabic.format(1))
    val currentAyah = currentLocationWord?.ayah ?: 1
    LaunchedEffect(page, preferredAyah) {
        bookmarksViewModel.bindContext(page, preferredAyah)
    }
    LaunchedEffect(tab) {
        if (tab == AppTab.Training) {
            trainingTextHidden = true
            revealedTrainingAyahs = emptySet()
            revealedTrainingAyah = null
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
    val navVisible = tab != AppTab.Home &&
        (chrome || tab == AppTab.Listening || tab == AppTab.Training)
    var pagePickerOpen by remember { mutableStateOf(false) }
    val highlight by viewModel.mushafHighlight.collectAsStateWithLifecycle()

    val pagerState = rememberPagerState(
        initialPage = pageNumber - 1,
        pageCount = { AppConstants.TOTAL_PAGES },
    )
    BackHandler(
        enabled = tab != AppTab.Home &&
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
                pageNumber = pageNumber,
                currentSurah = currentSurahName,
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
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HorizontalPager(
                        state = pagerState,
                        reverseLayout = false,
                        beyondViewportPageCount = 0,
                        key = { it + 1 },
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(
                                top = ChromeTokens.MushafHeaderReserve,
                                bottom = ChromeTokens.MushafNavReserve,
                            )
                            .clipToBounds(),
                    ) { index ->
                        val pageNo = index + 1
                        LaunchedEffect(pageNo) {
                            viewModel.ensurePageLoaded(pageNo)
                        }
                        val pageData by remember(pageNo) {
                            viewModel.pageCache
                                .map { cache -> cache[pageNo] }
                                .distinctUntilChanged()
                        }.collectAsStateWithLifecycle(initialValue = viewModel.pageCache.value[pageNo])
                        val lineRecords = remember(pageData) { pageData?.toLineRecords().orEmpty() }
                        if (settings.medinaMode) {
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
                                chromeVisible = chrome,
                                surahsByNumber = surahsByNumber,
                                quarter = pageData?.quarter,
                                sajda = pageData?.sajda,
                                onWordTap = { word ->
                                    if (tab == AppTab.Training) {
                                        val key = word.surah to word.ayah
                                        revealedTrainingAyahs = if (key in revealedTrainingAyahs) {
                                            revealedTrainingAyahs - key
                                        } else {
                                            revealedTrainingAyahs + key
                                        }
                                        revealedTrainingAyah = key.takeIf { it in revealedTrainingAyahs }
                                    } else viewModel.onMushafWordTap(word)
                                },
                                onWordLongPress = { word -> viewModel.onWordLongPress(word) },
                                onEmptyTap = { viewModel.toggleChrome() },
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
                                    val key = word.surah to word.ayah
                                    revealedTrainingAyahs = if (key in revealedTrainingAyahs) {
                                        revealedTrainingAyahs - key
                                    } else {
                                        revealedTrainingAyahs + key
                                    }
                                    revealedTrainingAyah = key.takeIf { it in revealedTrainingAyahs }
                                } } else null,
                                chromeVisible = chrome,
                                surahsByNumber = surahsByNumber,
                                quarter = pageData?.quarter,
                                sajda = pageData?.sajda,
                            )
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
            visible = chrome && (tab == AppTab.Reading || tab == AppTab.Training),
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
                modifier = Modifier.statusBarsPadding(),
                surahNames = remember(surahsByNumber) {
                    surahsByNumber.mapValues { it.value.nameArabic }
                },
            )
        }

        if (restoreBar) {
            ReadingPlaybackBar(
                viewModel = viewModel,
                listeningViewModel = listeningViewModel,
                studyViewModel = studyViewModel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, bottom = 66.dp),
            )
        }

        if (tab == AppTab.Reading || tab == AppTab.Training) {
            PageNumberBadge(
                pageNumber = pageNumber,
                onClick = { pagePickerOpen = true },
                modifier = Modifier
                    .align(
                        if (pageNumber % 2 == 1) Alignment.BottomEnd else Alignment.BottomStart,
                    )
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }

        if (tab == AppTab.Training) {
            TrainingControlBar(
                textHidden = trainingTextHidden,
                ratedAyah = revealedTrainingAyah,
                onOpenProgress = viewModel::openProgressPicker,
                onPrevious = { if (pageNumber > 1) viewModel.jumpToPageNumber(pageNumber - 1) },
                onNext = { if (pageNumber < AppConstants.TOTAL_PAGES) viewModel.jumpToPageNumber(pageNumber + 1) },
                onToggleTextVisibility = {
                    if (!trainingTextHidden) revealedTrainingAyahs = emptySet()
                    trainingTextHidden = !trainingTextHidden
                },
                onMarkMastered = { (surah, ayah) ->
                    memorizationViewModel.markTrainingAyahMastered(surah, ayah)
                    revealedTrainingAyahs = revealedTrainingAyahs - (surah to ayah)
                    revealedTrainingAyah = null
                    trainingTextHidden = true
                },
                onMarkNeedsReview = { (surah, ayah) ->
                    memorizationViewModel.markTrainingAyahForReview(surah, ayah)
                    revealedTrainingAyahs = revealedTrainingAyahs - (surah to ayah)
                    revealedTrainingAyah = null
                    trainingTextHidden = true
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 56.dp, start = 20.dp, end = 20.dp),
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
            page = page,
            medinaMode = settings.medinaMode,
            nightMode = settings.nightMode,
            fontSizeSp = textSizeSp,
            surahsByNumber = surahsByNumber,
            currentJuz = page?.juzNumber ?: 1,
            currentHizb = page?.hizbNumber ?: 1,
            currentSurah = page?.primarySurahNumber() ?: 1,
            fontManager = viewModel.fontManager,
            preferredAyah = preferredAyah,
            indexViewModel = indexViewModel,
            onClose = viewModel::closePicker,
            onSelectIndexPage = viewModel::jumpToIndexPage,
            onJumpPage = viewModel::jumpToPageNumber,
            onJumpBookmark = viewModel::jumpToBookmark,
            onStartReading = {
                viewModel.selectTab(AppTab.Reading)
            },
        )
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
private fun TrainingControlBar(
    textHidden: Boolean,
    ratedAyah: Pair<Int, Int>?,
    onOpenProgress: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleTextVisibility: () -> Unit,
    onMarkMastered: (Pair<Int, Int>) -> Unit,
    onMarkNeedsReview: (Pair<Int, Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(paper.pageBody.copy(alpha = 0.97f))
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val label = stringResource(if (textHidden) R.string.training_show_ayahs else R.string.training_hide_ayahs)
        val accessibilityLabel = stringResource(
            if (textHidden) R.string.training_show_ayahs_accessibility
            else R.string.training_hide_ayahs_accessibility,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainingSmallControl(
                label = stringResource(R.string.training_previous_short),
                onClick = onPrevious,
                modifier = Modifier.weight(0.72f),
            )
            Column(
                modifier = Modifier
                    .weight(1.05f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(paper.accent)
                    .clickable(onClick = onToggleTextVisibility)
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(
                        if (textHidden) R.drawable.ic_visibility_off_eye else R.drawable.ic_visibility_eye,
                    ),
                    contentDescription = accessibilityLabel,
                    tint = paper.pageBody,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    text = label,
                    color = paper.pageBody,
                    textAlign = TextAlign.Center,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            TrainingSmallControl(
                label = stringResource(R.string.training_next_short),
                onClick = onNext,
                modifier = Modifier.weight(0.72f),
            )
        }
        if (!textHidden && ratedAyah != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TrainingRatingControl(
                    label = "✓  ${stringResource(R.string.training_rate_mastered)}",
                    onClick = { onMarkMastered(ratedAyah) },
                    modifier = Modifier.weight(1f),
                )
                TrainingRatingControl(
                    label = "↻  ${stringResource(R.string.training_rate_review)}",
                    onClick = { onMarkNeedsReview(ratedAyah) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.training_progress_summary),
            color = paper.textMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onOpenProgress)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TrainingRatingControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Text(
        text = label,
        color = paper.textStrong,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(paper.tone300.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
    )
}

@Composable
private fun TrainingSmallControl(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Text(
        text = label,
        color = paper.textMuted,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
    )
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
    val reciterChoices = when (playback.domain) {
        PlaybackDomain.SURAH -> listeningUi.reciters.map { CompactReciterChoice(it.id.toString(), it.name) }
        PlaybackDomain.AYAH -> studyUi.reciters.map { CompactReciterChoice(it.id, it.name) }
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
