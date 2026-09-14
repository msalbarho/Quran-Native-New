package com.quransunah.app.ui.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quransunah.app.R
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

val BottomNavBarHeight = 50.dp
private val NavCornerRadius = ChromeTokens.Corner
private val NavBubbleWidth = 56.dp
private val NavBubbleHeight = 42.dp

private data class NavItemSpec(
    val id: String,
    val painter: Int,
    val labelRes: Int,
    val iconSize: androidx.compose.ui.unit.Dp,
    val onClick: () -> Unit,
    val selected: Boolean,
)

@Composable
fun CurvedBottomNav(
    activeTab: AppTab,
    onTabChange: (AppTab) -> Unit,
    onIndexPress: () -> Unit,
    onBookmarkPress: () -> Unit,
    onSettingsPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val bubbleFill = lerp(paper.tone500, paper.tone300, 0.58f)
    val bubbleIcon = if (night) Color.White else paper.textStrong
    val inactive = paper.textMuted
    val density = LocalDensity.current
    val bubbleWidthPx = with(density) { NavBubbleWidth.toPx() }

    val items = listOf(
        NavItemSpec(
            id = "settings",
            painter = R.drawable.ic_settings,
            labelRes = R.string.tab_settings,
            iconSize = 25.dp,
            onClick = onSettingsPress,
            selected = false,
        ),
        NavItemSpec(
            id = "bookmarks",
            painter = R.drawable.ic_bookmark,
            labelRes = R.string.training_tab,
            iconSize = 26.dp,
            onClick = onBookmarkPress,
            selected = false,
        ),
        NavItemSpec(
            id = "audio",
            painter = R.drawable.ic_headset,
            labelRes = R.string.tab_listening,
            iconSize = 26.dp,
            onClick = { onTabChange(AppTab.Listening) },
            selected = activeTab == AppTab.Listening,
        ),
        NavItemSpec(
            id = "quran",
            painter = R.drawable.ic_nav_quran,
            labelRes = R.string.tab_reading,
            iconSize = 26.dp,
            onClick = { onTabChange(AppTab.Reading) },
            selected = activeTab == AppTab.Reading,
        ),
        NavItemSpec(
            id = "index",
            painter = R.drawable.ic_format_list_bulleted,
            labelRes = R.string.tab_index,
            iconSize = 26.dp,
            onClick = onIndexPress,
            selected = false,
        ),
    )

    var centers by remember { mutableStateOf(FloatArray(items.size)) }
    val activeIndex = if (activeTab == AppTab.Listening) 2 else 3
    val targetCenter = centers.getOrElse(activeIndex) { 0f }
    val bubbleX by animateFloatAsState(
        targetValue = targetCenter,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "nav-bubble",
    )

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = ChromeTokens.HorizontalInset, vertical = 0.dp),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(BottomNavBarHeight)
                    .shadow(8.dp, RoundedCornerShape(NavCornerRadius), clip = false)
                    .clip(RoundedCornerShape(NavCornerRadius))
                    .background(paper.chromeFill)
                    .border(1.6.dp, ChromeTokens.Gold, RoundedCornerShape(NavCornerRadius)),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val tile = 22.dp.toPx()
                    val stroke = paper.decorPattern
                    var y = 0f
                    while (y < size.height + tile) {
                        var x = 0f
                        while (x < size.width + tile) {
                            drawGirihStar(Offset(x + tile / 2f, y + tile / 2f), tile * 0.36f, stroke)
                            x += tile
                        }
                        y += tile
                    }
                    drawRect(
                        color = ChromeTokens.GoldInner,
                        style = Stroke(width = 1.dp.toPx()),
                    )
                }

                if (bubbleX > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset {
                                IntOffset((bubbleX - bubbleWidthPx / 2f).roundToInt(), 0)
                            }
                            .width(NavBubbleWidth)
                            .height(NavBubbleHeight)
                            .shadow(6.dp, RoundedCornerShape(NavCornerRadius))
                            .clip(RoundedCornerShape(NavCornerRadius))
                            .background(bubbleFill)
                            .border(
                                1.dp,
                                paper.textStrong.copy(alpha = if (night) 0.16f else 0.18f),
                                RoundedCornerShape(NavCornerRadius),
                            ),
                    )
                }

                Row(Modifier.fillMaxSize()) {
                    items.forEachIndexed { index, item ->
                        val tint = if (item.selected) bubbleIcon else inactive
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .onGloballyPositioned { coords ->
                                    val center = coords.positionInParent().x + coords.size.width / 2f
                                    val previous = centers.getOrElse(index) { 0f }
                                    if (kotlin.math.abs(previous - center) > 0.5f) {
                                        centers = centers.copyOf().also { it[index] = center }
                                    }
                                }
                                .clickable(
                                    interactionSource = remember(item.id) { MutableInteractionSource() },
                                    indication = null,
                                    onClick = item.onClick,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            val iconModifier = Modifier
                                .size(item.iconSize)
                                .graphicsLayer { scaleX = if (item.selected) 1.06f else 1f; scaleY = scaleX }
                            Icon(
                                painter = painterResource(item.painter),
                                contentDescription = stringResource(item.labelRes),
                                tint = tint,
                                modifier = iconModifier,
                            )
                            if (item.selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp)
                                        .width(22.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(paper.darkAccent),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGirihStar(
    center: Offset,
    outer: Float,
    color: Color,
) {
    val inner = outer * 0.41f
    val path = Path()
    for (i in 0 until 16) {
        val angle = (PI / 8.0) * i - PI / 2.0
        val radius = if (i % 2 == 0) outer else inner
        val x = center.x + (cos(angle) * radius).toFloat()
        val y = center.y + (sin(angle) * radius).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color, style = Stroke(width = 0.75.dp.toPx()))
}

@ArabicPreviews
@Composable
private fun CurvedBottomNavPreview() {
    PreviewTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(LocalPaperColors.current.pageBackground),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CurvedBottomNav(
                activeTab = AppTab.Reading,
                onTabChange = {},
                onIndexPress = {},
                onBookmarkPress = {},
                onSettingsPress = {},
            )
        }
    }
}
