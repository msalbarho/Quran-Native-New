package com.quransunah.app.data.repository

import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicNormalize
import com.quransunah.app.core.PageMetaFallback
import com.quransunah.app.data.local.mushaf.AyahLineRangeRow
import com.quransunah.app.data.local.mushaf.MushafDao
import com.quransunah.app.data.local.mushaf.PageStructureRow
import com.quransunah.app.data.local.mushaf.entity.WordEntity
import com.quransunah.app.data.local.markers.MarkersDao
import com.quransunah.app.data.local.user.WordMeaningDao
import com.quransunah.app.data.local.user.entity.WordMeaningEntity
import com.quransunah.app.data.packaged.PackagedStoreManager
import com.quransunah.app.domain.model.DivisionInfo
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.domain.model.MushafLine
import com.quransunah.app.domain.model.MushafPage
import com.quransunah.app.domain.model.PageMeta
import com.quransunah.app.domain.model.QuarterMarker
import com.quransunah.app.domain.model.QuranWord
import com.quransunah.app.domain.model.SajdaMarker
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.repository.MushafRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class MushafRepositoryImpl @Inject constructor(
    private val mushafDao: MushafDao,
    private val markersDao: MarkersDao,
    private val wordMeaningDao: WordMeaningDao,
    private val packaged: PackagedStoreManager,
) : MushafRepository {

    private val warmupMutex = Mutex()
    private val heavyMutex = Mutex()
    private var warmed = false
    private var surahsCache: List<SurahInfo>? = null
    private var divisionsCache: List<DivisionInfo>? = null
    private var quartersCache: List<DivisionInfo>? = null
    @Volatile private var markersLoaded = false
    @Volatile private var quarterByPage: Map<Int, QuarterMarker> = emptyMap()
    @Volatile private var sajdaByPage: Map<Int, SajdaMarker> = emptyMap()
    @Volatile private var sajdaCatalog: List<SajdaMarker> = emptyList()
    @Volatile private var meaningByWordId: Map<Int, String> = emptyMap()
    private var basmallahWords: List<QuranWord> = emptyList()
    private var ayahPageLookup: AyahPageLookup? = null

    override suspend fun warmup(): Result<Unit> = warmupMutex.withLock {
        runCatching {
            if (warmed) return@runCatching
            mushafDao.getEdition() ?: error("missing mushaf edition")
            if (mushafDao.wordCount() <= 0) error("empty words table")
            packaged.markCurrent(
                AppConstants.QURAN_DB_ASSET,
                AppConstants.QURAN_DB_FILE,
                AppConstants.QURAN_DB_VERSION,
            )
            warmed = true
        }
    }

    override suspend fun prefetchHeavyData() = heavyMutex.withLock {
        warmup().getOrThrow()
        ensureMeanings()
        ensureMarkers()
        prefetchIndexCatalog()
    }

    override suspend fun prefetchIndexCatalog() {
        warmup().getOrThrow()
        getSurahs()
        getDivisions()
        getQuarters()
        getSajdas()
    }

    override suspend fun getPage(pageNumber: Int): MushafPage = withContext(Dispatchers.Default) {
        warmup().getOrThrow()
        ensureBasmallah()
        val page = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        val structure = mushafDao.getPageStructure(page)
        val lines = assembleLines(page, structure)
        val meta = getPageMeta(page)
        overlayMarkers(
            MushafPage(
                pageNumber = page,
                lines = lines,
                juzNumber = meta.juzNumber,
                hizbNumber = meta.hizbNumber,
            ),
        )
    }

    override fun overlayMarkers(page: MushafPage): MushafPage {
        var next = page
        if (markersLoaded) {
            val quarter = quarterByPage[page.pageNumber]
            next = next.copy(
                quarterLabel = quarter?.label,
                quarter = quarter,
                sajda = sajdaByPage[page.pageNumber],
            )
        }
        if (meaningByWordId.isNotEmpty()) {
            next = next.copy(
                lines = next.lines.map { line ->
                    line.copy(
                        words = line.words.map { word ->
                            if (!word.meaning.isNullOrBlank()) word
                            else word.copy(meaning = meaningByWordId[word.id])
                        },
                    )
                },
            )
        }
        return next
    }

    override suspend fun getSurahs(): List<SurahInfo> {
        warmup().getOrThrow()
        return surahsCache ?: loadSurahs().also { surahsCache = it }
    }

    override suspend fun getDivisions(): List<DivisionInfo> {
        warmup().getOrThrow()
        return divisionsCache ?: loadDivisions().also { divisionsCache = it }
    }

    override suspend fun getQuarters(): List<DivisionInfo> {
        warmup().getOrThrow()
        ensureMarkers()
        return quartersCache.orEmpty()
    }

    override suspend fun getSajdas(): List<SajdaMarker> {
        warmup().getOrThrow()
        ensureMarkers()
        return sajdaCatalog
    }

    private var packagedPageMetaPresent: Boolean? = null

    override suspend fun getPageMeta(pageNumber: Int): PageMeta {
        val page = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        if (packagedPageMetaAvailable()) {
            mushafDao.getPackagedPageMeta(page)?.let { row ->
                return PageMeta(row.pageNumber, row.juzNumber, row.hizbNumber)
            }
        }
        return PageMetaFallback.build(page)
    }

    override suspend fun getAyahWords(surah: Int, ayah: Int): List<QuranWord> =
        mushafDao.getAyahWords(surah, ayah).map { it.toDomain() }

    override suspend fun getPageForAyah(surah: Int, ayah: Int): Int {
        ayahPageLookup?.pageForAyah(surah, ayah)?.let { return it }
        return mushafDao.getPageForAyah(surah, ayah) ?: 1
    }

    override suspend fun getSurahForPage(pageNumber: Int): Int {
        PageMetaFallback.surahOverride(pageNumber)?.let { return it }
        return mushafDao.getSurahForPage(pageNumber) ?: 1
    }

    override suspend fun meaningForWord(wordId: Int): String? {
        meaningByWordId[wordId]?.let { return it }
        wordMeaningDao.getMeaning(wordId)?.let { return it }
        ensureMeanings()
        return meaningByWordId[wordId]
    }

    private suspend fun ensureBasmallah() {
        if (basmallahWords.isNotEmpty()) return
        basmallahWords = mushafDao.getBasmallahWords().map { it.toDomain() }
    }

    private suspend fun loadSurahs(): List<SurahInfo> {
        val starts = mushafDao.getSurahStartPages().associate { it.surahId to it.startPage }
        return mushafDao.getSurahs().map { entity ->
            val surahId = entity.id ?: error("surah row has null id")
            SurahInfo(
                number = surahId,
                nameArabic = entity.nameArabic,
                numberOfAyahs = entity.numberOfAyahs,
                revelationType = entity.revelationType.orEmpty(),
                startPage = starts[surahId] ?: 1,
            )
        }
    }

    private suspend fun loadDivisions(): List<DivisionInfo> {
        val entities = mushafDao.getDivisions()
        val needsLookup = entities.any { entity ->
            entity.pageId == null && (entity.divisionType == "juz" || entity.divisionType == "hizb")
        }
        val pageLookup = if (needsLookup) ayahPageIndex() else null
        return entities.mapNotNull { entity ->
            val type = entity.divisionType
            if (type != "juz" && type != "hizb") return@mapNotNull null
            val surah = entity.startSurah ?: return@mapNotNull null
            val ayah = entity.startAyah ?: return@mapNotNull null
            val divisionId = entity.id ?: return@mapNotNull null
            DivisionInfo(
                id = divisionId,
                type = type,
                number = entity.divisionNumber ?: 0,
                name = entity.name.orEmpty(),
                pageNumber = entity.pageId
                    ?: pageLookup?.pageForAyah(surah, ayah)
                    ?: 1,
                surah = surah,
                ayah = ayah,
            )
        }
    }

    private suspend fun packagedPageMetaAvailable(): Boolean {
        packagedPageMetaPresent?.let { return it }
        val present = runCatching { mushafDao.hasPageMetaTable() != null }.getOrDefault(false)
        packagedPageMetaPresent = present
        return present
    }

    private suspend fun ensureMeanings() {
        if (meaningByWordId.isNotEmpty()) return
        if (wordMeaningDao.count() > 0) {
            meaningByWordId = wordMeaningDao.getAll().associate { it.wordId to it.meaning }
            return
        }
        val ayahByGlobal = mushafDao.getAyahIndex().associate {
            it.globalAyahIndex to (it.surahId to it.ayahNumber)
        }
        val wordsByAyah = mushafDao.getMeaningWordRows().groupBy { "${it.surahId}:${it.ayahNumber}" }
        val mapped = LinkedHashMap<Int, String>()
        for (phrase in mushafDao.getMeaningPhrases()) {
            val loc = ayahByGlobal[phrase.ayahId] ?: continue
            val meaning = phrase.text?.trim().orEmpty()
            if (meaning.isEmpty()) continue
            val words = wordsByAyah["${loc.first}:${loc.second}"].orEmpty()
            val tokens = (phrase.word ?: "").let { raw ->
                if (raw.contains(' ')) raw.split(Regex("\\s+")) else listOf(raw)
            }
            for (token in tokens) {
                val normalizedToken = normalizeArabic(token)
                val hit = words.find { word ->
                    val text = word.textHafs.orEmpty()
                    text == token || normalizeArabic(text) == normalizedToken
                } ?: continue
                mapped.putIfAbsent(hit.wordId, meaning)
            }
        }
        if (mapped.isNotEmpty()) {
            wordMeaningDao.upsertAll(mapped.map { WordMeaningEntity(it.key, it.value) })
        }
        meaningByWordId = mapped
    }

    private suspend fun ensureMarkers() {
        if (markersLoaded) return
        loadMarkers()
        markersLoaded = true
    }

    private suspend fun loadMarkers() {
        val pageLookup = ayahPageIndex()
        val rub = markersDao.getRubMarkers()
        val sajda = markersDao.getSajdaMarkers()
        val quarter = mutableMapOf<Int, QuarterMarker>()
        val quarterRows = ArrayList<DivisionInfo>(rub.size)
        for (row in rub) {
            val key = row.firstVerseKey ?: continue
            val parsed = parseVerseKey(key) ?: continue
            val page = pageLookup.pageForAyah(parsed.first, parsed.second) ?: continue
            val label = row.label?.trim().orEmpty().ifBlank { row.quarterTypeLabel.orEmpty() }
            if (label.isEmpty()) continue
            quarter[page] = QuarterMarker(page, label, parsed.first, parsed.second)
            val rubNumber = row.rubNumber ?: continue
            quarterRows += DivisionInfo(
                id = 10_000 + rubNumber,
                type = "quarter",
                number = rubNumber,
                name = label,
                pageNumber = page,
                surah = parsed.first,
                ayah = parsed.second,
                parentNumber = row.hizbNumber ?: 0,
            )
        }
        quartersCache = quarterRows.sortedBy { it.number }
        val sajdaMap = mutableMapOf<Int, SajdaMarker>()
        val sajdaRows = ArrayList<SajdaMarker>(sajda.size)
        for (row in sajda) {
            val key = row.verseKey ?: continue
            val parsed = parseVerseKey(key) ?: continue
            val page = pageLookup.pageForAyah(parsed.first, parsed.second) ?: continue
            val marker = SajdaMarker(
                pageNumber = page,
                startSurah = parsed.first,
                startAyah = parsed.second,
                required = row.sajdahType == "required",
                number = row.sajdahNumber ?: (sajdaRows.size + 1),
            )
            sajdaMap[page] = marker
            sajdaRows += marker
        }
        quarterByPage = quarter
        sajdaByPage = sajdaMap
        sajdaCatalog = sajdaRows.sortedBy { it.number }
    }

    private suspend fun ayahPageIndex(): AyahPageLookup {
        ayahPageLookup?.let { return it }
        val ranges = mushafDao.getAyahLineRanges().sortedBy { it.firstWordIndex }
        val firstWords = HashMap<Long, Int>(6_400)
        for (row in mushafDao.getAyahFirstWordIds()) {
            firstWords[ayahKey(row.surahId, row.ayahNumber)] = row.firstWordId
        }
        return AyahPageLookup(ranges, firstWords).also { ayahPageLookup = it }
    }

    /**
     * Same semantics as React `getPageLinesComplete`: take each
     * `mushaf_pages` row in `line_number` order, then attach `words` whose
     * `word_id` is between `first_word_index` and `last_word_index`, ordered
     * by `word_id`. Ranges are applied in memory so a JOIN cannot leak words
     * from another line or surah.
     */
    private suspend fun assembleLines(pageNumber: Int, rows: List<PageStructureRow>): List<MushafLine> {
        val firstId = rows.mapNotNull { it.firstWordIndex }.minOrNull()
        val lastId = rows.mapNotNull { it.lastWordIndex }.maxOrNull()
        val pageWords = if (firstId != null && lastId != null && firstId <= lastId) {
            mushafDao.getWordsInRange(firstId, lastId).map { it.toDomain() }
        } else {
            emptyList()
        }
        val grouped = LinkedHashMap<Int, MushafLine>(AppConstants.LINES_PER_PAGE)
        for (row in rows) {
            val lineType = LineType.fromRaw(row.lineType)
            val words = if (
                (lineType == LineType.AYAH || lineType == LineType.BASMALLAH) &&
                row.firstWordIndex != null &&
                row.lastWordIndex != null
            ) {
                val first = row.firstWordIndex
                val last = row.lastWordIndex
                pageWords.filter { word -> word.id in first..last }.sortedBy { it.id }
            } else {
                emptyList()
            }
            grouped[row.lineNumber] = MushafLine(
                lineNumber = row.lineNumber,
                lineType = lineType,
                isCentered = row.isCentered == 1,
                surahNumber = row.surahId,
                words = words,
            )
        }
        for (decor in mushafDao.getDecorativeLines(pageNumber)) {
            if (!grouped.containsKey(decor.lineNumber)) {
                grouped[decor.lineNumber] = MushafLine(
                    lineNumber = decor.lineNumber,
                    lineType = LineType.fromRaw(decor.lineType),
                    isCentered = decor.isCentered == 1,
                    surahNumber = decor.surahId,
                    words = emptyList(),
                )
            }
        }
        return padLines(pageNumber, grouped.values.sortedBy { it.lineNumber })
    }

    private fun padLines(pageNumber: Int, source: List<MushafLine>): List<MushafLine> {
        var padded = (1..AppConstants.LINES_PER_PAGE).map { lineNumber ->
            source.find { it.lineNumber == lineNumber } ?: MushafLine(
                lineNumber = lineNumber,
                lineType = LineType.EMPTY,
                isCentered = false,
                surahNumber = null,
                words = emptyList(),
            )
        }
        if (pageNumber == 1 || pageNumber == 2) {
            val withGap = mutableListOf<MushafLine>()
            var inserted = false
            for (line in padded) {
                withGap += line
                if (!inserted && line.lineType == LineType.SURAH_NAME) {
                    withGap += MushafLine(-1, LineType.EMPTY, false, null, emptyList())
                    withGap += MushafLine(-1, LineType.EMPTY, false, null, emptyList())
                    inserted = true
                }
            }
            padded = withGap.take(AppConstants.LINES_PER_PAGE)
        }
        return padded.map { line ->
            if (line.lineType == LineType.BASMALLAH && line.words.isEmpty()) {
                line.copy(words = basmallahWords)
            } else {
                line
            }
        }
    }

    private fun WordEntity.toDomain(): QuranWord = QuranWord(
        id = wordId ?: error("word row has null word_id"),
        surah = surahId,
        ayah = ayahNumber,
        wordIndex = wordPosition,
        textLigature = qpcLigature?.ifBlank { null } ?: textHafs.orEmpty(),
        textHafs = textHafs.orEmpty(),
        meaning = meaningByWordId[wordId],
        isAyahMarker = isAyahMarkerGlyph(isAyahMarker, textHafs.orEmpty(), qpcLigature.orEmpty()),
    )

    private fun isAyahMarkerGlyph(column: Int?, textHafs: String, ligature: String): Boolean {
        if (column == 1) return true
        return ligature.length == 1 && textHafs.isNotEmpty() && textHafs.all { it in '\u0660'..'\u0669' }
    }

    private fun parseVerseKey(key: String): Pair<Int, Int>? {
        val parts = key.split(':')
        if (parts.size != 2) return null
        val surah = parts[0].toIntOrNull() ?: return null
        val ayah = parts[1].toIntOrNull() ?: return null
        return surah to ayah
    }

    private fun normalizeArabic(value: String): String = ArabicNormalize.fold(value)
}

private fun ayahKey(surah: Int, ayah: Int): Long =
    (surah.toLong() shl 32) or (ayah.toLong() and 0xFFFF_FFFFL)

private class AyahPageLookup(
    private val ranges: List<AyahLineRangeRow>,
    private val firstWordByAyah: Map<Long, Int>,
) {
    fun pageForAyah(surah: Int, ayah: Int): Int? {
        val wordId = firstWordByAyah[ayahKey(surah, ayah)] ?: return null
        return pageForWord(wordId)
    }

    private fun pageForWord(wordId: Int): Int? {
        if (ranges.isEmpty()) return null
        var lo = 0
        var hi = ranges.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val range = ranges[mid]
            when {
                wordId < range.firstWordIndex -> hi = mid - 1
                wordId > range.lastWordIndex -> lo = mid + 1
                else -> return range.pageNumber
            }
        }
        return null
    }
}
