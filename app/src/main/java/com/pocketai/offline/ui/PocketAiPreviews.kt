package com.pocketai.offline.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Input empty state", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun InputEmptyStatePreview() {
    PreviewInput(
        state = PocketAiUiState(
            inputText = "",
            outputText = "",
        ),
    )
}

@Preview(name = "Input long text", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun InputLongTextPreview() {
    PreviewInput(
        state = PocketAiUiState(
            modelReadiness = previewReadyModel(),
            inputText = previewLongInput,
        ),
    )
}

@Preview(name = "Result long summary", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ResultLongSummaryPreview() {
    PreviewResult(
        state = previewResultState(
            outputText = previewLongSummary,
            elapsedMs = 5400,
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(name = "Result generating", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ResultGeneratingPreview() {
    PreviewResult(
        state = previewResultState(
            outputText = "- Amit will test the app by Friday.\n- Priya will prepare samples",
            elapsedMs = 2100,
            generationState = GenerationState.Generating,
        ),
    )
}

@Preview(name = "Result refining", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ResultRefiningPreview() {
    PreviewResult(
        state = previewResultState(
            outputText = "",
            elapsedMs = 4300,
            generationState = GenerationState.Refining,
        ),
    )
}

@Preview(name = "Result format warning", showBackground = true, widthDp = 390, heightDp = 820)
@Composable
private fun ResultFormatWarningPreview() {
    PreviewResult(
        state = previewResultState(
            outputText = previewLongSummary,
            elapsedMs = 6200,
            formatWarning = "Format warning: expected 1-3 bullets and no extra prose.",
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(name = "Large font narrow", showBackground = true, widthDp = 320, heightDp = 720, fontScale = 1.6f)
@Composable
private fun LargeFontNarrowPreview() {
    PreviewResult(
        state = previewResultState(
            outputText = previewLongSummary,
            elapsedMs = 5000,
            generationState = GenerationState.Completed,
        ),
    )
}

@Preview(
    name = "Dark result",
    showBackground = true,
    widthDp = 390,
    heightDp = 820,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DarkThemePreview() {
    PreviewResult(
        state = previewResultState(
            outputText = previewLongSummary,
            elapsedMs = 5000,
            generationState = GenerationState.Completed,
        ),
        darkTheme = true,
    )
}

@Composable
private fun PreviewInput(
    state: PocketAiUiState,
    darkTheme: Boolean = false,
) {
    PocketAiTheme(darkTheme = darkTheme) {
        InputScreen(
            state = state.copy(destination = PocketAiDestination.Input),
            onHistory = {},
            onImport = {},
            onTextChange = {},
            onSummarise = {},
        )
    }
}

@Composable
private fun PreviewResult(
    state: PocketAiUiState,
    darkTheme: Boolean = false,
) {
    PocketAiTheme(darkTheme = darkTheme) {
        ResultScreen(
            state = state.copy(destination = PocketAiDestination.Result),
            onBack = {},
            onCancel = {},
            onCopySummary = {},
            onSave = {},
            onEditSource = {},
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

private fun previewResultState(
    outputText: String,
    elapsedMs: Long,
    generationState: GenerationState,
    formatWarning: String? = null,
): PocketAiUiState =
    PocketAiUiState(
        destination = PocketAiDestination.Result,
        modelReadiness = previewReadyModel(),
        inputText = previewLongInput,
        activeSourceText = previewLongInput,
        outputText = outputText,
        elapsedMs = elapsedMs,
        formatWarning = formatWarning,
        generationState = generationState,
        completedSummary = if (generationState == GenerationState.Completed) {
            CompletedSummarySnapshot(
                resultId = 1L,
                sourceText = previewLongInput,
                summaryText = outputText,
                durationMs = elapsedMs,
                refinementOccurred = formatWarning != null,
                formatWarning = formatWarning,
                modelName = "Qwen2.5-1.5B-Instruct-Q4_K_M.gguf",
                modelSizeBytes = 1_100_000_000,
            )
        } else {
            null
        },
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
