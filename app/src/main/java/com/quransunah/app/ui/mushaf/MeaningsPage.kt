package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
import android.text.TextUtils
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.LanguageRegistry
import com.quransunah.app.data.catalog.TranslationRegistry
import com.quransunah.app.share.AyahPlainText
import java.util.Locale
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.shell.MeaningEntry
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

/**
 * Arabic-script packaged translations. Direction follows the translation
 * language, not the app UI language.
 */
@Composable
fun TranslationLanguageDialog(
    selectedTag: String?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaperColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = paper.pageBackground,
        title = {
            Text(
                text = stringResource(R.string.translation_choose),
                color = paper.textStrong,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                TranslationRegistry.entries.forEach { entry ->
                    val language = LanguageRegistry.byTag(entry.languageTag)
                    val label = language?.nativeName ?: entry.languageTag
                    val selected = selectedTag?.equals(entry.languageTag, ignoreCase = true) == true
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(entry.languageTag) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = language?.flag.orEmpty(), fontSize = 18.sp)
                        Text(
                            text = label,
                            color = if (selected) paper.accentHover else paper.textStrong,
                            fontSize = 16.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    stringResource(R.string.close),
                    color = if (LocalNightMode.current) Color.White else Color.Black,
                )
            }
        },
    )
}

internal fun translationIsRtl(tag: String?): Boolean {
    val key = tag?.lowercase() ?: return false
    return key in RTL_TRANSLATIONS
}

private val RTL_TRANSLATIONS = setOf("ur", "fa", "fa-af", "ps", "ku")

@Composable
fun MeaningsPage(
    rows: List<MeaningEntry>,
    pageNumber: Int,
    translationTag: String,
    fontManager: QcfFontManager?,
    surahsByNumber: Map<Int, SurahInfo>,
    onChangeTranslation: () -> Unit,
    onToggleChrome: () -> Unit,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val translationRtl = translationIsRtl(translationTag)
    val languageName = LanguageRegistry.byTag(translationTag)?.nativeName
        ?: TranslationRegistry.forLanguage(translationTag)?.languageTag
        ?: translationTag
    val selectorLabel = stringResource(R.string.translation_selector, languageName)
    val inspection = LocalInspectionMode.current
    var titleFace by remember(fontManager) { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    var pageFace by remember(pageNumber, fontManager) {
        mutableStateOf(fontManager?.peekPageTypeface(pageNumber))
    }
    LaunchedEffect(pageNumber, fontManager) {
        if (fontManager != null && !inspection) {
            titleFace = fontManager.loadSurahTitleTypeface()
            pageFace = fontManager.loadPageTypeface(pageNumber)
        }
    }
    val pageFamily = pageFace?.let { FontFamily(it) } ?: FontFamily.Serif
    val listState = rememberLazyListState()
    LaunchedEffect(selected) {
        if (selected) listState.scrollToItem(0)
    }
    val uiTag = ArabicRtl.currentTag(LocalContext.current)
    val uiDirection = if (
        TextUtils.getLayoutDirectionFromLocale(Locale.forLanguageTag(uiTag)) ==
            View.LAYOUT_DIRECTION_RTL
    ) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(paper.pageBackground),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "translation-language") {
            CompositionLocalProvider(LocalLayoutDirection provides uiDirection) {
                Text(
                    text = "$selectorLabel ▾",
                    color = paper.accentHover,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangeTranslation)
                        .semantics { contentDescription = selectorLabel }
                        .padding(vertical = 2.dp),
                )
            }
        }
        items(
            count = rows.size,
            key = { index ->
                when (val row = rows[index]) {
                    is MeaningEntry.Surah -> "surah-${row.number}-$index"
                    MeaningEntry.Bismillah -> "bismillah-$index"
                    is MeaningEntry.Ayah -> "ayah-${row.surah}:${row.ayah}"
                }
            },
        ) { index ->
            when (val row = rows[index]) {
                is MeaningEntry.Surah -> MeaningSurahHeader(
                    surahNumber = row.number,
                    surah = surahsByNumber[row.number],
                    fontManager = fontManager,
                    titleTypeface = titleFace,
                    onToggleChrome = onToggleChrome,
                )
                MeaningEntry.Bismillah -> MeaningBismillah(
                    fontManager = fontManager,
                    titleTypeface = titleFace,
                    onToggleChrome = onToggleChrome,
                )
                is MeaningEntry.Ayah -> MeaningAyahBlock(
                    row = row,
                    tintOrdinal = rows.subList(0, index).count { it is MeaningEntry.Ayah },
                    translationRtl = translationRtl,
                    pageFamily = pageFamily,
                    onToggleChrome = onToggleChrome,
                )
            }
        }
    }
}

@Composable
private fun MeaningSurahHeader(
    surahNumber: Int,
    surah: SurahInfo?,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onToggleChrome: () -> Unit,
) {
    SurahNameFrame(
        surahNumber = surahNumber,
        surah = surah,
        fontManager = fontManager,
        titleTypeface = titleTypeface,
        onTap = onToggleChrome,
        onLongPress = {},
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(SURAH_FRAME_ASPECT)
            .heightIn(min = 38.dp),
    )
}

@Composable
private fun MeaningBismillah(
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onToggleChrome: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .quietClick(onToggleChrome)
            .padding(top = 2.dp, bottom = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        QcfGlyphText(
            ligature = fontManager?.displayBasmalahLigature().orEmpty(),
            fallback = fontManager?.displayBasmalahLigature().orEmpty(),
            fontManager = fontManager,
            titleTypeface = titleTypeface,
            color = ChromeTokens.BasmalaGold,
            ligatureSize = 22.sp,
            fallbackSize = 22.sp,
            preferLigature = true,
        )
    }
}

private fun meaningAyahTint(night: Boolean, ordinal: Int): Color {
    val green = ordinal % 2 == 0
    return if (night) {
        if (green) Color(0xFF1A221E) else Color(0xFF1A1E24)
    } else {
        if (green) Color(0xFFF1F7F2) else Color(0xFFF1F5FA)
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun MeaningAyahBlock(
    row: MeaningEntry.Ayah,
    tintOrdinal: Int,
    translationRtl: Boolean,
    pageFamily: FontFamily,
    onToggleChrome: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copied = stringResource(R.string.copied)
    val ayahSize = 22.sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(meaningAyahTint(night, tintOrdinal))
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggleChrome,
                onLongClick = {
                    val text = AyahPlainText.block(row.surah, row.ayah, row.arabic, row.translation)
                    if (text.isNotBlank()) {
                        clipboard.setText(AnnotatedString(text))
                        Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                    }
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                row.glyphs.forEach { glyph ->
                    Text(
                        text = glyph,
                        color = paper.textStrong,
                        fontSize = ayahSize,
                        lineHeight = 32.sp,
                        fontFamily = pageFamily,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                if (row.markerGlyph.isNotEmpty()) {
                    Text(
                        text = ayahBackgroundGlyph(night) + row.markerGlyph,
                        color = paper.textStrong,
                        fontSize = ayahSize * 0.92f,
                        lineHeight = 32.sp,
                        fontFamily = pageFamily,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        val direction = if (translationRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Text(
                text = row.translation.orEmpty(),
                color = paper.textMuted,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Start,
                style = TextStyle(
                    textDirection = if (translationRtl) TextDirection.Rtl else TextDirection.Ltr,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun Modifier.quietClick(onClick: () -> Unit): Modifier {
    return clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick,
    )
}
