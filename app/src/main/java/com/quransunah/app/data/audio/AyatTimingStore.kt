package com.quransunah.app.data.audio

import android.content.Context
import com.quransunah.app.core.AppConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class VerseTiming(
    val ayah: Int,
    val startMs: Long,
    val endMs: Long,
)

data class SurahVerseTimings(
    val moshafId: Int,
    val surah: Int,
    val verses: List<VerseTiming>,
) {
    fun ayahAt(positionMs: Long): Int? {
        if (verses.isEmpty()) return null
        if (positionMs < verses.first().startMs) return null
        var low = 0
        var high = verses.lastIndex
        var found = 0
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (verses[mid].startMs <= positionMs) {
                found = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return verses[found].ayah
    }

    fun startOf(ayah: Int): Long? = verses.firstOrNull { it.ayah == ayah }?.startMs
}

@Serializable
private data class AyatTimingJson(
    val ayah: Int,
    @SerialName("start_time") val startTime: Long = 0L,
    @SerialName("end_time") val endTime: Long = 0L,
)

/**
 * Verse timestamps for full-surah MP3s. Source is mp3quran `ayat_timing`
 * keyed by moshaf id (`read`) and surah — the same ids used in [reciters.json].
 */
@Singleton
class AyatTimingStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, SurahVerseTimings>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    fun peek(moshafId: Int, surah: Int): SurahVerseTimings? = memory[key(moshafId, surah)]

    fun trim(keep: Int = 2) {
        if (memory.size <= keep) return
        val extra = memory.size - keep.coerceAtLeast(0)
        val keys = memory.keys.take(extra)
        for (item in keys) {
            memory.remove(item)
            locks.remove(item)
        }
    }

    suspend fun load(moshafId: Int, surah: Int): SurahVerseTimings? {
        if (moshafId <= 0 || surah !in 1..AppConstants.SURAH_COUNT) return null
        val cacheKey = key(moshafId, surah)
        memory[cacheKey]?.let { return it }
        val mutex = locks.getOrPut(cacheKey) { Mutex() }
        return mutex.withLock {
            memory[cacheKey]?.let { return@withLock it }
            withContext(Dispatchers.IO) {
                readDisk(moshafId, surah)?.also { remember(cacheKey, it) }
                    ?: fetchRemote(moshafId, surah)?.also { remember(cacheKey, it) }
            }
        }
    }

    private fun remember(cacheKey: String, timings: SurahVerseTimings) {
        memory[cacheKey] = timings
        if (memory.size > MEMORY_CAP) trim(keep = MEMORY_KEEP)
    }

    private fun key(moshafId: Int, surah: Int): String = "$moshafId:$surah"

    private fun cacheFile(moshafId: Int, surah: Int): File {
        val dir = File(context.filesDir, "${AppConstants.AYAT_TIMING_CACHE_DIR}/$moshafId")
        return File(dir, "$surah.json")
    }

    private fun readDisk(moshafId: Int, surah: Int): SurahVerseTimings? {
        val file = cacheFile(moshafId, surah)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            parse(moshafId, surah, file.readText())
        }.getOrNull()
    }

    private fun writeRaw(moshafId: Int, surah: Int, body: String) {
        runCatching {
            val file = cacheFile(moshafId, surah)
            file.parentFile?.mkdirs()
            file.writeText(body)
        }
    }

    private fun fetchRemote(moshafId: Int, surah: Int): SurahVerseTimings? {
        val url = "${AppConstants.AYAT_TIMING_URL}?surah=$surah&read=$moshafId"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 12_000
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(moshafId, surah, body)?.also { writeRaw(moshafId, surah, body) }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(moshafId: Int, surah: Int, body: String): SurahVerseTimings? {
        val parsed = runCatching {
            json.decodeFromString<List<AyatTimingJson>>(body)
        }.getOrNull() ?: return null
        val verses = parsed
            .filter { it.ayah > 0 && it.endTime >= it.startTime }
            .map { VerseTiming(ayah = it.ayah, startMs = it.startTime, endMs = it.endTime) }
            .sortedBy { it.startMs }
        if (verses.isEmpty()) return null
        return SurahVerseTimings(moshafId = moshafId, surah = surah, verses = verses)
    }

    private companion object {
        const val MEMORY_CAP = 16
        const val MEMORY_KEEP = 8
    }
}
