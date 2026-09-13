package com.quransunah.app.ui.mushaf

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.EasternArabic
import com.quransunah.app.domain.model.LineType
import com.quransunah.app.fonts.QcfFontManager
import java.util.LinkedHashMap
import kotlin.math.max

/** Surah-name ligatures sit in the same 15-line slot as ayahs. */
private const val SURAH_NAME_SLOT_SIZE = 0.62f

/**
 * Basmala QBSML size vs the ayah slot, matching React
 * `--mushaf-basmallah-glyphs-scale: 1.38` on ayah `slotHeight * 0.52`.
 * Stays inside the existing line box so the first ayah is not pushed.
 */
private const val BASMALAH_SLOT_SIZE = 0.52f

internal enum class MedinaTypefaceKind {
    Page,
    Title,
    Uthmanic,
}

internal data class PlannedCenteredLine(
    val text: String,
    val x: Float,
    val baseline: Float,
    val textSize: Float,
    val kind: MedinaTypefaceKind,
    val gold: Boolean = false,
)

internal data class PlannedWord(
    val word: WordRecord,
    val x: Float,
    val width: Float,
    val boxTop: Float,
    val boxBottom: Float,
    val baseline: Float,
    val body: String,
    val marker: String?,
    val markerX: Float,
    val lineNum: Int,
) {
    fun toLayout(): WordLayoutInfo = WordLayoutInfo(
        word = word,
        bounds = RectF(x, boxTop, x + width, boxBottom),
        lineNum = lineNum,
    )
}

internal data class PlannedSlot(
    val centered: PlannedCenteredLine?,
    val words: List<PlannedWord>,
)

internal data class MedinaPagePlan(
    val pageNumber: Int,
    val width: Int,
    val height: Int,
    val pageTextSize: Float,
    val slots: List<PlannedSlot>,
    val wordLayouts: List<WordLayoutInfo>,
)

internal object MedinaPageLayoutStore {
    private const val LIMIT = 8
    private val lock = Any()
    private val cache = object : LinkedHashMap<String, MedinaPagePlan>(LIMIT + 2, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MedinaPagePlan>?): Boolean {
            return size > LIMIT
        }
    }

    fun clear() {
        synchronized(lock) { cache.clear() }
    }

    fun get(key: String): MedinaPagePlan? = synchronized(lock) { cache[key] }

    fun put(key: String, plan: MedinaPagePlan) {
        synchronized(lock) { cache[key] = plan }
    }

    fun key(
        pageNumber: Int,
        width: Int,
        height: Int,
        faceNumber: Int,
        titleReady: Boolean,
        firstWordId: Int,
        lastWordId: Int,
        lineFingerprint: Int,
        extras: String = "",
    ): String = "$pageNumber|$width|$height|$faceNumber|$titleReady|$firstWordId|$lastWordId|$lineFingerprint|$extras"
}

internal fun buildMedinaPagePlan(
    pageNumber: Int,
    lines: List<LineRecord>,
    width: Float,
    height: Float,
    pageTypeface: Typeface,
    titleTypeface: Typeface?,
    uthmanicTypeface: Typeface?,
    fontManager: QcfFontManager?,
    inspection: Boolean,
): MedinaPagePlan {
    val slotCount = AppConstants.LINES_PER_PAGE
    val slotHeight = height / slotCount.toFloat()
    val insetX = width * AppConstants.MUSHAF_HORIZONTAL_INSET
    val lineLeft = insetX
    val lineRight = width - insetX
    val availableWidth = (lineRight - lineLeft).coerceAtLeast(1f)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isLinearText = true
        typeface = pageTypeface
    }

    val slotsSource = lines.take(slotCount)
    val baseSize = slotHeight * 0.52f
    paint.textSize = baseSize
    var widest = 0f
    val runsBySlot = Array(slotCount) { emptyArray<WordPaintRun>() }
    val measureGap = baseSize * RubElHizb.GAP_EM
    for (slotIndex in 0 until slotCount) {
        val line = slotsSource.getOrNull(slotIndex) ?: continue
        if (line.lineType != LineType.AYAH || line.words.isEmpty()) continue
        val runs = Array(line.words.size) { index ->
            val word = line.words[index]
            val previous = line.words.getOrNull(index - 1)
                ?: slotsSource.getOrNull(slotIndex - 1)?.words?.lastOrNull()
            val next = line.words.getOrNull(index + 1)
                ?: slotsSource.getOrNull(slotIndex + 1)?.words?.firstOrNull()
            val display = fontManager?.displayText(word, pageNumber).orEmpty().ifBlank { word.uthmanic }
            SajdahAyah.paintRun(word, display, previous, next)
        }
        runsBySlot[slotIndex] = runs
        val widthSum = runs.fold(0f) { acc, run -> acc + run.measure(paint, measureGap) }
        if (widthSum > widest) widest = widthSum
    }
    val pageTextSize = if (widest > availableWidth && widest > 0f) {
        baseSize * (availableWidth / widest)
    } else {
        baseSize
    }

    val plannedSlots = ArrayList<PlannedSlot>(slotCount)
    val layouts = ArrayList<WordLayoutInfo>(64)
    for (slotIndex in 0 until slotCount) {
        val line = slotsSource.getOrNull(slotIndex)
        val slotTop = slotIndex * slotHeight
        val slotBottom = slotTop + slotHeight
        if (line == null) {
            plannedSlots += PlannedSlot(centered = null, words = emptyList())
            continue
        }
        when (line.lineType) {
            LineType.EMPTY -> plannedSlots += PlannedSlot(centered = null, words = emptyList())
            LineType.SURAH_NAME -> {
                val fallback = line.surahNumber?.let { "سورة ${EasternArabic.format(it)}" }.orEmpty()
                val ligature = line.surahNumber?.let { fontManager?.surahNameLigature(it) }.orEmpty()
                val useQbsml = !inspection && fontManager?.canDrawLigature(ligature, titleTypeface) == true
                val text = if (inspection) {
                    fallback
                } else {
                    fontManager?.displayLigature(ligature, fallback, titleTypeface) ?: fallback
                }
                val kind = when {
                    useQbsml -> MedinaTypefaceKind.Title
                    uthmanicTypeface != null -> MedinaTypefaceKind.Uthmanic
                    titleTypeface != null -> MedinaTypefaceKind.Title
                    else -> MedinaTypefaceKind.Page
                }
                val typeface = typefaceFor(kind, pageTypeface, titleTypeface, uthmanicTypeface)
                plannedSlots += PlannedSlot(
                    centered = if (text.isEmpty()) {
                        null
                    } else {
                        planCenteredLine(
                            paint = paint,
                            typeface = typeface,
                            kind = kind,
                            text = text,
                            left = lineLeft,
                            availableWidth = availableWidth,
                            slotTop = slotTop,
                            slotBottom = slotBottom,
                            gold = false,
                        )
                    },
                    words = emptyList(),
                )
            }
            LineType.BASMALLAH -> {
                val ligature = fontManager?.displayBasmalahLigature()
                    ?: QcfFontManager.DEFAULT_BASMALAH_LIGATURE
                val qbsmlFace = titleTypeface
                if (!inspection && qbsmlFace != null && ligature.isNotEmpty()) {
                    plannedSlots += PlannedSlot(
                        centered = planCenteredLine(
                            paint = paint,
                            typeface = qbsmlFace,
                            kind = MedinaTypefaceKind.Title,
                            text = ligature,
                            left = lineLeft,
                            availableWidth = availableWidth,
                            slotTop = slotTop,
                            slotBottom = slotBottom,
                            gold = true,
                            sizeFactor = BASMALAH_SLOT_SIZE,
                        ),
                        words = emptyList(),
                    )
                } else {
                    val runs = Array(line.words.size) { index ->
                        val word = line.words[index]
                        wordPaintRun(
                            word,
                            fontManager?.displayText(word, pageNumber).orEmpty().ifBlank { word.uthmanic },
                        )
                    }
                    val words = planWordLine(
                        paint = paint,
                        line = line,
                        runs = runs,
                        right = lineRight,
                        slotTop = slotTop,
                        slotBottom = slotBottom,
                        availableWidth = availableWidth,
                        textSize = pageTextSize,
                        justify = false,
                    )
                    layouts += words.map { it.toLayout() }
                    plannedSlots += PlannedSlot(centered = null, words = words)
                }
            }
            LineType.AYAH -> {
                val words = planWordLine(
                    paint = paint,
                    line = line,
                    runs = runsBySlot[slotIndex],
                    right = lineRight,
                    slotTop = slotTop,
                    slotBottom = slotBottom,
                    availableWidth = availableWidth,
                    textSize = pageTextSize,
                    justify = !line.centered && line.words.size > 1,
                )
                layouts += words.map { it.toLayout() }
                plannedSlots += PlannedSlot(centered = null, words = words)
            }
        }
    }
    return MedinaPagePlan(
        pageNumber = pageNumber,
        width = width.toInt(),
        height = height.toInt(),
        pageTextSize = pageTextSize,
        slots = plannedSlots,
        wordLayouts = layouts,
    )
}

private fun typefaceFor(
    kind: MedinaTypefaceKind,
    page: Typeface,
    title: Typeface?,
    uthmanic: Typeface?,
): Typeface = when (kind) {
    MedinaTypefaceKind.Page -> page
    MedinaTypefaceKind.Title -> title ?: page
    MedinaTypefaceKind.Uthmanic -> uthmanic ?: title ?: page
}

private fun planCenteredLine(
    paint: Paint,
    typeface: Typeface,
    kind: MedinaTypefaceKind,
    text: String,
    left: Float,
    availableWidth: Float,
    slotTop: Float,
    slotBottom: Float,
    gold: Boolean = false,
    sizeFactor: Float = SURAH_NAME_SLOT_SIZE,
): PlannedCenteredLine {
    val previous = paint.typeface
    paint.typeface = typeface
    val slotHeight = slotBottom - slotTop
    paint.textSize = slotHeight * sizeFactor
    var width = paint.measureText(text)
    if (width > availableWidth && width > 0f) {
        paint.textSize = paint.textSize * (availableWidth / width)
        width = paint.measureText(text)
    }
    val baseline = baselineForSlot(paint, slotTop, slotBottom)
    val x = left + (availableWidth - width) / 2f
    val textSize = paint.textSize
    paint.typeface = previous
    return PlannedCenteredLine(
        text = text,
        x = x,
        baseline = baseline,
        textSize = textSize,
        kind = kind,
        gold = gold,
    )
}

private fun planWordLine(
    paint: Paint,
    line: LineRecord,
    runs: Array<WordPaintRun>,
    right: Float,
    slotTop: Float,
    slotBottom: Float,
    availableWidth: Float,
    textSize: Float,
    justify: Boolean,
): List<PlannedWord> {
    val words = line.words
    if (words.isEmpty() || runs.isEmpty()) return emptyList()
    paint.textSize = textSize
    val markerGap = textSize * RubElHizb.GAP_EM
    val widths = FloatArray(runs.size) { index -> runs[index].measure(paint, markerGap) }
    val totalWidth = widths.sum()
    val leftover = max(0f, availableWidth - totalWidth)
    val gap = if (justify && words.size > 1) leftover / (words.size - 1).toFloat() else 0f
    val occupied = totalWidth + gap * max(0, words.size - 1)
    val rightEdge = if (line.centered) {
        right - (availableWidth - occupied) / 2f
    } else {
        right
    }
    val slotHeight = slotBottom - slotTop
    val baseline = baselineForSlot(paint, slotTop, slotBottom)
    val fm = paint.fontMetrics
    val glyphTop = baseline + fm.ascent
    val glyphBottom = baseline + fm.descent
    val hitTop = slotTop + slotHeight * 0.08f
    val hitBottom = slotBottom - slotHeight * 0.04f
    val boxTop = minOf(hitTop, glyphTop)
    val boxBottom = maxOf(hitBottom, glyphBottom)
    val planned = ArrayList<PlannedWord>(words.size)
    var cursor = rightEdge
    val count = minOf(words.size, runs.size)
    for (index in 0 until count) {
        val word = words[index]
        val run = runs[index]
        val glyphWidth = widths[index]
        cursor -= glyphWidth
        val x = cursor
        val bodyWidth = if (run.body.isEmpty()) 0f else paint.measureText(run.body)
        val innerGap = if (!run.marker.isNullOrEmpty() && bodyWidth > 0f) markerGap else 0f
        planned += PlannedWord(
            word = word,
            x = x,
            width = glyphWidth,
            boxTop = boxTop,
            boxBottom = boxBottom,
            baseline = baseline,
            body = run.body,
            marker = run.marker,
            markerX = x + bodyWidth + innerGap,
            lineNum = line.lineNum,
        )
        cursor -= gap
    }
    return planned
}

internal data class WordPaintRun(
    val body: String,
    val marker: String? = null,
)

internal fun wordPaintRun(word: WordRecord, display: String): WordPaintRun {
    val parts = word.rubParts(display)
    return if (parts != null) {
        WordPaintRun(body = parts.bodyLigature, marker = parts.markerLigature)
    } else {
        WordPaintRun(body = display)
    }
}

internal fun WordPaintRun.measure(paint: Paint, markerGap: Float): Float {
    val bodyWidth = if (body.isEmpty()) 0f else paint.measureText(body)
    val markerWidth = marker?.let { if (it.isEmpty()) 0f else paint.measureText(it) } ?: 0f
    val gap = if (markerWidth > 0f && bodyWidth > 0f) markerGap else 0f
    return bodyWidth + markerWidth + gap
}

internal fun baselineForSlot(paint: Paint, slotTop: Float, slotBottom: Float): Float {
    val fm = paint.fontMetrics
    val slotMid = (slotTop + slotBottom) / 2f
    return slotMid - (fm.ascent + fm.descent) / 2f
}
