package com.quransunah.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Bold bar label that starts at [fontSize] and shrinks (down to [minFontSize]) until it fits,
 * so long translations are never clipped or ellipsized.
 */
@Composable
internal fun FitBarLabel(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    minFontSize: TextUnit = 7.sp,
    fontWeight: FontWeight = FontWeight.Bold,
) {
    var size by remember(text, fontSize) { mutableStateOf(fontSize) }
    var ready by remember(text, fontSize) { mutableStateOf(false) }
    Text(
        text = text,
        color = color,
        fontSize = size,
        lineHeight = (size.value * 1.18f).sp,
        fontWeight = fontWeight,
        textAlign = TextAlign.Center,
        maxLines = maxLines,
        softWrap = maxLines > 1,
        overflow = TextOverflow.Clip,
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r ->
            if ((r.hasVisualOverflow || r.didOverflowWidth) && size.value > minFontSize.value) {
                size = (size.value - 0.5f).coerceAtLeast(minFontSize.value).sp
            } else {
                ready = true
            }
        },
    )
}