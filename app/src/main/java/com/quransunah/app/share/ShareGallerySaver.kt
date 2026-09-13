package com.quransunah.app.share

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream

/**
 * Saves share exports into the public gallery album "Holy Quran"
 * via [MediaStore] (scoped storage on Android 10+; no broad storage grant required).
 */
internal object ShareGallerySaver {
    private const val ALBUM = "Holy Quran"

    fun saveImage(context: Context, source: File, displayName: String): Uri? {
        return save(
            context = context,
            source = source,
            displayName = ensureExtension(displayName, ".jpg"),
            mimeType = "image/jpeg",
            relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM",
            collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            },
        )
    }

    fun saveVideo(context: Context, source: File, displayName: String): Uri? {
        return save(
            context = context,
            source = source,
            displayName = ensureExtension(displayName, ".mp4"),
            mimeType = "video/mp4",
            relativePath = "${Environment.DIRECTORY_MOVIES}/$ALBUM",
            collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            },
        )
    }

    private fun save(
        context: Context,
        source: File,
        displayName: String,
        mimeType: String,
        relativePath: String,
        collection: Uri,
    ): Uri? {
        if (!source.exists() || source.length() < 32L) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                put(MediaStore.MediaColumns.DATA, legacyAbsolutePath(relativePath, displayName))
            }
        }
        val uri = runCatching { resolver.insert(collection, values) }.getOrNull() ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { output ->
                FileInputStream(source).use { input -> input.copyTo(output) }
                output.flush()
            } ?: run {
                runCatching { resolver.delete(uri, null, null) }
                return null
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun legacyAbsolutePath(relativePath: String, displayName: String): String {
        val root = Environment.getExternalStorageDirectory()
        val dir = File(root, relativePath)
        if (!dir.exists()) dir.mkdirs()
        return File(dir, displayName).absolutePath
    }

    private fun ensureExtension(name: String, extension: String): String {
        return if (name.endsWith(extension, ignoreCase = true)) name else name + extension
    }
}
