package com.quransunah.app.core

fun formatPlaybackClock(positionMs: Long): String =
    UiNumbers.formatClock(positionMs, UiNumbers.resolvedLocale())

fun formatPlaybackClock(positionMs: Long, locale: java.util.Locale): String =
    UiNumbers.formatClock(positionMs, locale)
