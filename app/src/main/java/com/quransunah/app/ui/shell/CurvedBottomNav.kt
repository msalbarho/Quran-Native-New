package com.quransunah.app.ui.shell

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlin.math.roundToInt

val BottomNavBarHeight = 50.dp
private val NavBarShape = RoundedCornerShape(18.dp)
private val NavBubbleShape = RoundedCornerShape(14.dp)
private val NavBubbleWidth = 56.dp
private val NavBubbleHeight = 42.dp
private val NavIconSize = 20.dp
private val NavIconLabelGap = 1.dp

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
    onTrainingPress: () -> Unit,
    onSettingsPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val bubbleIcon = paper.textStrong
    val inactive = paper.textMuted
    val density = LocalDensity.current
    val bubbleWidthPx = with(density) { NavBubbleWidth.toPx() }

    val items = listOf(
        NavItemSpec(
            id = "settings",
            painter = R.drawable.ic_settings,
            labelRes = R.string.tab_settings,
            iconSize = NavIconSize,
            onClick = onSettingsPress,
            selected = false,
        ),
        NavItemSpec(
            id = "training",
            painter = R.drawable.ic_training_brain,
            labelRes = R.string.training_tab,
            iconSize = NavIconSize,
            onClick = onTrainingPress,
            selected = activeTab == AppTab.Training,
        ),
        NavItemSpec(
            id = "audio",
            painter = R.drawable.ic_headset,
            labelRes = R.string.tab_listening,
            iconSize = NavIconSize,
            onClick = { onTabChange(AppTab.Listening) },
            selected = activeTab == AppTab.Listening,
        ),
        NavItemSpec(
            id = "quran",
            painter = R.drawable.ic_nav_quran,
            labelRes = R.string.tab_reading,
            iconSize = NavIconSize,
            onClick = { onTabChange(AppTab.Reading) },
            selected = activeTab == AppTab.Reading,
        ),
        NavItemSpec(
            id = "index",
            painter = R.drawable.ic_format_list_bulleted,
            labelRes = R.string.tab_index,
            iconSize = NavIconSize,
            onClick = onIndexPress,
            selected = false,
        ),
    )

    var centers by remember { mutableStateOf(FloatArray(items.size)) }
    val activeIndex = when (activeTab) {
        AppTab.Training -> 1
        AppTab.Listening -> 2
        else -> 3
    }
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
                .padding(horizontal = ChromeTokens.HorizontalInset),
        ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(BottomNavBarHeight)
                .clip(NavBarShape)
                .background(paper.chromeFill)
                .border(1.dp, paper.accent.copy(alpha = if (night) 0.40f else 0.26f), NavBarShape),
        ) {
        IslamicEdgePattern(Modifier.matchParentSize())
        Box(Modifier.fillMaxSize()) {
                if (bubbleX > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .offset {
                                IntOffset((bubbleX - bubbleWidthPx / 2f).roundToInt(), 0)
                            }
                            .width(NavBubbleWidth)
                            .height(NavBubbleHeight)
                            .clip(NavBubbleShape)
                            .background(paper.accent.copy(alpha = if (night) 0.22f else 0.12f))
                            .border(
                                1.dp,
                                paper.accent.copy(alpha = if (night) 0.46f else 0.32f),
                                NavBubbleShape,
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
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(NavIconLabelGap, Alignment.CenterVertically),
                            ) {
                                Image(
                                    painter = painterResource(item.painter),
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(tint),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                                    modifier = iconModifier,
                                )
                                Text(
                                    text = stringResource(item.labelRes),
                                    color = tint,
                                    fontSize = 9.sp,
                                    lineHeight = 11.sp,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                            if (item.selected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 4.dp)
                                        .width(22.dp)
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(paper.accent),
                                )
                            }
                        }
                    }
                }
        }
        }
        }
    }
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
                onTrainingPress = {},
                onSettingsPress = {},
            )
        }
    }
}
