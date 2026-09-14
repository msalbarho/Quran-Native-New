package com.quransunah.app.ui.memorization

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.QcfGlyphText
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalPaperColors

private val MemorizationPill = RoundedCornerShape(999.dp)
private val MemorizationCard = RoundedCornerShape(16.dp)

@Composable
fun MemorizationScreen(
    fontManager: QcfFontManager?,
    onJump: (MemorizationItem) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MemorizationViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    MemorizationScreenContent(ui, fontManager, onJump, onStartReading, modifier)
}

@Composable
fun MemorizationScreenContent(
    ui: MemorizationUiState,
    fontManager: QcfFontManager?,
    onJump: (MemorizationItem) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mastered = ui.items.filter { it.item.state == MemorizationState.MASTERED }
    val learning = ui.items.filter { it.item.state == MemorizationState.LEARNING }
    Column(
        modifier = modifier.fillMaxSize().background(LocalPaperColors.current.pageBody)
            .padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
    ) {
        MemorizationProgressCard(ui.total, ui.learning, ui.mastered, ui.completionPercent)
        if (mastered.isNotEmpty()) MemorizationStatusList(stringResource(R.string.memorization_mastered), mastered, fontManager, onJump)
        if (learning.isNotEmpty()) MemorizationStatusList(stringResource(R.string.training_rate_review), learning, fontManager, onJump)
        if (ui.items.isEmpty()) MemorizationEmptyState(onStartReading, Modifier.fillMaxWidth())
    }
}

@Composable
private fun MemorizationStatusList(
    title: String,
    items: List<MemorizationListItem>,
    fontManager: QcfFontManager?,
    onJump: (MemorizationItem) -> Unit,
) {
    val paper = LocalPaperColors.current
    Column(Modifier.fillMaxWidth().padding(top = 10.dp).clip(MemorizationCard)
        .background(paper.pageBody).border(1.dp, paper.tone500, MemorizationCard).padding(12.dp)) {
        Text(title, color = paper.textStrong, fontFamily = LocalAppFontFamily.current, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        items.forEach { entry -> MemorizationStatusRow(entry, fontManager, onJump) }
    }
}

@Composable
private fun MemorizationStatusRow(
    entry: MemorizationListItem,
    fontManager: QcfFontManager?,
    onJump: (MemorizationItem) -> Unit,
) {
    val paper = LocalPaperColors.current
    val item = entry.item
    val name = entry.surahName.ifBlank { stringResource(R.string.surah_fallback, EasternArabic.format(item.surah)) }
    Row(Modifier.fillMaxWidth().padding(top = 7.dp).clip(RoundedCornerShape(10.dp))
        .background(paper.tone300.copy(alpha = 0.55f)).clickable { onJump(item) }
        .padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            QcfGlyphText(
                ligature = fontManager?.surahNameLigature(item.surah).orEmpty(),
                fallback = name,
                fontManager = fontManager,
                color = paper.accent,
                ligatureSize = 20.sp,
                fallbackSize = 13.sp,
            )
            Text(stringResource(R.string.memorization_item_meta, EasternArabic.format(item.ayah), EasternArabic.format(item.pageNumber)), color = paper.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
        }
        Text(stringResource(R.string.memorization_review_queue_open), color = paper.accent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MemorizationProgressCard(
    total: Int,
    learning: Int,
    mastered: Int,
    completionPercent: Int,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(MemorizationCard)
            .background(paper.tone300.copy(alpha = 0.72f))
            .border(1.dp, paper.darkAccent.copy(alpha = 0.38f), MemorizationCard)
            .padding(14.dp),
    ) {
        Text(
            text = stringResource(R.string.memorization_title),
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(
                R.string.memorization_summary,
                EasternArabic.format(mastered),
                EasternArabic.format(total),
            ),
            color = paper.textMuted,
            fontSize = 13.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(
            progress = { completionPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(MemorizationPill),
            color = paper.accent,
            trackColor = paper.pageBody,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MemorizationCount(
                label = stringResource(R.string.memorization_learning),
                count = learning,
            )
            MemorizationCount(
                label = stringResource(R.string.memorization_mastered),
                count = mastered,
            )
            MemorizationCount(
                label = stringResource(R.string.memorization_completion),
                count = completionPercent,
                suffix = "%",
            )
        }
    }
}

@Composable
private fun MemorizationCount(label: String, count: Int, suffix: String = "") {
    val paper = LocalPaperColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = EasternArabic.format(count) + suffix,
            color = paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        Text(text = label, color = paper.textMuted, fontSize = 11.sp)
    }
}

@Composable
private fun MemorizationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    enabled: Boolean = true,
) {
    val paper = LocalPaperColors.current
    val fill = if (emphasized) paper.accent else paper.pageBody
    val text = if (emphasized) paper.pageBody else paper.textStrong
    Box(
        modifier = modifier
            .clip(MemorizationPill)
            .background(fill.copy(alpha = if (enabled) 1f else 0.45f))
            .border(1.dp, (if (emphasized) paper.accent else paper.tone500).copy(alpha = if (enabled) 1f else 0.45f), MemorizationPill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = text.copy(alpha = if (enabled) 1f else 0.45f),
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun MemorizationEmptyState(
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .padding(top = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(paper.tone400.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bookmark_border),
                contentDescription = null,
                tint = paper.accent,
                modifier = Modifier.size(34.dp),
            )
        }
        Text(
            text = stringResource(R.string.memorization_empty_title),
            color = paper.textPrimary,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.memorization_empty_hint),
            color = paper.textMuted,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
        )
        MemorizationButton(
            label = stringResource(R.string.memorization_start_reading),
            emphasized = true,
            onClick = onStartReading,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
        )
    }
}
