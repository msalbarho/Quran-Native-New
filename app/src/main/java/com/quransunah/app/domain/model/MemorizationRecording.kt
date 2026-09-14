package com.quransunah.app.domain.model

data class MemorizationRecording(
    val id: String,
    val sessionId: String,
    val filePath: String,
    val createdAt: Long,
    val durationMs: Long,
)
