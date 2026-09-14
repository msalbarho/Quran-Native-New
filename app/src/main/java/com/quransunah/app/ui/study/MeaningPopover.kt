package com.quransunah.app.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.mushaf.WordRecord
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

@Composable
fun MeaningPopover(
    word: WordRecord,
    onDismiss: () -> Unit,
) {
    if (LocalInspectionMode.current) {
        MeaningPopoverCard(word = word, onDismiss = onDismiss)
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
        ),
    ) {
        MeaningPopoverCard(word = word, onDismiss = onDismiss)
    }
}

@Composable
fun MeaningPopoverCard(
    word: WordRecord,
    onDismiss: () -> Unit,
) {
    val paper = LocalPaperColors.current
    Column(
        modifier = Modifier
            .widthIn(min = 220.dp, max = 320.dp)
            .wrapContentHeight()
            .shadow(16.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(paper.pageBody)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = word.uthmanic.ifBlank { word.qcfLigature },
            color = paper.accent,
            fontFamily = LocalDisplayFontFamily.current,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(
                R.string.meaning_location,
                EasternArabic.format(word.surah),
                EasternArabic.format(word.ayah),
            ),
            color = paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Text(
            text = word.meaning.orEmpty(),
            color = paper.textPrimary,
            fontFamily = LocalAppFontFamily.current,
            fontSize = 16.sp,
            lineHeight = 26.sp,
            textAlign = TextAlign.Right,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.close),
            color = if (LocalNightMode.current) Color.White else Color(0xFFD10000),
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.Start)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onDismiss)
                .padding(horizontal = 6.dp, vertical = 8.dp),
        )
    }
}

@ArabicPreviews
@Composable
private fun MeaningPopoverPreview() {
    PreviewTheme {
        MeaningPopoverCard(word = PreviewFixtures.word, onDismiss = {})
    }
}
