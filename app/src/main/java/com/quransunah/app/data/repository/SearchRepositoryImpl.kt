package com.quransunah.app.data.repository

import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.ArabicNormalize
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.local.mushaf.MushafDao
import com.quransunah.app.data.local.user.SearchDao
import com.quransunah.app.data.local.user.entity.AyahSearchEntity
import com.quransunah.app.data.prefs.UserPreferences
import com.quransunah.app.domain.model.AyahSearchHit
import com.quransunah.app.domain.model.SurahInfo
import com.quransunah.app.domain.repository.MushafRepository
import com.quransunah.app.domain.repository.SearchRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class SearchRepositoryImpl @Inject constructor(
    private val mushafDao: MushafDao,
    private val searchDao: SearchDao,
    private val mushafRepository: MushafRepository,
    private val preferences: UserPreferences,
) : SearchRepository {
    private val mutex = Mutex()
    @Volatile private var surahs: List<SurahInfo> = emptyList()
    @Volatile private var surahNames: Map<Int, String> = emptyMap()

    override suspend fun ensureIndex() = mutex.withLock {
        if (surahs.isEmpty()) {
            surahs = mushafRepository.getSurahs()
            surahNames = surahs.associate { it.number to it.nameArabic }
        }
        val version = preferences.getSearchIndexVersion()
        if (searchDao.count() > 0 && version == AppConstants.SEARCH_INDEX_VERSION) return
        rebuildIndex()
        preferences.setSearchIndexVersion(AppConstants.SEARCH_INDEX_VERSION)
    }

    override suspend fun search(query: String): List<AyahSearchHit> {
        ensureIndex()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()

        parseNumericReference(trimmed)?.let { (surah, ayah) ->
            return listOfNotNull(referenceHit(surah, ayah))
        }
        parseNamedReference(trimmed)?.let { (surah, ayah) ->
            return listOfNotNull(referenceHit(surah, ayah))
        }

        val needle = ArabicNormalize.fold(trimmed, stripSpaces = true)
            .replace("%", "")
            .replace("_", "")
        if (needle.length < AppConstants.SEARCH_MIN_QUERY_LENGTH) return emptyList()
        return searchDao.containsSearch(needle, AppConstants.SEARCH_RESULT_LIMIT)
            .map { it.toDomain(needle) }
    }

    private suspend fun rebuildIndex() = withContext(Dispatchers.IO) {
        searchDao.clear()
        val ranges = mushafDao.getAyahLineRanges()
            .sortedBy { it.firstWordIndex }
        val rows = ArrayList<AyahSearchEntity>(6_236)
        val buffer = StringBuilder()
        var currentSurah = -1
        var currentAyah = -1
        var firstWordId = -1

        fun pageForWord(wordId: Int): Int {
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
            return 1
        }

        fun flush() {
            if (currentSurah < 0) return
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) {
                rows += AyahSearchEntity(
                    ayahKey = "$currentSurah:$currentAyah",
                    surah = currentSurah,
                    ayah = currentAyah,
                    pageNumber = pageForWord(firstWordId),
                    textHafs = text,
                    normalized = ArabicNormalize.fold(text, stripSpaces = true),
                )
            }
            buffer.setLength(0)
            firstWordId = -1
        }

        for (word in mushafDao.getSearchWordRows()) {
            if (word.surahId != currentSurah || word.ayahNumber != currentAyah) {
                flush()
                currentSurah = word.surahId
                currentAyah = word.ayahNumber
                firstWordId = word.wordId
            }
            val piece = word.textHafs.orEmpty()
            if (piece.isEmpty()) continue
            if (buffer.isNotEmpty()) buffer.append(' ')
            buffer.append(piece)
        }
        flush()
        if (rows.isNotEmpty()) searchDao.upsertAll(rows)
    }

    private suspend fun referenceHit(surah: Int, ayah: Int): AyahSearchHit? {
        val stored = searchDao.getReference(surah, ayah)
        if (stored != null) return stored.toDomain(queryNeedle = null)
        if (ayah !in 1..SurahAyahCounts.ayahCount(surah)) return null
        val words = mushafRepository.getAyahWords(surah, ayah)
        val text = words.filter { !it.isAyahMarker }.joinToString(" ") { it.textHafs }.trim()
        val page = mushafRepository.getPageForAyah(surah, ayah)
        return AyahSearchHit(
            surah = surah,
            ayah = ayah,
            pageNumber = page,
            juzNumber = mushafRepository.getPageMeta(page).juzNumber,
            text = text,
            surahName = surahName(surah),
            snippet = text,
        )
    }

    private fun parseNumericReference(raw: String): Pair<Int, Int>? {
        val western = EasternArabic.parseDigits(raw)
        val match = NUMERIC_REF.matchEntire(western) ?: return null
        val surah = match.groupValues[1].toInt()
        val ayah = match.groupValues[2].toInt()
        if (surah !in 1..AppConstants.SURAH_COUNT) return null
        if (ayah !in 1..SurahAyahCounts.ayahCount(surah)) return null
        return surah to ayah
    }

    private fun parseNamedReference(raw: String): Pair<Int, Int>? {
        val western = EasternArabic.parseDigits(raw).trim()
        val stripped = western.replace(SURAH_PREFIX, "").trim()
        if (stripped.isEmpty()) return null

        val withAyah = NAMED_WITH_AYAH.matchEntire(stripped)
        if (withAyah != null) {
            val name = withAyah.groupValues[1].trim()
            val ayah = withAyah.groupValues[2].toInt()
            val surah = matchSurah(name) ?: return null
            if (ayah !in 1..SurahAyahCounts.ayahCount(surah)) return null
            return surah to ayah
        }

        val surahOnly = matchSurah(stripped) ?: return null
        return surahOnly to 1
    }

    private fun matchSurah(name: String): Int? {
        val needle = ArabicNormalize.stripArticle(name)
        if (needle.isEmpty()) return null
        val exact = surahs.firstOrNull { ArabicNormalize.stripArticle(it.nameArabic) == needle }
        if (exact != null) return exact.number
        val starts = surahs.filter { ArabicNormalize.stripArticle(it.nameArabic).startsWith(needle) }
        return starts.singleOrNull()?.number
    }

    private suspend fun AyahSearchEntity.toDomain(queryNeedle: String?): AyahSearchHit {
        val range = queryNeedle?.let { highlightRange(textHafs, it) }
        val (snippet, start, end) = snippetAround(textHafs, range?.first ?: -1, range?.second ?: -1)
        return AyahSearchHit(
            surah = surah,
            ayah = ayah,
            pageNumber = pageNumber,
            juzNumber = mushafRepository.getPageMeta(pageNumber).juzNumber,
            text = textHafs,
            surahName = surahName(surah),
            snippet = snippet,
            highlightStart = start,
            highlightEnd = end,
        )
    }

    private fun surahName(surah: Int): String = surahNames[surah].orEmpty()

    companion object {
        private val NUMERIC_REF = Regex("""^\s*(\d{1,3})\s*[:：]\s*(\d{1,3})\s*$""")
        private val NAMED_WITH_AYAH = Regex("""^(.+?)(?:\s*[:：]\s*|\s+)(\d{1,3})\s*$""")
        private val SURAH_PREFIX = Regex("""^سورة\s+""")
        private const val SNIPPET_RADIUS = 36
        private const val SNIPPET_MAX = 140

        internal fun highlightRange(text: String, needle: String): Pair<Int, Int>? {
            if (needle.isEmpty()) return null
            val startMap = ArrayList<Int>(text.length)
            val endMap = ArrayList<Int>(text.length)
            val folded = buildString(text.length) {
                for (index in text.indices) {
                    val piece = ArabicNormalize.fold(text[index].toString(), stripSpaces = true)
                    if (piece.isEmpty()) continue
                    for (offset in piece.indices) {
                        append(piece[offset])
                        startMap.add(index)
                        endMap.add(index + 1)
                    }
                }
            }
            val at = folded.indexOf(needle)
            if (at < 0) return null
            val last = at + needle.length - 1
            return startMap[at] to endMap[last]
        }

        internal fun snippetAround(text: String, start: Int, end: Int): Triple<String, Int, Int> {
            if (text.length <= SNIPPET_MAX || start < 0 || end <= start) {
                return Triple(text, start, end)
            }
            val from = (start - SNIPPET_RADIUS).coerceAtLeast(0)
            val to = (end + SNIPPET_RADIUS).coerceAtMost(text.length)
            val prefix = if (from > 0) "…" else ""
            val suffix = if (to < text.length) "…" else ""
            val snippet = prefix + text.substring(from, to) + suffix
            val highlightStart = start - from + prefix.length
            val highlightEnd = end - from + prefix.length
            return Triple(snippet, highlightStart, highlightEnd)
        }
    }
}
