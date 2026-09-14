package com.quransunah.app.ui.memorization

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.MemorizationItem
import com.quransunah.app.domain.model.MemorizationState
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.QcfGlyphText
import com.quransunah.app.ui.mushaf.UthmanicText
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
    val recordingState by viewModel.recordingState.collectAsStateWithLifecycle()
    val checkResult by viewModel.checkResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startRecording()
    }
    MemorizationScreenContent(
        ui = ui,
        fontManager = fontManager,
        onJump = onJump,
        onMarkMastered = viewModel::markMastered,
        onMarkLearning = viewModel::markLearning,
        onReview = viewModel::recordReview,
        onDelete = viewModel::delete,
        onCreatePlan = viewModel::createPlanFromTrackedItems,
        onStartSession = viewModel::startSession,
        onFinishSession = viewModel::finishSession,
        recordingState = recordingState,
        onStartRecording = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                viewModel.startRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStopRecording = viewModel::stopRecording,
        onPlayRecording = viewModel::playRecording,
        onStopPlayback = viewModel::stopPlayback,
        onDeleteRecording = viewModel::deleteRecording,
        checkResult = checkResult,
        onCheckTranscript = viewModel::checkTranscript,
        onStartReading = onStartReading,
        modifier = modifier,
    )
}

@Composable
fun MemorizationScreenContent(
    ui: MemorizationUiState,
    fontManager: QcfFontManager?,
    onJump: (MemorizationItem) -> Unit,
    onMarkMastered: (String) -> Unit,
    onMarkLearning: (String) -> Unit,
    onReview: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCreatePlan: () -> Unit,
    onStartSession: (String) -> Unit,
    onFinishSession: () -> Unit,
    recordingState: com.quransunah.app.data.audio.RecordingState,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPlayRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    onDeleteRecording: () -> Unit,
    checkResult: com.quransunah.app.core.RecitationCheckResult?,
    onCheckTranscript: (String) -> Unit,
    onStartReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    var revealedIds by remember { mutableStateOf(emptySet<String>()) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paper.pageBody)
            .padding(horizontal = 16.dp),
    ) {
        MemorizationProgressCard(
            total = ui.total,
            learning = ui.learning,
            mastered = ui.mastered,
            completionPercent = ui.completionPercent,
        )
        MemorizationPlanCard(
            plans = ui.plans,
            activeSession = ui.activeSessionId != null,
            onCreatePlan = onCreatePlan,
            onStartSession = onStartSession,
            onFinishSession = onFinishSession,
        )
        RecordingCard(
            state = recordingState,
            onStart = onStartRecording,
            onStop = onStopRecording,
            onPlay = onPlayRecording,
            onStopPlayback = onStopPlayback,
            onDelete = onDeleteRecording,
        )
        TranscriptCheckCard(result = checkResult, onCheck = onCheckTranscript)
        if (ui.dailyItems.isNotEmpty()) {
            Text(
                text = stringResource(R.string.memorization_today_title),
                color = paper.textStrong,
                fontFamily = LocalAppFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        if (ui.items.isEmpty()) {
            MemorizationEmptyState(onStartReading = onStartReading)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ui.items, key = { it.item.id }, contentType = { "memorization-item" }) { entry ->
                    MemorizationCard(
                        entry = entry,
                        fontManager = fontManager,
                        revealed = entry.item.id in revealedIds,
                        onReveal = { revealedIds = revealedIds + entry.item.id },
                        onJump = { onJump(entry.item) },
                        onMarkMastered = { onMarkMastered(entry.item.id) },
                        onMarkLearning = { onMarkLearning(entry.item.id) },
                        onReview = { onReview(entry.item.id) },
                        onDelete = { onDelete(entry.item.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptCheckCard(
    result: com.quransunah.app.core.RecitationCheckResult?,
    onCheck: (String) -> Unit,
) {
    val paper = LocalPaperColors.current
    var transcript by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(MemorizationCard)
            .background(paper.pageBody).border(1.dp, paper.tone500, MemorizationCard).padding(12.dp),
    ) {
        Text(stringResource(R.string.memorization_check_title), color = paper.textStrong, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = transcript,
            onValueChange = { transcript = it },
            label = { Text(stringResource(R.string.memorization_transcript_hint)) },
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            minLines = 2,
        )
        MemorizationButton(
            label = stringResource(R.string.memorization_check_button),
            emphasized = true,
            onClick = { onCheck(transcript) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        if (result != null) {
            Text(
                stringResource(R.string.memorization_check_score, EasternArabic.format(result.scorePercent)),
                color = paper.accent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                stringResource(
                    R.string.memorization_check_differences,
                    EasternArabic.format(result.differences.count { it.type == com.quransunah.app.core.WordDifferenceType.MISSING }),
                    EasternArabic.format(result.differences.count { it.type == com.quransunah.app.core.WordDifferenceType.EXTRA }),
                    EasternArabic.format(result.differences.count { it.type == com.quransunah.app.core.WordDifferenceType.DIFFERENT }),
                ),
                color = paper.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun RecordingCard(
    state: com.quransunah.app.data.audio.RecordingState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onPlay: () -> Unit,
    onStopPlayback: () -> Unit,
    onDelete: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val recording = state is com.quransunah.app.data.audio.RecordingState.Recording
    val ready = state is com.quransunah.app.data.audio.RecordingState.Ready
    val playing = state is com.quransunah.app.data.audio.RecordingState.Playing
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp).clip(MemorizationCard)
            .background(paper.pageBody).border(1.dp, paper.tone500, MemorizationCard).padding(12.dp),
    ) {
        Text(stringResource(R.string.memorization_recitation_title), color = paper.textStrong, fontWeight = FontWeight.Bold)
        Text(
            stringResource(if (recording) R.string.memorization_recording else R.string.memorization_recording_hint),
            color = paper.textMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MemorizationButton(
                label = stringResource(if (recording) R.string.memorization_stop_recording else R.string.memorization_start_recording),
                emphasized = recording,
                onClick = if (recording) onStop else onStart,
                modifier = Modifier.weight(1f),
            )
            if (ready || playing) {
                MemorizationButton(
                    label = stringResource(if (playing) R.string.memorization_stop_playback else R.string.memorization_play_recording),
                    onClick = if (playing) onStopPlayback else onPlay,
                    modifier = Modifier.weight(1f),
                )
                MemorizationButton(
                    label = stringResource(R.string.memorization_delete_recording),
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                )
            }
        }
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
private fun MemorizationCard(
    entry: MemorizationListItem,
    fontManager: QcfFontManager?,
    revealed: Boolean,
    onReveal: () -> Unit,
    onJump: () -> Unit,
    onMarkMastered: () -> Unit,
    onMarkLearning: () -> Unit,
    onReview: () -> Unit,
    onDelete: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val item = entry.item
    val isMastered = item.state == MemorizationState.MASTERED
    val fallbackName = entry.surahName.ifBlank {
        stringResource(R.string.surah_fallback, EasternArabic.format(item.surah))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MemorizationCard)
            .background(paper.pageBody)
            .border(1.dp, paper.tone500, MemorizationCard)
            .clickable(onClick = onJump)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                QcfGlyphText(
                    ligature = fontManager?.surahNameLigature(item.surah).orEmpty(),
                    fallback = fallbackName,
                    fontManager = fontManager,
                    color = paper.accent,
                    ligatureSize = 24.sp,
                    fallbackSize = 17.sp,
                )
                Text(
                    text = stringResource(
                        R.string.memorization_item_meta,
                        EasternArabic.format(item.ayah),
                        EasternArabic.format(item.pageNumber),
                    ),
                    color = paper.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            StateBadge(isMastered = isMastered)
        }
        if (isMastered || revealed) {
            UthmanicText(
                text = item.ayahText,
                fontManager = fontManager,
                color = paper.textPrimary,
                fontSize = 18.sp,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        } else {
            MemorizationButton(
                label = stringResource(R.string.memorization_reveal_text),
                onClick = onReveal,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isMastered) {
                MemorizationButton(
                    label = stringResource(
                        R.string.memorization_review_count,
                        EasternArabic.format(item.reviewCount),
                    ),
                    emphasized = true,
                    onClick = onReview,
                    modifier = Modifier.weight(1f),
                )
                MemorizationButton(
                    label = stringResource(R.string.memorization_return_learning),
                    onClick = onMarkLearning,
                    modifier = Modifier.weight(1f),
                )
            } else {
                MemorizationButton(
                    label = stringResource(R.string.memorization_mark_mastered),
                    emphasized = true,
                    onClick = onMarkMastered,
                    modifier = Modifier.weight(1f),
                )
                MemorizationButton(
                    label = stringResource(R.string.memorization_open_ayah),
                    onClick = onJump,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text = stringResource(R.string.memorization_delete),
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 12.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
                .clickable(onClick = onDelete)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun StateBadge(isMastered: Boolean) {
    val paper = LocalPaperColors.current
    val fill = if (isMastered) paper.darkAccent.copy(alpha = 0.16f) else paper.tone400.copy(alpha = 0.6f)
    val border = if (isMastered) paper.darkAccent else paper.tone500
    Text(
        text = stringResource(if (isMastered) R.string.memorization_mastered else R.string.memorization_learning),
        color = paper.textStrong,
        fontFamily = LocalAppFontFamily.current,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(MemorizationPill)
            .background(fill)
            .border(1.dp, border, MemorizationPill)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun MemorizationButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val fill = if (emphasized) paper.accent else paper.pageBody
    val text = if (emphasized) paper.pageBody else paper.textStrong
    Box(
        modifier = modifier
            .clip(MemorizationPill)
            .background(fill)
            .border(1.dp, if (emphasized) paper.accent else paper.tone500, MemorizationPill)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = text,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

@Composable
private fun MemorizationEmptyState(onStartReading: () -> Unit) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
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

@Composable
private fun MemorizationPlanCard(
    plans: List<com.quransunah.app.domain.model.MemorizationPlan>,
    activeSession: Boolean,
    onCreatePlan: () -> Unit,
    onStartSession: (String) -> Unit,
    onFinishSession: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val plan = plans.firstOrNull()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(MemorizationCard)
            .background(paper.pageBody)
            .border(1.dp, paper.tone500, MemorizationCard)
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.memorization_plan_title),
            color = paper.textStrong,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        if (plan == null) {
            Text(stringResource(R.string.memorization_plan_empty), color = paper.textMuted, fontSize = 12.sp)
            MemorizationButton(
                label = stringResource(R.string.memorization_create_plan),
                emphasized = true,
                onClick = onCreatePlan,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        } else {
            Text(
                stringResource(R.string.memorization_plan_target, plan.name, EasternArabic.format(plan.dailyTarget), EasternArabic.format(plan.totalAyahs)),
                color = paper.textMuted,
                fontSize = 12.sp,
            )
            MemorizationButton(
                label = stringResource(if (activeSession) R.string.memorization_finish_session else R.string.memorization_start_session),
                emphasized = true,
                onClick = if (activeSession) onFinishSession else { { onStartSession(plan.id) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}
