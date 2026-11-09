package com.quransunah.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.ArabicRtl
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.ui.index.SurahNames
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalDisplayFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val HomeCardShape = GoldCornerFrameShape(16.dp)
private val HomeActionShape = GoldCornerFrameShape(10.dp)

/**
 * A deliberately small starting point for the app. The four cards are the
 * product's primary paths; saved places and settings remain available but do
 * not compete with the initial choice.
 */
@Composable
fun HomeScreen(
    currentSurah: String,
    currentSurahNumber: Int,
    currentAyah: Int,
    onOpenReading: () -> Unit,
    onOpenTraining: () -> Unit,
    onOpenListening: () -> Unit,
    onOpenIndex: () -> Unit,
    onOpenSavedPlaces: () -> Unit,
    onOpenSettings: () -> Unit,
    wirdPlan: com.quransunah.app.core.WirdPlan.State? = null,
    todayEpochDay: Long = 0L,
    onStartWird: () -> Unit = {},
    onSaveWird: (Int) -> Unit = {},
    onRemoveWird: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val surahLabel = SurahNames.displayName(
        ArabicRtl.currentTag(LocalContext.current),
        currentSurahNumber,
        currentSurah,
    )
    val readingPosition = stringResource(
        R.string.home_last_page,
        surahLabel,
        EasternArabic.format(currentAyah),
    )

    Box(
        modifier = modifier.background(paper.pageBackground),
    ) {
        TiledOrnamentBackground(Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_title),
                    color = paper.textStrong,
                    fontFamily = LocalDisplayFontFamily.current,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    color = paper.textMuted,
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryHomeAction(
                    iconRes = R.drawable.ic_bookmark,
                    description = stringResource(R.string.home_saved_places),
                    onClick = onOpenSavedPlaces,
                )
                SecondaryHomeAction(
                    iconRes = R.drawable.ic_settings,
                    description = stringResource(R.string.home_settings),
                    onClick = onOpenSettings,
                )
            }
        }

        HomeRouteCard(
            title = stringResource(R.string.home_reading_title),
            description = readingPosition,
            iconRes = R.drawable.ic_nav_quran,
            featured = true,
            onClick = onOpenReading,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HomeRouteCard(
                title = stringResource(R.string.home_training_title),
                description = stringResource(R.string.home_training_description),
                iconRes = R.drawable.ic_training_brain,
                featured = false,
                backgroundImageRes = R.drawable.home_training,
                onClick = onOpenTraining,
                modifier = Modifier.weight(1f),
            )
            HomeRouteCard(
                title = stringResource(R.string.home_listening_title),
                description = stringResource(R.string.home_listening_description),
                iconRes = R.drawable.ic_headset,
                featured = false,
                backgroundImageRes = R.drawable.home_listening,
                onClick = onOpenListening,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(12.dp))

        HomeRouteCard(
            title = stringResource(R.string.home_index_title),
            description = stringResource(R.string.home_index_description),
            iconRes = R.drawable.ic_format_list_bulleted,
            featured = false,
            backgroundImageRes = R.drawable.home_index,
            onClick = onOpenIndex,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )

        Spacer(Modifier.height(12.dp))

        var wirdSetupOpen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        WirdCard(
            state = wirdPlan,
            todayEpochDay = todayEpochDay,
            onStartReading = onStartWird,
            onOpenSetup = { wirdSetupOpen = true },
            modifier = Modifier.fillMaxWidth(),
        )
        if (wirdSetupOpen) {
            WirdSetupDialog(
                currentDays = wirdPlan?.takeIf { !it.finished }?.days,
                onConfirm = { onSaveWird(it); wirdSetupOpen = false },
                onRemove = if (wirdPlan != null) ({ onRemoveWird(); wirdSetupOpen = false }) else null,
                onDismiss = { wirdSetupOpen = false },
            )
        }

        }
    }
}

@Composable
private fun HomeRouteCard(
    title: String,
    description: String,
    iconRes: Int,
    featured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    compact: Boolean = false,
    backgroundImageRes: Int? = null,
) {
    val paper = LocalPaperColors.current
    val nightMode = LocalNightMode.current
    val textAtStart = LocalLayoutDirection.current == LayoutDirection.Rtl
    val themedImage = featured || backgroundImageRes != null
    // Light wash from the active palette. Day uses the theme chrome fill;
    // night tints a warm white with the palette accent so the photo stays bright.
    val imageBackdrop = if (nightMode) {
        lerp(Color(0xFFFFF8F1), paper.accent, 0.18f)
    } else {
        paper.chromeFill
    }
    val background = if (themedImage) imageBackdrop else paper.pageBody
    val titleColor = if (themedImage && nightMode) paper.pageBackground else paper.textStrong
    val descriptionColor = if (themedImage && nightMode) {
        paper.pageBackground.copy(alpha = 0.88f)
    } else {
        lerp(paper.textMuted, paper.textStrong, 0.55f)
    }
    val iconSurface = if (themedImage) {
        Color.White.copy(alpha = if (nightMode) 0.82f else 0.9f)
    } else {
        paper.tone300
    }
    val iconTint = if (nightMode && !themedImage) {
        paper.textStrong
    } else if (nightMode) {
        paper.pageBackground
    } else {
        paper.accent
    }
    val minHeight = if (compact) 104.dp else if (featured) 156.dp else 132.dp
    val cardDescription = "$title، $description"
    val contentFraction = when {
        featured -> 1f
        compact -> 0.84f
        else -> 1f
    }
    val borderColor = ChromeTokens.Gold
    val shadowElevation = when {
        nightMode -> if (featured) 6.dp else 3.dp
        featured -> 14.dp
        else -> 8.dp
    }

    Box(
        modifier = modifier
            .height(minHeight)
            .shadow(
                elevation = shadowElevation,
                shape = HomeCardShape,
                clip = false,
                ambientColor = paper.accent.copy(alpha = if (nightMode) 0.26f else 0.16f),
                spotColor = paper.accent.copy(alpha = if (nightMode) 0.14f else 0.10f),
            )
            .clip(HomeCardShape)
            .background(background)
            .border(width = ChromeTokens.GoldStroke, color = borderColor, shape = HomeCardShape)
            .semantics { this.contentDescription = cardDescription }
            .clickable(onClick = onClick),
    ) {
        if (themedImage) {
            Image(
                painter = painterResource(if (featured) R.drawable.home_continue_reading else backgroundImageRes!!),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center,
                alpha = 0.7f,
                modifier = Modifier.matchParentSize(),
            )
            val clear = Color.Transparent
            val soft = imageBackdrop.copy(alpha = 0.40f)
            val lift = imageBackdrop.copy(alpha = 0.78f)
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = if (textAtStart) {
                                arrayOf(
                                    0.00f to clear,
                                    0.14f to clear,
                                    0.36f to soft,
                                    0.58f to lift,
                                    1.00f to lift,
                                )
                            } else {
                                arrayOf(
                                    0.00f to lift,
                                    0.42f to lift,
                                    0.64f to soft,
                                    0.86f to clear,
                                    1.00f to clear,
                                )
                            },
                        ),
                    ),
            )
        }
        HomeCardCopy(
            title = title,
            description = description,
            detail = detail,
            iconRes = iconRes,
            featured = featured,
            compact = compact,
            titleColor = titleColor,
            descriptionColor = descriptionColor,
            iconSurface = iconSurface,
            iconTint = iconTint,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(contentFraction)
                .padding(
                    start = if (featured || compact) 14.dp else 8.dp,
                    end = if (featured) 12.dp else 8.dp,
                    top = 10.dp,
                    bottom = 10.dp,
                ),
        )
    }
}

@Composable
private fun HomeCardCopy(
    title: String,
    description: String,
    detail: String?,
    iconRes: Int,
    featured: Boolean,
    compact: Boolean,
    titleColor: Color,
    descriptionColor: Color,
    iconSurface: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val iconSize = when {
        featured -> 46.dp
        compact -> 40.dp
        else -> 30.dp
    }
    val glyphSize = when {
        featured -> 24.dp
        compact -> 22.dp
        else -> 16.dp
    }
    val titleSize = when {
        featured -> 20.sp
        compact -> 17.sp
        else -> 14.sp
    }
    val titleFloor = when {
        featured -> 16.sp
        compact -> 15.sp
        else -> 11.sp
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (featured || compact) 10.dp else 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
                .background(iconSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(glyphSize),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            SingleLineCardTitle(
                text = title,
                color = titleColor,
                maxSize = titleSize,
                minSize = titleFloor,
            )
            Text(
                text = description,
                color = descriptionColor,
                fontFamily = LocalAppFontFamily.current,
                fontWeight = FontWeight.Medium,
                fontSize = if (featured) 15.sp else 12.sp,
                lineHeight = if (featured) 20.sp else 16.sp,
                textAlign = TextAlign.Start,
                maxLines = if (featured || compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier.padding(top = 2.dp),
            )
            detail?.let {
                Text(
                    text = it,
                    color = titleColor.copy(alpha = 0.86f),
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

/** Amiri, packaged from public/fonts. Shrinks just enough to stay on one line. */
@Composable
private fun SingleLineCardTitle(
    text: String,
    color: Color,
    maxSize: TextUnit,
    minSize: TextUnit,
) {
    var size by remember(text, maxSize) { mutableStateOf(maxSize) }
    Text(
        text = text,
        color = color,
        fontFamily = LocalDisplayFontFamily.current,
        fontWeight = FontWeight.Bold,
        fontSize = size,
        lineHeight = size * 1.15f,
        textAlign = TextAlign.Start,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        onTextLayout = { layout ->
            if (layout.hasVisualOverflow && size > minSize) {
                size = (size.value - 0.5f).coerceAtLeast(minSize.value).sp
            }
        },
    )
}

@Composable
private fun SecondaryHomeAction(
    iconRes: Int,
    description: String,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val nightMode = LocalNightMode.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(HomeActionShape)
            .background(paper.pageBody)
            .border(ChromeTokens.GoldStroke, ChromeTokens.Gold, HomeActionShape)
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = if (nightMode) paper.textStrong else paper.accent,
            modifier = Modifier.size(21.dp),
        )
    }
}
