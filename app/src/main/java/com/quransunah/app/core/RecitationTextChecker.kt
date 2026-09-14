package com.quransunah.app.core

enum class WordDifferenceType { MISSING, EXTRA, DIFFERENT }
data class WordDifference(val type: WordDifferenceType, val expected: String?, val actual: String?)
data class RecitationCheckResult(
    val scorePercent: Int,
    val differences: List<WordDifference>,
    val expectedWordCount: Int,
    val actualWordCount: Int,
) {
    val passed: Boolean get() = scorePercent >= 80 && differences.none { it.type == WordDifferenceType.MISSING }
}

object RecitationTextChecker {
    fun compare(expected: String, actual: String): RecitationCheckResult {
        val expectedWords = words(expected)
        val actualWords = words(actual)
        val rows = expectedWords.size + 1
        val cols = actualWords.size + 1
        val distance = Array(rows) { IntArray(cols) }
        for (i in 1 until rows) distance[i][0] = i
        for (j in 1 until cols) distance[0][j] = j
        for (i in 1 until rows) for (j in 1 until cols) {
            val cost = if (expectedWords[i - 1] == actualWords[j - 1]) 0 else 1
            distance[i][j] = minOf(
                distance[i - 1][j] + 1,
                distance[i][j - 1] + 1,
                distance[i - 1][j - 1] + cost,
            )
        }
        val differences = ArrayList<WordDifference>()
        var i = expectedWords.size
        var j = actualWords.size
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && expectedWords[i - 1] == actualWords[j - 1]) {
                i--; j--
            } else {
                val diagonal = if (i > 0 && j > 0) distance[i - 1][j - 1] else Int.MAX_VALUE
                val delete = if (i > 0) distance[i - 1][j] else Int.MAX_VALUE
                val insert = if (j > 0) distance[i][j - 1] else Int.MAX_VALUE
                when (minOf(diagonal, delete, insert)) {
                    diagonal -> { differences += WordDifference(WordDifferenceType.DIFFERENT, expectedWords[i - 1], actualWords[j - 1]); i--; j-- }
                    delete -> { differences += WordDifference(WordDifferenceType.MISSING, expectedWords[i - 1], null); i-- }
                    else -> { differences += WordDifference(WordDifferenceType.EXTRA, null, actualWords[j - 1]); j-- }
                }
            }
        }
        val denominator = maxOf(expectedWords.size, actualWords.size, 1)
        val score = ((1f - distance[expectedWords.size][actualWords.size].toFloat() / denominator) * 100).toInt().coerceIn(0, 100)
        return RecitationCheckResult(score, differences.asReversed(), expectedWords.size, actualWords.size)
    }

    private fun words(value: String): List<String> = ArabicNormalize.fold(value).split(' ').filter(String::isNotBlank)
}
