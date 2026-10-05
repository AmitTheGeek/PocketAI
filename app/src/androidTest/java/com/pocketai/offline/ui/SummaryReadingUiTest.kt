package com.pocketai.offline.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.swipeDown
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SummaryReadingUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longSummaryFinalMarkerCanBeScrolledIntoView() {
        val finalMarker = "FINAL-LINE-MARKER-7421"
        val finalLineTag = "summaryLine-80"
        val longSummary = (1..80).joinToString(separator = "\n") { index ->
            "- Summary point $index keeps reading content reachable."
        } + "\n- $finalMarker"

        composeRule.setContent {
            PocketAiTheme {
                ResultScreen(
                    state = completedResultState(longSummary),
                    onBack = {},
                    onCancel = {},
                    onCopySummary = {},
                    onSave = {},
                    onEditSource = {},
                )
            }
        }

        composeRule
            .onNodeWithTag(finalLineTag, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun inputDraftSurvivesOpeningResultAndReturning() {
        var showingResult by mutableStateOf(false)
        var draft by mutableStateOf("First line")

        composeRule.setContent {
            PocketAiTheme {
                if (showingResult) {
                    ResultScreen(
                        state = completedResultState("- A short result"),
                        onBack = { showingResult = false },
                        onCancel = {},
                        onCopySummary = {},
                        onSave = {},
                        onEditSource = { showingResult = false },
                    )
                } else {
                    InputScreen(
                        state = PocketAiUiState(
                            modelReadiness = readyModel(),
                            inputText = draft,
                        ),
                        onHistory = {},
                        onImport = {},
                        onTextChange = { draft = it },
                        onSummarise = { showingResult = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("sourceTextField").performTextInput("\nSecond line")
        composeRule.onNodeWithTag("summariseButton").performClick()
        composeRule.onNodeWithTag("editSourceButton").performClick()

        composeRule
            .onNodeWithTag("sourceTextField")
            .assertTextContains("Second line", substring = true)
    }

    @Test
    fun copyUsesCompleteOutputText() {
        val completeOutput = (1..20).joinToString(separator = "\n") { index ->
            "- Saved summary line $index"
        } + "\n- COPY-END-MARKER"
        var copied: String? = null

        composeRule.setContent {
            PocketAiTheme {
                ResultScreen(
                    state = completedResultState(completeOutput),
                    onBack = {},
                    onCancel = {},
                    onCopySummary = { copied = it },
                    onSave = {},
                    onEditSource = {},
                )
            }
        }

        composeRule.onNodeWithTag("summaryCopyButton").performClick()

        composeRule.runOnIdle {
            assertEquals(completeOutput, copied)
        }
    }

    @Test
    fun cancelRemainsReachableAtBottomOfLongStreamingOutput() {
        val longSummary = (1..60).joinToString(separator = "\n") { index ->
            "- Streaming line $index"
        }

        composeRule.setContent {
            PocketAiTheme {
                ResultScreen(
                    state = PocketAiUiState(
                        destination = PocketAiDestination.Result,
                        modelReadiness = readyModel(),
                        inputText = "Source text",
                        activeSourceText = "Source text",
                        outputText = longSummary,
                        generationState = GenerationState.Generating,
                        elapsedMs = 2_000,
                    ),
                    onBack = {},
                    onCancel = {},
                    onCopySummary = {},
                    onSave = {},
                    onEditSource = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("summaryLine-59", useUnmergedTree = true)
            .performScrollTo()

        composeRule.onNodeWithTag("cancelButton").assertIsDisplayed()
    }

    @Test
    fun jumpToLatestAppearsAfterManualScrollAway() {
        val finalMarker = "BOTTOM-MARKER-911"
        val longSummary = (1..70).joinToString(separator = "\n") { index ->
            "- Streaming line $index"
        } + "\n- $finalMarker"

        composeRule.setContent {
            PocketAiTheme {
                ResultScreen(
                    state = streamingResultState(longSummary),
                    onBack = {},
                    onCancel = {},
                    onCopySummary = {},
                    onSave = {},
                    onEditSource = {},
                )
            }
        }

        composeRule.waitForIdle()
        repeat(2) {
            composeRule.onNodeWithTag("resultScroll").performTouchInput {
                swipeDown()
            }
        }

        composeRule.onNodeWithTag("jumpToLatestButton").assertIsDisplayed()
        composeRule.onNodeWithTag("jumpToLatestButton").performClick()
        composeRule.waitForIdle()
        composeRule
            .onNodeWithTag("summaryLine-70", useUnmergedTree = true)
            .assertIsDisplayed()
    }

    @Test
    fun manualScrollIntentSurvivesRefinementReplacement() {
        var state by mutableStateOf(
            streamingResultState(
                (1..70).joinToString(separator = "\n") { index ->
                    "- Initial line $index"
                }
            )
        )

        composeRule.setContent {
            PocketAiTheme {
                ResultScreen(
                    state = state,
                    onBack = {},
                    onCancel = {},
                    onCopySummary = {},
                    onSave = {},
                    onEditSource = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithTag("resultScroll").performTouchInput {
            swipeDown()
        }
        composeRule.onNodeWithTag("jumpToLatestButton").assertIsDisplayed()

        composeRule.runOnIdle {
            state = state.copy(
                generationState = GenerationState.Refining,
                outputText = (1..30).joinToString(separator = "\n") { index ->
                    "- Refined line $index"
                },
            )
        }

        composeRule.onNodeWithTag("jumpToLatestButton").assertIsDisplayed()
    }

    private fun completedResultState(outputText: String): PocketAiUiState =
        PocketAiUiState(
            destination = PocketAiDestination.Result,
            modelReadiness = readyModel(),
            inputText = "Source text",
            activeSourceText = "Source text",
            outputText = outputText,
            generationState = GenerationState.Completed,
            elapsedMs = 5_000,
            completedSummary = CompletedSummarySnapshot(
                resultId = 1L,
                sourceText = "Source text",
                summaryText = outputText,
                durationMs = 5_000,
                refinementOccurred = false,
                formatWarning = null,
                modelName = "local-model.gguf",
                modelSizeBytes = 1024,
            )
        )

    private fun streamingResultState(outputText: String): PocketAiUiState =
        PocketAiUiState(
            destination = PocketAiDestination.Result,
            modelReadiness = readyModel(),
            inputText = "Source text",
            activeSourceText = "Source text",
            outputText = outputText,
            generationState = GenerationState.Generating,
            elapsedMs = 2_000,
        )

    private fun readyModel(): ModelReadiness.Ready =
        ModelReadiness.Ready(
            ImportedModel(
                name = "local-model.gguf",
                sizeBytes = 1024,
                path = "/private/local-model.gguf",
            ),
        )
}
