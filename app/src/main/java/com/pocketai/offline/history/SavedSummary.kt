package com.pocketai.offline.history

data class SavedSummary(
    val id: Long,
    val sourceText: String,
    val summaryText: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val refinementOccurred: Boolean,
    val formatWarning: String?,
    val modelName: String?,
    val modelSizeBytes: Long?,
)

data class NewSavedSummary(
    val sourceText: String,
    val summaryText: String,
    val createdAtEpochMs: Long,
    val durationMs: Long,
    val refinementOccurred: Boolean,
    val formatWarning: String?,
    val modelName: String?,
    val modelSizeBytes: Long?,
)

