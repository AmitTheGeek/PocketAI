package com.pocketai.offline.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
internal fun PocketAiRoute(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importModel)
    }

    BackHandler(
        enabled = state.destination != PocketAiDestination.Input
    ) {
        viewModel.navigateBack()
    }

    when (state.destination) {
        PocketAiDestination.Input -> InputScreen(
            state = state,
            onHistory = viewModel::openHistory,
            onImport = { filePicker.launch(arrayOf("application/octet-stream", "*/*")) },
            onTextChange = viewModel::updateInput,
            onSummarise = viewModel::summarize,
        )

        PocketAiDestination.Result -> ResultScreen(
            state = state,
            onBack = { viewModel.navigateBack() },
            onCancel = viewModel::cancelSummary,
            onCopySummary = { text -> clipboard.setText(AnnotatedString(text)) },
            onSave = viewModel::saveCurrentSummary,
            onEditSource = viewModel::openInput,
        )

        PocketAiDestination.History -> HistoryScreen(
            state = state,
            onBack = viewModel::openInput,
            onOpenSummary = viewModel::openSummaryDetail,
        )

        is PocketAiDestination.Detail -> DetailScreen(
            state = state,
            onBack = viewModel::openHistory,
            onDelete = viewModel::deleteSelectedSummary,
        )
    }
}
