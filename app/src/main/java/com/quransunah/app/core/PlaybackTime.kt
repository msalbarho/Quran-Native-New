package com.quransunah.app.core

fun formatPlaybackClock(positionMs: Long): String {
    val totalSeconds = (positionMs.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${EasternArabic.format(minutes)}:${padEasternTwo(seconds)}"
}

private fun padEasternTwo(value: Int): String {
    val raw = EasternArabic.format(value)
    return if (raw.length >= 2) raw else "٠$raw"
}
