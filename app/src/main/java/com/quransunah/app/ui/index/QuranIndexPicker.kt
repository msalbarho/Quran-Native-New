package com.quransunah.app.ui.index

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Typeface
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quransunah.app.R
import com.quransunah.app.core.ArabicOrdinals
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.fonts.QcfFontManager
import com.quransunah.app.ui.mushaf.QcfGlyphText
import com.quransunah.app.ui.preview.ArabicPreviews
import com.quransunah.app.ui.preview.PreviewFixtures
import com.quransunah.app.ui.preview.PreviewTheme
import com.quransunah.app.ui.theme.LocalAppFontFamily
import com.quransunah.app.ui.theme.LocalNightMode
import com.quransunah.app.ui.theme.LocalPaperColors
import com.quransunah.app.ui.theme.PaperColors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield

private val CloseRed = Color(0xFFD10000)
private val CardShape = RoundedCornerShape(11.dp)
private val ChipShape = RoundedCornerShape(6.dp)

@Composable
fun QuranIndexPicker(
    initialTab: QuranIndexTab,
    pageNumber: Int,
    currentJuz: Int,
    currentHizb: Int,
    currentSurah: Int,
    fontManager: QcfFontManager?,
    onClose: () -> Unit,
    onSelectPage: (IndexJump) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuranIndexViewModel,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var openEpoch by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        openEpoch += 1
        viewModel.resetForOpen(initialTab)
    }
    LaunchedEffect(initialTab, pageNumber, currentJuz, currentHizb, currentSurah) {
        viewModel.prepare(initialTab, pageNumber, currentJuz, currentHizb, currentSurah)
    }
    QuranIndexPickerContent(
        ui = ui,
        openEpoch = openEpoch,
        fontManager = fontManager,
        onTab = viewModel::setTab,
        onQuery = viewModel::setQuery,
        onClose = onClose,
        onSelectPage = onSelectPage,
        onSubmitQuery = {
            viewModel.resolveJump()?.let(onSelectPage)
        },
        modifier = modifier,
    )
}

@Composable
fun QuranIndexPickerContent(
    ui: QuranIndexUiState,
    fontManager: QcfFontManager?,
    onTab: (QuranIndexTab) -> Unit,
    onQuery: (String) -> Unit,
    onClose: () -> Unit,
    onSelectPage: (IndexJump) -> Unit,
    onSubmitQuery: () -> Unit,
    modifier: Modifier = Modifier,
    openEpoch: Int = 0,
) {
    val paper = LocalPaperColors.current
    val title = stringResource(
        when (ui.tab) {
            QuranIndexTab.Surah -> R.string.index_tab_surah
            QuranIndexTab.Juz -> R.string.index_tab_juz
            QuranIndexTab.Hizb -> R.string.index_tab_hizb
            QuranIndexTab.Sajdah -> R.string.index_tab_sajdah
        },
    )
    val placeholder = stringResource(
        when (ui.tab) {
            QuranIndexTab.Surah -> R.string.index_search_surah
            QuranIndexTab.Juz -> R.string.index_search_juz
            QuranIndexTab.Hizb -> R.string.index_search_hizb
            QuranIndexTab.Sajdah -> R.string.index_search_sajdah
        },
    )
    val inspection = LocalInspectionMode.current
    var query by remember { mutableStateOf("") }
    LaunchedEffect(ui.tab) {
        query = ""
        onQuery("")
    }
    var titleTypeface by remember { mutableStateOf(fontManager?.peekSurahTitleTypeface()) }
    var juzNameTypeface by remember { mutableStateOf(fontManager?.peekJuzNameTypeface()) }
    LaunchedEffect(fontManager, inspection) {
        if (fontManager != null && !inspection) {
            titleTypeface = fontManager.loadSurahTitleTypeface() ?: fontManager.loadUthmanicTypeface()
            juzNameTypeface = fontManager.loadJuzNameTypeface()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(paper.pageBody),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(paper.chromeFill)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    color = paper.textStrong,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.close),
                    color = CloseRed,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { next ->
                    query = next
                    onQuery(next)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                placeholder = { Text(placeholder, color = paper.textMuted, fontSize = 14.sp) },
                singleLine = true,
                textStyle = TextStyle(
                    color = paper.textStrong,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Start,
                    textDirection = TextDirection.Rtl,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { onSubmitQuery() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = paper.textStrong,
                    unfocusedTextColor = paper.textPrimary,
                    focusedBorderColor = paper.darkAccent,
                    unfocusedBorderColor = paper.tone500,
                    cursorColor = paper.accent,
                    focusedContainerColor = paper.pageBody,
                    unfocusedContainerColor = paper.pageBody,
                ),
                shape = RoundedCornerShape(10.dp),
            )
            IndexTabRow(
                selected = ui.tab,
                onTab = onTab,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(paper.tone200),
        ) {
            val visibleSurahs = remember(ui.surahs, query) {
                if (query.isBlank()) ui.surahs
                else ui.surahs.filter { matchesSurahQuery(it, query) }
            }
            val searchBlank = query.isBlank()
            val surahTarget = remember(visibleSurahs, ui.currentSurah) {
                visibleSurahs.indexOfFirst { it.number == ui.currentSurah }
            }
            val juzTarget = remember(ui.visibleJuzSections, ui.currentJuz) {
                ui.visibleJuzSections.indexOfFirst { it.juz.number == ui.currentJuz }
            }
            val hizbTarget = remember(ui.visibleHizbSections, ui.currentHizb) {
                ui.visibleHizbSections.indexOfFirst { it.hizb.number == ui.currentHizb }
            }
            val sajdaTarget = remember(ui.visibleSajdas, ui.currentPage) {
                sajdaIndexForPage(ui.visibleSajdas, ui.currentPage)
            }
            // Recreate list state on each open / tab / target so the current
            // entry is the first visible row (React aligns active surah to top).
            val surahListState = rememberIndexAnchorState(
                openEpoch = openEpoch,
                tab = ui.tab,
                expectedTab = QuranIndexTab.Surah,
                targetIndex = surahTarget,
                itemCount = visibleSurahs.size,
                searchBlank = searchBlank,
                loaded = ui.loaded,
                inspection = inspection,
            )
            val juzListState = rememberIndexAnchorState(
                openEpoch = openEpoch,
                tab = ui.tab,
                expectedTab = QuranIndexTab.Juz,
                targetIndex = juzTarget,
                itemCount = ui.visibleJuzSections.size,
                searchBlank = searchBlank,
                loaded = ui.loaded,
                inspection = inspection,
            )
            val hizbListState = rememberIndexAnchorState(
                openEpoch = openEpoch,
                tab = ui.tab,
                expectedTab = QuranIndexTab.Hizb,
                targetIndex = hizbTarget,
                itemCount = ui.visibleHizbSections.size,
                searchBlank = searchBlank,
                loaded = ui.loaded,
                inspection = inspection,
            )
            val sajdaListState = rememberIndexAnchorState(
                openEpoch = openEpoch,
                tab = ui.tab,
                expectedTab = QuranIndexTab.Sajdah,
                targetIndex = sajdaTarget,
                itemCount = ui.visibleSajdas.size,
                searchBlank = searchBlank,
                loaded = ui.loaded,
                inspection = inspection,
            )
            LaunchedEffect(
                openEpoch,
                ui.tab,
                ui.loaded,
                searchBlank,
                inspection,
                surahTarget,
                juzTarget,
                hizbTarget,
                sajdaTarget,
                visibleSurahs.size,
                ui.visibleJuzSections.size,
                ui.visibleHizbSections.size,
                ui.visibleSajdas.size,
            ) {
                if (inspection || !ui.loaded || !searchBlank) return@LaunchedEffect
                when (ui.tab) {
                    QuranIndexTab.Surah ->
                        surahListState.scrollToIndexWhenReady(surahTarget)
                    QuranIndexTab.Juz ->
                        juzListState.scrollToIndexWhenReady(juzTarget)
                    QuranIndexTab.Hizb ->
                        hizbListState.scrollToIndexWhenReady(hizbTarget)
                    QuranIndexTab.Sajdah ->
                        sajdaListState.scrollToIndexWhenReady(sajdaTarget)
                }
            }
            when (ui.tab) {
                QuranIndexTab.Surah -> SurahIndexList(
                    surahs = visibleSurahs,
                    currentSurah = ui.currentSurah,
                    fontManager = fontManager,
                    titleTypeface = titleTypeface,
                    listState = surahListState,
                    onSelect = onSelectPage,
                )
                QuranIndexTab.Juz -> JuzIndexList(
                    sections = ui.visibleJuzSections,
                    currentJuz = ui.currentJuz,
                    currentHizb = ui.currentHizb,
                    surahName = { ui.surahName(it) },
                    fontManager = fontManager,
                    juzNameTypeface = juzNameTypeface,
                    listState = juzListState,
                    onSelect = onSelectPage,
                )
                QuranIndexTab.Hizb -> HizbIndexList(
                    sections = ui.visibleHizbSections,
                    currentHizb = ui.currentHizb,
                    currentPage = ui.currentPage,
                    surahName = { ui.surahName(it) },
                    listState = hizbListState,
                    onSelect = onSelectPage,
                )
                QuranIndexTab.Sajdah -> SajdaIndexList(
                    sajdas = ui.visibleSajdas,
                    currentPage = ui.currentPage,
                    surahName = { ui.surahName(it) },
                    fontManager = fontManager,
                    titleTypeface = titleTypeface,
                    listState = sajdaListState,
                    onSelect = onSelectPage,
                )
            }
        }
    }
}

@Composable
private fun IndexTabRow(
    selected: QuranIndexTab,
    onTab: (QuranIndexTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = LocalPaperColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuranIndexTab.entries.forEach { tab ->
            val active = tab == selected
            val label = stringResource(
                when (tab) {
                    QuranIndexTab.Surah -> R.string.index_tab_surah_short
                    QuranIndexTab.Juz -> R.string.index_tab_juz_short
                    QuranIndexTab.Hizb -> R.string.index_tab_hizb_short
                    QuranIndexTab.Sajdah -> R.string.index_tab_sajdah_short
                },
            )
            Text(
                text = label,
                color = if (active) paper.darkAccent else paper.textMuted,
                fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(ChipShape)
                    .background(if (active) paper.darkAccent.copy(alpha = 0.16f) else Color.Transparent)
                    .border(
                        width = if (active) 1.dp else 0.dp,
                        color = if (active) paper.darkAccent.copy(alpha = 0.72f) else Color.Transparent,
                        shape = ChipShape,
                    )
                    .clickable { onTab(tab) }
                    .padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SurahIndexList(
    surahs: List<SurahInfo>,
    currentSurah: Int,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onSelect: (IndexJump) -> Unit,
    listState: LazyListState,
) {
    if (surahs.isEmpty()) {
        IndexEmpty()
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(surahs, key = { it.number }, contentType = { "surah" }) { surah ->
            val jump = remember(surah.number, surah.startPage) { surah.toIndexJump() }
            SurahIndexCard(
                surah = surah,
                active = surah.number == currentSurah,
                fontManager = fontManager,
                titleTypeface = titleTypeface,
                onClick = { onSelect(jump) },
            )
        }
    }
}

@Composable
private fun SurahIndexCard(
    surah: SurahInfo,
    active: Boolean,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val place = revelationPlace(surah.revelationType)
    val fallbackName = surah.nameArabic.removePrefix("سُورَةُ ").removePrefix("سورة ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(CardShape)
            .goldCard(paper, active)
            .clickable(onClick = onClick),
    ) {
        RevelationPlaceArt(
            place = place,
            modifier = Modifier.matchParentSize(),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 40.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = EasternArabic.format(surah.number),
                color = paper.accent,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
            Text(
                text = " – ",
                color = paper.accent.copy(alpha = 0.55f),
                fontSize = 15.sp,
            )
            QcfGlyphText(
                ligature = fontManager?.surahNameLigature(surah.number).orEmpty(),
                fallback = fallbackName,
                fontManager = fontManager,
                titleTypeface = titleTypeface,
                color = if (night) Color.White else paper.textStrong,
                ligatureSize = 26.sp,
                fallbackSize = 16.sp,
                modifier = Modifier.widthIn(min = 88.dp, max = 130.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.index_ayahs_count, EasternArabic.format(surah.numberOfAyahs)),
                color = paper.textMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun JuzIndexList(
    sections: List<JuzIndexSection>,
    currentJuz: Int,
    currentHizb: Int,
    surahName: (Int) -> String,
    fontManager: QcfFontManager?,
    juzNameTypeface: Typeface?,
    onSelect: (IndexJump) -> Unit,
    listState: LazyListState,
) {
    if (sections.isEmpty()) {
        IndexEmpty()
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        items(sections, key = { it.juz.id }, contentType = { "juz" }) { section ->
            JuzSectionCard(
                section = section,
                active = section.juz.number == currentJuz,
                currentHizb = currentHizb,
                surahName = surahName,
                fontManager = fontManager,
                juzNameTypeface = juzNameTypeface,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun JuzSectionCard(
    section: JuzIndexSection,
    active: Boolean,
    currentHizb: Int,
    surahName: (Int) -> String,
    fontManager: QcfFontManager?,
    juzNameTypeface: Typeface?,
    onSelect: (IndexJump) -> Unit,
) {
    val paper = LocalPaperColors.current
    val juz = section.juz
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .goldCard(paper, active),
    ) {
        JuzHeadCard(
            juz = juz,
            active = active,
            surahName = surahName(juz.surah).ifBlank { EasternArabic.format(juz.surah) },
            fontManager = fontManager,
            juzNameTypeface = juzNameTypeface,
            framed = false,
            onClick = { onSelect(juz.toIndexJump()) },
        )
        section.hizbs.forEach { hizb ->
            DivisionRow(
                number = hizb.number,
                title = hizbTitle(hizb),
                meta = divisionMeta(hizb, surahName(hizb.surah)),
                active = hizb.number == currentHizb,
                onClick = { onSelect(hizb.toIndexJump()) },
            )
        }
    }
}

@Composable
private fun JuzHeadCard(
    juz: DivisionInfo,
    active: Boolean,
    surahName: String,
    fontManager: QcfFontManager?,
    juzNameTypeface: Typeface?,
    onClick: () -> Unit,
    framed: Boolean = true,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val fallback = ArabicOrdinals.juzIndexTitle(juz.number)
    val boxModifier = if (framed) {
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .goldCard(paper, active)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
    }
    Column(modifier = if (framed) Modifier else Modifier.fillMaxWidth()) {
    Box(modifier = boxModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(number = juz.number, active = active)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QcfGlyphText(
                    ligature = fontManager?.juzOpeningLigature(juz.number).orEmpty(),
                    fallback = fallback,
                    fontManager = fontManager,
                    titleTypeface = juzNameTypeface,
                    color = if (night) Color.White else paper.textStrong,
                    ligatureSize = 19.5.sp,
                    fallbackSize = 17.sp,
                    preferLigature = true,
                )
                Text(
                    text = stringResource(
                        R.string.index_juz_start_meta,
                        EasternArabic.format(juz.ayah),
                        surahName,
                    ),
                    color = paper.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            PageChip(pageNumber = juz.pageNumber, labeled = true)
        }
    }
        if (!framed) {
            HorizontalDivider(color = paper.darkAccent.copy(alpha = 0.22f))
        }
    }
}

@Composable
private fun HizbIndexList(
    sections: List<HizbIndexSection>,
    currentHizb: Int,
    currentPage: Int,
    surahName: (Int) -> String,
    onSelect: (IndexJump) -> Unit,
    listState: LazyListState,
) {
    if (sections.isEmpty()) {
        IndexEmpty()
        return
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(sections, key = { it.hizb.id }, contentType = { "hizb" }) { section ->
            HizbSectionCard(
                section = section,
                activeHizb = section.hizb.number == currentHizb,
                currentPage = currentPage,
                surahName = surahName,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun SajdaIndexList(
    sajdas: List<SajdaMarker>,
    currentPage: Int,
    surahName: (Int) -> String,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onSelect: (IndexJump) -> Unit,
    listState: LazyListState,
) {
    if (sajdas.isEmpty()) {
        IndexEmpty()
        return
    }
    val activeSajdaNumber = remember(sajdas, currentPage) {
        sajdas.getOrNull(sajdaIndexForPage(sajdas, currentPage))?.number
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        items(sajdas, key = { it.number }, contentType = { "sajda" }) { marker ->
            val jump = remember(marker.number, marker.pageNumber) { marker.toIndexJump() }
            SajdaIndexCard(
                marker = marker,
                active = marker.number == activeSajdaNumber,
                surahName = surahName(marker.startSurah),
                fontManager = fontManager,
                titleTypeface = titleTypeface,
                onClick = { onSelect(jump) },
            )
        }
    }
}

@Composable
private fun SajdaIndexCard(
    marker: SajdaMarker,
    active: Boolean,
    surahName: String,
    fontManager: QcfFontManager?,
    titleTypeface: Typeface?,
    onClick: () -> Unit,
) {
    val paper = LocalPaperColors.current
    val night = LocalNightMode.current
    val fallbackName = surahName.removePrefix("سُورَةُ ").removePrefix("سورة ")
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 62.dp)
            .clip(CardShape)
            .goldCard(paper, active)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberBadge(number = marker.number, active = active)
            Spacer(Modifier.width(8.dp))
            QcfGlyphText(
                ligature = fontManager?.surahNameLigature(marker.startSurah).orEmpty(),
                fallback = fallbackName.ifBlank { EasternArabic.format(marker.startSurah) },
                fontManager = fontManager,
                titleTypeface = titleTypeface,
                color = if (night) Color.White else paper.textStrong,
                ligatureSize = 24.sp,
                fallbackSize = 16.sp,
                modifier = Modifier.widthIn(min = 72.dp, max = 130.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = stringResource(
                        R.string.index_sajdah_ayah,
                        EasternArabic.format(marker.startAyah),
                    ),
                    color = paper.textMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        if (marker.required) R.string.index_sajdah_required else R.string.index_sajdah_recommended,
                    ),
                    color = paper.darkAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            PageChip(pageNumber = marker.pageNumber, labeled = true)
        }
    }
}

@Composable
private fun HizbSectionCard(
    section: HizbIndexSection,
    activeHizb: Boolean,
    currentPage: Int,
    surahName: (Int) -> String,
    onSelect: (IndexJump) -> Unit,
) {
    val paper = LocalPaperColors.current
    val activeDivisionId = remember(section.hizb.id, section.quarters, currentPage) {
        activeDivisionIdForPage(section.hizb, section.quarters, currentPage)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .goldCard(paper, activeHizb),
    ) {
        DivisionRow(
            number = section.hizb.number,
            title = hizbTitle(section.hizb),
            meta = divisionMeta(section.hizb, surahName(section.hizb.surah)),
            active = section.hizb.id == activeDivisionId,
            onClick = { onSelect(section.hizb.toIndexJump()) },
        )
        section.quarters.forEach { quarter ->
            DivisionRow(
                number = quarter.number,
                title = quarter.name.ifBlank { stringResource(R.string.index_quarter_fallback, EasternArabic.format(quarter.number)) },
                meta = divisionMeta(quarter, surahName(quarter.surah)),
                active = quarter.id == activeDivisionId,
                compact = true,
                onClick = { onSelect(quarter.toIndexJump()) },
            )
        }
    }
}

@Composable
private fun DivisionRow(
    number: Int,
    title: String,
    meta: String,
    active: Boolean,
    onClick: () -> Unit,
    compact: Boolean = false,
) {
    val paper = LocalPaperColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (active) {
                    Modifier.background(paper.accent.copy(alpha = 0.09f))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(
                start = if (compact) 18.dp else 10.dp,
                end = 10.dp,
                top = if (compact) 8.dp else 10.dp,
                bottom = if (compact) 8.dp else 10.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NumberBadge(number = number, active = active, compact = compact)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = if (active) paper.accent else paper.textStrong,
                fontWeight = FontWeight.Bold,
                fontFamily = LocalAppFontFamily.current,
                fontSize = if (compact) 13.sp else 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = meta,
                color = paper.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NumberBadge(number: Int, active: Boolean, compact: Boolean = false) {
    val paper = LocalPaperColors.current
    val size = if (compact) 22.dp else 32.dp
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(if (active) paper.darkAccent.copy(alpha = 0.22f) else paper.tone400.copy(alpha = 0.55f))
            .border(1.dp, paper.darkAccent.copy(alpha = if (active) 0.78f else 0.42f), RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = EasternArabic.format(number),
            color = if (active) paper.darkAccent else paper.accent,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (compact) 10.sp else 13.sp,
        )
    }
}

@Composable
private fun PageChip(pageNumber: Int, labeled: Boolean = false) {
    val paper = LocalPaperColors.current
    val text = if (labeled) {
        stringResource(R.string.index_page_label, EasternArabic.format(pageNumber))
    } else {
        EasternArabic.format(pageNumber)
    }
    Text(
        text = text,
        color = paper.accent,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(paper.tone400.copy(alpha = 0.5f))
            .border(1.dp, paper.darkAccent.copy(alpha = 0.42f), RoundedCornerShape(5.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
    )
}

@Composable
private fun IndexEmpty() {
    val paper = LocalPaperColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.index_empty),
            color = paper.textMuted,
            fontSize = 15.sp,
        )
    }
}

private fun Modifier.goldCard(paper: PaperColors, active: Boolean): Modifier {
    val stroke = paper.darkAccent.copy(alpha = if (active) 0.78f else 0.42f)
    return this
        .background(paper.pageBody.copy(alpha = 0.72f))
        .border(if (active) 1.5.dp else 1.dp, stroke, CardShape)
}

@Composable
private fun rememberIndexAnchorState(
    openEpoch: Int,
    tab: QuranIndexTab,
    expectedTab: QuranIndexTab,
    targetIndex: Int,
    itemCount: Int,
    searchBlank: Boolean,
    loaded: Boolean,
    inspection: Boolean,
): LazyListState {
    val initial = when {
        inspection -> 0
        !loaded || !searchBlank || tab != expectedTab || itemCount <= 0 -> 0
        targetIndex in 0 until itemCount -> targetIndex
        else -> 0
    }
    return remember(openEpoch, expectedTab, tab, initial, itemCount, searchBlank, loaded) {
        LazyListState(firstVisibleItemIndex = initial)
    }
}

private suspend fun LazyListState.scrollToIndexWhenReady(index: Int) {
    if (index < 0) return
    // Wait until LazyColumn has composed enough items, then pin the row to top
    // (matches React SurahListGrid align-active-to-top).
    snapshotFlow { layoutInfo.totalItemsCount }
        .first { count -> count > index }
    scrollToItem(index)
    yield()
    if (firstVisibleItemIndex != index) {
        scrollToItem(index)
    }
}

/** Last sajda at or before [page], else first upcoming; -1 if empty. */
internal fun sajdaIndexForPage(sajdas: List<SajdaMarker>, page: Int): Int {
    if (sajdas.isEmpty()) return -1
    val exact = sajdas.indexOfFirst { it.pageNumber == page }
    if (exact >= 0) return exact
    var best = -1
    for (i in sajdas.indices) {
        if (sajdas[i].pageNumber <= page) best = i
    }
    return if (best >= 0) best else 0
}

/** Active hizb or quarter marker for [page] within one hizb section. */
internal fun activeDivisionIdForPage(
    hizb: DivisionInfo,
    quarters: List<DivisionInfo>,
    page: Int,
): Int {
    val markers = buildList {
        add(hizb)
        addAll(quarters)
    }
    return markers
        .filter { it.pageNumber <= page }
        .maxWithOrNull(compareBy({ it.pageNumber }, { it.number }))
        ?.id
        ?: hizb.id
}

@Composable
private fun divisionMeta(entry: DivisionInfo, surahName: String): String {
    val name = surahName.ifBlank { EasternArabic.format(entry.surah) }
    return stringResource(
        R.string.index_page_meta,
        EasternArabic.format(entry.pageNumber),
        name,
        EasternArabic.format(entry.ayah),
    )
}

@ArabicPreviews
@Composable
private fun QuranIndexPickerPreview() {
    PreviewTheme {
        QuranIndexPickerContent(
            ui = PreviewFixtures.indexUi,
            fontManager = null,
            onTab = {},
            onQuery = {},
            onClose = {},
            onSelectPage = {},
            onSubmitQuery = {},
            modifier = Modifier.height(560.dp),
        )
    }
}
