package com.quransunah.app.ui.training

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.UiNumbers
import com.quransunah.app.domain.model.TrainingAssessmentEvent
import com.quransunah.app.domain.model.TrainingAssessmentResult
import com.quransunah.app.domain.model.TrainingSession
import com.quransunah.app.domain.model.TrainingSessionStatus
import com.quransunah.app.ui.theme.LocalPaperColors
import java.text.DateFormat
import java.util.Date

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun TrainingHistoryScreen(
    onResume: (surah: Int, ayah: Int, pageNumber: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TrainingHistoryViewModel = hiltViewModel(),
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var openSessionId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = ui.sessions.firstOrNull { it.id == openSessionId }
    if (open == null) {
        TrainingHistoryList(
            sessions = ui.sessions,
            surahNames = ui.surahNames,
            loaded = ui.loaded,
            onOpen = { openSessionId = it.id },
            modifier = modifier,
        )
    } else {
        TrainingHistoryDetail(
            session = open,
            surahNames = ui.surahNames,
            onBack = { openSessionId = null },
            onResume = {
                onResume(open.lastSurah, open.lastAyah, open.pageNumber)
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun TrainingHistoryList(
    sessions: List<TrainingSession>,
    surahNames: Map<Int, String>,
    loaded: Boolean,
    onOpen: (TrainingSession) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        if (loaded && sessions.isEmpty()) {
            Text(
                text = stringResource(R.string.training_history_empty),
                color = paper.textMuted,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 36.dp),
            )
        }
        sessions.forEach { session ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .clip(CardShape)
                    .background(paper.chromeFill)
                    .border(1.dp, paper.accent.copy(alpha = 0.22f), CardShape)
                    .clickable(role = Role.Button) { onOpen(session) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = trainingDateTime(session.startedAt),
                    color = paper.textStrong,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                )
                Text(
                    text = trainingRange(session, surahNames),
                    color = paper.textPrimary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = trainingSummary(session),
                    color = paper.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TrainingHistoryDetail(
    session: TrainingSession,
    surahNames: Map<Int, String>,
    onBack: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.training_history_back),
            color = paper.accent,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = 6.dp),
        )
        Text(
            text = trainingRange(session, surahNames),
            color = paper.textStrong,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        DetailLine(stringResource(R.string.training_history_started, trainingDateTime(session.startedAt)))
        val ended = session.endedAt
        DetailLine(
            if (ended != null) {
                stringResource(R.string.training_history_ended, trainingDateTime(ended))
            } else {
                stringResource(R.string.training_history_active)
            },
        )
        DetailLine(
            stringResource(
                R.string.training_history_last,
                surahNames[session.lastSurah].orEmpty().ifBlank {
                    stringResource(R.string.surah_fallback, EasternArabic.format(session.lastSurah))
                },
                EasternArabic.format(session.lastAyah),
            ),
        )
        DetailLine(stringResource(R.string.training_history_practiced, EasternArabic.format(session.practiced.size)))
        if (session.assessmentsEditable) {
            session.assessments.forEach { event ->
                DetailLine(assessmentLine(event, surahNames))
            }
            if (session.assessments.isEmpty()) {
                DetailLine(stringResource(R.string.training_history_no_assessments))
            }
        } else if (session.assessmentBlob.isNotBlank() && session.assessmentBlob != "[]") {
            DetailLine(stringResource(R.string.training_history_legacy_assessments))
        }
        Text(
            text = stringResource(R.string.training_history_resume),
            color = paper.pageBackground,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(paper.accent)
                .clickable(role = Role.Button, onClick = onResume)
                .padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun DetailLine(text: String) {
    Text(
        text = text,
        color = LocalPaperColors.current.textMuted,
        fontSize = 13.sp,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun trainingRange(session: TrainingSession, surahNames: Map<Int, String>): String {
    val startName = surahLabel(session.startSurah, surahNames)
    val endName = surahLabel(session.endSurah, surahNames)
    return if (session.startSurah == session.endSurah) {
        stringResource(
            R.string.training_history_same_surah,
            startName,
            EasternArabic.format(session.startAyah),
            EasternArabic.format(session.endAyah),
        )
    } else {
        stringResource(
            R.string.training_history_span,
            startName,
            EasternArabic.format(session.startAyah),
            endName,
            EasternArabic.format(session.endAyah),
        )
    }
}

@Composable
private fun surahLabel(surah: Int, surahNames: Map<Int, String>): String =
    surahNames[surah].orEmpty().ifBlank {
        stringResource(R.string.surah_fallback, EasternArabic.format(surah))
    }

@Composable
private fun trainingSummary(session: TrainingSession): String {
    val practiced = stringResource(R.string.training_history_practiced, EasternArabic.format(session.practiced.size))
    val assessments = if (session.assessmentsEditable) {
        val mastered = session.assessments.count { it.result == TrainingAssessmentResult.MASTERED }
        val review = session.assessments.count { it.result == TrainingAssessmentResult.LEARNING }
        stringResource(
            R.string.training_history_assessments,
            EasternArabic.format(mastered),
            EasternArabic.format(review),
        )
    } else {
        ""
    }
    val status = if (session.status == TrainingSessionStatus.ACTIVE && session.endedAt == null) {
        stringResource(R.string.training_history_active)
    } else {
        ""
    }
    return listOf(practiced, assessments, status).filter { it.isNotBlank() }.joinToString(" · ")
}

@Composable
private fun assessmentLine(event: TrainingAssessmentEvent, surahNames: Map<Int, String>): String {
    val label = if (event.result == TrainingAssessmentResult.MASTERED) {
        stringResource(R.string.training_mark_memorized)
    } else {
        stringResource(R.string.training_rate_review)
    }
    return "$label · ${surahLabel(event.surah, surahNames)} ${EasternArabic.format(event.ayah)} · ${trainingDateTime(event.at)}"
}

private fun trainingDateTime(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, UiNumbers.resolvedLocale())
        .format(Date(epochMs))
