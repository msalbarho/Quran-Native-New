package com.quransunah.app.share

/**
 * Smallest 25 files from `public/images/share_images`, packaged as `assets/share_images`.
 */
object ShareBackgrounds {
    const val ASSET_DIR = "share_images"

    val FILES: List<String> = listOf(
        "m45.jpg", "m56.jpg", "m67.jpg", "m74.jpg", "m75.jpg",
        "m2.jpg", "m3.jpg", "m5.jpg", "m6.jpg", "m7.jpg",
        "m8.jpg", "m9.jpg", "m10.jpg", "m11.jpg", "m13.jpg",
        "m14.jpg", "m15.jpg", "m17.jpg", "m18.jpg", "m19.jpg",
        "m20.jpg", "m21.jpg", "m22.jpg", "m24.jpg", "m25.jpg",
    )

    fun assetPath(fileName: String): String = "$ASSET_DIR/$fileName"

    fun pick(exclude: String? = null): String {
        val pool = if (exclude == null) FILES else FILES.filter { it != exclude }
        return (pool.ifEmpty { FILES }).random()
    }
}
