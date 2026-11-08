package com.quransunah.app.fonts

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The QPC V4 tajweed page fonts are COLR v0 (supported by Android's text stack on every API level we target,
 * minSdk 24) with 6 CPAL palettes: 0 light tajweed, 1 dark tajweed, 2 sepia, 3/4/5 plain variants.
 * Android always renders palette 0, so the night variant is produced by copying palette 1 into a single-palette
 * CPAL in place (same approach as Quran Trainer's PageFontManager).
 */
object ColrPalette {
    const val LIGHT = 0
    const val DARK = 1

    /** Rewrites CPAL so that palette 0 holds the colours of [paletteIndex]. Returns false if not applicable. */
    fun selectPalette(font: ByteArray, paletteIndex: Int): Boolean {
        val bb = ByteBuffer.wrap(font).order(ByteOrder.BIG_ENDIAN)
        val numTables = bb.getShort(4).toInt() and 0xFFFF
        var cpalOffset = -1; var cpalLength = 0; var recordPos = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            if (String(font, rec, 4, Charsets.US_ASCII) == "CPAL") {
                recordPos = rec; cpalOffset = bb.getInt(rec + 8); cpalLength = bb.getInt(rec + 12)
            }
        }
        if (cpalOffset < 0) return false
        val numEntries = bb.getShort(cpalOffset + 2).toInt() and 0xFFFF
        val numPalettes = bb.getShort(cpalOffset + 4).toInt() and 0xFFFF
        if (paletteIndex !in 0 until numPalettes) return false
        val firstColor = cpalOffset + bb.getInt(cpalOffset + 8)
        val start = bb.getShort(cpalOffset + 12 + paletteIndex * 2).toInt() and 0xFFFF
        val colors = IntArray(numEntries) { bb.getInt(firstColor + (start + it) * 4) }
        val newLen = 14 + numEntries * 4
        if (newLen > cpalLength) return false
        var p = cpalOffset
        bb.putShort(p, 0); p += 2
        bb.putShort(p, numEntries.toShort()); p += 2
        bb.putShort(p, 1); p += 2
        bb.putShort(p, numEntries.toShort()); p += 2
        bb.putInt(p, 14); p += 4
        bb.putShort(p, 0); p += 2
        for (c in colors) { bb.putInt(p, c); p += 4 }
        bb.putInt(recordPos + 12, newLen)
        return true
    }
}
