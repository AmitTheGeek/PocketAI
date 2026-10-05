package com.pocketai.offline.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SummarizerScreen(
    state: PocketAiUiState,
    onHistory: () -> Unit,
    onImport: () -> Unit,
    onTextChange: (String) -> Unit,
    onExpandEditor: () -> Unit,
    onSummarise: () -> Unit,
    onCancel: () -> Unit,
    onCopySummary: (String) -> Unit,
    onSave: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var followOutput by remember { mutableStateOf(true) }

    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.isScrollInProgress to scrollState.isNearEnd() }
            .collect { (isScrolling, isNearEnd) ->
                if (isScrolling) followOutput = isNearEnd
            }
    }

    LaunchedEffect(state.generationState) {
        if (state.isGenerating) followOutput = true
    }

    LaunchedEffect(state.outputText, state.generationState, followOutput) {
        if (state.outputText.isNotBlank() && state.isGenerating && followOutput) {
            withFrameNanos { }
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "PocketAI",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    TextButton(
                        onClick = onHistory,
                        modifier = Modifier.semantics {
                            contentDescription = "Open saved summaries history"
                        },
                    ) {
                        Text("History")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("main-scroll"),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ModelStatusSection(
                state = state,
                onImport = onImport,
            )

            SourceTextSection(
                text = state.inputText,
                enabled = !state.isBusy,
                onTextChange = onTextChange,
                onExpandEditor = onExpandEditor,
            )

            PrimaryGenerationAction(
                state = state,
                onSummarise = onSummarise,
                onCancel = onCancel,
            )

            SummarySection(
                state = state,
                showJumpToLatest = state.isGenerating &&
                    state.outputText.isNotBlank() &&
                    !followOutput,
                onJumpToLatest = {
                    followOutput = true
                    coroutineScope.launch {
                        scrollState.animateScrollTo(scrollState.maxValue)
                    }
                },
                onCopySummary = onCopySummary,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun ModelStatusSection(
    state: PocketAiUiState,
    onImport: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        ResponsiveActions(
            modifier = Modifier.padding(14.dp),
            first = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = state.modelStatusTitle(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = state.modelStatusDetail(),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            second = {
                OutlinedButton(
                    onClick = onImport,
                    enabled = state.canImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Import or change local model" },
                ) {
                    Text(if (state.model == null) "Import model" else "Change model")
                }
            },
        )
    }
}

@Composable
private fun PrimaryGenerationAction(
    state: PocketAiUiState,
    onSummarise: () -> Unit,
    onCancel: () -> Unit,
) {
    if (state.isGenerating) {
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Cancel current summary generation" },
        ) {
            Text("Cancel")
        }
    } else {
        Button(
            onClick = onSummarise,
            enabled = state.canSummarize,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Summarise source text" },
        ) {
            Text("Summarise")
        }
    }
}

private fun PocketAiUiState.modelStatusTitle(): String =
    when (modelReadiness) {
        ModelReadiness.NoModel -> "No model selected"
        ModelReadiness.Importing -> "Importing model"
        ModelReadiness.Loading -> "Preparing model"
        is ModelReadiness.Ready -> "Model ready"
        is ModelReadiness.ModelError -> "Model needs attention"
    }

private fun PocketAiUiState.modelStatusDetail(): String =
    when (val readiness = modelReadiness) {
        ModelReadiness.NoModel -> "Import a local model before summarising."
        ModelReadiness.Importing -> "Copying the selected file into private app storage."
        ModelReadiness.Loading -> "Preparing local summarisation."
        is ModelReadiness.Ready -> "${readiness.model.name}  ${formatBytesForUi(readiness.model.sizeBytes)}"
        is ModelReadiness.ModelError -> readiness.message
    }

private fun androidx.compose.foundation.ScrollState.isNearEnd(): Boolean =
    value >= maxValue - 24
