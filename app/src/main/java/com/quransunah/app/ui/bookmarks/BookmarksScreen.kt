package com.quransunah.app.ui.bookmarks

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.AyahRef
import com.quransunah.app.domain.model.ReadingBookmark
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.QcfGlyphText
import com.quransunah.app.ui.mushaf.UthmanicText
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalPaperColors
import java.util.Calendar

private val PillShape = RoundedCornerShape(999.dp)

@Composable
fun BookmarksScreen(
    pageNumber: Int,
    preferredAyah: AyahRef?,
    fontManager: QcfFontManager?,
    onJump: (ReadingBookmark) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(pageNumber, preferredAyah) {
        viewModel.bindContext(pageNumber, preferredAyah)
    }
    BookmarksScreenContent(
        ui = ui,
        fontManager = fontManager,
        onJump = onJump,
        onDelete = viewModel::delete,
        onToggleCurrent = viewModel::toggleCurrent,
        onStartReading = onStartReading,
        showTitle = showTitle,
        modifier = modifier,
    )
}

@Composable
fun BookmarksScreenContent(
    ui: BookmarksUiState,
    fontManager: QcfFontManager?,
    onJump: (ReadingBookmark) -> Unit,
    onDelete: (String) -> Unit,
    onToggleCurrent: () -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
    showTitle: Boolean = false,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paper.pageBody)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showTitle) {
            Text(
                text = stringResource(R.string.bookmarks_title),
                color = paper.textStrong,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = if (ui.items.isEmpty()) {
                stringResource(R.string.bookmarks_saved)
            } else {
                stringResource(R.string.bookmarks_saved_count, EasternArabic.format(ui.items.size))
            },
            color = paper.textMuted,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 10.dp),
            textAlign = TextAlign.Center,
        )
        QuickBookmarkButton(
            saved = ui.currentSaved,
            surahName = ui.targetSurahName,
            surah = ui.target.surah,
            pageNumber = ui.target.pageNumber,
            onClick = onToggleCurrent,
        )
        if (ui.items.isEmpty()) {
            BookmarksEmptyState(onStartReading = onStartReading)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ui.items, key = { it.bookmark.id }, contentType = { "bookmark" }) { item ->
                    SwipeBookmarkRow(
                        item = item,
                        fontManager = fontManager,
                        onJump = { onJump(item.bookmark) },
                        onDelete = { onDelete(item.bookmark.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickBookmarkButton(
    saved: Boolean,
    surahName: String,
    surah: Int,
    pageNumber: Int,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val label = if (saved) {
        stringResource(R.string.bookmarks_remove_current)
    } else {
        stringResource(R.string.bookmarks_save_current)
    }
    val displayName = surahName
        .removePrefix("سُورَةُ ")
        .removePrefix("سورة ")
        .ifBlank { EasternArabic.format(surah) }
    val meta = stringResource(
        R.string.bookmarks_current_meta,
        displayName,
        EasternArabic.format(surah),
        EasternArabic.format(pageNumber),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(if (saved) paper.darkAccent.copy(alpha = 0.16f) else paper.tone400.copy(alpha = 0.55f))
            .border(1.dp, paper.darkAccent.copy(alpha = if (saved) 0.72f else 0.38f), PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(if (saved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
            contentDescription = null,
            tint = paper.darkAccent,
            modifier = Modifier.size(18.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = paper.textStrong,
                fontFamily = LocalAppFontFamily.current,
                fontSize = 14.sp,
            )
            Text(
                text = meta,
                color = paper.textMuted,
                fontSize = 11.sp,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBookmarkRow(
    item: BookmarkListItem,
    fontManager: QcfFontManager?,
    onJump: () -> Unit,
    onDelete: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                if (dismissState.targetValue == SwipeToDismissBoxValue.Settled) {
                    paper.tone400
                } else {
                    Color(0xFFB42318).copy(alpha = 0.16f)
                },
                label = "bookmark-swipe-bg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.bookmarks_remove),
                    tint = Color(0xFFB42318),
                )
            }
        },
        content = {
            BookmarkCard(
                item = item,
                fontManager = fontManager,
                onJump = onJump,
                onDelete = onDelete,
            )
        },
    )
}

@Composable
private fun BookmarkCard(
    item: BookmarkListItem,
    fontManager: QcfFontManager?,
    onJump: () -> Unit,
    onDelete: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val bookmark = item.bookmark
    val fallbackName = item.surahName.ifBlank {
        stringResource(R.string.surah_fallback, EasternArabic.format(bookmark.surah))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(paper.pageBody)
            .clickable(onClick = onJump)
            .padding(top = 8.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        QcfGlyphText(
            ligature = fontManager?.surahNameLigature(bookmark.surah).orEmpty(),
            fallback = fallbackName,
            fontManager = fontManager,
            color = paper.accent,
            ligatureSize = 26.sp,
            fallbackSize = 18.sp,
        )
        UthmanicText(
            text = bookmark.ayahText,
            fontManager = fontManager,
            color = paper.textPrimary,
            fontSize = 18.sp,
            maxLines = 3,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            text = stringResource(
                R.string.bookmarks_meta,
                EasternArabic.format(bookmark.ayah),
                EasternArabic.format(bookmark.pageNumber),
                EasternArabic.format(AppConstants.TOTAL_PAGES),
            ),
            color = paper.textMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )
        val savedLabel = formatBookmarkDate(bookmark.savedAt)
        if (savedLabel.isNotBlank()) {
            Text(
                text = stringResource(R.string.bookmarks_saved_at, savedLabel),
                color = paper.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.bookmarks_continue),
                color = paper.textStrong,
                fontFamily = LocalAppFontFamily.current,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(PillShape)
                    .background(paper.tone400.copy(alpha = 0.55f))
                    .border(1.dp, paper.darkAccent.copy(alpha = 0.38f), PillShape)
                    .clickable(onClick = onJump)
                    .padding(vertical = 10.dp),
            )
            Text(
                text = stringResource(R.string.bookmarks_delete),
                color = paper.textMuted,
                fontFamily = LocalAppFontFamily.current,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(PillShape)
                    .border(1.dp, paper.tone500, PillShape)
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .background(paper.tone500.copy(alpha = 0.45f))
                .padding(top = 0.6.dp),
        )
    }
}

@Composable
private fun BookmarksEmptyState(onStartReading: () -> Unit) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(paper.tone400.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_border),
                contentDescription = null,
                tint = paper.accent,
                modifier = Modifier.size(36.dp),
            )
        }
        Text(
            text = stringResource(R.string.bookmarks_empty_title),
            color = paper.textPrimary,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.bookmarks_empty_hint),
            color = paper.textMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        Text(
            text = stringResource(R.string.bookmarks_start_reading),
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(PillShape)
                .background(paper.tone400.copy(alpha = 0.55f))
                .border(1.dp, paper.darkAccent.copy(alpha = 0.38f), PillShape)
                .clickable(onClick = onStartReading)
                .padding(vertical = 12.dp),
        )
    }
}

private fun formatBookmarkDate(savedAt: Long): String {
    if (savedAt <= 1L) return ""
    val cal = Calendar.getInstance().apply { timeInMillis = savedAt }
    return listOf(
        EasternArabic.format(cal.get(Calendar.DAY_OF_MONTH)),
        EasternArabic.format(cal.get(Calendar.MONTH) + 1),
        EasternArabic.format(cal.get(Calendar.YEAR)),
    ).joinToString("/")
}

@ArabicPreviews
@Composable
private fun BookmarksScreenPreview() {
    PreviewTheme {
        BookmarksScreenContent(
            ui = PreviewFixtures.bookmarksUi,
            fontManager = null,
            onJump = {},
            onDelete = {},
            onToggleCurrent = {},
            onStartReading = {},
        )
    }
}
