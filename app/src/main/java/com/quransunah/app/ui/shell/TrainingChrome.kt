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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.theme.LocalPaperColors

internal val TrainingBottomBarHeight = 50.dp

private val BarShape = RoundedCornerShape(16.dp)

@Composable
internal fun TrainingTopBar(
    onNextWord: () -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    showAllEnabled: Boolean,
    hideAllEnabled: Boolean,
    hintActive: Boolean,
    onToggleHint: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Row(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 10.dp)
            .padding(top = 3.dp, bottom = 3.dp)
            .fillMaxWidth()
            .height(36.dp)
            .clip(BarShape)
            .background(paper.chromeFill)
            .border(1.dp, paper.accent.copy(alpha = 0.28f), BarShape)
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TrainingAction(
            iconRes = R.drawable.ic_format_size,
            label = stringResource(R.string.training_next_word),
            enabled = true,
            onClick = onNextWord,
            iconSize = 14.dp,
            modifier = Modifier.weight(1.15f),
        )
        TrainingAction(
            iconRes = R.drawable.ic_visibility,
            label = stringResource(R.string.training_show_all),
            contentDescription = stringResource(R.string.training_show_all_accessibility),
            enabled = showAllEnabled,
            onClick = onShowAll,
            iconSize = 14.dp,
            modifier = Modifier.weight(1f),
        )
        TrainingAction(
            iconRes = R.drawable.ic_visibility_off_eye,
            label = stringResource(R.string.training_hide_all),
            contentDescription = stringResource(R.string.training_hide_all_accessibility),
            enabled = hideAllEnabled,
            onClick = onHideAll,
            iconSize = 14.dp,
            modifier = Modifier.weight(1f),
        )
        TrainingAction(
            iconRes = R.drawable.ic_guide_touch,
            label = stringResource(R.string.training_hint),
            enabled = true,
            onClick = onToggleHint,
            selected = hintActive,
            iconSize = 14.dp,
            modifier = Modifier.weight(0.7f),
        )
        TrainingAction(
            iconRes = R.drawable.ic_more_horiz,
            label = stringResource(R.string.training_more),
            enabled = true,
            onClick = onMore,
            iconSize = 16.dp,
            modifier = Modifier.weight(0.75f),
        )
    }
}

@Composable
internal fun TrainingBottomBar(
    selectedAyah: Int?,
    canAssess: Boolean,
    notice: String?,
    onClose: () -> Unit,
    onListen: () -> Unit,
    onNeedsReview: () -> Unit,
    onMemorized: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (notice != null) {
            Text(
                text = notice,
                color = paper.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        // Theme is RTL. This row stays Ltr, which the previous screenshot confirmed
        // places the first child on the physical left. Order below is that screen order.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(TrainingBottomBarHeight)
                .clip(BarShape)
                .background(paper.chromeFill)
                .border(1.dp, paper.accent.copy(alpha = 0.28f), BarShape)
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainingAction(
                iconRes = R.drawable.ic_close,
                label = stringResource(R.string.training_close),
                contentDescription = stringResource(R.string.training_close_accessibility),
                enabled = true,
                onClick = onClose,
                modifier = Modifier.weight(1f),
            )
            TrainingAction(
                iconRes = R.drawable.ic_check_circle,
                label = stringResource(R.string.training_mark_memorized),
                enabled = canAssess,
                onClick = onMemorized,
                modifier = Modifier.weight(1f),
            )
            TrainingAction(
                iconRes = R.drawable.ic_refresh,
                label = stringResource(R.string.training_rate_review),
                enabled = canAssess,
                onClick = onNeedsReview,
                modifier = Modifier.weight(1.15f),
            )
            TrainingAction(
                iconRes = R.drawable.ic_headset,
                label = stringResource(R.string.training_listen),
                contentDescription = stringResource(R.string.training_listen_accessibility),
                enabled = true,
                onClick = onListen,
                modifier = Modifier.weight(1f),
            )
            if (selectedAyah != null) {
                val verseLabel = stringResource(
                    R.string.training_selected_verse,
                    EasternArabic.format(selectedAyah),
                )
                Text(
                    text = EasternArabic.format(selectedAyah),
                    color = paper.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .width(28.dp)
                        .semantics { contentDescription = verseLabel },
                )
            }
        }
        }
    }
}

@Composable
internal fun TrainingMoreDialog(
    onResume: () -> Unit,
    onHistory: () -> Unit,
    onProgress: () -> Unit,
    onDismiss: () -> Unit,
) {
    ThemedListDialog(
        title = stringResource(R.string.training_more),
        onDismiss = onDismiss,
    ) {
        TrainingMoreRow(
            label = stringResource(R.string.training_continue_last),
            onClick = onResume,
        )
        TrainingMoreRow(
            label = stringResource(R.string.training_log),
            onClick = onHistory,
        )
        TrainingMoreRow(
            label = stringResource(R.string.training_my_progress),
            onClick = onProgress,
        )
    }
}

@Composable
private fun TrainingMoreRow(
    label: String,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(14.dp)
    Text(
        text = label,
        color = paper.textStrong,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(paper.accent.copy(alpha = 0.08f))
            .border(1.dp, paper.accent.copy(alpha = 0.22f), shape)
            .clickable(role = Role.Button, onClick = onClick)
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
    iconSize: Dp = 18.dp,
    selected: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val color = if (enabled) paper.textStrong else paper.textMuted.copy(alpha = 0.4f)
    Column(
        modifier = modifier
            .background(if (selected) paper.accentSoft else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription; this.selected = selected }
            .padding(horizontal = 2.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(iconSize),
        )
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
