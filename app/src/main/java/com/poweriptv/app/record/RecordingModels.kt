package com.poweriptv.app.record

import kotlinx.serialization.Serializable

// Datenmodell der Aufnahmen – geteilt mit der Windows-App (Windows/).

@Serializable
enum class RecStatus { SCHEDULED, RECORDING, COMPLETED, FAILED, CANCELLED }

@Serializable
data class Recording(
    val id: String,
    val title: String,
    val channelName: String,
    val url: String,
    val start: Long,
    val end: Long,
    val filePath: String,
    val logo: String? = null,
    val status: RecStatus = RecStatus.SCHEDULED,
    val bytes: Long = 0,
    val error: String? = null,
)
