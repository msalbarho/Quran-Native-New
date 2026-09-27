package com.quransunah.app.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalPaperColors

@Composable
fun TafsirSheet(
    surahName: String,
    ayah: Int,
    loading: Boolean,
    text: String?,
    error: String?,
    onDismiss: () -> Unit,
    bodySizeSp: Float = 17f,
) {
    val inspection = LocalInspectionMode.current
    if (inspection) {
        TafsirSheetContent(
            surahName = surahName,
            ayah = ayah,
            loading = loading,
            text = text,
            error = error,
            onDismiss = onDismiss,
            bodySizeSp = bodySizeSp,
        )
        return
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        TafsirSheetContent(
            surahName = surahName,
            ayah = ayah,
            loading = loading,
            text = text,
            error = error,
            onDismiss = onDismiss,
            bodySizeSp = bodySizeSp,
        )
    }
}

@Composable
fun TafsirSheetContent(
    surahName: String,
    ayah: Int,
    loading: Boolean,
    text: String?,
    error: String?,
    onDismiss: () -> Unit,
    bodySizeSp: Float = 17f,
) {
    val paper = LocalPaperColors.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .border(1.dp, paper.tone500, shape)
            .background(paper.pageBody),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper.tone300)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(
                    R.string.tafsir_title_with_ref,
                    surahName,
                    EasternArabic.format(ayah),
                ),
                color = paper.textStrong,
                fontFamily = LocalDisplayFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Text("×", color = paper.textStrong, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when {
                loading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(24.dp),
                        color = paper.accent,
                    )
                    Text(
                        text = stringResource(R.string.tafsir_loading),
                        color = paper.textMuted,
                    )
                }
                !error.isNullOrBlank() && text.isNullOrBlank() -> {
                    Text(
                        text = error,
                        color = paper.accent,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                text.isNullOrBlank() -> {
                    Text(
                        text = stringResource(R.string.tafsir_empty),
                        color = paper.textMuted,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    Text(
                        text = text,
                        color = paper.textPrimary,
                        fontFamily = LocalAppFontFamily.current,
                        fontSize = bodySizeSp.sp,
                        lineHeight = (bodySizeSp * 1.75f).sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@ArabicPreviews
@Composable
private fun TafsirSheetPreview() {
    PreviewTheme {
        TafsirSheetContent(
            surahName = "البقرة",
            ayah = 255,
            loading = false,
            text = "الله لا إله إلا هو الحي القيوم، لا تأخذه سنة ولا نوم، وهو القائم على كل نفس بما كسبت.",
            error = null,
            onDismiss = {},
        )
    }
}
