package com.pocketai.offline.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InputScreen(
    state: PocketAiUiState,
    onHistory: () -> Unit,
    onImport: () -> Unit,
    onTextChange: (String) -> Unit,
    onSummarise: () -> Unit,
) {
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
        bottomBar = {
            InputBottomBar(
                state = state,
                onSummarise = onSummarise,
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("inputScreen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ModelStatusSection(
                state = state,
                onImport = onImport,
            )

            InputFeedback(state)

            SectionTitle(text = "Source text")
            SourceTextEditor(
                text = state.inputText,
                enabled = !state.isBusy,
                onTextChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultScreen(
    state: PocketAiUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onCopySummary: (String) -> Unit,
    onSave: () -> Unit,
    onEditSource: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    var followOutput by remember(state.activeRequestId) { mutableStateOf(true) }
    val showJumpToLatest = state.outputText.isNotBlank() && !followOutput
    val userScrollConnection = remember(scrollState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    followOutput = scrollState.isNearEnd()
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source == NestedScrollSource.UserInput) {
                    followOutput = scrollState.isNearEnd()
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(state.activeRequestId, followOutput, state.destination) {
        if (state.destination != PocketAiDestination.Result || !followOutput) return@LaunchedEffect
        snapshotFlow { scrollState.maxValue }
            .distinctUntilChanged()
            .conflate()
            .collect { maxValue ->
                if (scrollState.value != maxValue) {
                    scrollState.scrollTo(maxValue)
                }
                delay(FOLLOW_SCROLL_INTERVAL_MS)
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Summary") },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        modifier = Modifier.semantics {
                            contentDescription = "Return to source input"
                        },
                    ) {
                        Text("Back")
                    }
                },
            )
        },
        bottomBar = {
            ResultBottomBar(
                state = state,
                onCancel = onCancel,
                onCopySummary = onCopySummary,
                onSave = onSave,
                onEditSource = onEditSource,
            )
        },
        floatingActionButton = {
            if (showJumpToLatest) {
                Button(
                    onClick = {
                        followOutput = true
                        coroutineScope.launch {
                            scrollState.scrollTo(scrollState.maxValue)
                        }
                    },
                    modifier = Modifier
                        .testTag("jumpToLatestButton")
                        .semantics { contentDescription = "Jump to latest summary text" },
                ) {
                    Text("Jump to latest")
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .nestedScroll(userScrollConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("resultScroll"),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ResultHeader(state)
            ResultFeedback(state)

            if (state.isGenerating) {
                StatusBand(message = "Going back stops this summary.")
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.outputText.isNotBlank()) {
                SelectableSummaryText(text = state.outputText)
            } else {
                Text(
                    text = if (state.isGenerating) {
                        "Waiting for the first tokens..."
                    } else {
                        "Summary will appear here."
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp)
                        .testTag("summaryEmptyState"),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun InputBottomBar(
    state: PocketAiUiState,
    onSummarise: () -> Unit,
) {
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSummarise,
                enabled = state.canSummarize,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("summariseButton")
                    .semantics { contentDescription = "Summarise source text" },
            ) {
                Text("Summarise")
            }
        }
    }
}

@Composable
private fun ResultBottomBar(
    state: PocketAiUiState,
    onCancel: () -> Unit,
    onCopySummary: (String) -> Unit,
    onSave: () -> Unit,
    onEditSource: () -> Unit,
) {
    var copiedText by remember(state.outputText) { mutableStateOf<String?>(null) }

    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 3.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.generationState) {
                GenerationState.Preparing,
                GenerationState.Generating,
                GenerationState.Refining,
                GenerationState.Stopping -> {
                    OutlinedButton(
                        onClick = onCancel,
                        enabled = state.generationState != GenerationState.Stopping,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cancelButton")
                            .semantics { contentDescription = "Cancel current summary generation" },
                    ) {
                        Text(if (state.generationState == GenerationState.Stopping) "Stopping..." else "Cancel")
                    }
                }

                GenerationState.Completed -> {
                    ResponsiveActions(
                        first = {
                            OutlinedButton(
                                onClick = {
                                    copiedText = state.outputText
                                    onCopySummary(state.outputText)
                                },
                                enabled = state.outputText.isNotBlank(),
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
                    OutlinedButton(
                        onClick = onEditSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editSourceButton")
                            .semantics { contentDescription = "Return to edit source text" },
                    ) {
                        Text("Edit source")
                    }
                }

                GenerationState.Cancelled -> {
                    ResponsiveActions(
                        first = {
                            OutlinedButton(
                                onClick = {
                                    copiedText = state.outputText
                                    onCopySummary(state.outputText)
                                },
                                enabled = state.outputText.isNotBlank(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("summaryCopyButton")
                                    .semantics { contentDescription = "Copy partial summary" },
                            ) {
                                Text("Copy partial")
                            }
                        },
                        second = {
                            Button(
                                onClick = onEditSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("editSourceButton")
                                    .semantics { contentDescription = "Return to edit source text" },
                            ) {
                                Text("Edit source")
                            }
                        },
                    )
                }

                is GenerationState.Failed -> {
                    Button(
                        onClick = onEditSource,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("editSourceButton")
                            .semantics { contentDescription = "Return to edit source text" },
                    ) {
                        Text("Edit source")
                    }
                }

                GenerationState.Idle -> Unit
            }

            if (copiedText == state.outputText && state.outputText.isNotBlank()) {
                Text(
                    text = "Copied summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
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
private fun ResultHeader(state: PocketAiUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionTitle(text = "Summary")
        Text(
            text = "${formatElapsed(state.elapsedMs)} elapsed",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InputFeedback(state: PocketAiUiState) {
    state.loadingMessage?.let { LoadingBand(message = it) }
    state.errorMessage?.let { ErrorBand(message = it) }
}

@Composable
private fun ResultFeedback(state: PocketAiUiState) {
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

    if (state.generationState == GenerationState.Cancelled && state.outputText.isNotBlank()) {
        StatusBand(message = "Cancelled. Partial text is kept for review but cannot be saved.")
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

private fun ScrollState.isNearEnd(): Boolean =
    value >= maxValue - 48

private const val FOLLOW_SCROLL_INTERVAL_MS = 96L
