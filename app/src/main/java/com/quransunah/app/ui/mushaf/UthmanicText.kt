package com.quransunah.app.ui.mushaf

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.quransunah.app.fonts.QcfFontManager

@Composable
fun UthmanicText(
    text: String,
    fontManager: QcfFontManager?,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 18.sp,
    lineHeight: TextUnit = 30.sp,
    maxLines: Int = 3,
    textAlign: TextAlign = TextAlign.Center,
) {
    val inspection = LocalInspectionMode.current
    var typeface by remember { mutableStateOf(fontManager?.peekUthmanicTypeface()) }
    LaunchedEffect(fontManager) {
        if (fontManager != null && !inspection) {
            typeface = fontManager.loadUthmanicTypeface()
        }
    }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontFamily = typeface?.let { FontFamily(it) } ?: FontFamily.Serif,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}
