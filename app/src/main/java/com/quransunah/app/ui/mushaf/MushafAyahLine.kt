package com.quransunah.app.ui.mushaf

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.ThemeTokens

@Composable
fun MushafAyahLine(
    words: List<QuranWord>,
    selectedWordId: Int,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    onSelectWord: (QuranWord) -> Unit,
    showAyahMarkerBackdrop: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val inspection = LocalInspectionMode.current
    var pageFace by remember(pageNumber, fontManager) {
        mutableStateOf(fontManager?.peekPageTypeface(pageNumber))
    }
    var uthmanicFace by remember(fontManager) {
        mutableStateOf(fontManager?.peekUthmanicTypeface())
    }

    LaunchedEffect(pageNumber, fontManager) {
        if (fontManager != null && !inspection) {
            pageFace = fontManager.loadPageTypeface(pageNumber)
            uthmanicFace = fontManager.loadUthmanicTypeface()
        }
    }

    val glyphSize = when {
        words.size <= 10 -> 26.sp
        words.size <= 24 -> 31.sp
        else -> 30.sp
    }
    val glyphColor = if (night) Color.White else Color.Black
    val markerColor = ThemeTokens.AyahMarker

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            words.forEachIndexed { index, word ->
                val selected = word.id == selectedWordId && !word.isAyahMarker
                MushafAyahWordGlyph(
                    word = word,
                    previous = words.getOrNull(index - 1),
                    next = words.getOrNull(index + 1),
                    pageNumber = pageNumber,
                    fontManager = fontManager,
                    pageFace = pageFace,
                    uthmanicFace = uthmanicFace,
                    selected = selected,
                    glyphSize = glyphSize,
                    glyphColor = if (word.isAyahMarker) markerColor else glyphColor,
                    highlight = paper.ayahHighlight,
                    accent = paper.accent,
                    showAyahMarkerBackdrop = showAyahMarkerBackdrop,
                    onSelect = { onSelectWord(word) },
                )
            }
        }
    }
}

@Composable
private fun MushafAyahWordGlyph(
    word: QuranWord,
    previous: QuranWord?,
    next: QuranWord?,
    pageNumber: Int,
    fontManager: QcfFontManager?,
    pageFace: Typeface?,
    uthmanicFace: Typeface?,
    selected: Boolean,
    glyphSize: TextUnit,
    glyphColor: Color,
    highlight: Color,
    accent: Color,
    showAyahMarkerBackdrop: Boolean,
    onSelect: () -> Unit,
) {
    val remapped = remember(word.textLigature, pageNumber, fontManager) {
        if (word.textLigature.isBlank() || fontManager == null) {
            word.textLigature
        } else {
            fontManager.remapLigature(word.textLigature, pageNumber)
        }
    }
    val record = remember(word) { word.toWordRecord() }
    val previousRecord = remember(previous) { previous?.toWordRecord() }
    val nextRecord = remember(next) { next?.toWordRecord() }
    val overlay = remember(record.id, nextRecord?.id) { SajdahAyah.overlayLigature(record, nextRecord) }
    val number = remember(record.id, previousRecord?.id) { SajdahAyah.numberLigature(record, previousRecord) }
    val display = buildString {
        if (!overlay.isNullOrEmpty()) append(overlay)
        append(remapped.ifBlank { word.textHafs })
    }
    val rubParts = remember(word.textHafs, display) {
        RubElHizb.parts(word.textHafs, display)
    }
    val family = when {
        pageFace != null && remapped.isNotBlank() -> FontFamily(pageFace)
        uthmanicFace != null -> FontFamily(uthmanicFace)
        else -> FontFamily.Serif
    }
    val shape = RoundedCornerShape(6.dp)
    val night = LocalNightMode.current
    val size = if (word.isAyahMarker) ayahMarkerGlyphSize(glyphSize) else glyphSize
    val markerStyle = ayahMarkerTextStyle(size)

    if (word.isAyahMarker) {
        val markerContent: @Composable () -> Unit = {
            MushafAyahGlyphContent(
                rubParts = rubParts,
                display = display,
                glyphColor = glyphColor,
                markerStyle = markerStyle,
                family = family,
            )
        }
        if (showAyahMarkerBackdrop) {
            AyahMarkerBackdrop(fontSize = glyphSize, night = night, content = markerContent)
        } else {
            markerContent()
        }
        return
    }

    Box(
        modifier = Modifier
            .padding(horizontal = 1.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(highlight)
                        .border(2.dp, accent.copy(alpha = 0.55f), shape)
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onSelect)
            .semantics { this.selected = selected }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        val glyph: @Composable () -> Unit = {
            if (rubParts == null) {
                Text(
                    text = display,
                    color = glyphColor,
                    fontSize = size,
                    lineHeight = glyphSize * 2.1f,
                    fontFamily = family,
                    maxLines = 1,
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rubParts.markerLigature,
                        color = glyphColor,
                        fontSize = size,
                        lineHeight = glyphSize * 2.1f,
                        fontFamily = family,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(2.dp))
                    if (rubParts.bodyLigature.isNotEmpty()) {
                        Text(
                            text = rubParts.bodyLigature,
                            color = glyphColor,
                            fontSize = size,
                            lineHeight = glyphSize * 2.1f,
                            fontFamily = family,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (number.isNullOrEmpty()) {
            glyph()
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                glyph()
                AyahMarkerBackdrop(fontSize = glyphSize, night = night) {
                    Text(
                        text = number,
                        color = glyphColor,
                        style = markerStyle,
                        fontFamily = family,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun MushafAyahGlyphContent(
    rubParts: RubElHizb.Parts?,
    display: String,
    glyphColor: Color,
    markerStyle: TextStyle,
    family: FontFamily,
) {
    if (rubParts == null) {
        Text(
            text = display,
            color = glyphColor,
            style = markerStyle,
            fontFamily = family,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rubParts.markerLigature,
                color = glyphColor,
                style = markerStyle,
                fontFamily = family,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            if (rubParts.bodyLigature.isNotEmpty()) {
                Text(
                    text = rubParts.bodyLigature,
                    color = glyphColor,
                    style = markerStyle,
                    fontFamily = family,
                    maxLines = 1,
                )
            }
        }
    }
}
