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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.bookmarks.BookmarksScreen
import com.quransunah.app.ui.index.QuranIndexPicker
import com.quransunah.app.ui.index.QuranIndexTab
import com.quransunah.app.ui.index.QuranIndexViewModel
import com.quransunah.app.ui.settings.SettingsScreen
import com.quransunah.app.ui.theme.LocalPaperColors

private val PickerCloseRed = Color(0xFFD10000)
private val PickerBackdrop = Color(0x6B0F172A)
private val SidePanelShape = RoundedCornerShape(ChromeTokens.SidePanelCorner)

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
            title = stringResource(R.string.bookmarks_title),
            onClose = onClose,
            minHeight = 420.dp,
            maxHeight = 560.dp,
        ) {
            BookmarksScreen(
                pageNumber = pageNumber,
                preferredAyah = preferredAyah,
                fontManager = fontManager,
                onJump = onJumpBookmark,
                onStartReading = onStartReading,
                showTitle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
        ShellPicker.Settings -> SettingsSideSheet(onClose = onClose)
        ShellPicker.None -> Unit
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
            color = PickerCloseRed,
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
            color = PickerCloseRed,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClose)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}
