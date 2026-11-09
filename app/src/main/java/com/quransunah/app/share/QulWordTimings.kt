package com.quransunah.app.share

import android.content.Context
import com.quransunah.app.core.SurahAyahCounts
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** QUL / Quran.com / KFGQPC word clocks. Separate cache; never writes playback/session state. */
class QulWordTimings(private val context: Context) {
    suspend fun load(surah: Int, ayah: Int, recitationId: Int = 18): List<WordSegment> =
        loadAyah(surah, ayah, recitationId).segments

    suspend fun loadForReciter(reciterId: String, surah: Int, ayah: Int): QulAyahTiming {
        val spec = QulRecitations.spec(reciterId) ?: return QulAyahTiming()
        return runCatching { loadAyah(surah, ayah, spec.recitationId, spec) }
            .getOrDefault(QulAyahTiming())
    }

    suspend fun loadAyah(
        surah: Int,
        ayah: Int,
        recitationId: Int = 18,
        spec: QulRecitations.Spec? = QulRecitations.spec(
            QulRecitations.reciterId(recitationId).orEmpty(),
        ),
    ): QulAyahTiming = withContext(Dispatchers.IO) {
        val resolved = spec ?: QulRecitations.spec(QulRecitations.reciterId(recitationId).orEmpty())
        if (resolved?.isKfc == true) {
            return@withContext loadKfc(surah, ayah, resolved)
        }
        if (resolved?.isSurahLevel == true) {
            return@withContext loadSurahLevel(surah, ayah, resolved)
        }
        val page = (ayah - 1) / 10 + 1
        val dir = File(context.cacheDir, "qul-word-timings").apply { mkdirs() }
        val file = File(dir, "recitation-$recitationId-$surah-$page.json")
        val text = if (file.exists()) file.readText() else {
            val connection = URL(
                "https://qul.tarteel.ai/api/v1/audio/ayah_segments/$recitationId?surah=$surah&per_page=10&page=$page",
            ).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            try {
                if (connection.responseCode != 200) return@withContext QulAyahTiming()
                connection.inputStream.bufferedReader().use { it.readText() }.also { body ->
                    JSONObject(body).optJSONObject("segments") ?: return@withContext QulAyahTiming()
                    file.writeText(body)
                }
            } finally {
                connection.disconnect()
            }
        }
        val row = JSONObject(text).optJSONObject("segments")?.optJSONObject("$surah:$ayah")
            ?: return@withContext QulAyahTiming()
        val audioUrl = row.optString("audio_url").takeIf { it.isNotBlank() }
            ?: QulRecitations.ayahAudioUrl(QulRecitations.reciterId(recitationId).orEmpty(), surah, ayah)
        QulAyahTiming(
            segments = parseQulWordSegments(row.optJSONArray("segments")),
            audioUrl = audioUrl,
        )
    }

    private fun loadKfc(surah: Int, ayah: Int, spec: QulRecitations.Spec): QulAyahTiming {
        val assetDir = spec.kfcTimingAssetDir ?: return QulAyahTiming()
        val name = "${SurahAyahCounts.pad3(surah)}.json"
        val cacheDir = File(context.cacheDir, "kfc-word-timings").apply { mkdirs() }
        val cache = File(cacheDir, name)
        val text = when {
            cache.exists() && cache.length() > 64 -> cache.readText()
            else -> {
                val fallbackUrl = spec.kfcSurahServer?.let {
                    "${it.trimEnd('/')}/${SurahAyahCounts.pad3(surah)}.mp3"
                }
                val body = runCatching {
                    context.assets.open("$assetDir/$name").bufferedReader().use { it.readText() }
                }.getOrNull() ?: return QulAyahTiming(surahAudioUrl = fallbackUrl)
                cache.writeText(body)
                body
            }
        }
        val root = JSONObject(text)
        val surahUrl = root.optString("audio_url").takeIf { it.isNotBlank() }
            ?: spec.kfcSurahServer?.let { "${it.trimEnd('/')}/${SurahAyahCounts.pad3(surah)}.mp3" }
        val ayahs = root.optJSONObject("ayahs") ?: return QulAyahTiming(surahAudioUrl = surahUrl)
        val row = ayahs.optJSONObject("$ayah") ?: return QulAyahTiming(surahAudioUrl = surahUrl)
        val from = row.optLong("crop_start_ms")
        val to = row.optLong("crop_end_ms")
        val segments = parseQulWordSegments(row.optJSONArray("segments"))
        return QulAyahTiming(
            segments = segments,
            surahAudioUrl = surahUrl,
            cropStartMs = from.takeIf { it >= 0L },
            cropEndMs = to.takeIf { it > from },
        )
    }

    private fun loadSurahLevel(surah: Int, ayah: Int, spec: QulRecitations.Spec): QulAyahTiming {
        val reciterId = spec.qurancdnReciterId ?: return QulAyahTiming()
        val dir = File(context.cacheDir, "qul-word-timings").apply { mkdirs() }
        val file = File(dir, "qurancdn-$reciterId-$surah.json")
        val text = if (file.exists()) file.readText() else {
            val connection = URL(QulRecitations.qurancdnChapterUrl(reciterId, surah)).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            try {
                if (connection.responseCode != 200) return QulAyahTiming()
                connection.inputStream.bufferedReader().use { it.readText() }.also { body ->
                    JSONObject(body).optJSONArray("audio_files") ?: return QulAyahTiming()
                    file.writeText(body)
                }
            } finally {
                connection.disconnect()
            }
        }
        val audio = JSONObject(text).optJSONArray("audio_files")?.optJSONObject(0)
            ?: return QulAyahTiming()
        val surahUrl = audio.optString("audio_url").takeIf { it.isNotBlank() }
        val verses = audio.optJSONArray("verse_timings") ?: return QulAyahTiming(surahAudioUrl = surahUrl)
        val verse = (0 until verses.length()).map { verses.getJSONObject(it) }
            .firstOrNull { it.optString("verse_key") == "$surah:$ayah" }
            ?: return QulAyahTiming(surahAudioUrl = surahUrl)
        val from = verse.optLong("timestamp_from")
        val to = verse.optLong("timestamp_to")
        val raw = parseQulWordSegments(verse.optJSONArray("segments"))
        val shifted = raw.mapNotNull { word ->
            val start = (word.startMs - from).coerceAtLeast(0L)
            val end = (word.endMs - from).coerceAtLeast(start + 1)
            word.copy(startMs = start, endMs = end).takeIf { end > start }
        }
        return QulAyahTiming(
            segments = shifted,
            surahAudioUrl = surahUrl,
            cropStartMs = from.takeIf { it >= 0L },
            cropEndMs = to.takeIf { it > from },
        )
    }

}

/** QUL rows are `[word, startMs, endMs]` or `[index, word, startMs, endMs]`. The leading index is not a word. */
internal fun parseQulWordSegments(rows: JSONArray?): List<WordSegment> {
    if (rows == null) return emptyList()
    return (0 until rows.length()).mapNotNull { i ->
        val row = rows.optJSONArray(i) ?: return@mapNotNull null
        wordSegmentFromQulRow((0 until row.length()).map { row.optLong(it) })
    }.sortedBy { it.startMs }
}

internal fun wordSegmentFromQulRow(values: List<Long>): WordSegment? {
    if (values.size < 3) return null
    val offset = if (values.size == 4) 1 else 0
    val position = values[offset].toInt()
    val start = values[offset + 1]
    val end = values[offset + 2]
    return WordSegment(position, start, end).takeIf { it.position > 0 && it.endMs > it.startMs && it.startMs >= 0 }
}
