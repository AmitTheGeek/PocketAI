package com.pocketai.offline.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pocketai.offline.history.SavedSummary
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HistoryScreen(
    state: PocketAiUiState,
    onBack: () -> Unit,
    onOpenSummary: (Long) -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val history = state.historyState) {
                HistoryUiState.Loading -> LoadingBand(message = "Loading saved summaries...")
                is HistoryUiState.Error -> ErrorBand(message = history.message)
                is HistoryUiState.Loaded -> {
                    if (history.items.isEmpty()) {
                        EmptyState(message = "No saved summaries yet.")
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = history.items,
                                key = { it.id },
                            ) { summary ->
                                HistoryRow(
                                    summary = summary,
                                    onClick = { onOpenSummary(summary.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailScreen(
    state: PocketAiUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Saved summary") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val detail = state.detailState) {
                DetailUiState.Idle,
                DetailUiState.Loading -> LoadingBand(message = "Loading saved summary...")
                DetailUiState.NotFound -> EmptyState(message = "Saved summary not found.")
                is DetailUiState.Error -> ErrorBand(message = detail.message)
                is DetailUiState.Loaded -> SavedSummaryDetail(
                    detail = detail,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(
    summary: SavedSummary,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Open saved summary" },
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = summary.sourcePreview(),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatTimestamp(summary.createdAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.formatWarning != null) {
                    Text(
                        text = "Warning",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SavedSummaryDetail(
    detail: DetailUiState.Loaded,
    onDelete: () -> Unit,
) {
    val summary = detail.summary
    val clipboard = LocalClipboardManager.current
    var copiedSummaryId by remember(summary.id) { mutableStateOf<Long?>(null) }
    var showDeleteDialog by remember(summary.id) { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = formatTimestamp(summary.createdAtEpochMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = summary.modelLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${formatElapsed(summary.durationMs)} total" +
                        if (summary.refinementOccurred) " with refinement" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(
                onClick = { showDeleteDialog = true },
                enabled = !detail.isDeleting,
                modifier = Modifier.semantics { contentDescription = "Delete saved summary" },
            ) {
                Text(if (detail.isDeleting) "Deleting" else "Delete")
            }
        }

        detail.deleteError?.let { ErrorBand(message = it) }
        summary.formatWarning?.let { WarningBand(message = it) }

        DetailTextSection(
            title = "Source",
            text = summary.sourceText,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle(text = "Saved summary")
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(summary.summaryText))
                        copiedSummaryId = summary.id
                    },
                    modifier = Modifier.semantics {
                        contentDescription = "Copy saved summary"
                    },
                ) {
                    Text("Copy")
                }
            }
            if (copiedSummaryId == summary.id) {
                Text(
                    text = "Copied summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            DetailBody(text = summary.summaryText)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete saved summary?") },
            text = { Text("This removes the saved source and summary from local history.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun DetailTextSection(
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title)
        DetailBody(text = text)
    }
}

@Composable
private fun DetailBody(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SelectionContainer {
            Text(
                text = text,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun SavedSummary.sourcePreview(): String =
    sourceText.replace(Regex("\\s+"), " ").trim().let { compact ->
        if (compact.length <= 140) compact else compact.take(137).trimEnd() + "..."
    }

private fun SavedSummary.modelLabel(): String =
    modelName?.let { name ->
        val size = modelSizeBytes?.let { "  ${formatBytesForUi(it)}" }.orEmpty()
        "Model: $name$size"
    } ?: "Model: unknown"

private fun formatTimestamp(epochMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(epochMs))
