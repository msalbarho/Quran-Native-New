package com.quransunah.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quransunah.app.R
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val DialogShape = RoundedCornerShape(30.dp)
private val ChoiceShape = RoundedCornerShape(14.dp)

@Composable
fun ThemedListDialog(
    title: String,
    onDismiss: () -> Unit,
    header: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val paper = LocalPaperColors.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val listMax = (maxHeight * 0.85f - 148.dp).coerceAtLeast(120.dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, DialogShape)
                    .clip(DialogShape)
                    .border(1.dp, paper.accent.copy(alpha = 0.16f), DialogShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(paper.chromeFill, paper.pageBody, paper.pageBackground),
                        ),
                    ),
            ) {
            IslamicEdgePattern(
                Modifier.matchParentSize(),
                fade = IslamicOrnamentFade.Vertical,
            )
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(paper.textMuted.copy(alpha = 0.45f))
                        .clearAndSetSemantics { },
                )
                Text(
                    text = title,
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                )
                header()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listMax)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = content,
                )
                HorizontalDivider(color = paper.accent.copy(alpha = 0.16f))
                Text(
                    text = stringResource(R.string.close),
                    color = paper.pageBackground,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(paper.accent)
                        .clickable(role = Role.Button, onClick = onDismiss)
                        .padding(vertical = 12.dp),
                )
            }
            }
        }
    }
}

@Composable
fun ThemedLanguageCard(
    mark: String,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    markSize: TextUnit,
    showCheck: Boolean = false,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val fill = if (active) {
        paper.accent.copy(alpha = if (night) 0.22f else 0.12f)
    } else if (night) {
        paper.tone400.copy(alpha = 0.72f)
    } else {
        lerp(paper.surface, paper.chromeFill, 0.24f)
    }
    val stroke = if (active) {
        paper.accent.copy(alpha = 0.55f)
    } else {
        paper.accent.copy(alpha = if (night) 0.28f else 0.16f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(ChoiceShape)
            .background(fill)
            .border(1.dp, stroke, ChoiceShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = mark, fontSize = markSize)
        Text(
            text = label,
            color = paper.textStrong,
            fontSize = 16.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (active && showCheck) {
            Text(
                text = "✓",
                color = paper.accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
