package com.quransunah.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.bookmarks.BookmarksScreen
import com.quransunah.app.ui.index.QuranIndexPicker
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.index.QuranIndexViewModel
import com.quransunah.app.ui.memorization.MemorizationScreen
import com.quransunah.app.ui.mushaf.MedinaCanvasPage
import com.quransunah.app.ui.mushaf.TextMushafPage
import com.quransunah.app.ui.mushaf.toLineRecords
import com.quransunah.app.ui.settings.SettingsScreen
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val PickerCloseRed = Color(0xFFD10000)
private val PickerBackdrop = Color(0x6B0F172A)
private val SidePanelShape = RoundedCornerShape(ChromeTokens.SidePanelCorner)
private val TrainingControlShape = RoundedCornerShape(999.dp)

@Composable
fun ShellPickerHost(
    picker: ShellPicker,
    indexTab: QuranIndexTab,
    pageNumber: Int,
    page: MushafPage?,
    medinaMode: Boolean,
    nightMode: Boolean,
    fontSizeSp: Float,
    surahsByNumber: Map<Int, com.quransunah.app.domain.model.SurahInfo>,
    currentJuz: Int,
    currentHizb: Int,
    currentSurah: Int,
    fontManager: QcfFontManager?,
    preferredAyah: AyahRef?,
    indexViewModel: QuranIndexViewModel,
    onClose: () -> Unit,
    onSelectIndexPage: (IndexJump) -> Unit,
    onJumpPage: (Int) -> Unit,
    onJumpBookmark: (ReadingBookmark) -> Unit,
    onStartReading: () -> Unit,
) {
    if (picker == ShellPicker.None) return
    BackHandler(onBack = onClose)

    when (picker) {
        ShellPicker.Index -> CenteredPickerCard(
            title = null,
            onClose = onClose,
            minHeight = 520.dp,
            maxHeight = 640.dp,
        ) {
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
        ShellPicker.Bookmarks -> CenteredPickerCard(
            title = stringResource(R.string.training_title),
            onClose = onClose,
            minHeight = 560.dp,
            maxHeight = 760.dp,
        ) {
            TrainingMushafSurface(
                page = page,
                pageNumber = pageNumber,
                fontManager = fontManager,
                medinaMode = medinaMode,
                nightMode = nightMode,
                fontSizeSp = fontSizeSp,
                surahsByNumber = surahsByNumber,
                onClose = onClose,
                onJumpPage = onJumpPage,
                modifier = Modifier.fillMaxWidth().weight(1f),
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
        ShellPicker.Settings -> SettingsSideSheet(onClose = onClose)
        ShellPicker.None -> Unit
    }
}

@Composable
private fun TrainingMushafSurface(
    page: MushafPage?,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    medinaMode: Boolean,
    nightMode: Boolean,
    fontSizeSp: Float,
    surahsByNumber: Map<Int, com.quransunah.app.domain.model.SurahInfo>,
    onClose: () -> Unit,
    onJumpPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    var hidden by remember(pageNumber) { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.training_mushaf_mode_title),
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
                Text(
                    text = stringResource(R.string.training_mushaf_mode_hint),
                    color = paper.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                text = stringResource(R.string.close),
                color = if (nightMode) Color.White else PickerCloseRed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onClose)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(paper.pageBackground),
        ) {
            val lines = page?.toLineRecords().orEmpty()
            if (page != null && lines.isNotEmpty()) {
                if (medinaMode) {
                    MedinaCanvasPage(
                        pageNumber = pageNumber,
                        lines = lines,
                        fontManager = fontManager,
                        glyphColor = if (nightMode) Color.White else Color.Black,
                        hideAyahText = hidden,
                        surahsByNumber = surahsByNumber,
                        onWordLongPress = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    TextMushafPage(
                        pageNumber = pageNumber,
                        lines = lines,
                        fontManager = fontManager,
                        fontSizeSp = fontSizeSp,
                        glyphColor = if (nightMode) Color.White else Color.Black,
                        hideAyahText = hidden,
                        surahsByNumber = surahsByNumber,
                        onWordLongPress = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.training_mushaf_loading),
                    color = paper.textMuted,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper.pageBody)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.training_previous_page),
                color = if (pageNumber > 1) paper.textStrong else paper.textMuted.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(TrainingControlShape)
                    .clickable(enabled = pageNumber > 1) { onJumpPage(pageNumber - 1) }
                    .padding(horizontal = 7.dp, vertical = 8.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(TrainingControlShape)
                    .background(paper.accent)
                    .clickable { hidden = !hidden }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        if (hidden) R.drawable.ic_visibility_off_eye else R.drawable.ic_visibility_eye,
                    ),
                    contentDescription = stringResource(
                        if (hidden) R.string.training_show_ayahs_accessibility
                        else R.string.training_hide_ayahs_accessibility,
                    ),
                    tint = paper.pageBody,
                    modifier = Modifier.size(30.dp),
                )
                Text(
                    text = stringResource(if (hidden) R.string.training_show_ayahs else R.string.training_hide_ayahs),
                    color = paper.pageBody,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
            Text(
                text = stringResource(R.string.training_next_page),
                color = if (pageNumber < AppConstants.TOTAL_PAGES) paper.textStrong else paper.textMuted.copy(alpha = 0.45f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                modifier = Modifier
                    .clip(TrainingControlShape)
                    .clickable(enabled = pageNumber < AppConstants.TOTAL_PAGES) { onJumpPage(pageNumber + 1) }
                    .padding(horizontal = 7.dp, vertical = 8.dp),
            )
        }
    }
}

private enum class SavedPlacesTab {
    Bookmarks,
    Memorization,
}

@Composable
private fun SavedPlacesHub(
    pageNumber: Int,
    preferredAyah: AyahRef?,
    fontManager: QcfFontManager?,
    onJumpBookmark: (ReadingBookmark) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    var selectedTab by remember { mutableStateOf(SavedPlacesTab.Bookmarks) }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(paper.tone400.copy(alpha = 0.48f)),
        ) {
            SavedPlacesTabLabel(
                text = stringResource(R.string.saved_places_tab),
                selected = selectedTab == SavedPlacesTab.Bookmarks,
                onClick = { selectedTab = SavedPlacesTab.Bookmarks },
                modifier = Modifier.weight(1f),
            )
            SavedPlacesTabLabel(
                text = stringResource(R.string.training_tab),
                selected = selectedTab == SavedPlacesTab.Memorization,
                onClick = { selectedTab = SavedPlacesTab.Memorization },
                modifier = Modifier.weight(1f),
            )
        }
        when (selectedTab) {
            SavedPlacesTab.Bookmarks -> BookmarksScreen(
                pageNumber = pageNumber,
                preferredAyah = preferredAyah,
                fontManager = fontManager,
                onJump = onJumpBookmark,
                onStartReading = onStartReading,
                showTitle = false,
                modifier = Modifier.weight(1f),
            )
            SavedPlacesTab.Memorization -> MemorizationScreen(
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
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SavedPlacesTabLabel(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Box(
        modifier = modifier
            .padding(3.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) paper.pageBody else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) paper.textStrong else paper.textMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
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
) {
    val paper = LocalPaperColors.current
    var open by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { open = true }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
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
            AnimatedVisibility(
                visible = open,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.75f)
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
                            .shadow(18.dp, SidePanelShape)
                            .clip(SidePanelShape)
                            .background(paper.pageBody),
                    ) {
                        SettingsPickerHeader(onClose = onClose)
                        SettingsScreen(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPickerHeader(onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val closeColor = if (LocalNightMode.current) Color.White else PickerCloseRed
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(paper.chromeFill)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = stringResource(R.string.tab_settings),
            color = paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            text = stringResource(R.string.close),
            color = closeColor,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PickerHeader(title: String, onClose: () -> Unit) {
    val paper = LocalPaperColors.current
    val closeColor = if (LocalNightMode.current) Color.White else PickerCloseRed
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
