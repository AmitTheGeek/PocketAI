package com.pocketai.offline.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Empty state", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun EmptyStatePreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            inputText = "",
            outputText = "",
        ),
    )
}

@Preview(name = "Long input and summary", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun LongInputAndSummaryPreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = previewLongSummary,
            elapsedMs = 5400,
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(name = "Generating", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun GeneratingPreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = "- Amit will test the app by Friday.\n- Priya will prepare samples",
            elapsedMs = 2100,
            generationState = GenerationState.Generating,
        ),
    )
}

@Preview(name = "Refining", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun RefiningPreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = "",
            elapsedMs = 4300,
            generationState = GenerationState.Refining,
        ),
    )
}

@Preview(name = "Format warning", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun FormatWarningPreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = previewLongSummary,
            elapsedMs = 6200,
            formatWarning = "Summary format warning: expected 1-3 bullets and no extra prose.",
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(name = "Large font narrow", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 1.6f)
@Composable
private fun LargeFontNarrowPreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = previewLongSummary,
            elapsedMs = 5000,
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(
    name = "Dark theme",
    showBackground = true,
    widthDp = 390,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DarkThemePreview() {
    PreviewSummarizer(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
            outputText = previewLongSummary,
            elapsedMs = 5000,
            generationState = GenerationState.Completed,
        ),
        darkTheme = true,
    )
}

@Composable
private fun PreviewSummarizer(
    state: PocketAiUiState,
    darkTheme: Boolean = false,
) {
    PocketAiTheme(darkTheme = darkTheme) {
        SummarizerScreen(
            state = state,
            onHistory = {},
            onImport = {},
            onTextChange = {},
            onExpandEditor = {},
            onSummarise = {},
            onCancel = {},
            onCopySummary = {},
            onSave = {},
        )
    }
}

private fun previewReadyModel(): ModelReadiness.Ready =
    ModelReadiness.Ready(
        ImportedModel(
            name = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
            sizeBytes = 1_100_000_000,
            path = "/app/private/model.gguf",
        ),
    )

private const val previewLongInput =
    "The PocketAI team met on Monday to review the Android prototype. " +
        "Offline summarisation is implemented, but testing on a physical phone is still pending. " +
        "Amit will test the app on a OnePlus 8 Pro by Friday. " +
        "Priya will prepare five evaluation samples by Thursday. " +
        "Cloud integration and saved history are outside the current milestone."

private const val previewLongSummary =
    "- Offline summarisation is implemented, while physical-phone testing remains pending.\n" +
        "- Amit will test on a OnePlus 8 Pro by Friday, and Priya will prepare five evaluation samples by Thursday.\n" +
        "- Cloud integration and saved history are outside the current milestone."
