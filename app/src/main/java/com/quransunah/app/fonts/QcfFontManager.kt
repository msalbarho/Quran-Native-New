package com.quransunah.app.fonts

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import com.quransunah.app.core.AppConstants
import com.quransunah.app.ui.mushaf.HeaderGlyphFit
import com.quransunah.app.ui.mushaf.WordRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Medina page faces are the same QCF4 Hafs files as React (`QCF4_Hafs_NN_W.woff2`),
 * converted to TTF. Each page uses its mapped face and the raw `qpc_ligature`
 * BMP PUA from `quran_unified.db` — no packed-plane remap.
 *
 * Decorative surah/juz/basmalah ligatures use `qbsml.ttf`. WOFF2 is never packaged.
 * Faces are loaded one-at-a-time on [Dispatchers.IO] and kept in a small Typeface LRU.
 */
@Singleton
class QcfFontManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val loadMutex = Mutex()
    private val mapsMutex = Mutex()
    private val faceCache = object : LinkedHashMap<Int, Typeface>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Typeface>?): Boolean {
            return size > AppConstants.PAGE_FONT_CACHE_SIZE
        }
    }
    private val namedCache = HashMap<String, Typeface>(4)
    private val paintCache = HashMap<Int, Paint>(8)
    private val glyphPaint = Paint()

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var mapsLoaded = false
    @Volatile private var pageFaceNumbers: Map<Int, Int> = emptyMap()
    @Volatile private var decorativeSurahLigatures: Map<Int, String> = emptyMap()
    @Volatile private var headerSingleSurahs: Map<Int, String> = emptyMap()
    @Volatile private var headerCombinedSurahs: Map<String, String> = emptyMap()
    @Volatile private var headerJuzMap: Map<Int, String> = emptyMap()
    @Volatile private var basmalahLigatureText: String = DEFAULT_BASMALAH_LIGATURE

    fun faceNumberForPage(pageNumber: Int): Int {
        val page = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        return pageFaceNumbers[page] ?: defaultFaceNumber(page)
    }

    fun packIdForPage(pageNumber: Int): Int = faceNumberForPage(pageNumber)

    fun familyForPage(pageNumber: Int): String {
        val face = faceNumberForPage(pageNumber)
        return "QCF4_Hafs_%02d".format(java.util.Locale.US, face)
    }

    fun assetPathForPage(pageNumber: Int): String =
        AppConstants.qcf4FaceAsset(faceNumberForPage(pageNumber))

    fun basmalahLigature(): String = basmalahLigatureText

    fun surahNameLigature(surahNumber: Int): String? = decorativeSurahLigatures[surahNumber]

    fun headerSurahLigature(surahNumbers: List<Int>): String {
        return HeaderGlyphFit.composeHeaderLigature(
            surahNumbers,
            headerCombinedSurahs,
            headerSingleSurahs,
            decorativeSurahLigatures,
        )
    }

    fun usesCombinedHeaderSurahGlyph(surahNumbers: List<Int>): Boolean =
        headerCombinedSurahs.containsKey(surahNumbers.joinToString("-"))

    fun juzHeaderLigature(juzNumber: Int): String? = headerJuzMap[juzNumber]

    fun peekPageTypeface(pageNumber: Int): Typeface? =
        synchronized(faceCache) { faceCache[faceNumberForPage(pageNumber)] }

    fun peekBasmalahTypeface(pageNumber: Int): Typeface? = peekSurahTitleTypeface()

    fun peekUthmanicTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.UTHMANIC_HAFS_FILE]
    }

    fun peekSurahTitleTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.QBSML_FONT]
            ?: namedCache[AppConstants.SURAH_TITLE_FONT]
            ?: namedCache[AppConstants.SURAH_TITLE_FALLBACK_FONT]
    }

    fun paintFor(typeface: Typeface): Paint {
        val key = System.identityHashCode(typeface)
        synchronized(paintCache) {
            paintCache[key]?.let { return it }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                this.typeface = typeface
                textAlign = Paint.Align.LEFT
                isLinearText = true
            }
            paintCache[key] = paint
            return paint
        }
    }

    fun canDrawLigature(ligature: String, typeface: Typeface?): Boolean {
        if (ligature.isBlank() || typeface == null) return false
        return synchronized(glyphPaint) {
            glyphPaint.typeface = typeface
            ligature.all { ch ->
                when {
                    ch.isWhitespace() -> true
                    // QCF page faces store ayah words/markers in the BMP PUA.
                    // Paint.hasGlyph() returns false for many PUA codepoints on
                    // Android even when the glyf exists — trust measureText instead.
                    ch.code in PUA_RANGE -> glyphPaint.measureText(ch.toString()) > 0.5f
                    else -> glyphPaint.hasGlyph(ch.toString())
                }
            }
        }
    }

    fun displayLigature(ligature: String, fallback: String, typeface: Typeface?): String {
        return if (canDrawLigature(ligature, typeface)) ligature else fallback
    }

    /**
     * QBSML PUA sequence (U+FAD0–U+FAD3). Private-use characters are bidi class L,
     * so the string is reversed to match React `direction:rtl; unicode-bidi:bidi-override`
     * and read as «بسم الله الرحمن الرحيم».
     */
    fun displayBasmalahLigature(): String {
        val raw = basmalahLigature().ifBlank { DEFAULT_BASMALAH_LIGATURE }
        return raw.reversed()
    }

    suspend fun ensureMaps() {
        if (mapsLoaded) return
        mapsMutex.withLock {
            if (mapsLoaded) return
            withContext(Dispatchers.IO) { loadGlyphMaps() }
            mapsLoaded = true
        }
    }

    suspend fun loadPageTypeface(pageNumber: Int): Typeface {
        ensureMaps()
        val face = faceNumberForPage(pageNumber)
        synchronized(faceCache) { faceCache[face] }?.let { return it }
        return loadMutex.withLock {
            synchronized(faceCache) { faceCache[face] }?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) {
                loadTypeface(assetPathForPage(pageNumber))
            }
            if (loaded != null && loaded != Typeface.DEFAULT) {
                synchronized(faceCache) { faceCache[face] = loaded }
                loaded
            } else {
                loadNamedTypeface(AppConstants.UTHMANIC_HAFS_FILE) ?: Typeface.DEFAULT
            }
        }
    }

    suspend fun loadBasmalahTypeface(pageNumber: Int): Typeface =
        loadSurahTitleTypeface() ?: loadPageTypeface(pageNumber)

    suspend fun loadUthmanicTypeface(): Typeface? = loadNamedTypeface(AppConstants.UTHMANIC_HAFS_FILE)

    suspend fun loadSurahTitleTypeface(): Typeface? {
        loadNamedTypeface(AppConstants.QBSML_FONT)?.let { return it }
        loadNamedTypeface(AppConstants.SURAH_TITLE_FONT)?.let { return it }
        return loadNamedTypeface(AppConstants.SURAH_TITLE_FALLBACK_FONT)
    }

    /** Decorative opening-name face (`juz.ttf` / `j001`…`j030` → PUA U+E900…). */
    suspend fun loadJuzNameTypeface(): Typeface? = loadNamedTypeface(AppConstants.JUZ_NAME_FONT)

    fun peekJuzNameTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.JUZ_NAME_FONT]
    }

    /**
     * Opening-name ligature for the juz index (`juzu.json` `juz-N-name` → `jNNN`).
     * Uses the font's PUA slot so Android does not need GSUB liga.
     */
    fun juzOpeningLigature(juzNumber: Int): String? {
        val n = juzNumber.coerceIn(1, 30)
        return (0xE900 + n - 1).toChar().toString()
    }

    suspend fun preloadNeighbors(pageNumber: Int) {
        val page = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        val currentFace = faceNumberForPage(page)
        if (page > 1 && faceNumberForPage(page - 1) != currentFace) {
            loadPageTypeface(page - 1)
        }
        if (page < AppConstants.TOTAL_PAGES && faceNumberForPage(page + 1) != currentFace) {
            loadPageTypeface(page + 1)
        }
        if (peekSurahTitleTypeface() == null) {
            loadSurahTitleTypeface()
        }
    }

    fun trimPageFaces() {
        synchronized(faceCache) { faceCache.clear() }
        synchronized(paintCache) { paintCache.clear() }
    }

    fun evictPageFaces() {
        synchronized(faceCache) { faceCache.clear() }
        synchronized(namedCache) { namedCache.clear() }
        synchronized(paintCache) { paintCache.clear() }
    }

    /**
     * Per-face TTF files use the same BMP PUA as `qpc_ligature` in the React DB.
     */
    fun remapLigature(raw: String, @Suppress("UNUSED_PARAMETER") pageNumber: Int): String = raw

    fun displayText(word: WordRecord, pageNumber: Int): String {
        val ligature = remapLigature(word.qcfLigature, pageNumber)
        return ligature.ifBlank { word.uthmanic }
    }

    private suspend fun loadNamedTypeface(relativePath: String): Typeface? {
        synchronized(namedCache) { namedCache[relativePath] }?.let { return it }
        return loadMutex.withLock {
            synchronized(namedCache) { namedCache[relativePath] }?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) { loadTypeface(relativePath) }
            if (loaded != null) {
                synchronized(namedCache) { namedCache[relativePath] = loaded }
            }
            loaded
        }
    }

    private fun loadTypeface(relativePath: String): Typeface? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Typeface.Builder(context.assets, relativePath).build()
            } else {
                Typeface.createFromAsset(context.assets, relativePath)
            }
        }.getOrNull()
    }

    private fun loadGlyphMaps() {
        pageFaceNumbers = loadPageFaceNumbers()
        runCatching {
            val raw = context.assets.open(QBSML_MAP_ASSET).bufferedReader().use { it.readText() }
            val root = json.parseToJsonElement(raw).jsonObject
            decorativeSurahLigatures = intGlyphTable(root, "decorative_surahs")
            headerSingleSurahs = intGlyphTable(root, "header_single_surahs")
            headerCombinedSurahs = stringGlyphTable(root, "header_combined_surahs")
            headerJuzMap = intGlyphTable(root, "header_juz_map")
            basmalahLigatureText = root["basmallah"]?.jsonPrimitive?.content
                .orEmpty()
                .ifBlank { DEFAULT_BASMALAH_LIGATURE }
        }
    }

    private fun intGlyphTable(
        root: kotlinx.serialization.json.JsonObject,
        key: String,
    ): Map<Int, String> {
        return stringGlyphTable(root, key).mapNotNull { (id, value) ->
            id.toIntOrNull()?.let { it to value }
        }.toMap()
    }

    private fun stringGlyphTable(
        root: kotlinx.serialization.json.JsonObject,
        key: String,
    ): Map<String, String> {
        val table = root[key]?.jsonObject ?: return emptyMap()
        return table.entries.associate { (id, value) -> id to value.jsonPrimitive.content }
    }

    private fun loadPageFaceNumbers(): Map<Int, Int> {
        return runCatching {
            val raw = context.assets.open(AppConstants.QCF4_FONT_MAP_ASSET).bufferedReader().use { it.readText() }
            json.parseToJsonElement(raw).jsonObject.entries.associate { (key, value) ->
                val page = key.toInt()
                val face = FACE_NUMBER.find(value.jsonPrimitive.content)?.groupValues?.get(1)?.toIntOrNull()
                    ?: defaultFaceNumber(page)
                page to face
            }
        }.getOrDefault(emptyMap())
    }

    companion object {
        const val QBSML_MAP_ASSET = "json/qcf4-qbsml-map.json"
        const val DEFAULT_BASMALAH_LIGATURE = "\uFAD0\uFAD1\uFAD2\uFAD3"
        private val FACE_NUMBER = Regex("(\\d+)$")
        private val PUA_RANGE = 0xE000..0xF8FF

        fun defaultFaceNumber(pageNumber: Int): Int =
            ((pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES) - 1) / 13) + 1
    }
}
