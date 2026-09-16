package com.quransunah.app.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
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
    pageNumber: Int,
    currentSurah: String,
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
    val nightMode = LocalNightMode.current
    val iconTint = if (nightMode) paper.textStrong else paper.accent

    Column(
        modifier = modifier
            .background(paper.pageBackground)
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
            description = stringResource(R.string.home_reading_description),
            detail = stringResource(
                R.string.home_last_page,
                currentSurah,
                EasternArabic.format(currentAyah),
                EasternArabic.format(pageNumber),
            ),
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
                onClick = onOpenTraining,
                modifier = Modifier.weight(1f),
            )
            HomeRouteCard(
                title = stringResource(R.string.home_listening_title),
                description = stringResource(R.string.home_listening_description),
                iconRes = R.drawable.ic_headset,
                featured = false,
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
            onClick = onOpenIndex,
            modifier = Modifier.fillMaxWidth(),
            compact = true,
        )

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
) {
    val paper = LocalPaperColors.current
    val nightMode = LocalNightMode.current
    val background = if (featured) paper.accent else paper.pageBody
    val titleColor = if (featured) Color.White else paper.textStrong
    val descriptionColor = if (featured) Color.White.copy(alpha = 0.9f) else paper.textMuted
    val iconSurface = if (featured) Color.White.copy(alpha = 0.16f) else paper.tone300
    val iconTint = if (featured) Color.White else if (nightMode) paper.textStrong else paper.accent
    val minHeight = if (compact) 104.dp else if (featured) 156.dp else 148.dp
    val cardDescription = "$title، $description"

    Row(
        modifier = modifier
            .height(minHeight)
            .shadow(if (featured) 10.dp else 2.dp, HomeCardShape, clip = false)
            .clip(HomeCardShape)
            .background(background)
            .border(
                width = 1.dp,
                color = if (featured) paper.accentHover.copy(alpha = 0.44f) else paper.tone500.copy(alpha = 0.74f),
                shape = HomeCardShape,
            )
            .semantics { this.contentDescription = cardDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (featured) 62.dp else 46.dp)
                .clip(CircleShape)
                .background(iconSurface),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(if (featured) 33.dp else 25.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = title,
                color = titleColor,
                fontFamily = LocalDisplayFontFamily.current,
                fontWeight = FontWeight.Bold,
                fontSize = if (featured) 21.sp else if (compact) 17.sp else 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                color = descriptionColor,
                fontFamily = LocalAppFontFamily.current,
                fontSize = if (featured) 14.sp else 12.sp,
                lineHeight = if (featured) 21.sp else 18.sp,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
            detail?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.76f),
                    fontFamily = LocalAppFontFamily.current,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
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
