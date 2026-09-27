package com.quransunah.app.ui.mushaf

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.HizbBadgeParts
import com.quransunah.app.core.HizbQuarterKind
import com.quransunah.app.ui.shell.ChromeFrameDecor
import com.quransunah.app.ui.shell.ChromeFrameOrientation
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val BadgeEasing = CubicBezierEasing(0.34f, 1.1f, 0.64f, 1f)

/** Shared margin-badge width (أرباع الأحزاب + سجدة) — half of prior 72.dp. */
internal val SideBadgeWidth = 36.dp
internal val HizbBadgeHeight = 96.dp
internal val SajdahBadgeHeight = 48.dp
/** Gap between paired hizb + sajdah squares (4–6px). */
internal val SideBadgePairGap = 5.dp

/** Physical top-right of the page (LTR box so End = screen right for the user). */
internal val MushafSideBadgeAlign = Alignment.TopEnd

/** Match header/nav chrome: same horizontal inset as [ChromeTokens.HorizontalInset]. */
internal fun mushafSideBadgeInsetPx(density: androidx.compose.ui.unit.Density): Int =
    with(density) { ChromeTokens.HorizontalInset.roundToPx() }

/** Match header/nav chrome inset on the physical right edge. */
internal fun Modifier.mushafSideBadgeAnchor(yOffsetPx: Int, density: androidx.compose.ui.unit.Density): Modifier =
    offset {
        IntOffset(-mushafSideBadgeInsetPx(density), yOffsetPx)
    }

@Composable
fun HizbFrameBadge(
    parts: HizbBadgeParts,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AnimatedVisibility(
            visible = visible,
            enter = badgeEnter(),
            exit = badgeExit(),
            modifier = modifier,
        ) {
            HizbBadgeBox(parts = parts, height = HizbBadgeHeight, onTap = onTap)
        }
    }
}

@Composable
fun SajdahFrameBadge(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AnimatedVisibility(
            visible = visible,
            enter = badgeEnter(),
            exit = badgeExit(),
            modifier = modifier,
        ) {
            SajdahBadgeBox(height = SajdahBadgeHeight, onTap = onTap)
        }
    }
}

/**
 * When hizb/quarter and sajdah share a line: two independent squares in a horizontal row.
 * RTL: hizb on the right (inline-start), sajdah immediately to its left, with a small gap.
 * The whole cluster fades/slides with chrome visibility.
 */
@Composable
fun MushafPairedSideBadges(
    hizbBadge: HizbBadgeParts,
    visible: Boolean,
    modifier: Modifier = Modifier,
    onTap: () -> Unit = {},
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        AnimatedVisibility(
            visible = visible,
            enter = badgeEnter(),
            exit = badgeExit(),
            modifier = modifier,
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(SideBadgePairGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HizbBadgeBox(parts = hizbBadge, height = HizbBadgeHeight, onTap = onTap)
                    SajdahBadgeBox(height = HizbBadgeHeight, onTap = onTap)
                }
            }
        }
    }
}

@Composable
private fun HizbBadgeBox(
    parts: HizbBadgeParts,
    height: Dp,
    onTap: () -> Unit,
) {
    val lines = hizbMarkerLines(parts)
    MushafSideBadgeBox(
        width = SideBadgeWidth,
        height = height,
        onTap = onTap,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
            ) {
                lines.forEach { line ->
                    val fraction = line == "¼" || line == "½" || line == "¾"
                    CenteredBadgeLine(
                        text = line,
                        maxFontSp = if (fraction) FractionFontMax else BadgeFontMax,
                    )
                }
            }
        }
    }
}

@Composable
private fun hizbMarkerLines(parts: HizbBadgeParts): List<String> {
    val number = EasternArabic.format(parts.number)
    if (ArabicRtl.currentTag(LocalContext.current) == "ar") {
        return when (parts.kind) {
            HizbQuarterKind.Full -> listOf("الحزب", number)
            HizbQuarterKind.Quarter -> listOf("ربع", "الحزب", number)
            HizbQuarterKind.Half -> listOf("نصف", "الحزب", number)
            HizbQuarterKind.ThreeQuarters -> listOf("ثلاثة", "أرباع", "الحزب", number)
        }
    }
    val term = stringResource(R.string.index_tab_hizb_short)
    return buildList {
        when (parts.kind) {
            HizbQuarterKind.Quarter -> add("¼")
            HizbQuarterKind.Half -> add("½")
            HizbQuarterKind.ThreeQuarters -> add("¾")
            HizbQuarterKind.Full -> Unit
        }
        add(term)
        add(number)
    }
}

@Composable
private fun SajdahBadgeBox(
    height: Dp,
    onTap: () -> Unit,
) {
    MushafSideBadgeBox(
        width = SideBadgeWidth,
        height = height,
        onTap = onTap,
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                CenteredBadgeLine(text = stringResource(R.string.index_tab_sajdah_short))
            }
        }
    }
}

@Composable
private fun CenteredBadgeLine(
    text: String,
    maxFontSp: Float = BadgeFontMax,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val family = LocalDisplayFontFamily.current
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        val maxWidth = constraints.maxWidth
        val fontSize = remember(text, maxWidth, family, maxFontSp) {
            fittedBadgeFontSize(measurer, text, family, maxWidth, maxFontSp)
        }
        Text(
            text = text,
            color = badgeLabelColor(),
            fontFamily = family,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            lineHeight = (fontSize.value + 2f).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun fittedBadgeFontSize(
    measurer: TextMeasurer,
    text: String,
    family: FontFamily,
    maxWidthPx: Int,
    maxFontSp: Float = BadgeFontMax,
): TextUnit {
    var size = maxFontSp
    if (maxWidthPx <= 0) return size.sp
    while (size > BadgeFontMin) {
        val layout = measurer.measure(
            text = AnnotatedString(text),
            style = TextStyle(
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = size.sp,
                textAlign = TextAlign.Center,
            ),
            overflow = TextOverflow.Clip,
            maxLines = 1,
            softWrap = false,
        )
        if (layout.size.width <= maxWidthPx) break
        size -= 0.25f
    }
    return size.sp
}

private const val BadgeFontMax = 9f
private const val FractionFontMax = 18f
private const val BadgeFontMin = 4f

@Composable
private fun MushafSideBadgeBox(
    width: Dp,
    height: Dp,
    onTap: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        ChromeFrameDecor(
            modifier = Modifier.matchParentSize(),
            orientation = ChromeFrameOrientation.Vertical,
        )
        content()
    }
}

private fun badgeEnter() =
    fadeIn(tween(300)) +
        slideInHorizontally(
            animationSpec = tween(380, easing = BadgeEasing),
            // LTR: slide in from physical screen right (React translate3d(110%, …)).
            initialOffsetX = { fullWidth -> (fullWidth * 1.1f).toInt() },
        )

private fun badgeExit() =
    fadeOut(tween(300)) +
        slideOutHorizontally(
            animationSpec = tween(380, easing = BadgeEasing),
            targetOffsetX = { fullWidth -> (fullWidth * 1.1f).toInt() },
        )

@Composable
private fun badgeLabelColor(): Color {
    val night = LocalNightMode.current
    val paper = LocalPaperColors.current
    return if (night) Color.White else paper.textStrong
}
