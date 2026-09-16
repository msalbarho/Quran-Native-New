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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.quransunah.app.ui.search.SearchPane
import com.quransunah.app.ui.study.MeaningPopover
import com.quransunah.app.ui.study.StudyViewModel
import com.quransunah.app.ui.study.WordSheet
import com.quransunah.app.ui.theme.HolyQuranTheme
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    var selectedTrainingAyah by remember(pageNumber) { mutableStateOf<Pair<Int, Int>?>(null) }
    val bookmarksViewModel: BookmarksViewModel = hiltViewModel()
    val listeningViewModel: ListeningViewModel = hiltViewModel()
    val studyViewModel: StudyViewModel = hiltViewModel()
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
    val trainingListenAyah = selectedTrainingAyah ?: currentLocationWord?.let { it.surah to it.ayah }
    val trainingScope = rememberCoroutineScope()
    LaunchedEffect(page, preferredAyah) {
        bookmarksViewModel.bindContext(page, preferredAyah)
    }
    LaunchedEffect(tab) {
        if (tab == AppTab.Training) {
            trainingTextHidden = true
            revealedTrainingAyahs = emptySet()
        } else {
            trainingTextHidden = true
            revealedTrainingAyahs = emptySet()
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
                                chromeVisible = chrome && tab != AppTab.Training,
                                surahsByNumber = surahsByNumber,
                                quarter = pageData?.quarter,
                                sajda = pageData?.sajda,
                                onWordTap = { word ->
                                    if (tab == AppTab.Training) {
                                        val key = word.surah to word.ayah
                                        selectedTrainingAyah = key
                                        revealedTrainingAyahs = if (key in revealedTrainingAyahs) {
                                            revealedTrainingAyahs - key
                                        } else {
                                            revealedTrainingAyahs + key
                                        }
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
                                    val key = word.surah to word.ayah
                                    selectedTrainingAyah = key
                                    revealedTrainingAyahs = if (key in revealedTrainingAyahs) {
                                        revealedTrainingAyahs - key
                                    } else {
                                        revealedTrainingAyahs + key
                                    }
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
                modifier = Modifier.statusBarsPadding(),
                surahNames = remember(surahsByNumber) {
                    surahsByNumber.mapValues { it.value.nameArabic }
                },
            )
        }

        if (restoreBar && chrome) {
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

        if (tab == AppTab.Training) {
            PageNumberBadge(
                pageNumber = pageNumber,
                onClick = { pagePickerOpen = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp),
            )
        } else if (tab == AppTab.Reading) {
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
                allRevealed = allTrainingAyahsRevealed,
                anyRevealed = anyTrainingAyahRevealed,
                onShowAll = {
                    revealedTrainingAyahs = currentPageAyahs
                },
                onHideAll = {
                    trainingTextHidden = true
                    revealedTrainingAyahs = emptySet()
                },
                onListen = {
                    trainingListenAyah?.let { (surah, ayah) ->
                        val reciterId = studyViewModel.uiState.value.selectedReciterId
                        trainingScope.launch {
                            studyViewModel.audioPlayer.playAyah(reciterId, surah, ayah)
                        }
                    }
                },
                onExit = {
                    revealedTrainingAyahs = emptySet()
                    selectedTrainingAyah = null
                    trainingTextHidden = true
                    viewModel.selectTab(AppTab.Reading)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
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
    allRevealed: Boolean,
    anyRevealed: Boolean,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    onListen: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    BottomNavSurface(modifier = modifier) {
        // BottomNavSurface forces LTR; this declaration order is the physical RTL order:
        // close (left), listen, hide all, show all (right).
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainingBarButton(
                label = stringResource(R.string.training_close),
                contentDescription = stringResource(R.string.training_close_accessibility),
                iconRes = R.drawable.ic_close,
                enabled = true,
                onClick = onExit,
                paper = paper,
                modifier = Modifier.weight(1f),
            )
            TrainingBarButton(
                label = stringResource(R.string.training_listen),
                contentDescription = stringResource(R.string.training_listen_accessibility),
                iconRes = R.drawable.ic_headset,
                enabled = true,
                onClick = onListen,
                paper = paper,
                modifier = Modifier.weight(1f),
            )
            TrainingBarButton(
                label = stringResource(R.string.training_hide_all),
                contentDescription = stringResource(R.string.training_hide_all_accessibility),
                iconRes = R.drawable.ic_training_hidden,
                enabled = anyRevealed,
                onClick = onHideAll,
                paper = paper,
                modifier = Modifier.weight(1f),
            )
            TrainingBarButton(
                label = stringResource(R.string.training_show_all),
                contentDescription = stringResource(R.string.training_show_all_accessibility),
                iconRes = R.drawable.ic_training_show,
                enabled = !allRevealed,
                onClick = onShowAll,
                paper = paper,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TrainingBarButton(
    label: String,
    contentDescription: String,
    iconRes: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    paper: com.quransunah.app.ui.theme.PaperColors,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = if (enabled) 1f else 0.42f
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(paper.textMuted.copy(alpha = contentAlpha)),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            color = paper.textMuted.copy(alpha = contentAlpha),
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
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
        onEmptyTap = { if (chromeVisible) viewModel.toggleChrome() },
        onSurahNameLongPress = { viewModel.openIndexPicker(QuranIndexTab.Surah) },
        modifier = Modifier.fillMaxSize().clipToBounds(),
    )
}
