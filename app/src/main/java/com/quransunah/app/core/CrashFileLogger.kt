package com.quransunah.app.core

import android.content.Context
import com.quransunah.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last fatal crash to app files for sideload diagnosis
 * (`files/last_crash.txt`) without depending on logcat.
 */
object CrashFileLogger {
    private const val TAG = "CrashFileLogger"
    private const val FILE_NAME = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        return runCatching { file.takeIf { it.isFile }?.readText() }.getOrNull()
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US).format(Date())
        val stack = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val body = buildString {
            appendLine("time=$stamp")
            appendLine("thread=${thread.name}")
            appendLine("message=${error.message}")
            appendLine()
            append(stack)
        }
        File(context.filesDir, FILE_NAME).writeText(body)
        if (BuildConfig.DEBUG) {
            android.util.Log.e(TAG, "Fatal crash saved to $FILE_NAME", error)
        }
    }
}
