package com.quransunah.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.uiLayoutDirection
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.bookmarks.BookmarksScreen
import com.quransunah.app.ui.index.QuranIndexPicker
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.index.QuranIndexViewModel
import com.quransunah.app.ui.memorization.MemorizationScreen
import com.quransunah.app.ui.settings.SettingsScreen
import com.quransunah.app.ui.training.TrainingHistoryScreen
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val PickerBackdrop = Color(0x6B0F172A)
private val SidePanelShape = RoundedCornerShape(ChromeTokens.SidePanelCorner)
private val SettingsModalShape = RoundedCornerShape(28.dp)
private val IndexModalShape = RoundedCornerShape(30.dp)
private val TrainingControlShape = RoundedCornerShape(999.dp)

@Composable
fun ShellPickerHost(
    picker: ShellPicker,
    indexTab: QuranIndexTab,
    pageNumber: Int,
    currentJuz: Int,
    currentHizb: Int,
    currentSurah: Int,
    fontManager: QcfFontManager?,
    preferredAyah: AyahRef?,
    indexViewModel: QuranIndexViewModel,
    onClose: () -> Unit,
    onSelectIndexPage: (IndexJump) -> Unit,
    onJumpBookmark: (ReadingBookmark) -> Unit,
    onStartReading: () -> Unit,
    onResumeTraining: (surah: Int, ayah: Int, pageNumber: Int) -> Unit = { _, _, _ -> },
    onOpenUserGuide: () -> Unit = {},
) {
    if (picker == ShellPicker.None) return
    BackHandler(onBack = onClose)

    when (picker) {
        ShellPicker.Index -> IndexModal(onClose = onClose) {
            QuranIndexPicker(
                initialTab = indexTab,
                pageNumber = pageNumber,
                currentJuz = currentJuz,
                currentHizb = currentHizb,
                currentSurah = currentSurah,
                fontManager = fontManager,
                onClose = onClose,
                onSelectPage = onSelectIndexPage,
                viewModel = indexViewModel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        ShellPicker.LastPosition -> CenteredPickerCard(
            title = stringResource(R.string.last_position_title),
            onClose = onClose,
            minHeight = 420.dp,
            maxHeight = 640.dp,
        ) {
            BookmarksScreen(
                pageNumber = pageNumber,
                preferredAyah = preferredAyah,
                fontManager = fontManager,
                onJump = onJumpBookmark,
                onStartReading = onStartReading,
                showTitle = false,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        ShellPicker.Progress -> CenteredPickerCard(
            title = stringResource(R.string.training_progress_summary),
            onClose = onClose,
            minHeight = 560.dp,
            maxHeight = 760.dp,
        ) {
            MemorizationScreen(
                fontManager = fontManager,
                onJump = { item ->
                    onJumpBookmark(
                        ReadingBookmark(
                            id = item.id,
                            surah = item.surah,
                            ayah = item.ayah,
                            pageNumber = item.pageNumber,
                            wordId = 0,
                            wordIndex = 0,
                            ayahText = item.ayahText,
                            savedAt = item.updatedAt,
                        ),
                    )
                },
                onStartReading = onStartReading,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
        ShellPicker.TrainingHistory -> CenteredPickerCard(
            title = stringResource(R.string.training_log),
            onClose = onClose,
            minHeight = 560.dp,
            maxHeight = 760.dp,
        ) {
            TrainingHistoryScreen(
                onResume = onResumeTraining,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        ShellPicker.Settings -> SettingsSideSheet(onClose = onClose, onOpenUserGuide = onOpenUserGuide)
        ShellPicker.None -> Unit
    }
}

@Composable
private fun IndexModal(
    onClose: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val paper = LocalPaperColors.current
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PickerBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.85f)
                    .shadow(8.dp, IndexModalShape)
                    .clip(IndexModalShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                paper.chromeFill,
                                paper.pageBody,
                                paper.pageBackground,
                            ),
                        ),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(paper.textMuted.copy(alpha = 0.45f))
                        .clearAndSetSemantics { },
                )
                content()
            }
        }
    }
}

@Composable
private fun CenteredPickerCard(
    title: String?,
    onClose: () -> Unit,
    minHeight: Dp? = null,
    maxHeight: Dp? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val paper = LocalPaperColors.current
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PickerBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp)
                .widthIn(max = 384.dp)
                .fillMaxWidth()
                .then(
                    when {
                        minHeight != null && maxHeight != null -> Modifier.heightIn(min = minHeight, max = maxHeight)
                        minHeight != null -> Modifier.heightIn(min = minHeight)
                        maxHeight != null -> Modifier.heightIn(max = maxHeight)
                        else -> Modifier
                    },
                )
                .shadow(18.dp, SidePanelShape)
                .clip(SidePanelShape)
                .background(paper.pageBody)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            if (title != null) {
                PickerHeader(title = title, onClose = onClose)
            }
            content()
        }
    }
}

@Composable
private fun SettingsSideSheet(
    onClose: () -> Unit,
    onOpenUserGuide: () -> Unit,
) {
    val paper = LocalPaperColors.current
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { open = true }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PickerBackdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 12.dp),
        ) {
            AnimatedVisibility(
                visible = open,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(10.dp, SettingsModalShape)
                        .clip(SettingsModalShape)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    paper.chromeFill,
                                    paper.pageBody,
                                    paper.pageBackground,
                                ),
                            ),
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ),
                ) {
                    SettingsPickerHeader(onClose = onClose)
                    SettingsScreen(
                        onOpenUserGuide = onOpenUserGuide,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsPickerHeader(onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val direction = uiLayoutDirection()
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(paper.textMuted.copy(alpha = 0.45f))
                    .clearAndSetSemantics { },
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text(
                    text = stringResource(R.string.tab_settings),
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 72.dp)
                        .semantics { heading() },
                )
                SettingsCloseLabel(
                    color = paper.textStrong,
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            HorizontalDivider(color = paper.tone500.copy(alpha = 0.28f))
        }
    }
}

@Composable
private fun SettingsCloseLabel(
    color: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = stringResource(R.string.close),
        color = color,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClose)
            .padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun PickerHeader(title: String, onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val closeColor = if (LocalNightMode.current) Color.White else Color.Black
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(paper.chromeFill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.close),
            color = closeColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
