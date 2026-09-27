package com.quransunah.app.share

/**
 * Unicode ayah text for clipboard and text share.
 * Reference uses ASCII digits so other apps keep the numbers readable.
 */
object AyahPlainText {
    fun block(surah: Int, ayah: Int, arabic: String, translation: String?): String {
        val body = arabic.trim()
        val head = "$surah:$ayah\n$body"
        val translated = translation?.trim().orEmpty()
        return if (translated.isEmpty()) head else "$head\n\n$translated"
    }

    fun document(blocks: List<String>): String = blocks.filter { it.isNotBlank() }.joinToString("\n\n")
}
