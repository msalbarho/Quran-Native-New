package com.quransunah.app.ui.listening

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import com.quransunah.app.R
import com.quransunah.app.core.formatPlaybackClock
import com.quransunah.app.domain.model.PlaybackDomain
import com.quransunah.app.domain.model.PlaybackSnapshot
import com.quransunah.app.domain.model.SurahRepeatMode
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.shell.ChromeTokens
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors

private val ScreenPlayerShape = RoundedCornerShape(16.dp)
private val TransportShape = RoundedCornerShape(14.dp)
private val RepeatChipShape = RoundedCornerShape(18.dp)
private val StopRed = Color(0xFFB42318)

/** RTL: left-pointing glyph = next, right-pointing glyph = previous. */
private val RtlPreviousIcon = R.drawable.ic_skip_next
private val RtlNextIcon = R.drawable.ic_skip_previous

data class CompactReciterChoice(
    val id: String,
    val name: String,
)

@Composable
fun ScreenAudioPlayer(
    snapshot: PlaybackSnapshot,
    surahNumber: Int,
    fallbackTitle: String,
    repeatMode: SurahRepeatMode,
    fontManager: QcfFontManager?,
    isCurrentTrackActive: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    primaryDisabled: Boolean,
    onPrimary: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val duration = snapshot.durationMs.coerceAtLeast(0L)
    val idle = snapshot.domain == PlaybackDomain.IDLE
    val buffering = snapshot.isBuffering
    val playing = isCurrentTrackActive && snapshot.isPlaying
    var dragging by remember { mutableFloatStateOf(-1f) }
    val progress = if (dragging >= 0f) {
        dragging
    } else if (duration > 0L) {
        (snapshot.positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(10.dp, ScreenPlayerShape, clip = false)
            .clip(ScreenPlayerShape)
            .background(paper.chromeFill)
            .border(1.6.dp, ChromeTokens.Gold, ScreenPlayerShape)
            .padding(horizontal = 13.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SurahLigatureText(
            surahNumber = snapshot.surah ?: surahNumber,
            fontManager = fontManager,
            color = paper.textStrong,
            fallback = snapshot.surahName ?: fallbackTitle,
            fontSize = 28.sp,
        )
        snapshot.errorMessage?.let { message ->
            Text(text = message, color = StopRed, fontSize = 12.sp)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(formatPlaybackClock(snapshot.positionMs), color = paper.textMuted, fontSize = 12.sp)
            Text(formatPlaybackClock(duration), color = paper.textMuted, fontSize = 12.sp)
        }
        Slider(
            value = progress,
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                if (duration > 0L && dragging >= 0f) {
                    onSeek((dragging * duration).toLong())
                }
                dragging = -1f
            },
            enabled = !idle && !buffering && duration > 0L,
            colors = SliderDefaults.colors(
                thumbColor = paper.accent,
                activeTrackColor = paper.accent,
                inactiveTrackColor = paper.tone500,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RepeatModeChip(
                mode = repeatMode,
                onClick = onCycleRepeat,
            )
            Row(
                modifier = Modifier
                    .padding(start = 7.dp)
                    .clip(TransportShape)
                    .background(paper.tone300)
                    .border(1.dp, paper.tone500, TransportShape)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                CircleControl(
                    onClick = onPrevious,
                    enabled = hasPrevious && !buffering,
                    background = paper.pageBody,
                    border = paper.tone500,
                ) {
                    Icon(
                        painter = painterResource(RtlPreviousIcon),
                        contentDescription = stringResource(R.string.audio_prev_surah),
                        tint = paper.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                CircleControl(
                    onClick = onPrimary,
                    enabled = !primaryDisabled && !buffering,
                    background = paper.accent,
                    border = Color.Transparent,
                    size = 42.dp,
                ) {
                    if (buffering && isCurrentTrackActive) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = if (night) paper.textStrong else Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            painter = painterResource(
                                if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                            ),
                            contentDescription = if (playing) {
                                stringResource(R.string.audio_pause)
                            } else {
                                stringResource(R.string.audio_play)
                            },
                            tint = if (night) paper.textStrong else Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                CircleControl(
                    onClick = onNext,
                    enabled = hasNext && !buffering,
                    background = paper.pageBody,
                    border = paper.tone500,
                ) {
                    Icon(
                        painter = painterResource(RtlNextIcon),
                        contentDescription = stringResource(R.string.audio_next_surah),
                        tint = paper.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(1.dp)
                        .height(23.dp)
                        .background(paper.tone500.copy(alpha = 0.88f)),
                )
                CircleControl(
                    onClick = onStop,
                    enabled = !idle,
                    background = StopRed.copy(alpha = 0.06f),
                    border = StopRed.copy(alpha = 0.30f),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stop),
                        contentDescription = stringResource(R.string.audio_stop),
                        tint = StopRed.copy(alpha = 0.82f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RepeatModeChip(
    mode: SurahRepeatMode,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val active = mode != SurahRepeatMode.OFF
    val label = repeatLabel(mode)
    Box(
        modifier = Modifier
            .height(36.dp)
            .widthIn(min = 78.dp, max = 108.dp)
            .wrapContentWidth()
            .clip(RepeatChipShape)
            .background(
                if (active) paper.accent.copy(alpha = 0.10f) else paper.tone300,
            )
            .border(
                1.dp,
                if (active) paper.accent.copy(alpha = 0.34f) else paper.tone500,
                RepeatChipShape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (active) paper.accent else paper.textMuted,
            fontFamily = LocalAppFontFamily.current,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CircleControl(
    onClick: () -> Unit,
    enabled: Boolean,
    background: Color,
    border: Color,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.38f)
            .clip(CircleShape)
            .background(background)
            .then(
                if (border == Color.Transparent) {
                    Modifier
                } else {
                    Modifier.border(1.dp, border, CircleShape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
fun CompactPlaybackBar(
    snapshot: PlaybackSnapshot,
    fontManager: QcfFontManager?,
    reciters: List<CompactReciterChoice> = emptyList(),
    selectedReciterId: String? = snapshot.reciterId,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onStop: () -> Unit,
    onRestore: () -> Unit,
    onSelectReciter: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val density = LocalDensity.current
    val screen = LocalConfiguration.current
    val screenW = with(density) { screen.screenWidthDp.dp.toPx() }
    val screenH = with(density) { screen.screenHeightDp.dp.toPx() }
    val edge = with(density) { 8.dp.toPx() }
    val bottomReserve = with(density) { 72.dp.toPx() }
    val sessionKey = "${snapshot.domain}:${snapshot.surah}:${snapshot.reciterId}"
    val offsetX = remember(sessionKey) { mutableFloatStateOf(0f) }
    val offsetY = remember(sessionKey) { mutableFloatStateOf(0f) }
    val barW = remember { mutableIntStateOf(0) }
    val barH = remember { mutableIntStateOf(0) }
    var reciterMenu by remember { mutableStateOf(false) }
    fun clamp(x: Float, y: Float): Pair<Float, Float> {
        val maxX = ((screenW - barW.intValue) / 2f - edge).coerceAtLeast(0f)
        val travelY = (screenH - barH.intValue - bottomReserve - edge).coerceAtLeast(0f)
        return x.coerceIn(-maxX, maxX) to y.coerceIn(-travelY, 0f)
    }
    Row(
        modifier = modifier
            .offset { IntOffset(offsetX.floatValue.roundToInt(), offsetY.floatValue.roundToInt()) }
            .onGloballyPositioned {
                barW.intValue = it.size.width
                barH.intValue = it.size.height
            }
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(paper.chromeFill)
            .pointerInput(sessionKey) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val next = clamp(offsetX.floatValue + drag.x, offsetY.floatValue + drag.y)
                    offsetX.floatValue = next.first
                    offsetY.floatValue = next.second
                }
            }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CompactDragHandle(
            tint = paper.textMuted,
            modifier = Modifier.pointerInput(sessionKey) {
                detectDragGestures { change, drag ->
                    change.consume()
                    val next = clamp(offsetX.floatValue + drag.x, offsetY.floatValue + drag.y)
                    offsetX.floatValue = next.first
                    offsetY.floatValue = next.second
                }
            },
        )
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(RtlPreviousIcon),
                contentDescription = stringResource(R.string.audio_prev_surah),
                tint = paper.textStrong,
            )
        }
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(paper.navBubble),
        ) {
            if (snapshot.isBuffering) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = paper.accent, strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(
                        if (snapshot.isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                    ),
                    contentDescription = null,
                    tint = paper.textStrong,
                )
            }
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(RtlNextIcon),
                contentDescription = stringResource(R.string.audio_next_surah),
                tint = paper.textStrong,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onRestore),
        ) {
            SurahLigatureText(
                surahNumber = snapshot.surah ?: 1,
                fontManager = fontManager,
                color = paper.textStrong,
                fallback = snapshot.surahName.orEmpty(),
                fontSize = 20.sp,
            )
        }
        if (reciters.isNotEmpty()) {
            Box {
                IconButton(onClick = { reciterMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_record_voice_over),
                        contentDescription = stringResource(R.string.audio_reciter),
                        tint = paper.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(
                    expanded = reciterMenu,
                    onDismissRequest = { reciterMenu = false },
                ) {
                    reciters.forEach { reciter ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = reciter.name,
                                    color = if (reciter.id == selectedReciterId) paper.accent else paper.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            onClick = {
                                reciterMenu = false
                                if (reciter.id != selectedReciterId) onSelectReciter(reciter.id)
                            },
                        )
                    }
                }
            }
        }
        IconButton(onClick = onStop, modifier = Modifier.size(36.dp)) {
            Icon(
                painter = painterResource(R.drawable.ic_stop),
                contentDescription = stringResource(R.string.audio_stop),
                tint = paper.textStrong,
            )
        }
    }
}

@Composable
private fun CompactDragHandle(
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .size(width = 16.dp, height = 28.dp),
    ) {
        val r = size.minDimension * 0.16f
        val colGap = size.width * 0.42f
        val rowGap = size.height / 4f
        val left = size.width / 2f - colGap / 2f
        val right = size.width / 2f + colGap / 2f
        val startY = size.height / 2f - rowGap
        for (row in 0..2) {
            val y = startY + row * rowGap
            drawCircle(tint, r, Offset(left, y))
            drawCircle(tint, r, Offset(right, y))
        }
    }
}

@Composable
private fun repeatLabel(mode: SurahRepeatMode): String {
    return stringResource(
        when (mode) {
            SurahRepeatMode.OFF -> R.string.audio_repeat_off
            SurahRepeatMode.ONE -> R.string.audio_repeat_one
            SurahRepeatMode.REMAINING -> R.string.audio_repeat_all
        },
    )
}

@ArabicPreviews
@Composable
private fun ScreenAudioPlayerPreview() {
    PreviewTheme {
        ScreenAudioPlayer(
            snapshot = PreviewFixtures.playback,
            surahNumber = 1,
            fallbackTitle = "الفاتحة",
            repeatMode = SurahRepeatMode.ONE,
            fontManager = null,
            isCurrentTrackActive = true,
            hasPrevious = false,
            hasNext = true,
            primaryDisabled = false,
            onPrimary = {},
            onStop = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onCycleRepeat = {},
        )
    }
}
