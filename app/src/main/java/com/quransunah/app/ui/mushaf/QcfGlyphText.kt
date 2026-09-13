package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.quransunah.app.fonts.QcfFontManager

@Composable
fun QcfGlyphText(
    ligature: String,
    fallback: String,
    fontManager: QcfFontManager?,
    color: Color,
    modifier: Modifier = Modifier,
    ligatureSize: TextUnit = 22.sp,
    fallbackSize: TextUnit = 15.sp,
    maxLines: Int = 1,
    titleTypeface: Typeface? = null,
    preferLigature: Boolean = false,
) {
    val inspection = LocalInspectionMode.current
    val typeface = titleTypeface ?: if (inspection) {
        null
    } else {
        fontManager?.peekSurahTitleTypeface()
    }
    var loadedFace by remember(titleTypeface) { mutableStateOf(typeface) }
    if (titleTypeface == null && fontManager != null && !inspection) {
        LaunchedEffect(fontManager) {
            loadedFace = fontManager.loadSurahTitleTypeface() ?: fontManager.loadUthmanicTypeface()
        }
    }
    val face = titleTypeface ?: loadedFace
    val canDraw = remember(ligature, face) {
        fontManager?.canDrawLigature(ligature, face) == true
    }
    val useLigature = canDraw || (preferLigature && face != null && ligature.isNotBlank())
    Text(
        text = if (useLigature) ligature else fallback,
        color = color,
        fontSize = if (useLigature) ligatureSize else fallbackSize,
        fontFamily = if (useLigature && face != null) {
            FontFamily(face)
        } else {
            FontFamily.Serif
        },
        fontWeight = FontWeight.Normal,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        overflow = if (preferLigature) TextOverflow.Visible else TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
