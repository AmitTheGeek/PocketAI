package com.pocketai.offline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SummarySection(
    state: PocketAiUiState,
    showJumpToLatest: Boolean,
    onJumpToLatest: () -> Unit,
    onCopySummary: (String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var copiedText by remember(state.outputText) { mutableStateOf<String?>(null) }
    val hasOutput = state.outputText.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("summarySection"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SectionTitle(text = "Summary")
                Text(
                    text = "${formatElapsed(state.elapsedMs)} elapsed",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SummaryFeedback(state = state)

        ResponsiveActions(
            first = {
                OutlinedButton(
                    onClick = {
                        copiedText = state.outputText
                        onCopySummary(state.outputText)
                    },
                    enabled = hasOutput,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summaryCopyButton")
                        .semantics { contentDescription = "Copy complete summary" },
                ) {
                    Text("Copy")
                }
            },
            second = {
                Button(
                    onClick = onSave,
                    enabled = state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summarySaveButton")
                        .semantics { contentDescription = "Save completed summary" },
                ) {
                    Text("Save")
                }
            },
        )

        if (copiedText == state.outputText && hasOutput) {
            Text(
                text = "Copied summary.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (showJumpToLatest) {
            OutlinedButton(
                onClick = onJumpToLatest,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Jump to latest summary text" },
            ) {
                Text("Jump to latest")
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (hasOutput) {
            SelectableSummaryText(text = state.outputText)
        } else {
            Text(
                text = if (state.isGenerating) "Waiting for the first tokens..." else "Summary will appear here.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp)
                    .testTag("summaryEmptyState"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryFeedback(state: PocketAiUiState) {
    state.loadingMessage?.let { LoadingBand(message = it) }
    state.errorMessage?.let { ErrorBand(message = it) }
    state.warningMessage?.let { WarningBand(message = it) }
    state.saveMessage?.let {
        if (state.saveState is SaveState.Error) {
            ErrorBand(message = it)
        } else {
            StatusBand(message = it)
        }
    }

    if (state.generationState is GenerationState.Cancelled && state.outputText.isNotBlank()) {
        StatusBand(message = "Cancelled. Partial text is kept for review but cannot be saved.")
    }
}

@Composable
internal fun SelectableSummaryText(
    text: String,
    modifier: Modifier = Modifier,
) {
    SelectionContainer {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .testTag("summaryText"),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            text.lineSequence().forEachIndexed { index, line ->
                Text(
                    text = line.ifBlank { " " },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("summaryLine-$index"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
