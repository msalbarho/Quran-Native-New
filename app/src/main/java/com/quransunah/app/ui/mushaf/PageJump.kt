package com.quransunah.app.ui.mushaf

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.quransunah.app.R
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.shell.ChromeFrameDecor
import com.quransunah.app.ui.theme.LocalPaperColors

private val PageNumberHeight = 24.dp
private val PageNumberWidth = PageNumberHeight * 364f / 151f
private val ErrorRed = Color(0xFFB42318)

@Composable
fun PageNumberBadge(
    pageNumber: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val label = stringResource(
        R.string.page_select_label,
        EasternArabic.format(pageNumber),
    )
    Box(
        modifier = modifier
            .width(PageNumberWidth)
            .height(PageNumberHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ChromeFrameDecor(modifier = Modifier.matchParentSize())
        Text(
            text = EasternArabic.format(pageNumber),
            color = paper.darkAccent,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun PagePickerDialog(
    pageNumber: Int,
    onDismiss: () -> Unit,
    onJump: (Int) -> Unit,
) {
    val paper = LocalPaperColors.current
    val focus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val minLabel = EasternArabic.format(1)
    val maxLabel = EasternArabic.format(AppConstants.TOTAL_PAGES)
    val rangeError = stringResource(R.string.page_picker_out_of_range, minLabel, maxLabel)
    val emptyError = stringResource(R.string.page_picker_placeholder)

    fun tryJump() {
        val digits = EasternArabic.westernDigits(query)
        val target = digits.toIntOrNull()
        when {
            target == null -> error = emptyError
            target !in 1..AppConstants.TOTAL_PAGES -> error = rangeError
            else -> {
                onJump(target)
                onDismiss()
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper.pageBody, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.page_picker_title),
                color = paper.textStrong,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.page_picker_hint,
                    EasternArabic.format(pageNumber),
                    minLabel,
                    maxLabel,
                ),
                color = paper.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    error = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus),
                singleLine = true,
                textStyle = TextStyle(
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = paper.textStrong,
                ),
                placeholder = {
                    Text(
                        text = stringResource(
                            R.string.page_picker_example,
                            EasternArabic.format(pageNumber),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                },
                isError = error != null,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { tryJump() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = paper.accent,
                    cursorColor = paper.accent,
                ),
            )
            if (error != null) {
                Text(
                    text = error!!,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            TextButton(
                onClick = { tryJump() },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text = stringResource(R.string.page_picker_go),
                    color = paper.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}
