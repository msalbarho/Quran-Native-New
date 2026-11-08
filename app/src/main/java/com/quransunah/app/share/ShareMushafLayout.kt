package com.quransunah.app.share

/**
 * Cuts selected ayat out of Madinah page lines: keep original line breaks,
 * drop words that belong to ayat outside the range. Partial first/last lines
 * stay as they were on the page (RTL: remaining glyphs sit on the right).
 */
object ShareMushafLayout {
    const val FONT_SIZE_MIN = 22f
    const val FONT_SIZE_MAX = 29f
    const val TITLE_SIZE_FACTOR = 0.60f
    /**
     * Baseline distance as a fraction of Arabic text size. Mushaf page slots
     * sit tight; extra leading (1.4+) opens a visible gutter between wrapped lines.
     */
    const val ARABIC_LINE_MULTIPLIER = 1.08f
    const val TRANSLATION_GAP_WIDTH = 0.028f
    const val TRACKING_BAND = 0
    const val TRACKING_CUMULATIVE = 1
    const val TRACKING_UNDERLINE = 2

    val HIGHLIGHT_COLOURS = listOf(
        "ذهبي" to 0xFFD4B06A.toInt(),
        "نعناعي" to 0xFF66D9B8.toInt(),
        "سماوي" to 0xFF91CAFF.toInt(),
        "برتقالي" to 0xFFF49C65.toInt(),
        "بنفسجي" to 0xFFC6A5ED.toInt(),
        "أخضر" to 0xFF8CCB7A.toInt(),
        "أحمر" to 0xFFE17872.toInt(),
    )

    val MARKER_COLOURS = listOf(
        "ذهبي" to 0xFFD4B06A.toInt(),
        "أبيض" to 0xFFFFFFFF.toInt(),
        "أخضر" to 0xFF4CAF50.toInt(),
    )

    fun fontSizeToScale(size: Float): Float =
        (size / FONT_SIZE_MIN).coerceIn(1f, FONT_SIZE_MAX / FONT_SIZE_MIN)

    fun scaleToFontSize(scale: Float): Float =
        (scale * FONT_SIZE_MIN).coerceIn(FONT_SIZE_MIN, FONT_SIZE_MAX)

    /** One ayah keeps the current size; more ayat only shrink, never enlarge. */
    fun fitScale(ayahCount: Int, lineCount: Int = ayahCount): Float {
        val count = maxOf(ayahCount, lineCount, 1)
        if (count <= 1) return 1f
        return (1f / kotlin.math.sqrt(count.toFloat())).coerceIn(0.42f, 1f)
    }

    fun arabicLineHeight(textSize: Float): Float = textSize * ARABIC_LINE_MULTIPLIER

    fun heightFitScale(lineCount: Int, lineHeight: Float, availableHeight: Float): Float {
        if (lineCount <= 0 || lineHeight <= 0f || availableHeight <= 0f) return 1f
        val needed = lineCount * lineHeight
        if (needed <= availableHeight) return 1f
        return (availableHeight / needed).coerceAtLeast(0.35f)
    }

    fun cutLines(
        pages: List<SharePageLines>,
        fromAyah: Int,
        toAyah: Int,
    ): List<ShareMushafLine> {
        val range = fromAyah..toAyah
        return pages.flatMap { page ->
            page.lines.mapNotNull { line ->
                val kept = line.words.filter { it.ayah in range }
                if (kept.isEmpty()) null
                else ShareMushafLine(
                    pageNumber = page.pageNumber,
                    lineNumber = line.lineNumber,
                    words = kept,
                )
            }
        }
    }

    fun bodyWords(lines: List<ShareMushafLine>): List<ShareMushafWord> =
        lines.flatMap { line -> line.words.filter { !it.isMarker } }

    fun globalWordIndex(lines: List<ShareMushafLine>, ayah: Int, position: Int): Int {
        val words = bodyWords(lines)
        val index = words.indexOfFirst { it.ayah == ayah && it.position == position }
        return if (index >= 0) index else words.indexOfLast { it.ayah < ayah }.let { if (it < 0) 0 else it }
    }

    /**
     * RTL cumulative underline: from the first recited word on a line through
     * every word read so far. [activeIndex] is 0-based in [bodyWords] order;
     * [progress] 0..1 extends the current word from right to left.
     */
    fun cumulativeUnderlines(
        boxes: List<ShareWordBox>,
        activeIndex: Int,
        progress: Float,
    ): List<ShareUnderline> {
        if (boxes.isEmpty() || activeIndex < 0) return emptyList()
        val fraction = progress.coerceIn(0f, 1f)
        return boxes.groupBy { it.line }.mapNotNull { (line, lineBoxes) ->
            val recited = lineBoxes.mapNotNull { box ->
                when {
                    box.index < activeIndex -> box to 1f
                    box.index == activeIndex -> box to fraction
                    else -> null
                }
            }
            if (recited.isEmpty()) return@mapNotNull null
            val right = recited.maxOf { it.first.right }
            val last = recited.maxBy { it.first.index }
            val left = last.first.right - last.first.width * last.second
            if (right - left < 0.5f) return@mapNotNull null
            ShareUnderline(
                line = line,
                left = minOf(left, right),
                right = maxOf(left, right),
                y = last.first.baseline + last.first.descent,
            )
        }
    }

    fun kenBurnsScale(elapsedUs: Long, totalUs: Long): Float {
        val t = if (totalUs <= 0L) 0f else (elapsedUs.toDouble() / totalUs).toFloat().coerceIn(0f, 1f)
        return 1f + 0.085f * t
    }
}

data class SharePageLines(
    val pageNumber: Int,
    val lines: List<ShareSourceLine>,
)

data class ShareSourceLine(
    val lineNumber: Int,
    val words: List<ShareMushafWord>,
)

data class ShareMushafLine(
    val pageNumber: Int,
    val lineNumber: Int,
    val words: List<ShareMushafWord>,
)

data class ShareMushafWord(
    val ayah: Int,
    val position: Int,
    val qcf: String,
    val uthmanic: String,
    val isMarker: Boolean = false,
)

data class ShareWordBox(
    val index: Int,
    val ayah: Int,
    val position: Int,
    val line: Int,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
    val baseline: Float,
    val descent: Float,
) {
    val width: Float get() = right - left
}

data class ShareUnderline(
    val line: Int,
    val left: Float,
    val right: Float,
    val y: Float,
)

data class QulAyahTiming(
    val segments: List<WordSegment> = emptyList(),
    val audioUrl: String? = null,
    val surahAudioUrl: String? = null,
    val cropStartMs: Long? = null,
    val cropEndMs: Long? = null,
) {
    val hasWordTracking: Boolean get() = segments.isNotEmpty()
}
