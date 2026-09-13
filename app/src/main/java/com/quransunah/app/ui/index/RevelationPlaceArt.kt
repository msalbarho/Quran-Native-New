package com.quransunah.app.ui.index

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.quransunah.app.R

private val PlaceArtSize = 32.dp

@Composable
internal fun RevelationPlaceArt(
    place: String?,
    modifier: Modifier = Modifier,
    size: Dp = PlaceArtSize,
) {
    val context = LocalContext.current.applicationContext
    val bitmap = remember(place) { PlaceArtCache.bitmap(context, place) } ?: return
    val description = when (place) {
        "meccan" -> stringResource(R.string.index_makkah)
        "medinan" -> stringResource(R.string.index_madinah)
        else -> null
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            Image(
                bitmap = bitmap,
                contentDescription = description,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
                    .size(size),
            )
        }
    }
}
