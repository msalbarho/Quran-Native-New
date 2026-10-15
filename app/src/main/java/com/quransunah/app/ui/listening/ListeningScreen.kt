package com.quransunah.app.ui.listening

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.formatPlaybackClock
import com.quransunah.app.data.catalog.ReciterNames
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.ui.index.SurahNames
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.IslamicEdgePattern
import com.quransunah.app.ui.shell.IslamicOrnamentFade
import com.quransunah.app.ui.shell.ThemeToggle
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperColors

private val PickerShape = RoundedCornerShape(16.dp)
private val PlayShape = RoundedCornerShape(16.dp)

private enum class ListeningPicker {
    Surah,
    Reciter,
    DownloadFrom,
    DownloadTo,
}

@Composable
fun ListeningScreen(
    modifier: Modifier = Modifier,
    nightMode: Boolean = false,
    onThemeToggle: () -> Unit = {},
    viewModel: ListeningViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val inspection = LocalInspectionMode.current
    ListeningScreenContent(
        ui = ui,
        playback = playback,
        fontManager = if (inspection) null else viewModel.fontManager,
        nightMode = nightMode,
        onThemeToggle = onThemeToggle,
        onSelectReciter = viewModel::selectReciter,
        onSelectSurah = viewModel::selectSurah,
        onRetryReciters = viewModel::retryReciters,
        onDownloadFrom = viewModel::setDownloadFrom,
        onDownloadTo = viewModel::setDownloadTo,
        onDownload = viewModel::downloadRange,
        onPlaySurah = { viewModel.playOrToggle() },
        onStop = viewModel::stop,
        onPrevious = viewModel::playPreviousSurah,
        onNext = viewModel::playNextSurah,
        onSeek = viewModel::seekTo,
        onSelectRepeat = viewModel::selectRepeatMode,
        modifier = modifier,
    )
}

@Composable
fun ListeningScreenContent(
    ui: ListeningUiState,
    playback: PlaybackSnapshot,
    fontManager: QcfFontManager?,
    nightMode: Boolean,
    onThemeToggle: () -> Unit,
    onSelectReciter: (SurahReciter) -> Unit,
    onSelectSurah: (Int) -> Unit,
    onRetryReciters: () -> Unit,
    onDownloadFrom: (Int) -> Unit,
    onDownloadTo: (Int) -> Unit,
    onDownload: () -> Unit,
    onPlaySurah: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectRepeat: (SurahRepeatMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val languageTag = ArabicRtl.currentTag(LocalContext.current)
    var picker by remember { mutableStateOf<ListeningPicker?>(null) }
    val surahName = listeningSurahName(ui.surahs, languageTag, ui.selectedSurah)
    val fromName = listeningSurahName(ui.surahs, languageTag, ui.downloadFrom)
    val toName = listeningSurahName(ui.surahs, languageTag, ui.downloadTo)
    val reciterName = ui.selectedReciter?.let { reciter ->
        ReciterNames.display(languageTag, reciter.id, reciter.name)
    }
    val index = ui.availableSurahs.indexOf(ui.selectedSurah)
    val currentActive = playback.domain == PlaybackDomain.SURAH &&
        playback.surah == ui.selectedSurah &&
        (ui.selectedMoshaf == null || playback.moshafId == ui.selectedMoshaf.id)
    val currentPlaying = currentActive && playback.isPlaying
    val pending = (ui.rangeTotal - ui.cachedInRange).coerceAtLeast(0)
    val allCached = ui.rangeTotal > 0 && ui.cachedInRange == ui.rangeTotal
    val progress = ui.download
    val downloading = progress?.running == true
    val downloadError = progress?.errorMessage != null
    val playEnabled = ui.selectedReciter != null && ui.selectedMoshaf != null
    val downloadLabel = when {
        progress?.running == true -> {
            val current = minOf(progress.done + 1, progress.total)
            stringResource(
                R.string.audio_downloading,
                EasternArabic.format(current),
                EasternArabic.format(progress.total),
            )
        }
        downloadError -> progress?.errorMessage ?: stringResource(R.string.audio_download_failed)
        allCached -> stringResource(R.string.audio_downloaded)
        pending > 1 -> stringResource(R.string.audio_download_count, EasternArabic.format(pending))
        else -> stringResource(R.string.audio_download)
    }
    Box(modifier.fillMaxSize().background(paper.pageBackground)) {
        CompositionLocalProvider(LocalLayoutDirection provides uiLayoutDirection()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            if (ui.reciters.isEmpty()) {
                AudioScreenHeader(nightMode = nightMode, onThemeToggle = onThemeToggle)
                Text(
                    text = stringResource(
                        if (ui.recitersError) R.string.audio_reciters_error
                        else R.string.audio_loading_reciters,
                    ),
                    color = paper.textMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp, start = 16.dp, end = 16.dp),
                )
                if (ui.recitersError) {
                    Text(
                        text = stringResource(R.string.audio_retry),
                        color = paper.accent,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onRetryReciters)
                            .padding(top = 14.dp),
                    )
                }
            } else {
                ListeningHeroPlayer(
                    snapshot = playback,
                    surahNumber = ui.selectedSurah,
                    surahTitle = surahName,
                    reciterName = reciterName,
                    repeatMode = ui.repeatMode,
                    fontManager = fontManager,
                    nightMode = nightMode,
                    onThemeToggle = onThemeToggle,
                    isCurrentTrackActive = currentActive,
                    playing = currentPlaying,
                    hasPrevious = index > 0,
                    hasNext = index >= 0 && index < ui.availableSurahs.lastIndex,
                    primaryDisabled = !playEnabled,
                    onPrimary = onPlaySurah,
                    onStop = onStop,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onSelectRepeat = onSelectRepeat,
                )
                ListeningControlSheet(
                    surahName = surahName,
                    reciterName = reciterName,
                    fromName = fromName,
                    toName = toName,
                    picker = picker,
                    surahEnabled = ui.availableSurahs.isNotEmpty(),
                    reciterEnabled = ui.reciters.isNotEmpty(),
                    rangeEnabled = ui.availableSurahs.isNotEmpty() && !downloading,
                    playing = currentPlaying,
                    downloadLabel = downloadLabel,
                    downloadError = downloadError,
                    allCached = allCached,
                    downloading = downloading,
                    pending = pending,
                    downloadEnabled = ui.selectedMoshaf != null &&
                        ui.selectedReciter != null &&
                        !downloading &&
                        !allCached &&
                        pending > 0,
                    hint = buildString {
                        append(
                            if (ui.rangeTotal > 1) {
                                stringResource(
                                    R.string.audio_range_from_to,
                                    EasternArabic.format(ui.downloadFrom),
                                    EasternArabic.format(ui.downloadTo),
                                )
                            } else {
                                stringResource(R.string.audio_surah_only, EasternArabic.format(ui.selectedSurah))
                            },
                        )
                        reciterName?.let { append(" · "); append(it) }
                        if (allCached) {
                            append(" · ")
                            append(stringResource(R.string.playback_from_local))
                        } else if (ui.cachedInRange > 0) {
                            append(" · ")
                            append(
                                stringResource(
                                    R.string.audio_cached_of_total,
                                    EasternArabic.format(ui.cachedInRange),
                                    EasternArabic.format(ui.rangeTotal),
                                ),
                            )
                        }
                    },
                    onSurah = { picker = ListeningPicker.Surah },
                    onReciter = { picker = ListeningPicker.Reciter },
                    onFrom = { picker = ListeningPicker.DownloadFrom },
                    onTo = { picker = ListeningPicker.DownloadTo },
                    onDownload = onDownload,
                )
            }
        }
        }

        when (picker) {
            ListeningPicker.Surah, ListeningPicker.DownloadFrom, ListeningPicker.DownloadTo -> {
                val selected = when (picker) {
                    ListeningPicker.DownloadFrom -> ui.downloadFrom
                    ListeningPicker.DownloadTo -> ui.downloadTo
                    else -> ui.selectedSurah
                }
                AudioSurahPicker(
                    surahs = ui.surahs,
                    selectedSurah = selected,
                    fontManager = fontManager,
                    onClose = { picker = null },
                    onSelect = { surah ->
                        when (picker) {
                            ListeningPicker.DownloadFrom -> onDownloadFrom(surah)
                            ListeningPicker.DownloadTo -> onDownloadTo(surah)
                            else -> onSelectSurah(surah)
                        }
                        picker = null
                    },
                )
            }
            ListeningPicker.Reciter -> AudioReciterPicker(
                reciters = ui.reciters,
                selectedId = ui.selectedReciter?.id,
                onClose = { picker = null },
                onSelect = { reciter ->
                    onSelectReciter(reciter)
                    picker = null
                },
            )
            null -> Unit
        }
    }
}

@Composable
private fun listeningSurahName(
    surahs: List<SurahInfo>,
    languageTag: String,
    number: Int,
): String {
    val arabic = surahs.firstOrNull { it.number == number }?.nameArabic
    if (arabic.isNullOrBlank()) {
        return stringResource(R.string.playback_surah_fallback, EasternArabic.format(number))
    }
    val stripped = arabic.removePrefix("سُورَةُ ").removePrefix("سورة ")
    return SurahNames.displayName(languageTag, number, stripped)
}

private val SheetShape = RoundedCornerShape(28.dp)

private fun listeningPlayerColor(paper: PaperColors, night: Boolean): Color {
    return if (night) paper.pageBody else paper.pageBody
}

private fun listeningHeroTitle(paper: PaperColors, night: Boolean): Color {
    return paper.textStrong
}

private fun listeningHeroMuted(paper: PaperColors, night: Boolean): Color {
    return paper.textMuted
}

@Composable
private fun ListeningHeroPlayer(
    snapshot: PlaybackSnapshot,
    surahNumber: Int,
    surahTitle: String,
    reciterName: String?,
    repeatMode: SurahRepeatMode,
    fontManager: QcfFontManager?,
    nightMode: Boolean,
    onThemeToggle: () -> Unit,
    isCurrentTrackActive: Boolean,
    playing: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    primaryDisabled: Boolean,
    onPrimary: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSelectRepeat: (SurahRepeatMode) -> Unit,
) {
    val paper = LocalPaperColors.current
    val player = listeningPlayerColor(paper, nightMode)
    val heroMuted = listeningHeroMuted(paper, nightMode)
    val heroArtInk = Color(0xFF25211D)
    val heroBackdrop = if (nightMode) {
        lerp(Color(0xFFFFF8F1), paper.accent, 0.18f)
    } else {
        paper.chromeFill
    }
    val duration = snapshot.durationMs.coerceAtLeast(0L)
    val idle = snapshot.domain == PlaybackDomain.IDLE
    val buffering = snapshot.isBuffering
    var dragging by remember { mutableFloatStateOf(-1f) }
    val progress = if (dragging >= 0f) {
        dragging
    } else if (duration > 0L) {
        (snapshot.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val imageHeight = (LocalConfiguration.current.screenHeightDp * 0.30f).dp.coerceIn(156.dp, 228.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(player),
    ) {
        IslamicEdgePattern(
            Modifier.matchParentSize(),
            fade = IslamicOrnamentFade.Vertical,
        )
        Column(Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .background(heroBackdrop),
        ) {
            Image(
                painter = painterResource(R.drawable.home_listening),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = 0.8f,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = if (nightMode) {
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.88f to Color.Transparent,
                                    1f to player,
                                )
                            } else {
                                arrayOf(
                                    0f to Color.Transparent,
                                    0.58f to Color.Transparent,
                                    0.84f to player.copy(alpha = 0.78f),
                                    1f to player,
                                )
                            },
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                ListeningHeroHeader(
                    nightMode = nightMode,
                    titleColor = if (nightMode) paper.pageBackground else paper.textStrong,
                    onThemeToggle = onThemeToggle,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 6.dp, bottom = 12.dp),
                ) {
                    val onVisualRight = LocalLayoutDirection.current == LayoutDirection.Rtl
                    Column(
                        modifier = Modifier
                            .align(if (onVisualRight) Alignment.TopStart else Alignment.TopEnd)
                            .fillMaxWidth(0.46f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        SurahLigatureText(
                            surahNumber = snapshot.surah ?: surahNumber,
                            fontManager = fontManager,
                            color = heroArtInk,
                            fallback = snapshot.surahName ?: surahTitle,
                            fontSize = 30.sp,
                        )
                        reciterName?.takeIf { it.isNotBlank() }?.let { name ->
                            Text(
                                text = name,
                                color = heroArtInk,
                                fontFamily = LocalAppFontFamily.current,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        snapshot.errorMessage?.let { message ->
                            Text(
                                text = message,
                                color = Color(0xFFFFB4A8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatPlaybackClock(if (dragging >= 0f) (dragging * duration).toLong() else snapshot.positionMs),
                    color = heroMuted,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                )
                Text(
                    text = formatPlaybackClock(duration),
                    color = heroMuted,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                )
            }
            Slider(
                value = progress,
                onValueChange = { dragging = it },
                onValueChangeFinished = {
                    if (duration > 0L && dragging >= 0f) {
                        onSeek((dragging * duration).toLong())
                    }
                    dragging = -1f
                },
                enabled = !idle && !buffering && duration > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = paper.accent,
                    activeTrackColor = paper.accent,
                    inactiveTrackColor = paper.accent.copy(alpha = if (nightMode) 0.22f else 0.16f),
                    disabledThumbColor = paper.accent.copy(alpha = 0.4f),
                    disabledActiveTrackColor = paper.accent.copy(alpha = 0.28f),
                    disabledInactiveTrackColor = paper.accent.copy(alpha = 0.1f),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            ListeningTransport(
                playing = playing,
                buffering = buffering && isCurrentTrackActive,
                previousEnabled = hasPrevious && !buffering,
                nextEnabled = hasNext && !buffering,
                primaryEnabled = !primaryDisabled && !buffering,
                stopEnabled = !idle,
                onPrevious = onPrevious,
                onPrimary = onPrimary,
                onNext = onNext,
                onStop = onStop,
            )
            Spacer(Modifier.height(12.dp))
            ListeningRepeatModes(
                selected = repeatMode,
                onSelect = onSelectRepeat,
            )
        }
        }
    }
}

@Composable
private fun ListeningHeroHeader(
    nightMode: Boolean,
    titleColor: Color,
    onThemeToggle: () -> Unit,
) {
    val paper = LocalPaperColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(40.dp))
        Text(
            text = stringResource(R.string.tab_listening),
            color = titleColor,
            fontFamily = LocalDisplayFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (nightMode) paper.surface.copy(alpha = 0.88f) else Color.White.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            ThemeToggle(night = nightMode, onToggle = onThemeToggle)
        }
    }
}

@Composable
private fun ListeningTransport(
    playing: Boolean,
    buffering: Boolean,
    previousEnabled: Boolean,
    nextEnabled: Boolean,
    primaryEnabled: Boolean,
    stopEnabled: Boolean,
    onPrevious: () -> Unit,
    onPrimary: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val ink = listeningHeroTitle(paper, night)
    val onAccent = paper.pageBackground
    val sideFill = if (night) paper.tone400 else paper.chromeFill
    val sideBorder = paper.accent.copy(alpha = if (night) 0.34f else 0.2f)
    val stop = if (night) Color(0xFFFF8A80) else Color(0xFFB42318)
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val previousIcon = if (rtl) R.drawable.ic_skip_next else R.drawable.ic_skip_previous
    val nextIcon = if (rtl) R.drawable.ic_skip_previous else R.drawable.ic_skip_next
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
            HeroCircleButton(
                onClick = onPrevious,
                enabled = previousEnabled,
                size = 46.dp,
                background = sideFill,
                border = sideBorder,
            ) {
                Icon(
                    painter = painterResource(previousIcon),
                    contentDescription = stringResource(R.string.audio_prev_surah),
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            HeroCircleButton(
                onClick = onPrimary,
                enabled = primaryEnabled,
                size = 68.dp,
                background = paper.accent,
                border = Color.Transparent,
            ) {
                if (buffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = onAccent,
                        strokeWidth = 2.4.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                        contentDescription = stringResource(if (playing) R.string.audio_pause else R.string.audio_play),
                        tint = onAccent,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            HeroCircleButton(
                onClick = onNext,
                enabled = nextEnabled,
                size = 46.dp,
                background = sideFill,
                border = sideBorder,
            ) {
                Icon(
                    painter = painterResource(nextIcon),
                    contentDescription = stringResource(R.string.audio_next_surah),
                    tint = ink,
                    modifier = Modifier.size(22.dp),
                )
            }
            HeroCircleButton(
                onClick = onStop,
                enabled = stopEnabled,
                size = 42.dp,
                background = stop.copy(alpha = 0.12f),
                border = stop.copy(alpha = 0.7f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_stop),
                    contentDescription = stringResource(R.string.audio_stop),
                    tint = stop,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
}

@Composable
private fun HeroCircleButton(
    onClick: () -> Unit,
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    background: Color,
    border: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(background)
            .then(
                if (border == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.border(1.dp, border, CircleShape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ListeningRepeatModes(
    selected: SurahRepeatMode,
    onSelect: (SurahRepeatMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        RepeatModeButton(
            icon = R.drawable.ic_repeat,
            label = stringResource(R.string.audio_repeat_all),
            selected = selected == SurahRepeatMode.REMAINING,
            onClick = { onSelect(SurahRepeatMode.REMAINING) },
            modifier = Modifier.weight(1f),
        )
        RepeatModeButton(
            icon = R.drawable.ic_repeat_one,
            label = stringResource(R.string.audio_repeat_one),
            selected = selected == SurahRepeatMode.ONE,
            onClick = { onSelect(SurahRepeatMode.ONE) },
            modifier = Modifier.weight(1f),
        )
        RepeatModeButton(
            icon = R.drawable.ic_play_once,
            label = stringResource(R.string.audio_repeat_off),
            selected = selected == SurahRepeatMode.OFF,
            onClick = { onSelect(SurahRepeatMode.OFF) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RepeatModeButton(
    icon: Int,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val mark = if (selected) paper.pageBackground else paper.textMuted
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 46.dp)
            .clip(shape)
            .background(if (selected) paper.accent else if (night) paper.surface else paper.surface)
            .border(
                width = 1.dp,
                color = if (selected) paper.accent else paper.accent.copy(alpha = if (night) 0.32f else 0.18f),
                shape = shape,
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = mark,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            color = mark,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ListeningControlSheet(
    surahName: String,
    reciterName: String?,
    fromName: String,
    toName: String,
    picker: ListeningPicker?,
    surahEnabled: Boolean,
    reciterEnabled: Boolean,
    rangeEnabled: Boolean,
    playing: Boolean,
    downloadLabel: String,
    downloadError: Boolean,
    allCached: Boolean,
    downloading: Boolean,
    pending: Int,
    downloadEnabled: Boolean,
    hint: String,
    onSurah: () -> Unit,
    onReciter: () -> Unit,
    onFrom: () -> Unit,
    onTo: () -> Unit,
    onDownload: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val sheetColor = if (night) paper.surface else paper.surface
    val cardColor = if (night) paper.pageBody else paper.pageBody
    val errorRed = Color(0xFFC53030)
    val downloadBg = when {
        downloadError -> errorRed.copy(alpha = 0.08f)
        allCached -> paper.accent.copy(alpha = 0.08f)
        else -> cardColor
    }
    val downloadColor = when {
        downloadError -> errorRed
        allCached -> paper.accent
        downloading -> paper.textMuted
        else -> paper.textStrong
    }
    val downloadBorder = when {
        downloadError -> errorRed
        allCached -> paper.accent
        else -> paper.accent.copy(alpha = if (night) 0.28f else 0.16f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-16).dp)
            .shadow(
                elevation = if (night) 4.dp else 10.dp,
                shape = SheetShape,
                clip = false,
                ambientColor = paper.accent.copy(alpha = if (night) 0.22f else 0.12f),
                spotColor = paper.accent.copy(alpha = if (night) 0.12f else 0.08f),
            )
            .clip(SheetShape)
            .background(sheetColor)
            .border(1.dp, paper.accent.copy(alpha = if (night) 0.28f else 0.16f), SheetShape),
    ) {
        IslamicEdgePattern(
            Modifier.matchParentSize(),
            fade = IslamicOrnamentFade.Vertical,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        PickerRow(
            label = stringResource(R.string.audio_surah),
            value = surahName,
            open = picker == ListeningPicker.Surah,
            playing = playing,
            enabled = surahEnabled,
            onClick = onSurah,
        )
        PickerRow(
            label = stringResource(R.string.audio_reciter),
            value = reciterName ?: "—",
            open = picker == ListeningPicker.Reciter,
            playing = false,
            enabled = reciterEnabled,
            onClick = onReciter,
        )
        Text(
            text = stringResource(R.string.audio_choose_download_surahs),
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 300.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangePickerButton(
                        label = stringResource(R.string.audio_range_from),
                        value = fromName,
                        open = picker == ListeningPicker.DownloadFrom,
                        enabled = rangeEnabled,
                        onClick = onFrom,
                    )
                    RangePickerButton(
                        label = stringResource(R.string.audio_range_to),
                        value = toName,
                        open = picker == ListeningPicker.DownloadTo,
                        enabled = rangeEnabled,
                        onClick = onTo,
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangePickerButton(
                        label = stringResource(R.string.audio_range_from),
                        value = fromName,
                        open = picker == ListeningPicker.DownloadFrom,
                        enabled = rangeEnabled,
                        onClick = onFrom,
                        modifier = Modifier.weight(1f),
                    )
                    RangePickerButton(
                        label = stringResource(R.string.audio_range_to),
                        value = toName,
                        open = picker == ListeningPicker.DownloadTo,
                        enabled = rangeEnabled,
                        onClick = onTo,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Text(
            text = downloadLabel,
            color = downloadColor,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (downloading || (!allCached && pending <= 0)) 0.65f else 1f)
                .clip(PlayShape)
                .background(downloadBg)
                .border(1.dp, downloadBorder, PlayShape)
                .clickable(enabled = downloadEnabled, onClick = onDownload)
                .padding(vertical = 13.dp, horizontal = 16.dp),
        )
        Text(
            text = hint,
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        )
        }
    }
}

@Composable
private fun AudioScreenHeader(nightMode: Boolean, onThemeToggle: () -> Unit) {
    val paper = LocalPaperColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp, bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(55.dp))
        Text(
            text = stringResource(R.string.tab_listening),
            color = paper.textStrong,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        ThemeToggle(night = nightMode, onToggle = onThemeToggle)
    }
}

private val SelectorCardHeight = 52.dp

@Composable
private fun PickerRow(
    label: String,
    value: String,
    open: Boolean,
    playing: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val cardColor = if (night) paper.surface else paper.pageBody
    val highlight = open || playing
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (night) {
                    if (highlight) 3.dp else 1.dp
                } else {
                    if (highlight) 1.dp else 0.dp
                },
                shape = PickerShape,
                clip = false,
                ambientColor = paper.accent.copy(alpha = if (night) 0.18f else 0.05f),
                spotColor = paper.accent.copy(alpha = if (night) 0.1f else 0.03f),
            )
            .clip(PickerShape)
            .background(cardColor)
            .border(
                1.dp,
                if (highlight) paper.accent.copy(alpha = if (night) 1f else 0.45f) else paper.accent.copy(alpha = if (night) 0.28f else 0.1f),
                PickerShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .height(SelectorCardHeight)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 14.sp,
        )
        Text(
            text = value,
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "›",
            color = paper.textMuted,
            fontSize = 20.sp,
            modifier = Modifier.graphicsLayer { scaleX = if (rtl) -1f else 1f },
        )
    }
}

@Composable
private fun RangePickerButton(
    label: String,
    value: String,
    open: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = label,
            color = paper.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(16.dp), clip = false)
                .clip(RoundedCornerShape(16.dp))
                .background(if (LocalNightMode.current) paper.pageBody else paper.pageBody)
                .border(
                    1.dp,
                    if (open) paper.accent else paper.accent.copy(alpha = if (LocalNightMode.current) 0.28f else 0.16f),
                    RoundedCornerShape(16.dp),
                )
                .clickable(enabled = enabled, onClick = onClick)
                .height(SelectorCardHeight)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = value,
                color = paper.textStrong,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@ArabicPreviews
@Composable
private fun ListeningScreenPreview() {
    PreviewTheme {
        ListeningScreenContent(
            ui = PreviewFixtures.listeningUi,
            playback = PreviewFixtures.playback,
            fontManager = null,
            nightMode = false,
            onThemeToggle = {},
            onSelectReciter = {},
            onSelectSurah = {},
            onRetryReciters = {},
            onDownloadFrom = {},
            onDownloadTo = {},
            onDownload = {},
            onPlaySurah = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onSelectRepeat = {},
        )
    }
}
