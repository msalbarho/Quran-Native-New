package com.quransunah.app.ui.listening

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.fonts.QcfFontManager

@Composable
fun SurahLigatureText(
    surahNumber: Int,
    fontManager: QcfFontManager?,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 28.sp,
    fallback: String,
) {
    val inspection = LocalInspectionMode.current
    if (inspection || fontManager == null) {
        Text(
            text = fallback,
            color = color,
            fontSize = fontSize,
            fontFamily = FontFamily.Serif,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        )
        return
    }
    var typeface by remember { mutableStateOf(fontManager.peekSurahTitleTypeface()) }
    LaunchedEffect(surahNumber) {
        typeface = fontManager.loadSurahTitleTypeface()
            ?: fontManager.loadUthmanicTypeface()
    }
    val ligature = fontManager.surahNameLigature(surahNumber).orEmpty()
    val canDraw = remember(ligature, typeface) {
        fontManager.canDrawLigature(ligature, typeface)
    }
    val family = typeface?.let { FontFamily(it) } ?: FontFamily.Serif
    Text(
        text = if (canDraw) ligature else fallback,
        color = color,
        fontSize = fontSize,
        fontFamily = if (canDraw) family else FontFamily.Serif,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    )
}
