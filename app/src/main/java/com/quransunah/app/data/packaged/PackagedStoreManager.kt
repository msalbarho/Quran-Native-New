package com.quransunah.app.data.packaged

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class PackagedStoreManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()

    /**
     * Cheap main-thread-safe check: delete a stale packaged DB so Room's
     * `createFromAsset` can copy it on the first background query. Never copies
     * bytes here — that used to freeze cold start via `runBlocking`.
     */
    fun invalidateStaleSync(assetPath: String, destName: String, version: String) {
        val dest = destFile(destName)
        dest.parentFile?.mkdirs()
        if (isCurrent(dest, destName, assetPath, version)) return
        removeInstalled(dest, destName)
        if (dest.exists()) {
            error("Could not replace stale database $destName")
        }
    }

    fun markCurrent(assetPath: String, destName: String, version: String) {
        val dest = destFile(destName)
        dest.parentFile?.mkdirs()
        stampFile(destName).writeText(stampValue(version, assetPath))
    }

    suspend fun ensureCopied(assetPath: String, destName: String, version: String): File {
        mutex.withLock {
            return withContext(Dispatchers.IO) {
                invalidateIfStale(assetPath, destName, version)
            }
        }
    }

    fun destFile(destName: String): File = context.getDatabasePath(destName)

    private fun stampFile(destName: String): File = File(destFile(destName).parentFile, "$destName.version")

    private fun stampValue(version: String, assetPath: String): String = "$version:$assetPath"

    private fun invalidateIfStale(assetPath: String, destName: String, version: String): File {
        val dest = destFile(destName)
        dest.parentFile?.mkdirs()
        if (isCurrent(dest, destName, assetPath, version)) return dest
        removeInstalled(dest, destName)
        if (dest.exists()) error("Could not replace stale database $destName")
        context.assets.open(assetPath).use { input ->
            dest.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        stampFile(destName).writeText(stampValue(version, assetPath))
        return dest
    }

    private fun isCurrent(dest: File, destName: String, assetPath: String, version: String): Boolean {
        if (!dest.exists() || dest.length() <= 0L) return false
        val stamp = stampFile(destName)
        return stamp.exists() && stamp.readText() == stampValue(version, assetPath)
    }

    private fun removeInstalled(dest: File, destName: String) {
        File("${dest.path}-wal").delete()
        File("${dest.path}-shm").delete()
        File("${dest.path}-journal").delete()
        dest.delete()
        stampFile(destName).delete()
    }
}
