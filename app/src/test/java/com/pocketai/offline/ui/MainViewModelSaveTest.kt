package com.pocketai.offline.ui

import android.app.Application
import android.net.Uri
import com.pocketai.offline.history.NewSavedSummary
import com.pocketai.offline.history.SavedSummary
import com.pocketai.offline.history.SummaryHistoryRepository
import com.pocketai.offline.inference.ImportedModelFile
import com.pocketai.offline.inference.LoadedModelInfo
import com.pocketai.offline.inference.ModelImporter
import com.pocketai.offline.inference.ModelSelectionRepository
import com.pocketai.offline.inference.PersistedModelSelection
import com.pocketai.offline.inference.RestoredModelSelection
import com.pocketai.offline.inference.SummarizationEngine
import com.pocketai.offline.summarization.SummaryAttempt
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainViewModelSaveTest {
    @Test
    fun doubleSaveTapsDoNotDuplicateTheDisplayedResult() {
        val repository = FakeHistoryRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.summarize()
        idleMain()

        viewModel.saveCurrentSummary()
        viewModel.saveCurrentSummary()
        idleMain()

        assertEquals(1, repository.savedRequests.size)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun saveUsesCompletedRequestSourceSnapshot() {
        val repository = FakeHistoryRepository()
        val viewModel = viewModel(repository = repository)

        viewModel.updateInput("Source used for generation")
        viewModel.summarize()
        idleMain()
        viewModel.updateInput("Edited after completion")

        viewModel.saveCurrentSummary()
        idleMain()

        assertEquals("Source used for generation", repository.savedRequests.single().sourceText)
    }

    @Test
    fun failedSaveRetainsResultAndPermitsRetry() {
        val repository = FakeHistoryRepository().apply {
            failNextSave = true
        }
        val viewModel = viewModel(repository = repository)

        viewModel.summarize()
        idleMain()
        viewModel.saveCurrentSummary()
        idleMain()

        val afterFailure = viewModel.uiState.value
        assertEquals(GenerationState.Completed, afterFailure.generationState)
        assertEquals("- Saved result", afterFailure.outputText)
        assertTrue(afterFailure.saveState is SaveState.Error)
        assertTrue(afterFailure.canSave)
        assertEquals(0, repository.savedRequests.size)

        viewModel.saveCurrentSummary()
        idleMain()

        assertEquals(1, repository.savedRequests.size)
        assertTrue(viewModel.uiState.value.saveState is SaveState.Saved)
    }

    @Test
    fun completedResultWithFormatWarningCanBeSaved() {
        val repository = FakeHistoryRepository()
        val engine = AttemptEngine(
            SummaryAttempt.Initial to "Summary:\n- One",
            SummaryAttempt.Refinement to "- One\n- Two\n- Three\n- Four"
        )
        val viewModel = viewModel(
            engine = engine,
            repository = repository
        )

        viewModel.summarize()
        idleMain()

        assertTrue(viewModel.uiState.value.formatWarning != null)
        assertTrue(viewModel.uiState.value.canSave)

        viewModel.saveCurrentSummary()
        idleMain()

        assertEquals(viewModel.uiState.value.formatWarning, repository.savedRequests.single().formatWarning)
    }

    @Test
    fun partialAndCancelledGenerationsCannotBeSaved() = runBlocking {
        val repository = FakeHistoryRepository()
        val started = CompletableDeferred<Unit>()
        val engine = BlockingEngine(started)
        val viewModel = viewModel(
            engine = engine,
            repository = repository
        )

        viewModel.summarize()
        idleMain()
        withTimeout(1_000L) { started.await() }

        viewModel.saveCurrentSummary()
        idleMain()
        assertEquals(0, repository.savedRequests.size)

        viewModel.cancelSummary()
        idleMain()
        viewModel.saveCurrentSummary()
        idleMain()

        assertEquals(0, repository.savedRequests.size)
        assertEquals(GenerationState.Cancelled, viewModel.uiState.value.generationState)
    }

    private fun viewModel(
        engine: SummarizationEngine = ScriptedEngine("- Saved result"),
        repository: FakeHistoryRepository = FakeHistoryRepository(),
    ): MainViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val modelFile = File.createTempFile("pocketai-save-ready-", ".gguf").apply {
            writeText("fake model")
            deleteOnExit()
        }
        return MainViewModel(
            application = app,
            engine = engine,
            modelImporter = FakeModelImporter(),
            modelSelectionRepository = FakeModelSelectionRepository(),
            historyRepository = repository,
            nowEpochMs = { 42_000L },
            initialState = PocketAiUiState(
                modelReadiness = ModelReadiness.Ready(
                    ImportedModel(
                        name = "qwen.gguf",
                        sizeBytes = modelFile.length(),
                        path = modelFile.absolutePath
                    )
                ),
                historyState = HistoryUiState.Loaded(emptyList())
            )
        )
    }

    private fun idleMain() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private class ScriptedEngine(
        private val output: String,
    ) : SummarizationEngine {
        override val loadedModel: StateFlow<LoadedModelInfo?> = MutableStateFlow(null)
        override val contextWindowTokens: Int = 2_048
        override val maxGeneratedTokens: Int = 512

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit
        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100
        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            emit(output)
        }

        override fun cancel() = Unit
        override suspend fun unloadModel() = Unit
        override suspend fun close() = Unit
    }

    private class AttemptEngine(
        private vararg val outputs: Pair<SummaryAttempt, String>,
    ) : SummarizationEngine {
        override val loadedModel: StateFlow<LoadedModelInfo?> = MutableStateFlow(null)
        override val contextWindowTokens: Int = 2_048
        override val maxGeneratedTokens: Int = 512

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit
        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100
        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            emit(outputs.first { it.first == attempt }.second)
        }

        override fun cancel() = Unit
        override suspend fun unloadModel() = Unit
        override suspend fun close() = Unit
    }

    private class BlockingEngine(
        private val started: CompletableDeferred<Unit>,
    ) : SummarizationEngine {
        override val loadedModel: StateFlow<LoadedModelInfo?> = MutableStateFlow(null)
        override val contextWindowTokens: Int = 2_048
        override val maxGeneratedTokens: Int = 512

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit
        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100
        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            started.complete(Unit)
            emit("- Partial output")
            awaitCancellation()
        }

        override fun cancel() = Unit
        override suspend fun unloadModel() = Unit
        override suspend fun close() = Unit
    }

    private class FakeModelImporter : ModelImporter {
        override suspend fun import(uri: Uri): ImportedModelFile =
            error("Import is not used by save tests.")

        override suspend fun deleteImportedFile(file: File) = Unit

        override suspend fun deleteObsoleteModels(activeModelPath: String) = Unit
    }

    private class FakeModelSelectionRepository : ModelSelectionRepository {
        override suspend fun restoreSelection(): RestoredModelSelection =
            RestoredModelSelection.None

        override suspend fun selectionForImportedModel(
            importedModelFile: ImportedModelFile,
        ): PersistedModelSelection = PersistedModelSelection(
            file = importedModelFile.file,
            relativeFileName = importedModelFile.file.name,
            displayName = importedModelFile.displayName,
            sizeBytes = importedModelFile.file.length(),
            verified = true
        )

        override suspend fun persistSelection(selection: PersistedModelSelection) = Unit
    }

    private class FakeHistoryRepository : SummaryHistoryRepository {
        private val summaries = MutableStateFlow<List<SavedSummary>>(emptyList())
        val savedRequests = mutableListOf<NewSavedSummary>()
        var failNextSave = false
        private var nextId = 1L

        override fun observeSummaries(): Flow<List<SavedSummary>> = summaries

        override fun observeSummary(id: Long): Flow<SavedSummary?> =
            summaries.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun save(summary: NewSavedSummary): Long {
            if (failNextSave) {
                failNextSave = false
                throw IOException("storage unavailable")
            }

            savedRequests += summary
            val id = nextId++
            summaries.value = listOf(summary.toSavedSummary(id)) + summaries.value
            return id
        }

        override suspend fun delete(id: Long) {
            summaries.value = summaries.value.filterNot { it.id == id }
        }

        private fun NewSavedSummary.toSavedSummary(id: Long): SavedSummary =
            SavedSummary(
                id = id,
                sourceText = sourceText,
                summaryText = summaryText,
                createdAtEpochMs = createdAtEpochMs,
                durationMs = durationMs,
                refinementOccurred = refinementOccurred,
                formatWarning = formatWarning,
                modelName = modelName,
                modelSizeBytes = modelSizeBytes
            )
    }
}
