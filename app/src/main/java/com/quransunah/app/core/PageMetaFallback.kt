package com.quransunah.app.core

import com.quransunah.app.domain.model.PageMeta

object PageMetaFallback {
    private val juzStartPages = intArrayOf(
        1, 22, 42, 62, 82, 102, 121, 142, 162, 182, 201, 222, 242, 262, 282, 302, 322, 342, 362,
        382, 402, 422, 442, 462, 482, 502, 522, 542, 562, 582,
    )

    fun build(pageNumber: Int): PageMeta = PageMeta(
        pageNumber = pageNumber,
        juzNumber = juzForPage(pageNumber),
        hizbNumber = hizbForPage(pageNumber),
    )

    fun surahOverride(pageNumber: Int): Int? = when (pageNumber) {
        584 -> 79
        585 -> 80
        76 -> 3
        77 -> 4
        else -> null
    }

    private fun juzForPage(pageNumber: Int): Int {
        for (index in juzStartPages.indices.reversed()) {
            if (pageNumber >= juzStartPages[index]) return index + 1
        }
        return 1
    }

    private fun hizbStartPage(hizbNumber: Int): Int {
        if (hizbNumber == 1) return 1
        return "${hizbNumber - 1}2".toInt()
    }

    private fun hizbForPage(pageNumber: Int): Int {
        for (hizb in 60 downTo 1) {
            if (pageNumber >= hizbStartPage(hizb)) return hizb
        }
        return 1
    }
}
