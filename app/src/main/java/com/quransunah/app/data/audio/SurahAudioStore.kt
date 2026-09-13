package com.quransunah.app.data.audio

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.quransunah.app.core.AppConstants
import com.quransunah.app.core.SurahAyahCounts
import com.quransunah.app.data.catalog.MoshafEdition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class DownloadProgress(
    val done: Int,
    val total: Int,
    val currentSurah: Int? = null,
    val errorMessage: String? = null,
    val running: Boolean = false,
)

@Singleton
class SurahAudioStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun file(moshafId: Int, surah: Int): File {
        val dir = File(context.filesDir, "${AppConstants.SURAH_AUDIO_CACHE_DIR}/$moshafId")
        return File(dir, "${SurahAyahCounts.pad3(surah)}.mp3")
    }

    fun has(moshafId: Int, surah: Int): Boolean {
        val local = file(moshafId, surah)
        return local.isFile && local.length() > 0L
    }

    fun cachedCount(moshafId: Int, surahs: Collection<Int>): Int {
        return surahs.count { has(moshafId, it) }
    }

    fun playbackUri(moshaf: MoshafEdition, surah: Int): Pair<Uri, Boolean> {
        val local = file(moshaf.id, surah)
        return if (local.isFile && local.length() > 0L) {
            local.toUri() to true
        } else {
            AudioUrls.surahStreamUri(moshaf, surah) to false
        }
    }

    suspend fun download(moshaf: MoshafEdition, surah: Int): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val dest = file(moshaf.id, surah)
            if (dest.isFile && dest.length() > 0L) return@runCatching
            dest.parentFile?.mkdirs()
            val part = File(dest.parentFile, "${dest.name}.part")
            if (part.exists()) part.delete()
            val remote = AudioUrls.surahStreamUri(moshaf, surah).toString()
            val connection = (URL(remote).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 90_000
                instanceFollowRedirects = true
                requestMethod = "GET"
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("http-$code")
                connection.inputStream.use { input ->
                    part.outputStream().use { output -> input.copyTo(output) }
                }
                if (!part.renameTo(dest)) {
                    part.copyTo(dest, overwrite = true)
                    part.delete()
                }
                if (!dest.isFile || dest.length() <= 0L) error("empty-file")
            } finally {
                connection.disconnect()
            }
            _revision.value = _revision.value + 1
        }
    }
}
