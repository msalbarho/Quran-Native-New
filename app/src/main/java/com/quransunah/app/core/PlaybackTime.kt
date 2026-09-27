package com.quransunah.app.core

fun formatPlaybackClock(positionMs: Long): String =
    UiNumbers.formatClock(positionMs, UiNumbers.resolvedLocale())

fun formatPlaybackClock(positionMs: Long, locale: java.util.Locale): String =
    UiNumbers.formatClock(positionMs, locale)

private fun padEasternTwo(value: Int): String {
    val raw = UiNumbers.format(value, UiNumbers.resolvedLocale())
    if (raw.length >= 2) return raw
    return "0$raw"
}
