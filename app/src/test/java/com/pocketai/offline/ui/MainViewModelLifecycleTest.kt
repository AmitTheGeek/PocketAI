package com.pocketai.offline.ui

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pocketai.offline.history.NewSavedSummary
import com.pocketai.offline.history.SavedSummary
import com.pocketai.offline.history.SummaryHistoryRepository
import com.pocketai.offline.inference.ImportedModelFile
import com.pocketai.offline.inference.LoadedModelInfo
import com.pocketai.offline.inference.ModelImporter
import com.pocketai.offline.inference.SummarizationEngine
import com.pocketai.offline.summarization.SummaryAttempt
import java.io.File
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MainViewModelLifecycleTest {
    @Test
    fun viewModelDisposalDoesNotCloseSharedEngineAndNewConsumerCanSummarize() {
        val engine = RecordingEngine()
        val first = viewModel(engine = engine, readyModel = true)

        clear(first)
        idleMain()

        val second = viewModel(engine = engine, readyModel = true)
        second.summarize()
        idleMain()

        assertEquals(0, engine.closeCount)
        assertEquals("- Fresh result", second.uiState.value.outputText)
        assertEquals(GenerationState.Completed, second.uiState.value.generationState)
    }

    @Test
    fun historyOnlyDisposalDoesNotTouchEngine() {
        val engine = RecordingEngine()
        val viewModel = viewModel(engine = engine, readyModel = false)

        viewModel.openHistory()
        clear(viewModel)
        idleMain()

        assertEquals(0, engine.cancelCount)
        assertEquals(0, engine.unloadCount)
        assertEquals(0, engine.closeCount)
        assertEquals(0, engine.loadedPaths.size)
        assertEquals(0, engine.summarizeCount)
    }

    @Test
    fun invalidInputStaysOnInputWithError() {
        val engine = RecordingEngine()
        val viewModel = viewModel(engine = engine, readyModel = true)

        viewModel.updateInput("   ")
        viewModel.summarize()
        idleMain()

        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
        assertTrue(viewModel.uiState.value.generationState is GenerationState.Failed)
        assertEquals(0, engine.summarizeCount)
    }

    @Test
    fun oversizedInputStaysOnInputWithBudgetError() {
        val engine = RecordingEngine(promptTokens = 1_700)
        val viewModel = viewModel(engine = engine, readyModel = true)

        viewModel.updateInput("Large source")
        viewModel.summarize()
        idleMain()

        val failure = viewModel.uiState.value.generationState as GenerationState.Failed
        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
        assertTrue(failure.message.contains("Input is too long"))
        assertEquals(0, engine.summarizeCount)
    }

    @Test
    fun validSubmissionOpensResultAndStartsOneRequest() {
        val engine = RecordingEngine()
        val viewModel = viewModel(engine = engine, readyModel = true)

        viewModel.summarize()
        idleMain()

        assertEquals(PocketAiDestination.Result, viewModel.uiState.value.destination)
        assertEquals(GenerationState.Completed, viewModel.uiState.value.generationState)
        assertEquals(1, engine.summarizeCount)
    }

    @Test
    fun returningToInputPreservesDraft() {
        val viewModel = viewModel(readyModel = true)

        viewModel.updateInput("Draft survives navigation")
        viewModel.summarize()
        idleMain()
        assertEquals(PocketAiDestination.Result, viewModel.uiState.value.destination)

        assertTrue(viewModel.navigateBack())

        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
        assertEquals("Draft survives navigation", viewModel.uiState.value.inputText)
    }

    @Test
    fun backDuringGenerationCancelsAndNewWorkWaitsForTermination() = runBlocking {
        val engine = BlockingThenCompletingEngine()
        val viewModel = viewModel(engine = engine, readyModel = true)

        viewModel.summarize()
        idleMain()
        withTimeout(1_000L) { engine.firstGenerationStarted.await() }

        assertTrue(viewModel.navigateBack())
        idleMain()
        withTimeout(1_000L) { engine.cleanupStarted.await() }

        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
        assertEquals(GenerationState.Stopping, viewModel.uiState.value.generationState)
        viewModel.summarize()
        idleMain()
        assertEquals(1, engine.summarizeCount)

        engine.allowCleanup.complete(Unit)
        idleMain()
        viewModel.summarize()
        idleMain()

        assertEquals(2, engine.summarizeCount)
        assertEquals(GenerationState.Completed, viewModel.uiState.value.generationState)
        assertEquals("- Completed after cancellation", viewModel.uiState.value.outputText)
        assertEquals(1, engine.cancelCount)
    }

    @Test
    fun newViewModelReflectsAlreadyLoadedAppOwnedModel() = runBlocking {
        val engine = RecordingEngine()
        val modelFile = tempModelFile("already-loaded")

        engine.loadModel(modelFile, "already-loaded.gguf")
        val viewModel = viewModel(engine = engine, readyModel = false)

        val readiness = viewModel.uiState.value.modelReadiness as ModelReadiness.Ready
        assertEquals("already-loaded.gguf", readiness.model.name)
        assertEquals(modelFile.absolutePath, readiness.model.path)
    }

    @Test
    fun importsDifferentModelsThatShareTheSameDisplayFilename() {
        val engine = RecordingEngine()
        val firstFile = tempModelFile("first")
        val secondFile = tempModelFile("second")
        val importer = QueueModelImporter(
            ImportedModelFile("qwen.gguf", firstFile),
            ImportedModelFile("qwen.gguf", secondFile)
        )
        val viewModel = viewModel(
            engine = engine,
            modelImporter = importer,
            readyModel = false
        )

        viewModel.importModel(Uri.parse("content://models/first"))
        idleMain()
        viewModel.importModel(Uri.parse("content://models/second"))
        idleMain()

        val ready = viewModel.uiState.value.modelReadiness as ModelReadiness.Ready
        assertEquals("qwen.gguf", ready.model.name)
        assertEquals(secondFile.absolutePath, ready.model.path)
        assertEquals(listOf(firstFile.absolutePath, secondFile.absolutePath), engine.loadedPaths)
        assertEquals(listOf(firstFile.absolutePath, secondFile.absolutePath), importer.obsoleteCleanupRoots)
    }

    @Test
    fun failedModelLoadCanBeFollowedBySuccessfulImport() {
        val firstFile = tempModelFile("bad")
        val secondFile = tempModelFile("good")
        val engine = RecordingEngine(failLoadPaths = setOf(firstFile.absolutePath))
        val importer = QueueModelImporter(
            ImportedModelFile("broken.gguf", firstFile),
            ImportedModelFile("valid.gguf", secondFile)
        )
        val viewModel = viewModel(
            engine = engine,
            modelImporter = importer,
            readyModel = false
        )

        viewModel.importModel(Uri.parse("content://models/bad"))
        idleMain()
        assertTrue(viewModel.uiState.value.modelReadiness is ModelReadiness.ModelError)
        assertEquals(listOf(firstFile.absolutePath), importer.deletedFiles)

        viewModel.importModel(Uri.parse("content://models/good"))
        idleMain()

        val ready = viewModel.uiState.value.modelReadiness as ModelReadiness.Ready
        assertEquals("valid.gguf", ready.model.name)
        assertEquals(secondFile.absolutePath, ready.model.path)
        assertEquals(listOf(firstFile.absolutePath, secondFile.absolutePath), engine.loadedPaths)
    }

    @Test
    fun delayedDeletionDoesNotOverrideNewerNavigation() = runBlocking {
        val repository = FakeHistoryRepository(
            initialSummaries = listOf(savedSummary(id = 7L)),
            delayDelete = true
        )
        val viewModel = viewModel(repository = repository, readyModel = false)

        viewModel.openSummaryDetail(7L)
        idleMain()
        viewModel.deleteSelectedSummary()
        idleMain()
        withTimeout(1_000L) { repository.deleteStarted.await() }

        viewModel.openInput()
        repository.allowDelete.complete(Unit)
        idleMain()

        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
    }

    @Test
    fun systemBackRoutesDetailHistoryInputThenFallsThrough() {
        val viewModel = viewModel(readyModel = false)

        assertFalse(viewModel.navigateBack())

        viewModel.openHistory()
        assertTrue(viewModel.navigateBack())
        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)

        viewModel.openSummaryDetail(1L)
        assertTrue(viewModel.navigateBack())
        assertEquals(PocketAiDestination.History, viewModel.uiState.value.destination)
        assertTrue(viewModel.navigateBack())
        assertEquals(PocketAiDestination.Input, viewModel.uiState.value.destination)
        assertFalse(viewModel.navigateBack())
    }

    private fun viewModel(
        engine: SummarizationEngine = RecordingEngine(),
        modelImporter: ModelImporter = QueueModelImporter(),
        repository: SummaryHistoryRepository = FakeHistoryRepository(),
        readyModel: Boolean,
    ): MainViewModel {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val modelState = if (readyModel) {
            ModelReadiness.Ready(
                ImportedModel(
                    name = "qwen.gguf",
                    sizeBytes = 1_024L,
                    path = "/models/qwen.gguf"
                )
            )
        } else {
            ModelReadiness.NoModel
        }
        return MainViewModel(
            application = app,
            engine = engine,
            modelImporter = modelImporter,
            historyRepository = repository,
            initialState = PocketAiUiState(
                modelReadiness = modelState,
                historyState = HistoryUiState.Loaded(emptyList())
            )
        )
    }

    private fun clear(viewModel: MainViewModel) {
        MainViewModel::class.java
            .getDeclaredMethod("onCleared")
            .apply { isAccessible = true }
            .invoke(viewModel)
    }

    private fun idleMain() {
        shadowOf(android.os.Looper.getMainLooper()).idle()
    }

    private fun tempModelFile(label: String): File =
        File.createTempFile("pocketai-$label-", ".gguf").apply {
            writeText("fake-$label")
            deleteOnExit()
        }

    private class RecordingEngine(
        private val failLoadPaths: Set<String> = emptySet(),
        private val promptTokens: Int = 100,
    ) : SummarizationEngine {
        private val mutableLoadedModel = MutableStateFlow<LoadedModelInfo?>(null)
        override val loadedModel: StateFlow<LoadedModelInfo?> = mutableLoadedModel
        override val contextWindowTokens: Int = 2_048
        override val maxGeneratedTokens: Int = 512
        val loadedPaths = mutableListOf<String>()
        var summarizeCount = 0
        var cancelCount = 0
        var unloadCount = 0
        var closeCount = 0

        override suspend fun loadModel(modelFile: File, displayName: String) {
            loadedPaths += modelFile.absolutePath
            if (modelFile.absolutePath in failLoadPaths) {
                throw IOException("native load failed")
            }
            mutableLoadedModel.value = LoadedModelInfo(
                name = displayName,
                sizeBytes = modelFile.length(),
                path = modelFile.absolutePath
            )
        }

        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = promptTokens

        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            summarizeCount += 1
            emit("- Fresh result")
        }

        override fun cancel() {
            cancelCount += 1
        }

        override suspend fun unloadModel() {
            unloadCount += 1
            mutableLoadedModel.value = null
        }

        override suspend fun close() {
            closeCount += 1
            mutableLoadedModel.value = null
        }
    }

    private class BlockingThenCompletingEngine : SummarizationEngine {
        override val loadedModel: StateFlow<LoadedModelInfo?> = MutableStateFlow(null)
        override val contextWindowTokens: Int = 2_048
        override val maxGeneratedTokens: Int = 512
        val firstGenerationStarted = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val allowCleanup = CompletableDeferred<Unit>()
        var summarizeCount = 0
        var cancelCount = 0

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit

        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100

        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            summarizeCount += 1
            if (summarizeCount == 1) {
                firstGenerationStarted.complete(Unit)
                emit("- Partial")
                try {
                    awaitCancellation()
                } finally {
                    cleanupStarted.complete(Unit)
                    withContext(NonCancellable) {
                        allowCleanup.await()
                    }
                }
            } else {
                emit("- Completed after cancellation")
            }
        }

        override fun cancel() {
            cancelCount += 1
        }

        override suspend fun unloadModel() = Unit

        override suspend fun close() = Unit
    }

    private class QueueModelImporter(
        vararg importedModels: ImportedModelFile,
    ) : ModelImporter {
        private val queuedModels = ArrayDeque(importedModels.toList())
        val deletedFiles = mutableListOf<String>()
        val obsoleteCleanupRoots = mutableListOf<String>()

        override suspend fun import(uri: Uri): ImportedModelFile =
            queuedModels.removeFirstOrNull() ?: error("No model queued for $uri")

        override suspend fun deleteImportedFile(file: File) {
            deletedFiles += file.absolutePath
        }

        override suspend fun deleteObsoleteModels(activeModelPath: String) {
            obsoleteCleanupRoots += activeModelPath
        }
    }

    private class FakeHistoryRepository(
        initialSummaries: List<SavedSummary> = emptyList(),
        private val delayDelete: Boolean = false,
    ) : SummaryHistoryRepository {
        private val summaries = MutableStateFlow(initialSummaries)
        val deleteStarted = CompletableDeferred<Unit>()
        val allowDelete = CompletableDeferred<Unit>()

        override fun observeSummaries(): Flow<List<SavedSummary>> = summaries

        override fun observeSummary(id: Long): Flow<SavedSummary?> =
            summaries.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun save(summary: NewSavedSummary): Long = error("Save is not used.")

        override suspend fun delete(id: Long) {
            deleteStarted.complete(Unit)
            if (delayDelete) allowDelete.await()
            summaries.value = summaries.value.filterNot { it.id == id }
        }
    }

    private fun savedSummary(id: Long): SavedSummary =
        SavedSummary(
            id = id,
            sourceText = "source",
            summaryText = "- summary",
            createdAtEpochMs = 1_000L,
            durationMs = 4_000L,
            refinementOccurred = false,
            formatWarning = null,
            modelName = "qwen.gguf",
            modelSizeBytes = 1_024L
        )
}
