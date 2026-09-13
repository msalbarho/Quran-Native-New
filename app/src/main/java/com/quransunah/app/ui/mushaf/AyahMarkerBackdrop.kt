package com.quransunah.app.ui.mushaf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.quransunah.app.R

/** Source assets `ayah_nr.png` / `ayah_nr_night.png` (100×129). */
internal const val AYAH_MARKER_ASPECT = 100f / 129f

/** Ayah-marker glyph is slightly smaller than body text (matches React / TextMushaf). */
internal const val AYAH_MARKER_GLYPH_SCALE = 0.92f

/** Badge height relative to ayah-marker glyph size (not line slot). */
private const val BADGE_HEIGHT_FACTOR = 1.22f

/** Visual centroid offset from bitmap geometric center (asset analysis). */
private const val AYAH_MARKER_VISUAL_CENTER_X_FACTOR = -0.00486157253601117f
private const val AYAH_MARKER_VISUAL_CENTER_Y_FACTOR = 0.02598449612403178f

/** Fine-tune after QCF ligature bounds vs. medallion art (canvas + Compose). */
private const val AYAH_MARKER_CALIBRATION_X_EM = -0.01f
private const val AYAH_MARKER_CALIBRATION_Y_EM = 0.055f

internal fun ayahMarkerBadgeCenter(
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
): Pair<Float, Float> {
    val bounds = Rect()
    if (text.isNotEmpty()) {
        paint.getTextBounds(text, 0, text.length, bounds)
    } else {
        val half = (paint.textSize * 0.3f).toInt()
        bounds.set(-half, -paint.textSize.toInt(), half, 0)
    }
    val fm = paint.fontMetrics
    val textCenterX = x + (bounds.left + bounds.right) / 2f
    val textCenterY = baseline + (bounds.top + bounds.bottom) / 2f
    val glyphSizePx = paint.textSize * AYAH_MARKER_GLYPH_SCALE
    val badgeHeight = ayahMarkerBadgeHeightPx(glyphSizePx)
    val badgeWidth = ayahMarkerBadgeWidthPx(badgeHeight)
    val badgeCenterX =
        textCenterX -
            badgeWidth * AYAH_MARKER_VISUAL_CENTER_X_FACTOR +
            glyphSizePx * AYAH_MARKER_CALIBRATION_X_EM
    val badgeCenterY =
        textCenterY -
            badgeHeight * AYAH_MARKER_VISUAL_CENTER_Y_FACTOR +
            glyphSizePx * AYAH_MARKER_CALIBRATION_Y_EM
    return badgeCenterX to badgeCenterY
}

internal fun ayahMarkerBackdropOffset(
    badgeWidth: Dp,
    badgeHeight: Dp,
    glyphSize: TextUnit,
): Pair<Dp, Dp> {
    val calX = (glyphSize.value * AYAH_MARKER_CALIBRATION_X_EM).dp
    val calY = (glyphSize.value * AYAH_MARKER_CALIBRATION_Y_EM).dp
    val offsetX = -badgeWidth * AYAH_MARKER_VISUAL_CENTER_X_FACTOR + calX
    val offsetY = -badgeHeight * AYAH_MARKER_VISUAL_CENTER_Y_FACTOR + calY
    return offsetX to offsetY
}

internal fun ayahMarkerGlyphSize(fontSize: TextUnit): TextUnit =
    fontSize * AYAH_MARKER_GLYPH_SCALE

internal fun ayahMarkerBadgeHeight(fontSize: TextUnit): Dp =
    (fontSize.value * BADGE_HEIGHT_FACTOR).dp

internal fun ayahMarkerBadgeWidth(height: Dp): Dp =
    (height.value * AYAH_MARKER_ASPECT).dp

internal fun ayahMarkerBadgeHeightPx(glyphSizePx: Float): Float =
    glyphSizePx * BADGE_HEIGHT_FACTOR

internal fun ayahMarkerBadgeWidthPx(heightPx: Float): Float =
    heightPx * AYAH_MARKER_ASPECT

internal fun ayahMarkerBackdropDrawable(night: Boolean): Int =
    if (night) R.drawable.ayah_nr_night else R.drawable.ayah_nr

internal fun ayahMarkerTextStyle(fontSize: TextUnit): TextStyle =
    TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )

/** Tight rect centered on the ayah-number glyph only. */
internal fun ayahMarkerBadgeRect(
    paint: Paint,
    text: String,
    x: Float,
    baseline: Float,
): RectF {
    val glyphSizePx = paint.textSize * AYAH_MARKER_GLYPH_SCALE
    val badgeHeight = ayahMarkerBadgeHeightPx(glyphSizePx)
    val badgeWidth = ayahMarkerBadgeWidthPx(badgeHeight)
    val (badgeCenterX, badgeCenterY) = ayahMarkerBadgeCenter(paint, text, x, baseline)
    return RectF(
        badgeCenterX - badgeWidth / 2f,
        badgeCenterY - badgeHeight / 2f,
        badgeCenterX + badgeWidth / 2f,
        badgeCenterY + badgeHeight / 2f,
    )
}

internal fun Canvas.drawAyahMarkerBackdrop(bitmap: Bitmap?, rect: RectF) {
    if (bitmap == null || rect.width() <= 0f || rect.height() <= 0f) return
    val dest = Rect(
        rect.left.toInt(),
        rect.top.toInt(),
        rect.right.toInt(),
        rect.bottom.toInt(),
    )
    drawBitmap(bitmap, null, dest, null)
}

/**
 * Decorative medallion behind a single ayah-end digit — day/night assets (no tint).
 */
@Composable
fun AyahMarkerBackdrop(
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    night: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glyphSize = ayahMarkerGlyphSize(fontSize)
    val badgeHeight = ayahMarkerBadgeHeight(glyphSize)
    val badgeWidth = ayahMarkerBadgeWidth(badgeHeight)
    val (offsetX, offsetY) = ayahMarkerBackdropOffset(badgeWidth, badgeHeight, glyphSize)
    Box(
        modifier = modifier
            .width(badgeWidth)
            .height(badgeHeight),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(ayahMarkerBackdropDrawable(night)),
            contentDescription = null,
            modifier = Modifier
                .matchParentSize()
                .offset(x = offsetX, y = offsetY),
            contentScale = ContentScale.Fit,
        )
        content()
    }
}
