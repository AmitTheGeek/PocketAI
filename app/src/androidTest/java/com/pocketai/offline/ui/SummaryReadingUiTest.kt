package com.pocketai.offline.ui

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
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
                SummarizerScreen(
                    state = PocketAiUiState(
                        modelReadiness = readyModel(),
                        inputText = "Source text",
                        outputText = longSummary,
                        generationState = GenerationState.Completed,
                        elapsedMs = 5000,
                    ),
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

        composeRule
            .onNodeWithTag(finalLineTag, useUnmergedTree = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun expandedEditorKeepsDraftAfterClosingAndReopening() {
        var expanded by mutableStateOf(false)
        var draft by mutableStateOf("First line")

        composeRule.setContent {
            PocketAiTheme {
                if (expanded) {
                    ExpandedSourceEditor(
                        text = draft,
                        enabled = true,
                        onTextChange = { draft = it },
                        onDone = { expanded = false },
                    )
                } else {
                    Button(onClick = { expanded = true }) {
                        Text("Open editor")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Open editor").performClick()
        composeRule.onNodeWithTag("expandedSourceTextField").performTextInput("\nSecond line")
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Open editor").performClick()

        composeRule
            .onNodeWithTag("expandedSourceTextField")
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
                SummarySection(
                    state = PocketAiUiState(
                        outputText = completeOutput,
                        generationState = GenerationState.Cancelled,
                    ),
                    showJumpToLatest = false,
                    onJumpToLatest = {},
                    onCopySummary = { copied = it },
                    onSave = {},
                )
            }
        }

        composeRule.onNodeWithTag("summaryCopyButton").performClick()

        composeRule.runOnIdle {
            assertEquals(completeOutput, copied)
        }
    }

    private fun readyModel(): ModelReadiness.Ready =
        ModelReadiness.Ready(
            ImportedModel(
                name = "local-model.gguf",
                sizeBytes = 1024,
                path = "/private/local-model.gguf",
            ),
        )
}
