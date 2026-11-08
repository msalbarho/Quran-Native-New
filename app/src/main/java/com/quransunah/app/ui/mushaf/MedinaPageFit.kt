package com.quransunah.app.ui.mushaf

/**
 * Printed Medina page proportion (height ÷ width).
 * Training fit uses this so the whole leaf scales together.
 * Line spacing is never squeezed on its own.
 */
internal const val MEDINA_PAGE_HEIGHT_OVER_WIDTH = 1.62f

internal data class MedinaPageBox(
    val width: Float,
    val height: Float,
)

/** How the Medina leaf uses the space around it. Reading keeps [Fill]. */
enum class MedinaPageFit {
    /** Stretch the leaf to the given box. Existing reading layout. */
    Fill,

    /** Scale the whole leaf, at a fixed proportion, until it fits. */
    FitScreen,

    /** Full width at the same proportion, with vertical scroll when needed. */
    OriginalScroll,
}

/** Largest page of the Medina proportion that fits inside [maxWidth] × [maxHeight]. */
internal fun fittedMedinaPageBox(maxWidth: Float, maxHeight: Float): MedinaPageBox {
    if (maxWidth <= 0f || maxHeight <= 0f) return MedinaPageBox(0f, 0f)
    val heightAtFullWidth = maxWidth * MEDINA_PAGE_HEIGHT_OVER_WIDTH
    return if (heightAtFullWidth <= maxHeight) {
        MedinaPageBox(maxWidth, heightAtFullWidth)
    } else {
        MedinaPageBox(maxHeight / MEDINA_PAGE_HEIGHT_OVER_WIDTH, maxHeight)
    }
}

/** Full-width leaf. Taller than the viewport when the screen is short. */
internal fun originalMedinaPageBox(maxWidth: Float): MedinaPageBox {
    val width = maxWidth.coerceAtLeast(0f)
    return MedinaPageBox(width, width * MEDINA_PAGE_HEIGHT_OVER_WIDTH)
}
