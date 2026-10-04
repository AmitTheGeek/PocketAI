package com.pocketai.offline.history

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_summaries")
data class SavedSummaryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,
    @ColumnInfo(name = "source_text")
    val sourceText: String,
    @ColumnInfo(name = "summary_text")
    val summaryText: String,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "refinement_occurred")
    val refinementOccurred: Boolean,
    @ColumnInfo(name = "format_warning")
    val formatWarning: String?,
    @ColumnInfo(name = "model_name")
    val modelName: String?,
    @ColumnInfo(name = "model_size_bytes")
    val modelSizeBytes: Long?,
)

fun SavedSummaryEntity.toSavedSummary(): SavedSummary =
    SavedSummary(
        id = id,
        sourceText = sourceText,
        summaryText = summaryText,
        createdAtEpochMs = createdAtEpochMs,
        durationMs = durationMs,
        refinementOccurred = refinementOccurred,
        formatWarning = formatWarning,
        modelName = modelName,
        modelSizeBytes = modelSizeBytes
    )

fun NewSavedSummary.toEntity(): SavedSummaryEntity =
    SavedSummaryEntity(
        sourceText = sourceText,
        summaryText = summaryText,
        createdAtEpochMs = createdAtEpochMs,
        durationMs = durationMs,
        refinementOccurred = refinementOccurred,
        formatWarning = formatWarning,
        modelName = modelName,
        modelSizeBytes = modelSizeBytes
    )

