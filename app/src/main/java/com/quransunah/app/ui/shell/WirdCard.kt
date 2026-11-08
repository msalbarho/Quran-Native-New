package com.quransunah.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.WirdPlan
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalPaperColors

private val WirdShape = GoldCornerFrameShape(16.dp)
private val WirdButtonShape = RoundedCornerShape(12.dp)

@Composable
fun WirdCard(
    state: WirdPlan.State?,
    todayEpochDay: Long,
    onStartReading: () -> Unit,
    onOpenSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val app = LocalAppFontFamily.current
    Column(
        modifier = modifier
            .clip(WirdShape)
            .background(paper.pageBody)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, WirdShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.wird_title),
                color = paper.textStrong,
                fontFamily = LocalDisplayFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                modifier = Modifier.weight(1f),
            )
            if (state != null) {
                val desc = stringResource(R.string.wird_change_plan)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(WirdButtonShape)
                        .semantics { contentDescription = desc }
                        .clickable(onClick = onOpenSetup),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        tint = paper.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        val body: String
        var status: String? = null
        val button: String
        val action: () -> Unit
        when {
            state == null -> {
                body = stringResource(R.string.wird_empty_body)
                button = stringResource(R.string.wird_setup_button)
                action = onOpenSetup
            }
            state.finished -> {
                body = stringResource(R.string.wird_khatma_done)
                button = stringResource(R.string.wird_new_khatma)
                action = onOpenSetup
            }
            else -> {
                val range = state.current!!
                body = stringResource(
                    R.string.wird_range,
                    EasternArabic.format(range.first),
                    EasternArabic.format(range.last),
                )
                val delta = state.scheduleDelta(todayEpochDay)
                status = when {
                    delta > 0 -> stringResource(R.string.wird_behind, EasternArabic.format(delta))
                    state.todayDone(todayEpochDay) -> stringResource(R.string.wird_today_done)
                    else -> null
                }
                button = stringResource(R.string.wird_start_reading)
                action = onStartReading
            }
        }

        Text(
            text = body,
            color = paper.textStrong,
            fontFamily = app,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
        )
        status?.let {
            Text(text = it, color = paper.accent, fontFamily = app, fontWeight = FontWeight.Medium, fontSize = 13.sp)
        }

        if (state != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ChromeTokens.GoldInner.copy(alpha = 0.5f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(ChromeTokens.Gold),
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(
                        R.string.wird_progress,
                        EasternArabic.format(state.completed),
                        EasternArabic.format(state.days),
                    ),
                    color = paper.textMuted,
                    fontFamily = app,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f),
                )
                if (!state.finished) {
                    Text(
                        text = stringResource(
                            R.string.wird_days_left,
                            EasternArabic.format(state.daysLeft(todayEpochDay).coerceAtLeast(1)),
                        ),
                        color = paper.textMuted,
                        fontFamily = app,
                        fontSize = 12.sp,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .height(44.dp)
                .clip(WirdButtonShape)
                .background(paper.accent)
                .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, WirdButtonShape)
                .clickable(onClick = action),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = button, color = Color.White, fontFamily = app, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

/** One-time khatma duration setup: 15 / 30 / 60 days or a custom number. */
@Composable
fun WirdSetupDialog(
    currentDays: Int?,
    onConfirm: (Int) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val app = LocalAppFontFamily.current
    var selected by remember { mutableStateOf(currentDays ?: 30) }
    var custom by remember {
        mutableStateOf(if (currentDays != null && currentDays !in WirdPlan.PRESET_DAYS) currentDays.toString() else "")
    }
    val customDays = custom.toIntOrNull()
    val days = if (custom.isNotBlank()) customDays else selected
    val valid = days != null && days in WirdPlan.MIN_DAYS..WirdPlan.MAX_DAYS

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = paper.pageBody,
        title = {
            Text(
                stringResource(R.string.wird_setup_title),
                fontFamily = LocalDisplayFontFamily.current,
                fontWeight = FontWeight.Bold,
                color = paper.textStrong,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.wird_setup_body), fontFamily = app, color = paper.textMuted, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf(30, 60, 15).forEach { d ->
                        val on = custom.isBlank() && selected == d
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(WirdButtonShape)
                                .background(if (on) paper.accent else paper.pageBackground)
                                .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, WirdButtonShape)
                                .clickable { selected = d; custom = "" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.wird_days_option, EasternArabic.format(d)),
                                color = if (on) Color.White else paper.textStrong,
                                fontFamily = app,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { v -> custom = v.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.wird_custom_days), fontFamily = app) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (valid) {
                    val perDay = kotlin.math.round(604f / days!!).toInt().coerceAtLeast(1)
                    Text(
                        stringResource(R.string.wird_pages_per_day, EasternArabic.format(perDay)),
                        fontFamily = app,
                        color = paper.accent,
                        fontSize = 13.sp,
                    )
                }
                if (currentDays != null) {
                    Text(stringResource(R.string.wird_restart_note), fontFamily = app, color = paper.textMuted, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { days?.let(onConfirm) }) {
                Text(stringResource(R.string.wird_setup_confirm), fontFamily = app, fontWeight = FontWeight.Bold, color = paper.accent)
            }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.wird_remove), fontFamily = app, color = paper.textMuted)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.wird_cancel), fontFamily = app, color = paper.textMuted)
                }
            }
        },
    )
}
