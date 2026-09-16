package com.quransunah.app.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.MushafAyahLine
import com.quransunah.app.ui.mushaf.UthmanicText
import com.quransunah.app.ui.mushaf.WordRecord
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperPalettes
import kotlinx.coroutines.launch

private val CloseRedLight = Color(0xFFC53030)
private val CloseRedDark = Color.White
private val WordSheetShape = RoundedCornerShape(ChromeTokens.SidePanelCorner)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordSheet(
    word: WordRecord,
    onDismiss: () -> Unit,
    onStepAyah: (WordRecord) -> Unit,
    onAyahPlaybackStarted: () -> Unit = {},
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val paper = LocalPaperColors.current
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val inspection = LocalInspectionMode.current
    val selected = ui.word ?: word

    LaunchedEffect(word.id, word.surah, word.ayah) {
        viewModel.bind(word)
    }
    if (!inspection) {
        BackHandler {
            viewModel.onDismiss()
            onDismiss()
        }
    }

    val body = @Composable {
        WordSheetContent(
            word = selected,
            ui = ui,
            playback = playback,
            fontManager = viewModel.fontManager,
            onDismiss = {
                viewModel.onDismiss()
                onDismiss()
            },
            onPrevAyah = {
                scope.launch { viewModel.stepWord(-1)?.let(onStepAyah) }
            },
            onNextAyah = {
                scope.launch { viewModel.stepWord(1)?.let(onStepAyah) }
            },
            onSelectWord = { ayahWord ->
                viewModel.selectAyahWord(ayahWord)?.let(onStepAyah)
            },
            onPlayWord = viewModel::playWord,
            onPlayAyah = {
                viewModel.playAyah(target = selected) {
                    onAyahPlaybackStarted()
                    viewModel.onDismiss()
                    onDismiss()
                }
            },
            onReciterMenu = viewModel::setReciterMenuOpen,
            onSelectReciter = viewModel::selectReciter,
            onOpenTafsir = viewModel::openTafsir,
            onToggleBookmark = viewModel::toggleBookmark,
            onShareImage = viewModel::shareImage,
            onVideoFrom = viewModel::setVideoFrom,
            onVideoTo = viewModel::setVideoTo,
            onSelectVideoReciter = viewModel::selectVideoReciter,
            onShareVideo = viewModel::shareVideo,
        )
    }

    if (inspection) {
        body()
    } else {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x6B0F172A))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                viewModel.onDismiss()
                                onDismiss()
                            },
                        ),
                )
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn() + slideInHorizontally { it },
                    exit = fadeOut() + slideOutHorizontally { it },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxWidth(0.8f)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(
                            top = ChromeTokens.MushafHeaderReserve,
                            bottom = ChromeTokens.MushafNavReserve,
                        ),
                ) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .shadow(18.dp, WordSheetShape)
                                .clip(WordSheetShape)
                                .background(paper.pageBody),
                        ) {
                            body()
                        }
                    }
                }
            }
        }
    }

    if (ui.tafsirOpen && !inspection) {
        TafsirSheet(
            surahName = ui.surahName.ifBlank {
                stringResource(R.string.surah_fallback, EasternArabic.format(selected.surah))
            },
            ayah = selected.ayah,
            loading = ui.tafsirLoading,
            text = ui.tafsirText,
            error = ui.tafsirError,
            bodySizeSp = ui.tafsirSizeSp,
            onDismiss = viewModel::closeTafsir,
        )
    }
}

@Composable
fun WordSheetContent(
    word: WordRecord,
    ui: StudyUiState,
    playback: PlaybackSnapshot,
    fontManager: QcfFontManager?,
    onDismiss: () -> Unit,
    onPrevAyah: () -> Unit,
    onNextAyah: () -> Unit,
    onSelectWord: (QuranWord) -> Unit,
    onPlayWord: () -> Unit,
    onPlayAyah: () -> Unit,
    onReciterMenu: (Boolean) -> Unit,
    onSelectReciter: (String) -> Unit,
    onOpenTafsir: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShareImage: () -> Unit,
    onVideoFrom: (Int) -> Unit,
    onVideoTo: (Int) -> Unit,
    onSelectVideoReciter: (String) -> Unit,
    onShareVideo: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val closeColor = if (night) CloseRedDark else CloseRedLight
    val onAccent = if (night) PaperPalettes.CharcoalBlack else Color.White
    val bodyWords = ui.ayahWords.filter { !it.isAyahMarker }
    val wordIndex = bodyWords.indexOfFirst { it.id == word.id }
    val canPrev = wordIndex > 0 || SurahAyahCounts.previous(word.surah, word.ayah) != null
    val canNext = (wordIndex >= 0 && wordIndex < bodyWords.lastIndex) ||
        SurahAyahCounts.next(word.surah, word.ayah) != null
    val wordPlaying = playback.domain == PlaybackDomain.WORD &&
        playback.wordId == word.id &&
        playback.isPlaying
    val wordActive = playback.domain == PlaybackDomain.WORD && playback.wordId == word.id
    val ayahActive = (playback.domain == PlaybackDomain.AYAH || playback.domain == PlaybackDomain.SURAH) &&
        playback.surah == word.surah &&
        playback.ayah == word.ayah
    val ayahPlaying = ayahActive && playback.isPlaying
    val maxAyah = SurahAyahCounts.ayahCount(word.surah)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .padding(bottom = 28.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaChip(
                    label = stringResource(R.string.word_meta_surah),
                    value = EasternArabic.format(word.surah),
                )
                MetaChip(
                    label = stringResource(R.string.word_meta_ayah),
                    value = EasternArabic.format(word.ayah),
                )
                MetaChip(
                    label = stringResource(R.string.word_meta_word),
                    value = EasternArabic.format(word.position),
                    highlight = true,
                )
            }
            Text(
                text = stringResource(R.string.close),
                color = closeColor,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }

        if (ui.ayahWords.isNotEmpty()) {
            MushafAyahLine(
                words = ui.ayahWords,
                selectedWordId = word.id,
                pageNumber = ui.pageNumber,
                fontManager = fontManager,
                onSelectWord = onSelectWord,
                showAyahMarkerBackdrop = false,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            UthmanicText(
                text = ui.ayahText.ifBlank { word.uthmanic },
                fontManager = fontManager,
                color = if (night) paper.textStrong else Color.Black,
                fontSize = 22.sp,
                lineHeight = 40.sp,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.word_play_word),
                    color = paper.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(end = 12.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleControl(
                            enabled = canPrev,
                            onClick = onPrevAyah,
                            background = paper.pageBody,
                            border = paper.tone500,
                            size = 36.dp,
                            contentDescription = stringResource(R.string.word_prev_word),
                        ) {
                            SkipControlIcon(pointingRight = false, tint = paper.accent)
                        }
                        Spacer(Modifier.size(8.dp))
                        CircleControl(
                            enabled = true,
                            onClick = onPlayWord,
                            background = paper.accent,
                            border = Color.Transparent,
                            size = 46.dp,
                        ) {
                            Icon(
                                painter = painterResource(
                                    if (wordPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                                ),
                                contentDescription = stringResource(
                                    when {
                                        wordPlaying -> R.string.word_pause_word
                                        wordActive -> R.string.word_resume_word
                                        else -> R.string.word_play_word
                                    },
                                ),
                                tint = onAccent,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Spacer(Modifier.size(8.dp))
                        CircleControl(
                            enabled = canNext,
                            onClick = onNextAyah,
                            background = paper.pageBody,
                            border = paper.tone500,
                            size = 36.dp,
                            contentDescription = stringResource(R.string.word_next_word),
                        ) {
                            SkipControlIcon(pointingRight = true, tint = paper.accent)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SheetChipButton(
                    label = stringResource(R.string.word_tafsir),
                    onClick = onOpenTafsir,
                    emphasized = ui.tafsirOpen,
                    modifier = Modifier.weight(1f),
                )
                SheetChipButton(
                    label = stringResource(if (ui.bookmarked) R.string.word_saved else R.string.word_save_ayah),
                    onClick = onToggleBookmark,
                    emphasized = ui.bookmarked,
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, paper.tone500, RoundedCornerShape(12.dp))
                    .background(paper.ayahHighlight.copy(alpha = 0.35f))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReciterDropdown(
                    reciters = ui.reciters.map { it.id to it.name },
                    selectedId = ui.selectedReciterId,
                    expanded = ui.reciterMenuOpen,
                    onExpanded = onReciterMenu,
                    onSelect = onSelectReciter,
                    label = stringResource(R.string.audio_reciter),
                )
                AccentFillButton(
                    label = stringResource(
                        when {
                            ayahPlaying -> R.string.word_pause_ayah
                            ayahActive -> R.string.word_resume_ayah
                            else -> R.string.word_play_ayah
                        },
                    ),
                    onClick = onPlayAyah,
                    enabled = ui.surahReciters.isNotEmpty(),
                )
                AccentFillButton(
                    label = stringResource(
                        if (ui.sharing) R.string.word_preparing else R.string.word_share_image,
                    ),
                    onClick = onShareImage,
                    enabled = !ui.sharing,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    VideoFromAyahField(
                        ayah = ui.videoFromAyah,
                        onClick = { onVideoFrom(word.ayah) },
                        modifier = Modifier.weight(1f),
                    )
                    VideoToAyahDropdown(
                        fromAyah = ui.videoFromAyah,
                        toAyah = ui.videoToAyah,
                        maxAyah = maxAyah,
                        onSelect = onVideoTo,
                        modifier = Modifier.weight(1f),
                    )
                }
                ReciterDropdown(
                    reciters = ui.reciters.map { it.id to it.name },
                    selectedId = ui.videoReciterId,
                    expanded = false,
                    onExpanded = {},
                    onSelect = onSelectVideoReciter,
                    label = stringResource(R.string.word_video_reader),
                )
                AccentFillButton(
                    label = stringResource(
                        if (ui.sharing) R.string.word_wait_video else R.string.word_share_video,
                    ),
                    onClick = onShareVideo,
                    enabled = !ui.sharing && ui.videoReciterId.isNotBlank(),
                )
                Text(
                    text = stringResource(R.string.word_wait_video),
                    color = paper.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (ui.sharing) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = paper.accent,
                    )
                }
                ui.shareError?.let { error ->
                    Text(
                        error,
                        color = closeColor,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaChip(
    label: String,
    value: String,
    highlight: Boolean = false,
) {
    val paper = LocalPaperColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = paper.textMuted, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            text = value,
            color = if (highlight) paper.accent else paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun SkipControlIcon(
    pointingRight: Boolean,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    // Narrower than tall so the skip glyph sits lighter inside the 36dp circle.
    Canvas(modifier = modifier.size(width = 13.dp, height = 18.dp)) {
        val barWidth = size.width * 0.18f
        val barHeight = size.height * 0.72f
        val barTop = (size.height - barHeight) / 2f
        val triangle = Path()
        if (pointingRight) {
            val barLeft = size.width - barWidth
            drawRect(
                color = tint,
                topLeft = Offset(barLeft, barTop),
                size = Size(barWidth, barHeight),
            )
            triangle.moveTo(0f, barTop)
            triangle.lineTo(0f, barTop + barHeight)
            triangle.lineTo(barLeft - size.width * 0.05f, size.height / 2f)
            triangle.close()
        } else {
            drawRect(
                color = tint,
                topLeft = Offset(0f, barTop),
                size = Size(barWidth, barHeight),
            )
            triangle.moveTo(size.width, barTop)
            triangle.lineTo(size.width, barTop + barHeight)
            triangle.lineTo(barWidth + size.width * 0.05f, size.height / 2f)
            triangle.close()
        }
        drawPath(triangle, SolidColor(tint))
    }
}

@Composable
private fun CircleControl(
    enabled: Boolean,
    onClick: () -> Unit,
    background: Color,
    size: Dp,
    contentDescription: String? = null,
    border: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
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
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(
                if (contentDescription != null) {
                    Modifier.semantics { this.contentDescription = contentDescription }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun SheetChipButton(
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(12.dp)
    val border = if (emphasized) paper.accent else paper.tone500
    val fill = if (emphasized) paper.accent.copy(alpha = 0.14f) else paper.pageBody
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .border(1.dp, border, shape)
            .background(fill)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun AccentFillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(if (enabled) paper.accent else paper.accent.copy(alpha = 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (night) PaperPalettes.CharcoalBlack else Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReciterDropdown(
    reciters: List<Pair<String, String>>,
    selectedId: String,
    expanded: Boolean,
    onExpanded: (Boolean) -> Unit,
    onSelect: (String) -> Unit,
    label: String = "",
) {
    val paper = LocalPaperColors.current
    var localExpanded by remember(expanded, selectedId) { mutableStateOf(expanded) }
    val selected = reciters.firstOrNull { it.first == selectedId }?.second
        ?: reciters.firstOrNull()?.second.orEmpty()
    val resolvedLabel = label.ifBlank { stringResource(R.string.word_choose_reader) }
    ExposedDropdownMenuBox(
        expanded = localExpanded,
        onExpandedChange = {
            localExpanded = it
            onExpanded(it)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(resolvedLabel) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = localExpanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = paper.textStrong,
                unfocusedTextColor = paper.textPrimary,
                focusedBorderColor = paper.accent,
                unfocusedBorderColor = paper.tone500,
            ),
        )
        ExposedDropdownMenu(expanded = localExpanded, onDismissRequest = {
            localExpanded = false
            onExpanded(false)
        }) {
            reciters.forEach { reciter ->
                DropdownMenuItem(
                    text = { Text(reciter.second) },
                    onClick = {
                        onSelect(reciter.first)
                        localExpanded = false
                        onExpanded(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun VideoFromAyahField(
    ayah: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    OutlinedTextField(
        value = EasternArabic.format(ayah),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.audio_range_from)) },
        singleLine = true,
        modifier = modifier.clickable(onClick = onClick),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = paper.textStrong,
            unfocusedTextColor = paper.textPrimary,
            focusedBorderColor = paper.accent,
            unfocusedBorderColor = paper.tone500,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoToAyahDropdown(
    fromAyah: Int,
    toAyah: Int,
    maxAyah: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val options = remember(fromAyah, maxAyah) {
        val start = fromAyah.coerceIn(1, maxAyah.coerceAtLeast(1))
        val firstNext = (start + 1).coerceAtMost(maxAyah.coerceAtLeast(1))
        val end = (start + AppConstants.SHARE_AYAH_RANGE_MAX).coerceAtMost(maxAyah.coerceAtLeast(1))
        val following = if (firstNext <= end && firstNext != start) {
            (firstNext..end).toList()
        } else {
            emptyList()
        }
        (listOf(start) + following).distinct()
    }
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = EasternArabic.format(toAyah),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.audio_range_to)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = paper.textStrong,
                unfocusedTextColor = paper.textPrimary,
                focusedBorderColor = paper.accent,
                unfocusedBorderColor = paper.tone500,
            ),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { ayah ->
                DropdownMenuItem(
                    text = { Text(EasternArabic.format(ayah)) },
                    onClick = {
                        onSelect(ayah)
                        expanded = false
                    },
                )
            }
        }
    }
}

@ArabicPreviews
@Composable
private fun WordSheetPreview() {
    PreviewTheme {
        WordSheetContent(
            word = PreviewFixtures.word,
            ui = PreviewFixtures.studyUi,
            playback = PreviewFixtures.playback,
            fontManager = null,
            onDismiss = {},
            onPrevAyah = {},
            onNextAyah = {},
            onSelectWord = {},
            onPlayWord = {},
            onPlayAyah = {},
            onReciterMenu = {},
            onSelectReciter = {},
            onOpenTafsir = {},
            onToggleBookmark = {},
            onShareImage = {},
            onVideoFrom = {},
            onVideoTo = {},
            onSelectVideoReciter = {},
            onShareVideo = {},
        )
    }
}
