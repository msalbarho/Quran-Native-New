package com.quransunah.app.share

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** QUL Alafasy ayah clock. Separate cache; never writes playback/session state. */
class QulWordTimings(private val context: Context) {
    suspend fun load(surah: Int, ayah: Int, recitationId: Int = 18): List<WordSegment> = withContext(Dispatchers.IO) {
        val page = (ayah - 1) / 10 + 1
        val dir = File(context.cacheDir, "qul-word-timings").apply { mkdirs() }
        val file = File(dir, "recitation-$recitationId-$surah-$page.json")
        val text = if (file.exists()) file.readText() else {
            val connection = URL("https://qul.tarteel.ai/api/v1/audio/ayah_segments/$recitationId?surah=$surah&per_page=10&page=$page").openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            try {
                check(connection.responseCode == 200) { "QUL timings unavailable" }
                connection.inputStream.bufferedReader().use { it.readText() }.also {
                    JSONObject(it).getJSONObject("segments") // validate before caching
                    file.writeText(it)
                }
            } finally { connection.disconnect() }
        }
        val rows = JSONObject(text).getJSONObject("segments").optJSONObject("$surah:$ayah")?.optJSONArray("segments")
            ?: return@withContext emptyList()
        (0 until rows.length()).mapNotNull { i ->
            val row = rows.getJSONArray(i)
            // QUL's live endpoint uses [zero-based index, 1-based position, start, end].
            val offset = if (row.length() == 4) 1 else 0
            val segment = WordSegment(row.getInt(offset), row.getLong(offset + 1), row.getLong(offset + 2))
            segment.takeIf { it.position > 0 && it.endMs > it.startMs && it.startMs >= 0 }
        }.sortedBy { it.startMs }
    }
}
