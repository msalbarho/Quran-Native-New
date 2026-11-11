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
 * Medina pages use the 604 coloured QPC V4 tajweed fonts (`fonts/qcf4/pN.ttf`, COLR v0 + CPAL),
 * one font per page, with per-word v4 codes from [QpcV4Glyphs]. Night mode uses the fonts' dark
 * tajweed palette (CPAL 1), patched in place and cached as a file. Fonts come from a [FontSource]
 * (bundled today; downloadable later).
 *
 * Decorative surah/juz/basmalah ligatures use `qbsml.ttf`. WOFF2 is never packaged.
 * Faces are loaded one-at-a-time on [Dispatchers.IO] and kept in a small Typeface LRU.
 */
@Singleton
class QcfFontManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val v4Glyphs: QpcV4Glyphs,
) {
    /** Source of the page fonts (bundled in the APK for now). */
    val fontSource: FontSource = BundledFontSource(context)
    /** Night palette currently used by the reader; set by the theme (see [setNightPalette]). */
    @Volatile var nightPalette: Boolean = false
        private set
    private val nightDir: java.io.File by lazy {
        java.io.File(context.cacheDir ?: context.filesDir, "qcf4-night").apply { mkdirs() }
    }

    private val loadMutex = Mutex()
    private val pageLoadMutex = Mutex()
    private val mapsMutex = Mutex()
    /** Key = page * 2 + (1 if night palette). Access-ordered LRU. */
    private val faceCache = object : LinkedHashMap<Int, Typeface>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Typeface>?): Boolean {
            return size > AppConstants.PAGE_FONT_CACHE_SIZE
        }
    }
    private val namedCache = HashMap<String, Typeface>(4)
    private val paintCache = HashMap<Int, Paint>(8)
    private val glyphPaint = Paint()

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var mapsLoaded = false
    @Volatile private var decorativeSurahLigatures: Map<Int, String> = emptyMap()
    @Volatile private var headerSingleSurahs: Map<Int, String> = emptyMap()
    @Volatile private var headerCombinedSurahs: Map<String, String> = emptyMap()
    @Volatile private var headerJuzMap: Map<Int, String> = emptyMap()
    @Volatile private var basmalahLigatureText: String = DEFAULT_BASMALAH_LIGATURE

    /** One font per page: the "face" of a page is the page itself. */
    fun faceNumberForPage(pageNumber: Int): Int = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)

    /** Called by the theme whenever night mode changes. */
    fun setNightPalette(night: Boolean) { nightPalette = night }

    private fun cacheKey(page: Int, night: Boolean): Int = faceNumberForPage(page) * 2 + if (night) 1 else 0

    fun packIdForPage(pageNumber: Int): Int = faceNumberForPage(pageNumber)

    fun familyForPage(pageNumber: Int): String {
        val face = faceNumberForPage(pageNumber)
        return "QPC_V4_p$face"
    }

    fun assetPathForPage(pageNumber: Int): String =
        AppConstants.qcf4PageAsset(faceNumberForPage(pageNumber))

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

    /**
     * Top-bar Surah name from `header_single_surahs` in the qbsml map.
     * Only a single glyph in U+F174..U+F1D5 is returned. Frame glyphs
     * (U+F100..) and combined multi-Surah glyphs are not used.
     */
    fun headerSingleSurahLigature(surahNumber: Int): String {
        val glyph = headerSingleSurahs[surahNumber].orEmpty()
        if (glyph.codePointCount(0, glyph.length) != 1) return ""
        val cp = glyph.codePointAt(0)
        if (cp !in QBSML_HEADER_SURAH_MIN..QBSML_HEADER_SURAH_MAX) return ""
        return glyph
    }

    fun usesCombinedHeaderSurahGlyph(surahNumbers: List<Int>): Boolean =
        headerCombinedSurahs.containsKey(surahNumbers.joinToString("-"))

    fun juzHeaderLigature(juzNumber: Int): String? = headerJuzMap[juzNumber]

    fun peekPageTypeface(pageNumber: Int, night: Boolean = nightPalette): Typeface? =
        synchronized(faceCache) { faceCache[cacheKey(pageNumber, night)] }

    fun peekBasmalahTypeface(pageNumber: Int): Typeface? = peekSurahTitleTypeface()

    fun peekUthmanicTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.UTHMANIC_HAFS_FILE]
    }

    fun peekSurahTitleTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.QBSML_FONT]
            ?: namedCache[AppConstants.SURAH_TITLE_FONT]
            ?: namedCache[AppConstants.SURAH_TITLE_FALLBACK_FONT]
    }

    fun peekQbsmlTypeface(): Typeface? = synchronized(namedCache) {
        namedCache[AppConstants.QBSML_FONT]
    }

    suspend fun loadQbsmlTypeface(): Typeface? = loadNamedTypeface(AppConstants.QBSML_FONT)

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

    suspend fun loadPageTypeface(pageNumber: Int, night: Boolean = nightPalette): Typeface {
        ensureMaps()
        val face = cacheKey(pageNumber, night)
        synchronized(faceCache) { faceCache[face] }?.let { return it }
        return pageLoadMutex.withLock {
            synchronized(faceCache) { faceCache[face] }?.let { return@withLock it }
            val loaded = withContext(Dispatchers.IO) {
                loadPageFace(faceNumberForPage(pageNumber), night)
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

    /** Loads current ±[AppConstants.PAGE_FONT_PRELOAD_RADIUS] pages off the main thread (nearest first). */
    suspend fun preloadNeighbors(pageNumber: Int) {
        val page = pageNumber.coerceIn(1, AppConstants.TOTAL_PAGES)
        val radius = AppConstants.PAGE_FONT_PRELOAD_RADIUS
        fontSource.prefetch((page - 20).coerceAtLeast(1)..(page + 20).coerceAtMost(AppConstants.TOTAL_PAGES))
        for (d in 1..radius) {
            for (p in intArrayOf(page + d, page - d)) {
                if (p in 1..AppConstants.TOTAL_PAGES) runCatching { loadPageTypeface(p) }
            }
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

    /** QPC V4 glyph strings are already encoded for their page fonts; never remap them to legacy face slots. */
    fun remapLigature(raw: String, @Suppress("UNUSED_PARAMETER") pageNumber: Int): String = raw

    /** v4 page-font glyphs of a word (null if unknown). */
    fun v4Glyphs(wordId: Int): String? = v4Glyphs.forWord(wordId)

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

    /** Light = the bundled font as is (palette 0). Night = palette 1 copied to palette 0, cached as a file. */
    private fun loadPageFace(page: Int, night: Boolean): Typeface? {
        if (!night) {
            fontSource.file(page)?.let { f -> return runCatching { Typeface.createFromFile(f) }.getOrNull() }
            return fontSource.assetPath(page)?.let { loadTypeface(it) }
        }
        val file = java.io.File(nightDir, "p$page-pal1.ttf")
        if (!file.isFile || file.length() == 0L) {
            val bytes = fontSource.bytes(page) ?: return null
            ColrPalette.selectPalette(bytes, ColrPalette.DARK)
            val tmp = java.io.File(nightDir, "p$page.tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(file)) { tmp.delete(); return null }
            trimNightDir()
        }
        file.setLastModified(System.currentTimeMillis())
        return runCatching { Typeface.createFromFile(file) }.getOrNull()
    }

    /** Keeps the night-palette disk cache bounded (fonts are ~280 KB each). */
    private fun trimNightDir() {
        val files = nightDir.listFiles { f -> f.name.endsWith(".ttf") } ?: return
        if (files.size <= NIGHT_DISK_CACHE_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - NIGHT_DISK_CACHE_FILES).forEach { it.delete() }
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
        runCatching { v4Glyphs.forWord(1) } // parse the v4 glyph table off the main thread
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

    companion object {
        const val QBSML_MAP_ASSET = "json/qcf4-qbsml-map.json"
        private const val NIGHT_DISK_CACHE_FILES = 60
        const val DEFAULT_BASMALAH_LIGATURE = "\uFAD0\uFAD1\uFAD2\uFAD3"
        private val PUA_RANGE = 0xE000..0xF8FF


        const val QBSML_HEADER_SURAH_MIN = 0xF174
        const val QBSML_HEADER_SURAH_MAX = 0xF1D5
    }
}
