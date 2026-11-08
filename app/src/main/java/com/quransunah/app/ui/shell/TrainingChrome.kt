package com.quransunah.app.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.recitation.RecitationEngineId
import com.quransunah.app.ui.mushaf.TrainingWordMark
import com.quransunah.app.ui.mushaf.trainingMarkColor
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.training.TrainingPhase
import com.quransunah.app.ui.training.TrainingReciteScope
import com.quransunah.app.ui.training.TrainingReciteSummary
import com.quransunah.app.ui.training.parseTrainingNumber

internal fun trainingPageTopInset(): Dp = 54.dp

internal fun trainingPageBottomInset(
    phase: TrainingPhase,
    resultsOpen: Boolean,
    singleAyahOpen: Boolean,
): Dp = when {
    phase == TrainingPhase.During -> 60.dp
    resultsOpen -> 160.dp
    singleAyahOpen -> 104.dp
    else -> 60.dp
}

private val BarShape = GoldCornerFrameShape(16.dp)

/** Training top bar on the same ornamented gold plaque as the mushaf header (SurahFrameBackground). */
@Composable
private fun TrainingTopSurface(modifier: Modifier, content: @Composable () -> Unit) {
    Box(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = ChromeTokens.HorizontalInset)
            .padding(top = 4.dp, bottom = 2.dp)
            .fillMaxWidth()
            .height(46.dp),
    ) {
        com.quransunah.app.ui.mushaf.SurahFrameBackground(Modifier.matchParentSize())
        content()
    }
}

/** Training bottom bars on the same bracket shape, tiled ornament and mosque ends as the main bottom nav. */
@Composable
internal fun TrainingBottomSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val paper = LocalPaperColors.current
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = ChromeTokens.HorizontalInset)
            .fillMaxWidth()
            .height(BottomNavBarHeight)
            .clip(BottomNavBracketShape)
            .background(paper.chromeFill)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, BottomNavBracketShape),
    ) {
        TiledOrnamentBackground(Modifier.matchParentSize())
        BottomNavMosqueEnd(alignStart = true)
        BottomNavMosqueEnd(alignStart = false)
        content()
    }
}

@Composable
internal fun TrainingTopBar(
    onNextWord: () -> Unit,
    onHint: () -> Unit,
    onRevealAll: () -> Unit,
    onHideAll: () -> Unit,
    onShowAyah: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrainingTopSurface(modifier) {
    Row(
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .padding(top = 4.dp, bottom = 2.dp)
            .fillMaxWidth()
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrainingAction(R.drawable.ic_skip_next, stringResource(R.string.training_next_word), true, onNextWord, Modifier.weight(1.1f))
        TrainingAction(R.drawable.ic_guide_touch, stringResource(R.string.training_hint), true, onHint, Modifier.weight(0.9f))
        TrainingAction(R.drawable.ic_visibility, stringResource(R.string.training_show_all), true, onRevealAll, Modifier.weight(1f))
        TrainingAction(R.drawable.ic_visibility_off_eye, stringResource(R.string.training_hide_all), true, onHideAll, Modifier.weight(1f))
        TrainingAction(R.drawable.ic_visibility, stringResource(R.string.training_show_ayah), true, onShowAyah, Modifier.weight(0.95f))
    }
    }
}

@Composable
internal fun TrainingBottomBar(
    engine: RecitationEngineId,
    onStart: () -> Unit,
    onEngine: () -> Unit,
    onListen: () -> Unit,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    TrainingBottomSurface(modifier) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrainingAction(R.drawable.ic_record_voice_over, stringResource(R.string.training_start_recite), true, onStart, Modifier.weight(1.1f))
        TrainingAction(R.drawable.ic_record_voice_over, engineShortTitle(engine), true, onEngine, Modifier.weight(1f))
        TrainingAction(R.drawable.ic_play_arrow, stringResource(R.string.training_listen), true, onListen, Modifier.weight(0.9f))
        TrainingAction(R.drawable.ic_more_horiz, stringResource(R.string.training_select), true, onSelect, Modifier.weight(0.9f))
        TrainingAction(R.drawable.ic_close, stringResource(R.string.training_close), true, onClose, Modifier.weight(0.7f))
    }
    }
}

@Composable
internal fun TrainingSingleAyahNavigation(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TrainingBottomSurface(modifier) {
    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrainingAction(R.drawable.ic_skip_next, stringResource(R.string.training_previous), true, onPrevious, Modifier.weight(1f))
        TrainingAction(R.drawable.ic_skip_previous, stringResource(R.string.training_next), true, onNext, Modifier.weight(1f))
        TrainingAction(R.drawable.ic_close, stringResource(R.string.close), true, onClose, Modifier.weight(0.7f))
    }
    }
}

@Composable
internal fun TrainingRecordingStrip(
    elapsedMs: Long,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(BarShape)
            .background(paper.chromeFill)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, BarShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.training_recording),
            color = paper.textStrong,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = formatTrainingClock(elapsedMs),
            color = paper.textStrong,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        TrainingAction(
            iconRes = R.drawable.ic_stop,
            label = stringResource(R.string.training_stop_recite),
            enabled = true,
            onClick = onStop,
            modifier = Modifier.width(64.dp),
        )
    }
}

@Composable
internal fun TrainingResultsPanel(
    summary: TrainingReciteSummary,
    onRetake: () -> Unit,
    onReviewErrors: () -> Unit,
    onClose: () -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val closeLabel = stringResource(R.string.training_close)
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .fillMaxWidth()
            .heightIn(max = 154.dp)
            .clip(BarShape)
            .background(paper.chromeFill)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, BarShape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.training_results),
                color = paper.textStrong,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = formatTrainingClock(summary.durationMs),
                color = paper.textStrong,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Text(
                text = "Ã—",
                color = paper.accent,
                fontSize = 20.sp,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onClose)
                    .semantics { contentDescription = closeLabel }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatChip(stringResource(R.string.training_correct), summary.correct, trainingMarkColor(TrainingWordMark.Correct))
            StatChip(stringResource(R.string.training_possible_error), summary.possibleErrors, trainingMarkColor(TrainingWordMark.PossibleError))
            StatChip(stringResource(R.string.training_uncertain), summary.uncertain, trainingMarkColor(TrainingWordMark.Uncertain))
            StatChip(stringResource(R.string.training_legend_missing), summary.missing, trainingMarkColor(TrainingWordMark.Missing))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ResultButton(stringResource(R.string.training_retake), onRetake, Modifier.weight(1f))
            ResultButton(stringResource(R.string.training_review_errors), onReviewErrors, Modifier.weight(1f), enabled = summary.issues.isNotEmpty())
            ResultButton(stringResource(R.string.training_continue), onContinue, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun TrainingSelectionDialog(
    scope: TrainingReciteScope,
    currentSurah: Int,
    currentAyah: Int,
    currentPage: Int,
    totalPages: Int,
    totalSurahs: Int,
    currentSurahName: String,
    onPickSurah: () -> Unit,
    onNavigatePage: (Int) -> Unit,
    onNavigateSurah: (Int) -> Unit,
    onSelectScope: (TrainingReciteScope) -> Unit,
    onApplyRange: (String, String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var surahInput by remember(currentSurah) { mutableStateOf(currentSurah.toString()) }
    var pageInput by remember(currentPage) { mutableStateOf(currentPage.toString()) }
    var fromInput by remember(currentSurah, currentAyah) { mutableStateOf("$currentSurah:$currentAyah") }
    var toInput by remember(currentSurah, currentAyah) { mutableStateOf("$currentSurah:$currentAyah") }
    var chosenScope by remember(scope) { mutableStateOf(scope) }
    var invalidRange by remember { mutableStateOf(false) }
    ThemedListDialog(title = stringResource(R.string.training_select), onDismiss = onDismiss, dismissOnOutsideTap = true) {
        Text(stringResource(R.string.training_select_surah), color = LocalPaperColors.current.textMuted, fontSize = 12.sp)
        // Tappable row with the current surah; opens the same surah list as Â«Ø§Ù„ÙÙ‡Ø±Ø³Â».
        TrainingMoreRow(currentSurahName.ifBlank { currentSurah.toString() }, onPickSurah)
        Text(stringResource(R.string.training_select_page), color = LocalPaperColors.current.textMuted, fontSize = 12.sp)
        val pageValue = parseTrainingNumber(pageInput)
        val pageValid = pageValue != null && pageValue in 1..totalPages
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TrainingNumberField(
                pageInput,
                { pageInput = it },
                Modifier.weight(1f),
                digitsOnly = true,
                maxLength = totalPages.toString().length,
                isError = pageInput.isNotEmpty() && !pageValid,
                placeholder = "1â€“totalPages".let { "1â€“$totalPages" },
                onDone = { if (pageValid) onNavigatePage(pageValue!!) },
            )
            TrainingGoButton(
                stringResource(R.string.training_go_to_page),
                { if (pageValid) onNavigatePage(pageValue!!) },
                enabled = pageValid,
            )
        }
        TrainingMoreRow(
            stringResource(R.string.training_scope_open),
            { onSelectScope(TrainingReciteScope.Open) },
            selected = chosenScope == TrainingReciteScope.Open,
        )
        TrainingMoreRow(
            stringResource(R.string.training_scope_page),
            { onSelectScope(TrainingReciteScope.Page) },
            selected = chosenScope == TrainingReciteScope.Page,
        )
        TrainingMoreRow(
            stringResource(R.string.training_scope_range),
            { chosenScope = TrainingReciteScope.Range },
            selected = chosenScope == TrainingReciteScope.Range,
        )
        if (chosenScope == TrainingReciteScope.Range) {
            Text(stringResource(R.string.training_range_format_hint), color = LocalPaperColors.current.textMuted, fontSize = 12.sp)
            TrainingNumberField(fromInput, { fromInput = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.training_range_from))
            TrainingNumberField(toInput, { toInput = it }, Modifier.fillMaxWidth(), label = stringResource(R.string.training_range_to))
            TrainingMoreRow(
                stringResource(R.string.training_apply_range),
                { invalidRange = !onApplyRange(fromInput, toInput) },
            )
            if (invalidRange) {
                Text(stringResource(R.string.training_range_invalid), color = trainingMarkColor(TrainingWordMark.PossibleError), fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrainingNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    digitsOnly: Boolean = false,
    maxLength: Int = 9,
    isError: Boolean = false,
    placeholder: String? = null,
    onDone: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            onValueChange(next.filter { it.isDigit() || (!digitsOnly && it == ':') }.take(maxLength))
        },
        modifier = modifier,
        singleLine = true,
        isError = isError,
        label = label?.let { { Text(it) } },
        placeholder = placeholder?.let { { Text(it) } },
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (digitsOnly) KeyboardType.Number else KeyboardType.Text,
            imeAction = if (onDone != null) androidx.compose.ui.text.input.ImeAction.Go else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onGo = { onDone?.invoke() }),
    )
}

/** Compact Â«Ø§Ø°Ù‡Ø¨Â» button sized to its label, same height as the text field beside it. */
@Composable
private fun TrainingGoButton(label: String, onClick: () -> Unit, enabled: Boolean) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(14.dp)
    Box(
        modifier = Modifier
            .heightIn(min = 56.dp)
            .widthIn(min = 88.dp)
            .clip(shape)
            .background(if (enabled) paper.accent.copy(alpha = 0.16f) else paper.accent.copy(alpha = 0.06f))
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) paper.textStrong else paper.textMuted.copy(alpha = 0.45f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
internal fun TrainingEngineDialog(
    selected: RecitationEngineId,
    available: (RecitationEngineId) -> Boolean,
    onSelect: (RecitationEngineId) -> Unit,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaperColors.current
    ThemedListDialog(title = stringResource(R.string.settings_recitation_engine), onDismiss = onDismiss, dismissOnOutsideTap = true) {
        RecitationEngineId.entries.forEach { engine ->
            val enabled = available(engine)
            val (titleRes, descRes) = when (engine) {
                RecitationEngineId.ZIPFORMER -> R.string.recitation_engine_zipformer to R.string.recitation_engine_zipformer_desc
                RecitationEngineId.WHISPER -> R.string.recitation_engine_whisper to R.string.recitation_engine_whisper_desc
                RecitationEngineId.ANDROID -> R.string.recitation_engine_android to R.string.recitation_engine_android_desc
            }
            TrainingMoreRow(
                stringResource(titleRes),
                { onSelect(engine) },
                enabled = enabled,
                selected = engine == selected,
            )
            Text(
                text = if (enabled) stringResource(descRes) else stringResource(R.string.recitation_engine_unavailable),
                color = paper.textMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
        }
    }
}

internal fun engineShortTitle(engine: RecitationEngineId): String = when (engine) {
    RecitationEngineId.ZIPFORMER -> "Zipformer"
    RecitationEngineId.WHISPER -> "Whisper"
    RecitationEngineId.ANDROID -> "Android"
}

internal fun formatTrainingClock(ms: Long): String {
    val total = (ms / 1000L).coerceAtLeast(0L)
    val m = total / 60
    val sec = total % 60
    return EasternArabic.format(m).padStart(2, EasternArabic.format(0)[0]) + ":" + EasternArabic.format(sec).padStart(2, EasternArabic.format(0)[0])
}

@Composable
private fun StatChip(label: String, count: Int, color: Color) {
    val paper = LocalPaperColors.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = EasternArabic.format(count),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(text = label, color = paper.textMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ResultButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(12.dp)
    Text(
        text = label,
        color = if (enabled) paper.textStrong else paper.textMuted.copy(alpha = 0.45f),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(shape)
            .background(paper.accent.copy(alpha = 0.08f))
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
    )
}

@Composable
private fun TrainingMoreRow(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(14.dp)
    Text(
        text = label,
        color = if (enabled) paper.textStrong else paper.textMuted.copy(alpha = 0.45f),
        fontSize = 16.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) paper.accentSoft else paper.accent.copy(alpha = 0.08f))
            .border(ChromeTokens.GoldStroke, if (selected) paper.accent else ChromeTokens.Gold, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 14.dp),
    )
}

@Composable
private fun TrainingAction(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    selected: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val color = if (enabled) paper.textStrong else paper.textMuted.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) paper.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription; this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(14.dp),
        )
        FitBarLabel(
            text = label,
            color = color,
            fontSize = 10.5.sp,
            maxLines = 2,
            minFontSize = 6.5.sp,
        )
    }
}

/** After a surah finished on its last ayah, the next surah to keep listening to; null = stop (paused early or An-Nas). */
internal fun trainingContinuousNext(lastSurah: Int, lastAyah: Int): Pair<Int, Int>? =
    if (lastSurah in 1 until 114 && lastAyah >= com.quransunah.app.core.SurahAyahCounts.ayahCount(lastSurah)) lastSurah + 1 to 1 else null
