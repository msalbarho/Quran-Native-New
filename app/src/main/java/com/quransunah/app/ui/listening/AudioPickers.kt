package com.quransunah.app.ui.listening

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.data.catalog.SurahReciter
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.index.RevelationPlaceArt
import com.quransunah.app.ui.index.matchesSurahQuery
import com.quransunah.app.ui.index.revelationPlace
import com.quransunah.app.ui.mushaf.QcfGlyphText
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val DialogShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(11.dp)
private val CloseRed = Color(0xFFD10000)
private val Backdrop = Color(0x6B0F172A)

@Composable
fun AudioSurahPicker(
    surahs: List<SurahInfo>,
    selectedSurah: Int,
    fontManager: QcfFontManager?,
    onClose: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    val paper = LocalPaperColors.current
    val inspection = LocalInspectionMode.current
    var query by remember { mutableStateOf("") }
    var titleTypeface by remember { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    val filtered = remember(surahs, query) { surahs.filter { matchesSurahQuery(it, query) } }
    val listState = rememberLazyListState()
    LaunchedEffect(fontManager, inspection) {
        if (fontManager != null && !inspection) {
            titleTypeface = fontManager.loadSurahTitleTypeface() ?: fontManager.loadUthmanicTypeface()
        }
    }
    LaunchedEffect(selectedSurah, filtered.size, inspection) {
        if (inspection) return@LaunchedEffect
        val index = filtered.indexOfFirst { it.number == selectedSurah }
        if (index >= 0) listState.scrollToItem(index)
    }
    val jump = {
        val digits = EasternArabic.westernDigits(query)
        val target = digits.toIntOrNull()
        val available = surahs.map { it.number }.toSet()
        when {
            target != null && target in 1..114 && available.contains(target) -> onSelect(target)
            filtered.size == 1 -> onSelect(filtered.first().number)
        }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(max = 576.dp)
                .shadow(18.dp, DialogShape)
                .clip(DialogShape)
                .background(paper.pageBody)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(paper.chromeFill)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.audio_choose_surah),
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.close),
                    color = if (LocalNightMode.current) Color.White else CloseRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                placeholder = {
                    Text(stringResource(R.string.audio_search_surah), color = paper.textMuted, fontSize = 14.sp)
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { jump() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = paper.textStrong,
                    unfocusedTextColor = paper.textPrimary,
                    focusedBorderColor = paper.darkAccent,
                    unfocusedBorderColor = paper.tone500,
                    cursorColor = paper.accent,
                    focusedContainerColor = paper.pageBody,
                    unfocusedContainerColor = paper.pageBody,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.index_empty), color = paper.textMuted, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    items(filtered, key = { it.number }, contentType = { "surah" }) { surah ->
                        AudioSurahCard(
                            surah = surah,
                            active = surah.number == selectedSurah,
                            fontManager = fontManager,
                            titleTypeface = titleTypeface,
                            onClick = { onSelect(surah.number) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioSurahCard(
    surah: SurahInfo,
    active: Boolean,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val place = revelationPlace(surah.revelationType)
    val fallbackName = surah.nameArabic.removePrefix("سُورَةُ ").removePrefix("سورة ")
    val stroke = paper.darkAccent.copy(alpha = if (active) 0.78f else 0.42f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(CardShape)
            .background(paper.pageBody.copy(alpha = 0.72f))
            .border(if (active) 1.5.dp else 1.dp, stroke, CardShape)
            .clickable(onClick = onClick),
    ) {
        if (place != null) {
            RevelationPlaceArt(
                place = place,
                modifier = Modifier.matchParentSize(),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 40.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = EasternArabic.format(surah.number),
                color = paper.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = " – ",
                color = paper.accent.copy(alpha = 0.55f),
                fontSize = 15.sp,
            )
            QcfGlyphText(
                ligature = fontManager?.surahNameLigature(surah.number).orEmpty(),
                fallback = fallbackName,
                fontManager = fontManager,
                titleTypeface = titleTypeface,
                color = if (night) Color.White else paper.textStrong,
                ligatureSize = 26.sp,
                fallbackSize = 16.sp,
                modifier = Modifier.widthIn(min = 88.dp, max = 160.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.index_ayahs_count, EasternArabic.format(surah.numberOfAyahs)),
                color = paper.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun AudioReciterPicker(
    reciters: List<SurahReciter>,
    selectedId: Int?,
    onClose: () -> Unit,
    onSelect: (SurahReciter) -> Unit,
) {
    val paper = LocalPaperColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Backdrop)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClose,
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .heightIn(max = 448.dp)
                .shadow(18.dp, DialogShape)
                .clip(DialogShape)
                .background(paper.pageBody)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.audio_choose_reciter),
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "×",
                    color = paper.textMuted,
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            HorizontalDivider(color = paper.tone500.copy(alpha = 0.7f))
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(7.dp),
            ) {
                items(reciters, key = { it.id }, contentType = { "reciter" }) { reciter ->
                    val selected = reciter.id == selectedId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) paper.accent.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { onSelect(reciter) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = reciter.name,
                            color = paper.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) {
                            Text(
                                text = "✓",
                                color = paper.accent,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
