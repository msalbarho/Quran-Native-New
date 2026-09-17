package com.quransunah.app.ui.listening

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SleepTimerMode
import com.quransunah.app.domain.model.SleepTimerState
import com.quransunah.app.core.AppConstants
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ThemeToggle
import com.quransunah.app.ui.theme.LocalPaperColors

private val PickerShape = RoundedCornerShape(14.dp)
private val PlayShape = RoundedCornerShape(14.dp)

private enum class ListeningPicker {
    Surah,
    Reciter,
    DownloadFrom,
    DownloadTo,
    PlaybackRate,
    SleepTimer,
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
    val sleepTimer by viewModel.sleepTimer.collectAsStateWithLifecycle()
    val playbackRate by viewModel.playbackRate.collectAsStateWithLifecycle()
    val inspection = LocalInspectionMode.current
    ListeningScreenContent(
        ui = ui,
        playback = playback,
        playbackRate = playbackRate,
        sleepTimer = sleepTimer,
        fontManager = if (inspection) null else viewModel.fontManager,
        nightMode = nightMode,
        onThemeToggle = onThemeToggle,
        onSelectReciter = viewModel::selectReciter,
        onSelectSurah = viewModel::selectSurah,
        onDownloadFrom = viewModel::setDownloadFrom,
        onDownloadTo = viewModel::setDownloadTo,
        onDownload = viewModel::downloadRange,
        onPlaySurah = { viewModel.playOrToggle() },
        onStop = viewModel::stop,
        onPrevious = viewModel::playPreviousSurah,
        onNext = viewModel::playNextSurah,
        onSeek = viewModel::seekTo,
        onCycleRepeat = viewModel::cycleRepeatMode,
        onSetPlaybackRate = viewModel::setPlaybackRate,
        onSetSleepTimer = viewModel::setSleepTimer,
        modifier = modifier,
    )
}

@Composable
fun ListeningScreenContent(
    ui: ListeningUiState,
    playback: PlaybackSnapshot,
    playbackRate: Float,
    sleepTimer: SleepTimerState,
    fontManager: QcfFontManager?,
    nightMode: Boolean,
    onThemeToggle: () -> Unit,
    onSelectReciter: (SurahReciter) -> Unit,
    onSelectSurah: (Int) -> Unit,
    onDownloadFrom: (Int) -> Unit,
    onDownloadTo: (Int) -> Unit,
    onDownload: () -> Unit,
    onPlaySurah: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    onSetPlaybackRate: (Float) -> Unit,
    onSetSleepTimer: (SleepTimerMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    var picker by remember { mutableStateOf<ListeningPicker?>(null) }
    val surahName = ui.surahs.firstOrNull { it.number == ui.selectedSurah }?.nameArabic
        ?: stringResource(R.string.playback_surah_fallback, EasternArabic.format(ui.selectedSurah))
    val fromName = ui.surahs.firstOrNull { it.number == ui.downloadFrom }?.nameArabic
        ?: stringResource(R.string.playback_surah_fallback, EasternArabic.format(ui.downloadFrom))
    val toName = ui.surahs.firstOrNull { it.number == ui.downloadTo }?.nameArabic
        ?: stringResource(R.string.playback_surah_fallback, EasternArabic.format(ui.downloadTo))
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
        downloadError -> stringResource(R.string.audio_download_failed)
        allCached -> stringResource(R.string.audio_downloaded)
        pending > 1 -> stringResource(R.string.audio_download_count, EasternArabic.format(pending))
        else -> stringResource(R.string.audio_download)
    }
    val playLabel = when {
        currentPlaying -> stringResource(R.string.audio_pause)
        currentActive -> stringResource(R.string.audio_resume)
        else -> stringResource(R.string.audio_play_surah)
    }

    Box(modifier.fillMaxSize().background(paper.pageBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            AudioScreenHeader(nightMode = nightMode, onThemeToggle = onThemeToggle)
            if (ui.reciters.isEmpty()) {
                Text(
                    text = stringResource(R.string.audio_loading_reciters),
                    color = paper.textMuted,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                )
            } else {
                ScreenAudioPlayer(
                    snapshot = playback,
                    surahNumber = ui.selectedSurah,
                    fallbackTitle = surahName,
                    reciterName = ui.selectedReciter?.name,
                    repeatMode = ui.repeatMode,
                    fontManager = fontManager,
                    isCurrentTrackActive = currentActive,
                    hasPrevious = index > 0,
                    hasNext = index >= 0 && index < ui.availableSurahs.lastIndex,
                    primaryDisabled = !playEnabled,
                    onPrimary = onPlaySurah,
                    onStop = onStop,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeek = onSeek,
                    onCycleRepeat = onCycleRepeat,
                    modifier = Modifier.padding(bottom = 18.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlaybackOption(
                        label = stringResource(R.string.audio_playback_rate),
                        value = formatPlaybackRate(playbackRate),
                        open = picker == ListeningPicker.PlaybackRate,
                        onClick = { picker = ListeningPicker.PlaybackRate },
                        onDismiss = { picker = null },
                        modifier = Modifier.weight(1f),
                    ) { dismiss ->
                        AppConstants.PLAYBACK_RATES.forEach { rate ->
                            DropdownMenuItem(
                                text = { Text(formatPlaybackRate(rate)) },
                                onClick = { onSetPlaybackRate(rate); dismiss() },
                            )
                        }
                    }
                    PlaybackOption(
                        label = stringResource(R.string.audio_sleep_timer),
                        value = stringResource(sleepTimerLabel(sleepTimer.mode)),
                        open = picker == ListeningPicker.SleepTimer,
                        onClick = { picker = ListeningPicker.SleepTimer },
                        onDismiss = { picker = null },
                        modifier = Modifier.weight(1f),
                    ) { dismiss ->
                        SleepTimerMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(sleepTimerLabel(mode))) },
                                onClick = { onSetSleepTimer(mode); dismiss() },
                            )
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    PickerRow(
                        label = stringResource(R.string.audio_surah),
                        value = surahName,
                        open = picker == ListeningPicker.Surah,
                        playing = currentPlaying,
                        enabled = ui.availableSurahs.isNotEmpty(),
                        onClick = { picker = ListeningPicker.Surah },
                    )
                    PickerRow(
                        label = stringResource(R.string.audio_reciter),
                        value = ui.selectedReciter?.name ?: "—",
                        open = picker == ListeningPicker.Reciter,
                        playing = false,
                        enabled = ui.reciters.isNotEmpty(),
                        onClick = { picker = ListeningPicker.Reciter },
                    )
                    Text(
                        text = playLabel,
                        color = paper.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp)
                            .alpha(if (playEnabled) 1f else 0.55f),
                    )
                    Text(
                        text = stringResource(R.string.audio_choose_download_surahs),
                        color = paper.textMuted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                        RangePickerButton(
                            label = stringResource(R.string.audio_range_from),
                            value = fromName,
                            open = picker == ListeningPicker.DownloadFrom,
                            enabled = ui.availableSurahs.isNotEmpty() && !downloading,
                            onClick = { picker = ListeningPicker.DownloadFrom },
                            modifier = Modifier.weight(1f),
                        )
                        RangePickerButton(
                            label = stringResource(R.string.audio_range_to),
                            value = toName,
                            open = picker == ListeningPicker.DownloadTo,
                            enabled = ui.availableSurahs.isNotEmpty() && !downloading,
                            onClick = { picker = ListeningPicker.DownloadTo },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val errorRed = Color(0xFFC53030)
                    val downloadBg = when {
                        downloadError -> errorRed.copy(alpha = 0.08f)
                        allCached -> paper.accent.copy(alpha = 0.08f)
                        else -> paper.pageBody
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
                        else -> paper.tone500.copy(alpha = 0.7f)
                    }
                    Text(
                        text = downloadLabel,
                        color = downloadColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .alpha(if (downloading || (!allCached && pending <= 0)) 0.65f else 1f)
                            .shadow(3.dp, PlayShape, clip = false)
                            .clip(PlayShape)
                            .background(downloadBg)
                            .border(1.dp, downloadBorder, PlayShape)
                            .clickable(
                                enabled = ui.selectedMoshaf != null &&
                                    ui.selectedReciter != null &&
                                    !downloading &&
                                    !allCached &&
                                    pending > 0,
                                onClick = onDownload,
                            )
                            .padding(vertical = 13.dp, horizontal = 16.dp),
                    )
                    val hint = buildString {
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
                        ui.selectedReciter?.name?.let { append(" · "); append(it) }
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
                    }
                    Text(
                        text = hint,
                        color = paper.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 12.dp),
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
            ListeningPicker.PlaybackRate, ListeningPicker.SleepTimer -> Unit
            null -> Unit
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
    val highlight = open || playing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(if (highlight) 8.dp else 3.dp, PickerShape, clip = false)
            .clip(PickerShape)
            .background(paper.pageBody)
            .border(
                1.dp,
                if (highlight) paper.accent else paper.tone500.copy(alpha = 0.7f),
                PickerShape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = label, color = paper.textMuted, fontSize = 14.sp)
        Text(
            text = value,
            color = paper.textStrong,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = "›", color = paper.textMuted, fontSize = 20.sp)
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
        modifier = modifier
            .shadow(3.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(paper.pageBody)
            .border(
                1.dp,
                if (open) paper.accent else paper.tone500.copy(alpha = 0.7f),
                RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = label, color = paper.textMuted, fontSize = 12.sp)
        Text(
            text = value,
            color = paper.textStrong,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@ArabicPreviews
@Composable
private fun ListeningScreenPreview() {
    PreviewTheme {
        ListeningScreenContent(
            ui = PreviewFixtures.listeningUi,
            playback = PreviewFixtures.playback,
            playbackRate = AppConstants.DEFAULT_PLAYBACK_RATE,
            sleepTimer = SleepTimerState(),
            fontManager = null,
            nightMode = false,
            onThemeToggle = {},
            onSelectReciter = {},
            onSelectSurah = {},
            onDownloadFrom = {},
            onDownloadTo = {},
            onDownload = {},
            onPlaySurah = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onCycleRepeat = {},
            onSetPlaybackRate = {},
            onSetSleepTimer = {},
        )
    }
}

@Composable
private fun PlaybackOption(
    label: String,
    value: String,
    open: Boolean,
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable (dismiss: () -> Unit) -> Unit,
) {
    val paper = LocalPaperColors.current
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(paper.pageBody)
                .border(1.dp, paper.tone500, RoundedCornerShape(10.dp))
                .clickable { onClick() }
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(label, color = paper.textMuted, fontSize = 11.sp)
            Text(value, color = paper.textStrong, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = onDismiss,
        ) {
            menuContent(onDismiss)
        }
    }
}

private fun formatPlaybackRate(rate: Float): String =
    if (rate % 1f == 0f) "${rate.toInt()}x" else "${rate}x"

private fun sleepTimerLabel(mode: SleepTimerMode): Int = when (mode) {
    SleepTimerMode.OFF -> R.string.audio_sleep_timer_off
    SleepTimerMode.MINUTES_15 -> R.string.audio_sleep_timer_15
    SleepTimerMode.MINUTES_30 -> R.string.audio_sleep_timer_30
    SleepTimerMode.MINUTES_60 -> R.string.audio_sleep_timer_60
    SleepTimerMode.END_OF_SURAH -> R.string.audio_sleep_timer_end_surah
}
