package com.quransunah.app.fonts

import android.content.Context
import com.quransunah.app.core.AppConstants
import java.io.File

/**
 * Where the 604 coloured page fonts come from. Today: bundled in the APK ([BundledFontSource]).
 * A later downloadable source can implement the same contract (return null until the page is downloaded,
 * and use [prefetch] to fetch the next ~20 pages in the background) without touching the renderer.
 */
interface FontSource {
    /** Asset path inside the APK if the page font is bundled, else null. */
    fun assetPath(page: Int): String?

    /** File on disk if the page font is available as a file (downloaded fonts), else null. */
    fun file(page: Int): File? = null

    /** Raw font bytes (for palette patching), or null if not available yet. */
    fun bytes(page: Int): ByteArray?

    fun isAvailable(page: Int): Boolean = assetPath(page) != null || file(page) != null

    /** Hint that these pages will be needed soon (no-op for bundled fonts). */
    fun prefetch(pages: IntRange) {}
}

class BundledFontSource(private val context: Context) : FontSource {
    override fun assetPath(page: Int): String = AppConstants.qcf4PageAsset(page)

    override fun bytes(page: Int): ByteArray? =
        runCatching { context.assets.open(assetPath(page)).use { it.readBytes() } }.getOrNull()
}
