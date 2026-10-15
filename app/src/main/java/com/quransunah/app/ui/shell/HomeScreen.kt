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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

private val HomeCardShape = RoundedCornerShape(18.dp)
private val HomeActionShape = RoundedCornerShape(14.dp)

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
        IslamicEdgePattern(
            Modifier.matchParentSize(),
            fade = IslamicOrnamentFade.VerticalHome,
        )
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
    val minHeight = if (compact) 104.dp else if (featured) 156.dp else 148.dp
    val cardDescription = "$title، $description"
    val stackText = !featured && !compact
    val contentFraction = when {
        featured -> 0.58f
        compact -> 0.60f
        else -> 0.68f
    }
    val borderColor = paper.accent.copy(alpha = if (nightMode) 0.34f else if (featured) 0.24f else 0.16f)
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
            .border(width = 1.dp, color = borderColor, shape = HomeCardShape)
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
                                    0.34f to clear,
                                    0.62f to soft,
                                    1.00f to lift,
                                )
                            } else {
                                arrayOf(
                                    0.00f to lift,
                                    0.38f to soft,
                                    0.66f to clear,
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
            stackText = stackText,
            titleColor = titleColor,
            descriptionColor = descriptionColor,
            iconSurface = iconSurface,
            iconTint = iconTint,
            modifier = Modifier
                .align(if (stackText) Alignment.TopStart else Alignment.CenterStart)
                .fillMaxWidth(contentFraction)
                .padding(
                    start = if (compact) 14.dp else 16.dp,
                    end = 8.dp,
                    top = if (featured) 16.dp else 12.dp,
                    bottom = if (featured) 14.dp else 10.dp,
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
    stackText: Boolean,
    titleColor: Color,
    descriptionColor: Color,
    iconSurface: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    val iconSize = if (featured) 56.dp else 40.dp
    val glyphSize = if (featured) 30.dp else 22.dp
    val icon: @Composable () -> Unit = {
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
    }
    val copy: @Composable () -> Unit = {
        Text(
            text = title,
            color = titleColor,
            fontFamily = LocalDisplayFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = if (featured) 21.sp else if (compact) 17.sp else 16.sp,
            lineHeight = if (featured) 28.sp else if (compact) 22.sp else 21.sp,
            textAlign = TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = description,
            color = descriptionColor,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Medium,
            fontSize = if (featured) 15.sp else 13.sp,
            lineHeight = if (featured) 22.sp else 18.sp,
            textAlign = TextAlign.Start,
            maxLines = if (compact) 1 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        detail?.let {
            Text(
                text = it,
                color = titleColor.copy(alpha = 0.86f),
                fontFamily = LocalAppFontFamily.current,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                textAlign = TextAlign.Start,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
    if (stackText) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            icon()
            Column { copy() }
        }
    } else {
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) { copy() }
        }
    }
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
            .border(1.dp, paper.tone500.copy(alpha = 0.72f), HomeActionShape)
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
