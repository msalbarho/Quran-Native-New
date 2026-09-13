package com.quransunah.app.ui.index

import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicNormalize
import com.quransunah.app.core.ArabicOrdinals
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.IndexJump
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.repository.MushafRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.Immutable

enum class QuranIndexTab {
    Surah,
    Juz,
    Hizb,
    Sajdah,
}

@Immutable
data class HizbIndexSection(
    val hizb: DivisionInfo,
    val quarters: List<DivisionInfo>,
)

@Immutable
data class JuzIndexSection(
    val juz: DivisionInfo,
    val hizbs: List<DivisionInfo>,
)

@Immutable
data class QuranIndexUiState(
    val tab: QuranIndexTab = QuranIndexTab.Surah,
    val query: String = "",
    val loaded: Boolean = false,
    val currentSurah: Int = 1,
    val currentJuz: Int = 1,
    val currentHizb: Int = 1,
    val currentPage: Int = 1,
    val surahs: List<SurahInfo> = emptyList(),
    val juz: List<DivisionInfo> = emptyList(),
    val hizbs: List<DivisionInfo> = emptyList(),
    val quarters: List<DivisionInfo> = emptyList(),
    val sajdas: List<SajdaMarker> = emptyList(),
    val surahNames: Map<Int, String> = emptyMap(),
    val hizbsByJuz: Map<Int, List<DivisionInfo>> = emptyMap(),
    val visibleSurahs: List<SurahInfo> = emptyList(),
    val visibleJuzSections: List<JuzIndexSection> = emptyList(),
    val visibleHizbSections: List<HizbIndexSection> = emptyList(),
    val visibleSajdas: List<SajdaMarker> = emptyList(),
) {
    fun surahName(number: Int): String = surahNames[number].orEmpty()

    val filteredSurahs: List<SurahInfo> get() = visibleSurahs
    val juzSections: List<JuzIndexSection> get() = visibleJuzSections
    val filteredJuz: List<DivisionInfo> get() = visibleJuzSections.map { it.juz }
    val hizbSections: List<HizbIndexSection> get() = visibleHizbSections
}

internal fun QuranIndexUiState.derived(): QuranIndexUiState {
    val names = if (surahNames.size == surahs.size && surahs.isNotEmpty()) {
        surahNames
    } else {
        surahs.associate { it.number to it.nameArabic }
    }
    val groupedHizbs = if (hizbsByJuz.isNotEmpty() && hizbsByJuz.size >= (hizbs.size / 2).coerceAtLeast(1)) {
        hizbsByJuz
    } else {
        hizbs.groupBy { hizb -> ((hizb.number - 1) / 2) + 1 }
    }
    val nextSurahs = if (query.isBlank()) surahs else surahs.filter { matchesSurahQuery(it, query) }
    return copy(
        surahNames = names,
        hizbsByJuz = groupedHizbs,
        visibleSurahs = nextSurahs,
        visibleJuzSections = if (tab == QuranIndexTab.Juz) {
            buildJuzSections(query, juz, groupedHizbs, names)
        } else {
            visibleJuzSections
        },
        visibleHizbSections = if (tab == QuranIndexTab.Hizb) {
            buildHizbSections(query, hizbs, quarters, names)
        } else {
            visibleHizbSections
        },
        visibleSajdas = if (tab == QuranIndexTab.Sajdah) {
            buildSajdaRows(query, sajdas, names)
        } else {
            visibleSajdas
        },
    )
}

private fun buildJuzSections(
    query: String,
    juz: List<DivisionInfo>,
    hizbsByJuz: Map<Int, List<DivisionInfo>>,
    names: Map<Int, String>,
): List<JuzIndexSection> {
    val q = query.trim()
    fun nameOf(surah: Int) = names[surah].orEmpty()
    return juz.mapNotNull { entry ->
        val children = hizbsByJuz[entry.number].orEmpty()
        val selfMatch = matchesDivisionQuery(
            entry,
            q,
            nameOf(entry.surah),
            ArabicOrdinals.juzLabel(entry.number),
            ArabicOrdinals.juzIndexTitle(entry.number),
            ArabicOrdinals.juzOpeningName(entry.number),
        )
        val nested = children.filter { hizb ->
            q.isEmpty() || matchesDivisionQuery(hizb, q, nameOf(hizb.surah), hizbTitle(hizb))
        }
        if (q.isNotEmpty() && !selfMatch && nested.isEmpty()) return@mapNotNull null
        JuzIndexSection(juz = entry, hizbs = if (q.isEmpty()) children else nested)
    }
}

private fun buildHizbSections(
    query: String,
    hizbs: List<DivisionInfo>,
    quarters: List<DivisionInfo>,
    names: Map<Int, String>,
): List<HizbIndexSection> {
    val q = query.trim()
    fun nameOf(surah: Int) = names[surah].orEmpty()
    return hizbs.mapNotNull { hizb ->
        val childQuarters = quarters.filter { quarter ->
            quarter.parentNumber == hizb.number &&
                quarter.pageNumber != hizb.pageNumber &&
                (q.isEmpty() || matchesDivisionQuery(quarter, q, nameOf(quarter.surah), quarter.name))
        }
        val selfMatch = matchesDivisionQuery(hizb, q, nameOf(hizb.surah), hizbTitle(hizb))
        if (q.isNotEmpty() && !selfMatch && childQuarters.isEmpty()) return@mapNotNull null
        HizbIndexSection(hizb = hizb, quarters = childQuarters)
    }
}

private fun buildSajdaRows(
    query: String,
    sajdas: List<SajdaMarker>,
    names: Map<Int, String>,
): List<SajdaMarker> {
    val q = query.trim()
    if (q.isEmpty()) return sajdas
    return sajdas.filter { marker ->
        matchesSajdaQuery(marker, q, names[marker.startSurah].orEmpty())
    }
}

@HiltViewModel
class QuranIndexViewModel @Inject constructor(
    private val mushafRepository: MushafRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(QuranIndexUiState())
    val uiState: StateFlow<QuranIndexUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val surahs = runCatching { mushafRepository.getSurahs() }.getOrDefault(emptyList())
            val divisions = runCatching { mushafRepository.getDivisions() }.getOrDefault(emptyList())
            val sajdas = runCatching { mushafRepository.getSajdas() }.getOrDefault(emptyList())
            val quarters = runCatching { mushafRepository.getQuarters() }.getOrDefault(emptyList())
            _ui.update { current ->
                current.copy(
                    loaded = true,
                    surahs = surahs,
                    juz = divisions.filter { it.type == "juz" }.sortedBy { it.number },
                    hizbs = divisions.filter { it.type == "hizb" }.sortedBy { it.number },
                    quarters = quarters.sortedBy { it.number },
                    sajdas = sajdas,
                ).derived()
            }
        }
    }

    fun prepare(tab: QuranIndexTab, pageNumber: Int, juz: Int, hizb: Int, currentSurah: Int) {
        val provisional = currentSurah.coerceIn(1, AppConstants.SURAH_COUNT)
        _ui.update { current ->
            current.copy(
                tab = tab,
                query = "",
                currentJuz = juz.coerceAtLeast(1),
                currentHizb = hizb.coerceAtLeast(1),
                currentPage = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES),
                currentSurah = provisional,
            ).derived()
        }
        if (tab == QuranIndexTab.Hizb) loadQuarters()
        // Authoritative page→surah mapping (matches React getSurahForPage).
        viewModelScope.launch(Dispatchers.Default) {
            val resolved = runCatching { mushafRepository.getSurahForPage(pageNumber) }
                .getOrDefault(provisional)
                .coerceIn(1, AppConstants.SURAH_COUNT)
            if (resolved != _ui.value.currentSurah) {
                _ui.update { it.copy(currentSurah = resolved).derived() }
            }
        }
    }

    fun resetForOpen(tab: QuranIndexTab) {
        _ui.update { current ->
            current.copy(tab = tab, query = "").derived()
        }
        if (tab == QuranIndexTab.Hizb) loadQuarters()
    }

    fun setTab(tab: QuranIndexTab) {
        _ui.update { current ->
            if (current.tab == tab) current
            else current.copy(tab = tab, query = "").derived()
        }
        if (tab == QuranIndexTab.Hizb) loadQuarters()
    }

    fun setQuery(value: String) {
        _ui.update { it.copy(query = value).derived() }
    }

    private fun loadQuarters() {
        if (_ui.value.quarters.isNotEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            val quarters = runCatching { mushafRepository.getQuarters() }.getOrDefault(emptyList())
            _ui.update { current ->
                current.copy(quarters = quarters.sortedBy { it.number }).derived()
            }
        }
    }

    fun resolveJump(): IndexJump? {
        val ui = _ui.value
        val digits = EasternArabic.westernDigits(ui.query.trim())
        val target = digits.toIntOrNull()
        return when (ui.tab) {
            QuranIndexTab.Surah -> {
                val surah = if (target != null && target in 1..AppConstants.SURAH_COUNT) {
                    ui.surahs.find { it.number == target }
                } else {
                    ui.filteredSurahs.singleOrNull()
                }
                surah?.toIndexJump()
            }
            QuranIndexTab.Juz -> {
                when {
                    target != null && target in 1..30 ->
                        ui.juz.find { it.number == target }?.toIndexJump()
                    else ->
                        ui.filteredJuz.singleOrNull()?.toIndexJump()
                            ?: ui.juzSections.singleOrNull()?.hizbs?.singleOrNull()?.toIndexJump()
                }
            }
            QuranIndexTab.Hizb -> {
                when {
                    target != null && target in 1..60 ->
                        ui.hizbs.find { it.number == target }?.toIndexJump()
                    target != null && target in 1..240 ->
                        ui.quarters.find { it.number == target }?.toIndexJump()
                    else ->
                        ui.hizbSections.firstOrNull()?.hizb?.toIndexJump()
                }
            }
            QuranIndexTab.Sajdah -> {
                when {
                    target != null && target in 1..15 ->
                        ui.sajdas.find { it.number == target }?.toIndexJump()
                    else ->
                        ui.visibleSajdas.singleOrNull()?.toIndexJump()
                }
            }
        }
    }
}

internal fun hizbTitle(hizb: DivisionInfo): String =
    hizb.name.trim().ifBlank { ArabicOrdinals.hizbLabel(hizb.number) }

internal fun matchesSurahQuery(surah: SurahInfo, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    val digits = EasternArabic.westernDigits(trimmed)
    if (digits.isNotEmpty() && surah.number.toString().contains(digits)) return true
    val foldedQuery = foldIndexQuery(trimmed)
    if (foldedQuery.isEmpty()) return digits.isEmpty()
    return foldIndexQuery(surah.nameArabic).contains(foldedQuery)
}

internal fun matchesDivisionQuery(
    entry: DivisionInfo,
    query: String,
    surahName: String,
    vararg displayNames: String,
): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    val digits = EasternArabic.westernDigits(trimmed)
    if (digits.isNotEmpty()) {
        if (entry.number.toString().contains(digits)) return true
        if (entry.pageNumber.toString().contains(digits)) return true
        if (entry.parentNumber > 0 && entry.parentNumber.toString().contains(digits)) return true
    }
    val foldedQuery = foldIndexQuery(trimmed)
    if (foldedQuery.isEmpty()) return digits.isNotEmpty()
    return (sequenceOf(entry.name, surahName) + displayNames.asSequence()).any { candidate ->
        foldIndexQuery(candidate).contains(foldedQuery)
    }
}

internal fun revelationPlace(type: String): String? {
    val normalized = type.trim().lowercase()
    return when (normalized) {
        "meccan", "مكية" -> "meccan"
        "medinan", "مدنية" -> "medinan"
        else -> null
    }
}

private fun foldIndexQuery(value: String): String {
    val stripped = value
        .replace(Regex("^سُورَةُ\\s+"), "")
        .replace(Regex("^سورة\\s+"), "")
    return ArabicNormalize.fold(stripped, stripSpaces = true).lowercase()
}

internal fun matchesSajdaQuery(
    marker: SajdaMarker,
    query: String,
    surahName: String,
): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true
    val digits = EasternArabic.westernDigits(trimmed)
    if (digits.isNotEmpty()) {
        if (marker.number.toString().contains(digits)) return true
        if (marker.startAyah.toString().contains(digits)) return true
        if (marker.pageNumber.toString().contains(digits)) return true
        if (marker.startSurah.toString().contains(digits)) return true
    }
    val foldedQuery = foldIndexQuery(trimmed)
    if (foldedQuery.isEmpty()) return digits.isNotEmpty()
    return foldIndexQuery(surahName).contains(foldedQuery)
}

internal fun SurahInfo.toIndexJump(): IndexJump =
    IndexJump(pageNumber = startPage, surah = number, ayah = 1)

internal fun DivisionInfo.toIndexJump(): IndexJump =
    IndexJump(pageNumber = pageNumber, surah = surah, ayah = ayah)

internal fun SajdaMarker.toIndexJump(): IndexJump =
    IndexJump(pageNumber = pageNumber, surah = startSurah, ayah = startAyah)
