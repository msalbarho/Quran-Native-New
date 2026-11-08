package com.quransunah.app.share

/** Immutable editor state; positions are normalized so preview and export agree. */
data class VideoMakerOptions(
    val background: String = ShareBackgrounds.FILES.first(),
    val backgrounds: List<String> = listOf(background),
    val trackingMode: Int = 0, // 0 progress band, 1 cumulative, 2 underline, 3 current, 4 glow
    val markerColor: Int = 0xFFD4B06A.toInt(),
    val font: Int = 0,
    val highlightColor: Int = 0xFFD4B06A.toInt(),
    val glow: Boolean = false,
    val motion: Boolean = false,
    val textBackdrop: Boolean = false,
    val showTitle: Boolean = true,
    val translation: Boolean = false,
    val marker: Boolean = true,
    val textScale: Float = 1f,
    val ayahX: Float = .5f,
    val ayahY: Float = .48f,
    val titleX: Float = .5f,
    val titleY: Float = .16f,
)

data class WordSegment(val position: Int, val startMs: Long, val endMs: Long)

/** Pure clock lookup: gaps deliberately have no active word. */
fun List<WordSegment>.wordAt(positionMs: Long): Int? =
    firstOrNull { positionMs >= it.startMs && positionMs < it.endMs }?.position

/** Exact segment intervals cover all silence as well as the entire audio tail. */
data class WordVideoFrame(val startUs: Long, val durationUs: Long, val word: Int?)
fun List<WordSegment>.videoFrames(durationUs: Long): List<WordVideoFrame> {
    require(durationUs > 0)
    val points = (listOf(0L, durationUs) + flatMap { listOf(it.startMs * 1000, it.endMs * 1000) })
        .filter { it in 0..durationUs }.distinct().sorted()
    return points.zipWithNext().map { (start, end) ->
        WordVideoFrame(start, end - start, wordAt((start + (end - start) / 2) / 1000))
    }
}

/** A cursor remains on the current word until the next word onset; no blinking in alignment gaps.
 * The initial word is visible during the ayah file's leading safety margin as well.
 */
fun List<WordSegment>.cursorSegments(durationUs: Long): List<WordSegment> {
    val ordered = sortedBy { it.startMs }
    val endMs = (durationUs + 999) / 1000
    return ordered.mapIndexedNotNull { index, word ->
        val start = if (index == 0) 0L else word.startMs.coerceIn(0, endMs)
        val end = ordered.getOrNull(index + 1)?.startMs?.coerceIn(start, endMs) ?: endMs
        word.copy(startMs = start, endMs = end).takeIf { end > start }
    }
}

/** Linear 0.3-second fades at selection edges only; every internal join has unity gain. */
fun makerEdgeFadeGain(frame: Long, totalFrames: Long, sampleRate: Int = 44100): Float {
    val fadeFrames = minOf(sampleRate * 3L / 10, totalFrames / 2).coerceAtLeast(1)
    return minOf(1f, frame.toFloat() / fadeFrames, (totalFrames - 1 - frame).toFloat() / fadeFrames).coerceAtLeast(0f)
}

data class MakerBackgroundMix(val from: Int, val to: Int, val blend: Float)
fun makerBackgroundMix(positionUs: Long, durationUs: Long, count: Int): MakerBackgroundMix {
    require(count > 0 && durationUs > 0)
    val slot = durationUs.toDouble() / count
    val halfFade = minOf(200000.0, slot * .15)
    val position = positionUs.coerceIn(0, durationUs).toDouble()
    for (boundary in 1 until count) {
        val time = slot * boundary
        if (position >= time - halfFade && position <= time + halfFade) {
            return MakerBackgroundMix(boundary - 1, boundary, ((position - time + halfFade) / (2 * halfFade)).toFloat())
        }
    }
    val index = (position / slot).toInt().coerceIn(0, count - 1)
    return MakerBackgroundMix(index, index, 0f)
}

fun makerRenderFrames(segments: List<WordSegment>, durationUs: Long, smooth: Boolean): List<WordVideoFrame> {
    require(durationUs > 0)
    val cursor = segments.cursorSegments(durationUs)
    // Media3's image TimestampIterator floors duration*fps. A shorter item can have
    // zero timestamps, so never enqueue a sub-frame image. Preserve total audio time.
    val frameUs = 33334L
    val desired = if (smooth) generateSequence(0L) { it + frameUs }.takeWhile { it < durationUs }.toList() + durationUs
        else (listOf(0L,durationUs) + cursor.flatMap { listOf(it.startMs*1000,it.endMs*1000) }.filter { it in 0..durationUs }).distinct().sorted()
    val points = mutableListOf(0L)
    desired.drop(1).forEach { point -> if(point-points.last()>=frameUs || point==durationUs) points += point }
    if(points.size>2 && points.last()-points[points.lastIndex-1]<frameUs)points.removeAt(points.lastIndex-1)
    return points.zipWithNext().map { (a,b) -> WordVideoFrame(a,b-a,cursor.wordAt((a+(b-a)/2)/1000)) }
}

/** Normalized real layout bounds, shared by preview hit targets and final export. */
data class MakerPreviewGeometry(val ayahCenterY:Float=.48f,val titleCenterY:Float=.16f,
    val ayahHeight:Float=.10f,val titleHeight:Float=.06f)
fun makerBlockSeparation(ayahHeight:Float,titleHeight:Float):Float =
    (maxOf(ayahHeight,.18f)+maxOf(titleHeight,.165f))/2f+.028f
fun makerVerticalPlacement(ayahY:Float,titleY:Float,ayahHeight:Float,titleHeight:Float,
                           showTitle:Boolean,translation:Boolean):MakerPreviewGeometry {
    val minAyah=.08f+ayahHeight/2f
    val maxAyah=(if(translation).80f else .86f)-ayahHeight/2f
    var ayah=ayahY.coerceIn(minAyah,maxOf(minAyah,maxAyah))
    val minTitle=.05f+titleHeight/2f
    val gap=makerBlockSeparation(ayahHeight,titleHeight)
    if(showTitle)ayah=maxOf(ayah,minTitle+gap).coerceAtMost(maxOf(minAyah,maxAyah))
    val maxTitle=if(showTitle)ayah-gap else .85f
    val title=titleY.coerceIn(minTitle,maxOf(minTitle,maxTitle))
    return MakerPreviewGeometry(ayah,title,ayahHeight,titleHeight)
}