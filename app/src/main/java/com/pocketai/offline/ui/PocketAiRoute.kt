package com.pocketai.offline.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PocketAiRoute(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var expandedEditorOpen by rememberSaveable { mutableStateOf(false) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importModel)
    }

    BackHandler(enabled = expandedEditorOpen) {
        expandedEditorOpen = false
    }

    BackHandler(
        enabled = !expandedEditorOpen && state.destination != PocketAiDestination.Summarizer
    ) {
        viewModel.navigateBack()
    }

    if (expandedEditorOpen) {
        ExpandedSourceEditor(
            text = state.inputText,
            enabled = !state.isBusy,
            onTextChange = viewModel::updateInput,
            onDone = { expandedEditorOpen = false },
        )
        return
    }

    when (state.destination) {
        PocketAiDestination.Summarizer -> SummarizerScreen(
            state = state,
            onHistory = viewModel::openHistory,
            onImport = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
            onTextChange = viewModel::updateInput,
            onExpandEditor = { expandedEditorOpen = true },
            onSummarise = viewModel::summarize,
            onCancel = viewModel::cancelSummary,
            onCopySummary = { text -> clipboard.setText(AnnotatedString(text)) },
            onSave = viewModel::saveCurrentSummary,
        )

        PocketAiDestination.History -> HistoryScreen(
            state = state,
            onBack = viewModel::openSummarizer,
            onOpenSummary = viewModel::openSummaryDetail,
        )

        is PocketAiDestination.Detail -> DetailScreen(
            state = state,
            onBack = viewModel::openHistory,
            onDelete = viewModel::deleteSelectedSummary,
        )
    }
}
