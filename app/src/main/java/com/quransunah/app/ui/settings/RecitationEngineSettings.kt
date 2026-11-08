package com.quransunah.app.ui.settings

import com.quransunah.app.ui.shell.GoldCornerFrame
import com.quransunah.app.ui.shell.GoldCornerFrameShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.recitation.RecitationEngineId
import com.quransunah.app.recitation.engine.ModelState
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalPaperColors

/** What the engine rows need to know about the device (kept out of the composable for previews). */
data class RecitationSettingsState(
    val whisperModel: ModelState = ModelState.NotDownloaded,
    val zipformerAvailable: Boolean = true,
    val whisperAvailable: Boolean = true,
    val androidAvailable: Boolean = true,
)

/** Engine picker in the same paper style as the other settings rows (pill = accent when selected). */
@Composable
internal fun RecitationEngineSettings(
    selected: RecitationEngineId,
    state: RecitationSettingsState,
    onSelect: (RecitationEngineId) -> Unit,
    onWhisperDownload: () -> Unit,
    onWhisperDelete: () -> Unit,
) {
    val paper = LocalPaperColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EngineRow(
            title = stringResource(R.string.recitation_engine_zipformer),
            desc = stringResource(R.string.recitation_engine_zipformer_desc),
            selected = selected == RecitationEngineId.ZIPFORMER,
            available = state.zipformerAvailable,
            onClick = { onSelect(RecitationEngineId.ZIPFORMER) },
        )
        EngineRow(
            title = stringResource(R.string.recitation_engine_whisper),
            desc = stringResource(R.string.recitation_engine_whisper_desc),
            selected = selected == RecitationEngineId.WHISPER,
            available = state.whisperAvailable,
            onClick = { onSelect(RecitationEngineId.WHISPER) },
        )
        if (selected == RecitationEngineId.WHISPER && state.whisperAvailable) {
            WhisperModelRow(state.whisperModel, onWhisperDownload, onWhisperDelete)
        }
        EngineRow(
            title = stringResource(R.string.recitation_engine_android),
            desc = stringResource(R.string.recitation_engine_android_desc),
            selected = selected == RecitationEngineId.ANDROID,
            available = state.androidAvailable,
            onClick = { onSelect(RecitationEngineId.ANDROID) },
        )
        Text(
            text = stringResource(R.string.recitation_disclaimer),
            color = paper.textMuted,
            fontSize = 12.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun EngineRow(title: String, desc: String, selected: Boolean, available: Boolean, onClick: () -> Unit) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) paper.accent else paper.tone300)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, shape)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val fg = if (selected) paper.pageBackground else paper.textStrong
        Text(title, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(
            text = if (available) desc else stringResource(R.string.recitation_engine_unavailable),
            color = if (selected) paper.pageBackground.copy(alpha = 0.85f) else paper.textMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun WhisperModelRow(state: ModelState, onDownload: () -> Unit, onDelete: () -> Unit) {
    val paper = LocalPaperColors.current
    when (state) {
        is ModelState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.recitation_whisper_downloading, (state.progress * 100).toInt()), color = paper.textMuted, fontSize = 12.sp)
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth(), color = paper.accent)
        }
        ModelState.Ready -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.recitation_whisper_ready), color = paper.textStrong, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            SmallPill(stringResource(R.string.recitation_whisper_delete), Modifier.weight(1f), onDelete)
        }
        is ModelState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.recitation_whisper_failed, state.message), color = paper.textMuted, fontSize = 12.sp)
            SmallPill(stringResource(R.string.recitation_whisper_download), Modifier.fillMaxWidth(), onDownload)
        }
        ModelState.NotDownloaded -> SmallPill(stringResource(R.string.recitation_whisper_download), Modifier.fillMaxWidth(), onDownload)
        ModelState.Unsupported -> Unit
    }
}

@Composable
private fun SmallPill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val paper = LocalPaperColors.current
    val shape = GoldCornerFrameShape(12.dp)
    Text(
        text = label,
        color = paper.textStrong,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(shape)
            .background(paper.tone300)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 10.dp),
    )
}

