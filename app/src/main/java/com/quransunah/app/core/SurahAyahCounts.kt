package com.quransunah.app.core

object SurahAyahCounts {
    val counts: IntArray = intArrayOf(
        7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128,
        111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73,
        54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60,
        49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18, 12, 12, 30, 52, 52,
        44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19,
        26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3,
        6, 3, 5, 4, 5, 6,
    )

    fun ayahCount(surah: Int): Int {
        if (surah !in 1..counts.size) return 0
        return counts[surah - 1]
    }

    fun globalAyahIndex(surah: Int, ayah: Int): Int {
        var total = 0
        for (index in 0 until (surah - 1).coerceAtLeast(0)) {
            total += counts.getOrElse(index) { 0 }
        }
        return total + ayah
    }

    fun ayahId(surah: Int, ayah: Int): Int {
        if (ayahCount(surah) <= 0 || ayah !in 1..ayahCount(surah)) return 0
        return globalAyahIndex(surah, ayah)
    }

    fun fromAyahId(ayahId: Int): Pair<Int, Int>? {
        if (ayahId <= 0) return null
        var remaining = ayahId
        for (surah in 1..counts.size) {
            val count = counts[surah - 1]
            if (remaining <= count) return surah to remaining
            remaining -= count
        }
        return null
    }

    fun next(surah: Int, ayah: Int): Pair<Int, Int>? {
        val count = ayahCount(surah)
        if (count <= 0) return null
        if (ayah < count) return surah to (ayah + 1)
        if (surah >= counts.size) return null
        return (surah + 1) to 1
    }

    fun previous(surah: Int, ayah: Int): Pair<Int, Int>? {
        if (ayah > 1) return surah to (ayah - 1)
        if (surah <= 1) return null
        val previousSurah = surah - 1
        return previousSurah to ayahCount(previousSurah)
    }

    fun range(
        startSurah: Int,
        startAyah: Int,
        endSurah: Int,
        endAyah: Int,
    ): List<Pair<Int, Int>> {
        val startOk = ayahCount(startSurah) > 0 && startAyah in 1..ayahCount(startSurah)
        val endOk = ayahCount(endSurah) > 0 && endAyah in 1..ayahCount(endSurah)
        if (!startOk) return emptyList()
        val end = if (endOk) endSurah to endAyah else startSurah to startAyah
        if (end.first < startSurah || (end.first == startSurah && end.second < startAyah)) {
            return listOf(startSurah to startAyah)
        }
        val out = ArrayList<Pair<Int, Int>>(16)
        var cursor = startSurah to startAyah
        var guard = 0
        while (guard++ < 6236) {
            out.add(cursor)
            if (cursor == end) break
            cursor = next(cursor.first, cursor.second) ?: break
            if (cursor.first > end.first ||
                (cursor.first == end.first && cursor.second > end.second)
            ) {
                break
            }
        }
        return out
    }

    fun pad3(value: Int): String = value.toString().padStart(3, '0')

    fun pad5(value: Int): String = value.toString().padStart(5, '0')
}
