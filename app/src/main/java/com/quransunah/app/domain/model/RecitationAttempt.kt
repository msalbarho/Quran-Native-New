package com.quransunah.app.domain.model

data class RecitationAttempt(
    val id: String,
    val sessionId: String,
    val recordingId: String?,
    val scorePercent: Int,
    val missingCount: Int,
    val extraCount: Int,
    val differentCount: Int,
    val createdAt: Long,
)
