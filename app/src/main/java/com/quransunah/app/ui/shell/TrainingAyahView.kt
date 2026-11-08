package com.quransunah.app.ui.shell

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quransunah.app.R
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.ui.mushaf.SurahNameFrame
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.res.stringResource
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.TrainingWordMark
import com.quransunah.app.ui.mushaf.trainingMarkColor
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import kotlinx.coroutines.flow.distinctUntilChanged

/** Total ayahs in the mushaf; pager index = global ayah id - 1. */
private const val AYAH_TOTAL = 6236

/** Same 3420×384 artwork ratio as the mushaf header, a little taller so the name stays legible. */
private const val SURAH_HEADER_ASPECT = 3420f / 520f

/**
 * «عرض الآية»: one ayah drawn with its QPC V4 page font(s) (the same coloured glyphs and night palette as the
 * Mushaf), swipeable to the previous/next ayah. Paging follows the Mushaf pager: same layout direction, no
 * reverseLayout, so in RTL the next ayah comes from the left exactly like the next page.
 */
@Composable
internal fun TrainingSingleAyahPager(
    ayah: Pair<Int, Int>,
    loadWords: suspend (Int, Int) -> List<Pair<Int, QuranWord>>,
    fontManager: QcfFontManager,
    onAyahChange: (Pair<Int, Int>) -> Unit,
    marks: Map<Int, TrainingWordMark> = emptyMap(),
    surahs: Map<Int, SurahInfo> = emptyMap(),
    reciting: Boolean = false,
    recitationDone: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    val targetIndex = (SurahAyahCounts.ayahId(ayah.first, ayah.second) - 1).coerceIn(0, AYAH_TOTAL - 1)
    val pagerState = rememberPagerState(initialPage = targetIndex) { AYAH_TOTAL }
    val latestAyah by rememberUpdatedState(ayah)
    val latestChange by rememberUpdatedState(onAyahChange)
    // buttons / external changes -> animate the pager
    LaunchedEffect(targetIndex) {
        if (pagerState.currentPage != targetIndex) {
            if (kotlin.math.abs(pagerState.currentPage - targetIndex) == 1) pagerState.animateScrollToPage(targetIndex)
            else pagerState.scrollToPage(targetIndex)
        }
    }
    // swipe -> report the settled ayah
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.distinctUntilChanged().collect { page ->
            val settled = SurahAyahCounts.fromAyahId(page + 1) ?: return@collect
            if (settled != latestAyah) latestChange(settled)
        }
    }
    HorizontalPager(
        state = pagerState,
        reverseLayout = false,
        beyondViewportPageCount = 1,
        userScrollEnabled = !reciting,
        key = { it },
        modifier = modifier.background(paper.pageBody.copy(alpha = 0.98f)),
    ) { index ->
        val key = SurahAyahCounts.fromAyahId(index + 1) ?: return@HorizontalPager
        TrainingAyahGlyphs(key, loadWords, fontManager, marks, surahs[key.first], recitationDone)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingAyahGlyphs(
    ayah: Pair<Int, Int>,
    loadWords: suspend (Int, Int) -> List<Pair<Int, QuranWord>>,
    fontManager: QcfFontManager,
    marks: Map<Int, TrainingWordMark>,
    surah: SurahInfo?,
    recitationDone: Boolean,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    var words by remember(ayah) { mutableStateOf<List<Pair<Int, QuranWord>>?>(null) }
    var faces by remember(ayah, night) { mutableStateOf<Map<Int, FontFamily>>(emptyMap()) }
    LaunchedEffect(ayah) { words = runCatching { loadWords(ayah.first, ayah.second) }.getOrDefault(emptyList()) }
    LaunchedEffect(words, night) {
        val list = words ?: return@LaunchedEffect
        val loaded = HashMap<Int, FontFamily>()
        list.map { it.first }.distinct().forEach { page ->
            val tf: Typeface? = runCatching { fontManager.loadPageTypeface(page, night) }.getOrNull()
            if (tf != null) loaded[page] = FontFamily(tf)
        }
        faces = loaded
    }
    var titleFace by remember { mutableStateOf(fontManager.peekSurahTitleTypeface()) }
    LaunchedEffect(Unit) {
        if (titleFace == null) titleFace = runCatching { fontManager.loadSurahTitleTypeface() }.getOrNull()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        if (ayah.second == 1) {
            // Same frame, background and surah-name font as the mushaf page header.
            SurahNameFrame(
                surahNumber = ayah.first,
                surah = surah,
                fontManager = fontManager,
                titleTypeface = titleFace,
                onTap = {},
                onLongPress = {},
                modifier = Modifier.fillMaxWidth().aspectRatio(SURAH_HEADER_ASPECT),
            )
            // Al-Fatiha 1:1 is the basmala itself; At-Tawbah has none.
            val face = titleFace
            if (ayah.first != 1 && ayah.first != 9 && face != null) {
                Text(
                    text = fontManager.displayBasmalahLigature(),
                    fontFamily = remember(face) { FontFamily(face) },
                    color = paper.textStrong,
                    fontSize = 34.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val list = words
        if (list != null) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                list.forEach { (page, word) ->
                    val family = faces[page]
                    val glyph = word.textLigature
                    val useGlyph = family != null && glyph.isNotBlank()
                    if (!useGlyph && word.isAyahMarker) return@forEach
                    val mark = if (word.isAyahMarker) null else marks[word.id]
                    val tint = trainingAyahWordTint(mark, recitationDone)
                    Text(
                        text = if (useGlyph) glyph else word.textHafs,
                        color = tint ?: paper.textStrong,
                        fontFamily = if (useGlyph) family else null,
                        fontSize = if (useGlyph) 40.sp else 30.sp,
                        lineHeight = if (useGlyph) 66.sp else 50.sp,
                        textAlign = TextAlign.Center,
                        style = TextStyle(textDirection = TextDirection.Rtl),
                        modifier = if (tint != null && useGlyph) Modifier.background(tint.copy(alpha = 0.16f)) else Modifier,
                    )
                }
            }
            val total = list.count { !it.second.isAyahMarker }
            val read = list.count { (_, w) -> !w.isAyahMarker && marks[w.id].let { it != null && it != TrainingWordMark.Unread && it != TrainingWordMark.Missing } }
            Text(
                text = stringResource(
                    R.string.training_ayah_word_progress,
                    EasternArabic.format(read),
                    EasternArabic.format(total),
                ),
                color = paper.textMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            if (marks.isNotEmpty()) TrainingAyahLegend()
        }
    }
}

/** Live colour of a word: green correct, red wrong, orange unsure; grey = missing once the ayah is finished. */
internal fun trainingAyahWordTint(mark: TrainingWordMark?, recitationDone: Boolean): Color? = when {
    mark == null -> null
    mark == TrainingWordMark.Unread -> if (recitationDone) trainingMarkColor(TrainingWordMark.Unread) else null
    else -> trainingMarkColor(mark)
}

/** «آية بآية» legend (Arabic-Steps): correct / wrong / missing. */
@Composable
private fun TrainingAyahLegend() {
    val paper = LocalPaperColors.current
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            TrainingWordMark.Correct to R.string.training_legend_correct,
            TrainingWordMark.PossibleError to R.string.training_legend_wrong,
            TrainingWordMark.Missing to R.string.training_legend_missing,
        ).forEach { (mark, label) ->
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .then(Modifier.size(10.dp))
                        .background(trainingMarkColor(mark), androidx.compose.foundation.shape.CircleShape),
                )
                Text(stringResource(label), color = paper.textMuted, fontSize = 12.sp)
            }
        }
    }
}
