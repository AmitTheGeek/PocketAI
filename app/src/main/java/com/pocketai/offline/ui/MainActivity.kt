package com.pocketai.offline.ui

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketai.offline.R
import com.pocketai.offline.history.SavedSummary
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val viewModel = ViewModelProvider(
            this,
            MainViewModel.factory(application)
        )[MainViewModel::class.java]

        setContent {
            PocketAiTheme {
                PocketAiScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PocketAiScreen(viewModel: MainViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = state.destination != PocketAiDestination.Summarizer) {
        viewModel.navigateBack()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF7F8F3)
    ) {
        when (state.destination) {
            PocketAiDestination.Summarizer -> SummaryScreen(
                state = state,
                viewModel = viewModel
            )
            PocketAiDestination.History -> HistoryScreen(
                state = state,
                onBack = viewModel::openSummarizer,
                onOpenSummary = viewModel::openSummaryDetail
            )
            is PocketAiDestination.Detail -> DetailScreen(
                state = state,
                onBack = viewModel::openHistory,
                onDelete = viewModel::deleteSelectedSummary
            )
        }
    }
}

@Composable
private fun SummaryScreen(
    state: PocketAiUiState,
    viewModel: MainViewModel,
) {
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(viewModel::importModel)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Header(
            state = state,
            onHistory = viewModel::openHistory
        )

        ActionRow(
            state = state,
            onImport = {
                filePicker.launch(arrayOf("application/octet-stream", "*/*"))
            },
            onSummarize = viewModel::summarize,
            onCancel = viewModel::cancelSummary,
            onSave = viewModel::saveCurrentSummary
        )

        state.loadingMessage?.let {
            LoadingBand(message = it)
        }

        state.errorMessage?.let {
            MessageBand(message = it)
        }

        state.warningMessage?.let {
            WarningBand(message = it)
        }

        state.saveMessage?.let {
            if (state.saveState is SaveState.Error) {
                MessageBand(message = it)
            } else {
                StatusBand(message = it)
            }
        }

        InputSection(
            text = state.inputText,
            enabled = !state.isBusy,
            onTextChange = viewModel::updateInput
        )

        OutputSection(state = state)
    }
}

@Composable
private fun Header(
    state: PocketAiUiState,
    onHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "PocketAI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF13201A)
            )
            Text(
                text = state.modelLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF425148),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(
            onClick = onHistory,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("History")
        }
    }
}

@Composable
private fun ActionRow(
    state: PocketAiUiState,
    onImport: () -> Unit,
    onSummarize: () -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onImport,
                enabled = state.canImport,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_upload_file),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Import")
            }

            Button(
                onClick = onSummarize,
                enabled = state.canSummarize,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C6B4A))
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Summarize")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = state.isGenerating,
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cancel),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(8.dp))
                Text("Cancel")
            }

            Button(
                onClick = onSave,
                enabled = state.canSave,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF315F80))
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
private fun LoadingBand(message: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF27352E)
        )
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF1C6B4A),
            trackColor = Color(0xFFD8E3D5)
        )
    }
}

@Composable
private fun MessageBand(message: String) {
    Surface(
        color = Color(0xFFFFE8E4),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF8A1F11),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun WarningBand(message: String) {
    Surface(
        color = Color(0xFFFFF1CC),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF6B4B00),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun StatusBand(message: String) {
    Surface(
        color = Color(0xFFE2F0E6),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(12.dp),
            color = Color(0xFF1E5135),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun InputSection(
    text: String,
    enabled: Boolean,
    onTextChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Input",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF13201A)
        )
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            enabled = enabled,
            minLines = 7,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            textStyle = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun OutputSection(state: PocketAiUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Summary",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF13201A)
            )
            Text(
                text = formatElapsed(state.elapsedMs),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5D544B)
            )
        }

        HorizontalDivider(color = Color(0xFFD8DAD3))

        Text(
            text = state.outputText.ifBlank {
                if (state.isGenerating) "" else "Output will stream here."
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .padding(top = 4.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF16221B)
        )
    }
}

@Composable
private fun HistoryScreen(
    state: PocketAiUiState,
    onBack: () -> Unit,
    onOpenSummary: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "History",
            onBack = onBack
        )

        when (val history = state.historyState) {
            HistoryUiState.Loading -> LoadingBand(message = "Loading saved summaries...")
            is HistoryUiState.Error -> MessageBand(message = history.message)
            is HistoryUiState.Loaded -> {
                if (history.items.isEmpty()) {
                    EmptyState(message = "No saved summaries yet.")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = history.items,
                            key = { it.id }
                        ) { summary ->
                            HistoryRow(
                                summary = summary,
                                onClick = { onOpenSummary(summary.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailScreen(
    state: PocketAiUiState,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenHeader(
            title = "Saved summary",
            onBack = onBack
        )

        when (val detail = state.detailState) {
            DetailUiState.Idle,
            DetailUiState.Loading -> LoadingBand(message = "Loading saved summary...")
            DetailUiState.NotFound -> EmptyState(message = "Saved summary not found.")
            is DetailUiState.Error -> MessageBand(message = detail.message)
            is DetailUiState.Loaded -> SavedSummaryDetail(
                detail = detail,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF13201A)
        )
        Spacer(Modifier.size(64.dp))
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
            .clickable(onClick = onClick),
        color = Color(0xFFFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = summary.sourcePreview(),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF16221B),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTimestamp(summary.createdAtEpochMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D544B)
                )
                if (summary.formatWarning != null) {
                    Text(
                        text = "Warning",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color(0xFF8B5E00)
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatTimestamp(summary.createdAtEpochMs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF425148)
                )
                Text(
                    text = summary.modelLabel(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D544B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${formatElapsed(summary.durationMs)} total" +
                        if (summary.refinementOccurred) " with refinement" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF5D544B)
                )
            }
            Button(
                onClick = { showDeleteDialog = true },
                enabled = !detail.isDeleting,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF9B2C1D))
            ) {
                Text(if (detail.isDeleting) "Deleting" else "Delete")
            }
        }

        detail.deleteError?.let { MessageBand(message = it) }
        summary.formatWarning?.let { WarningBand(message = it) }

        DetailTextSection(
            title = "Source",
            text = summary.sourceText
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Saved summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF13201A)
                )
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(summary.summaryText))
                        copiedSummaryId = summary.id
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Copy")
                }
            }
            if (copiedSummaryId == summary.id) {
                Text(
                    text = "Copied summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF1E5135)
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
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailTextSection(
    title: String,
    text: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF13201A)
        )
        DetailBody(text = text)
    }
}

@Composable
private fun DetailBody(text: String) {
    Surface(
        color = Color(0xFFFFFFFF),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
                .padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF16221B)
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF5D544B)
        )
    }
}

@Composable
private fun PocketAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF1C6B4A),
            secondary = Color(0xFF315F80),
            background = Color(0xFFF7F8F3),
            surface = Color(0xFFF7F8F3),
            error = Color(0xFF9B2C1D)
        ),
        content = content
    )
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

private fun formatElapsed(ms: Long): String {
    val seconds = ms / 1000
    val tenths = (ms % 1000) / 100
    return "$seconds.${tenths}s"
}

private fun formatBytesForUi(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024.0) {
        "%.2f GB".format(mib / 1024.0)
    } else {
        "%.0f MB".format(mib)
    }
}
