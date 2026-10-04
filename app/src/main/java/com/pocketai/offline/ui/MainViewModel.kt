package com.pocketai.offline.ui

import android.app.Application
import android.database.Cursor
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arm.aichat.gguf.GgufMetadataReader
import com.pocketai.offline.PocketAiApplication
import com.pocketai.offline.PocketAiContainer
import com.pocketai.offline.history.NewSavedSummary
import com.pocketai.offline.history.SavedSummary
import com.pocketai.offline.history.SummaryHistoryRepository
import com.pocketai.offline.inference.SummarizationEngine
import com.pocketai.offline.summarization.SummaryGenerationCoordinator
import com.pocketai.offline.summarization.SummaryGenerationEvent
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class ImportedModel(
    val name: String,
    val sizeBytes: Long,
    val path: String,
)

sealed interface ModelReadiness {
    data object NoModel : ModelReadiness
    data object Importing : ModelReadiness
    data object Loading : ModelReadiness
    data class Ready(val model: ImportedModel) : ModelReadiness
    data class ModelError(val message: String) : ModelReadiness
}

sealed interface GenerationState {
    data object Idle : GenerationState
    data object Generating : GenerationState
    data object Refining : GenerationState
    data object Completed : GenerationState
    data object Cancelled : GenerationState
    data class Failed(val message: String) : GenerationState
}

sealed interface PocketAiDestination {
    data object Summarizer : PocketAiDestination
    data object History : PocketAiDestination
    data class Detail(val summaryId: Long) : PocketAiDestination
}

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Loaded(val items: List<SavedSummary>) : HistoryUiState
    data class Error(val message: String) : HistoryUiState
}

sealed interface DetailUiState {
    data object Idle : DetailUiState
    data object Loading : DetailUiState
    data class Loaded(
        val summary: SavedSummary,
        val isDeleting: Boolean = false,
        val deleteError: String? = null,
    ) : DetailUiState
    data object NotFound : DetailUiState
    data class Error(val message: String) : DetailUiState
}

sealed interface SaveState {
    data object Idle : SaveState
    data class Saving(val resultId: Long) : SaveState
    data class Saved(val resultId: Long, val savedSummaryId: Long) : SaveState
    data class Error(val resultId: Long, val message: String) : SaveState
}

data class CompletedSummarySnapshot(
    val resultId: Long,
    val sourceText: String,
    val summaryText: String,
    val durationMs: Long,
    val refinementOccurred: Boolean,
    val formatWarning: String?,
    val modelName: String?,
    val modelSizeBytes: Long?,
) {
    fun toNewSavedSummary(createdAtEpochMs: Long): NewSavedSummary =
        NewSavedSummary(
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

data class PocketAiUiState(
    val destination: PocketAiDestination = PocketAiDestination.Summarizer,
    val modelReadiness: ModelReadiness = ModelReadiness.NoModel,
    val generationState: GenerationState = GenerationState.Idle,
    val inputText: String = SAMPLE_PARAGRAPH,
    val outputText: String = "",
    val elapsedMs: Long = 0L,
    val formatWarning: String? = null,
    val completedSummary: CompletedSummarySnapshot? = null,
    val saveState: SaveState = SaveState.Idle,
    val historyState: HistoryUiState = HistoryUiState.Loading,
    val detailState: DetailUiState = DetailUiState.Idle,
) {
    val model: ImportedModel?
        get() = (modelReadiness as? ModelReadiness.Ready)?.model

    val modelLabel: String
        get() = when (val state = modelReadiness) {
            ModelReadiness.NoModel -> "No GGUF imported"
            ModelReadiness.Importing -> "Importing GGUF"
            ModelReadiness.Loading -> "Loading model"
            is ModelReadiness.Ready -> "${state.model.name}  ${formatBytesForState(state.model.sizeBytes)}"
            is ModelReadiness.ModelError -> "Model error"
        }

    val loadingMessage: String?
        get() = when {
            modelReadiness is ModelReadiness.Importing -> "Importing GGUF"
            modelReadiness is ModelReadiness.Loading -> "Loading model"
            generationState is GenerationState.Refining -> "Refining summary..."
            else -> null
        }

    val errorMessage: String?
        get() = when {
            modelReadiness is ModelReadiness.ModelError -> modelReadiness.message
            generationState is GenerationState.Failed -> generationState.message
            else -> null
        }

    val warningMessage: String?
        get() = formatWarning

    val saveMessage: String?
        get() = when (val state = saveState) {
            is SaveState.Saving -> if (state.resultId == completedSummary?.resultId) {
                "Saving summary..."
            } else {
                null
            }
            is SaveState.Saved -> if (state.resultId == completedSummary?.resultId) {
                "Summary saved."
            } else {
                null
            }
            is SaveState.Error -> if (state.resultId == completedSummary?.resultId) {
                state.message
            } else {
                null
            }
            SaveState.Idle -> null
        }

    val isGenerating: Boolean
        get() = generationState.isActive

    val isBusy: Boolean
        get() = modelReadiness.isChanging || generationState.isActive

    val canImport: Boolean
        get() = !isBusy

    val canSummarize: Boolean
        get() = modelReadiness is ModelReadiness.Ready &&
            inputText.isNotBlank() &&
            !isBusy

    val canSave: Boolean
        get() {
            val snapshot = completedSummary ?: return false
            if (generationState !is GenerationState.Completed) return false
            return when (val state = saveState) {
                is SaveState.Saving -> state.resultId != snapshot.resultId
                is SaveState.Saved -> state.resultId != snapshot.resultId
                is SaveState.Error,
                SaveState.Idle -> true
            }
        }
}

class MainViewModel(
    application: Application,
    private val engine: SummarizationEngine,
    private val historyRepository: SummaryHistoryRepository,
    private val nowEpochMs: () -> Long = { System.currentTimeMillis() },
    initialState: PocketAiUiState = PocketAiUiState(),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(initialState)
    val uiState: StateFlow<PocketAiUiState> = _uiState.asStateFlow()

    private val appContext = application.applicationContext
    private val summaryCoordinator = SummaryGenerationCoordinator(engine)
    private var summaryJob: Job? = null
    private var importJob: Job? = null
    private var detailJob: Job? = null
    private var activeSummaryRequestId: Long = 0L

    init {
        observeHistory()
    }

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun openSummarizer() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                destination = PocketAiDestination.Summarizer,
                detailState = DetailUiState.Idle
            )
        }
    }

    fun openHistory() {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                destination = PocketAiDestination.History,
                detailState = DetailUiState.Idle
            )
        }
    }

    fun openSummaryDetail(summaryId: Long) {
        detailJob?.cancel()
        _uiState.update {
            it.copy(
                destination = PocketAiDestination.Detail(summaryId),
                detailState = DetailUiState.Loading
            )
        }
        detailJob = viewModelScope.launch {
            historyRepository.observeSummary(summaryId)
                .catch { throwable ->
                    updateDetailIfCurrent(summaryId) {
                        DetailUiState.Error(throwable.userMessage("Unable to load saved summary"))
                    }
                }
                .collect { summary ->
                    updateDetailIfCurrent(summaryId) {
                        if (summary == null) {
                            DetailUiState.NotFound
                        } else {
                            DetailUiState.Loaded(summary)
                        }
                    }
                }
        }
    }

    fun importModel(uri: Uri) {
        if (!_uiState.value.canImport) return

        importJob?.cancel()
        importJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    modelReadiness = ModelReadiness.Importing,
                    generationState = GenerationState.Idle,
                    formatWarning = null
                )
            }

            runCatching {
                val modelFile = copyModelToPrivateStorage(uri)
                _uiState.update { it.copy(modelReadiness = ModelReadiness.Loading) }
                engine.loadModel(modelFile)
                ImportedModel(
                    name = modelFile.name,
                    sizeBytes = modelFile.length(),
                    path = modelFile.absolutePath
                )
            }.onSuccess { model ->
                _uiState.update {
                    it.copy(
                        modelReadiness = ModelReadiness.Ready(model),
                        generationState = GenerationState.Idle,
                        outputText = "",
                        elapsedMs = 0L,
                        formatWarning = null,
                        completedSummary = null,
                        saveState = SaveState.Idle
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        modelReadiness = ModelReadiness.ModelError(
                            throwable.userMessage("Model import failed")
                        )
                    )
                }
            }
        }
    }

    fun summarize() {
        val current = _uiState.value
        if (current.isBusy) return
        if (current.modelReadiness !is ModelReadiness.Ready) {
            _uiState.update {
                it.copy(generationState = GenerationState.Failed("Import a GGUF model first."))
            }
            return
        }

        val paragraph = current.inputText.trim()
        if (paragraph.isEmpty()) {
            _uiState.update {
                it.copy(generationState = GenerationState.Failed("Enter text to summarize."))
            }
            return
        }

        val requestId = ++activeSummaryRequestId
        val model = current.model
        summaryJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            var refinementOccurred = false
            _uiState.update {
                it.copy(
                    generationState = GenerationState.Generating,
                    outputText = "",
                    elapsedMs = 0L,
                    formatWarning = null,
                    completedSummary = null,
                    saveState = SaveState.Idle
                )
            }

            val timer = launch {
                while (true) {
                    delay(250)
                    updateIfCurrent(requestId) {
                        it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                    }
                }
            }

            try {
                summaryCoordinator.summarize(paragraph).collect { event ->
                    if (!isCurrentRequest(requestId)) return@collect
                    when (event) {
                        is SummaryGenerationEvent.Token -> {
                            updateIfCurrent(requestId) {
                                it.copy(
                                    outputText = it.outputText + event.value,
                                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                                )
                            }
                        }
                        SummaryGenerationEvent.Refining -> {
                            refinementOccurred = true
                            updateIfCurrent(requestId) {
                                it.copy(
                                    generationState = GenerationState.Refining,
                                    outputText = "",
                                    formatWarning = null,
                                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                                )
                            }
                        }
                        is SummaryGenerationEvent.FormatWarning -> {
                            updateIfCurrent(requestId) {
                                it.copy(
                                    formatWarning = event.message,
                                    elapsedMs = SystemClock.elapsedRealtime() - startedAt
                                )
                            }
                        }
                    }
                }
                val finalElapsed = SystemClock.elapsedRealtime() - startedAt
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Completed,
                        elapsedMs = finalElapsed,
                        completedSummary = CompletedSummarySnapshot(
                            resultId = requestId,
                            sourceText = paragraph,
                            summaryText = it.outputText,
                            durationMs = finalElapsed,
                            refinementOccurred = refinementOccurred,
                            formatWarning = it.formatWarning,
                            modelName = model?.name,
                            modelSizeBytes = model?.sizeBytes
                        )
                    )
                }
            } catch (_: CancellationException) {
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Cancelled,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        completedSummary = null,
                        saveState = SaveState.Idle
                    )
                }
            } catch (throwable: Throwable) {
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Failed(
                            throwable.userMessage("Summarization failed")
                        ),
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt,
                        completedSummary = null,
                        saveState = SaveState.Idle
                    )
                }
            } finally {
                timer.cancel()
                updateIfCurrent(requestId) {
                    it.copy(elapsedMs = SystemClock.elapsedRealtime() - startedAt)
                }
                if (isCurrentRequest(requestId)) summaryJob = null
            }
        }
    }

    fun cancelSummary() {
        engine.cancel()
        summaryJob?.cancel(CancellationException("User cancelled summarization."))
    }

    fun saveCurrentSummary() {
        val state = _uiState.value
        val snapshot = state.completedSummary ?: return
        if (!state.canSave) return

        _uiState.update {
            if (it.completedSummary?.resultId == snapshot.resultId) {
                it.copy(saveState = SaveState.Saving(snapshot.resultId))
            } else {
                it
            }
        }

        viewModelScope.launch {
            runCatching {
                historyRepository.save(snapshot.toNewSavedSummary(nowEpochMs()))
            }.onSuccess { savedSummaryId ->
                _uiState.update {
                    if (it.completedSummary?.resultId == snapshot.resultId) {
                        it.copy(
                            saveState = SaveState.Saved(
                                resultId = snapshot.resultId,
                                savedSummaryId = savedSummaryId
                            )
                        )
                    } else {
                        it
                    }
                }
            }.onFailure { throwable ->
                _uiState.update {
                    if (it.completedSummary?.resultId == snapshot.resultId) {
                        it.copy(
                            saveState = SaveState.Error(
                                resultId = snapshot.resultId,
                                message = throwable.userMessage("Save failed")
                            )
                        )
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun deleteSelectedSummary() {
        val detail = _uiState.value.detailState as? DetailUiState.Loaded ?: return
        if (detail.isDeleting) return
        val summaryId = detail.summary.id

        updateDetailIfCurrent(summaryId) {
            detail.copy(isDeleting = true, deleteError = null)
        }

        viewModelScope.launch {
            runCatching {
                historyRepository.delete(summaryId)
            }.onSuccess {
                openHistory()
            }.onFailure { throwable ->
                updateDetailIfCurrent(summaryId) { current ->
                    when (current) {
                        is DetailUiState.Loaded -> current.copy(
                            isDeleting = false,
                            deleteError = throwable.userMessage("Delete failed")
                        )
                        else -> current
                    }
                }
            }
        }
    }

    override fun onCleared() {
        summaryJob?.cancel()
        importJob?.cancel()
        detailJob?.cancel()
        Thread {
            runBlocking {
                engine.close()
            }
        }.apply {
            name = "PocketAI-native-cleanup"
            start()
        }
        super.onCleared()
    }

    private fun observeHistory() {
        viewModelScope.launch {
            historyRepository.observeSummaries()
                .catch { throwable ->
                    _uiState.update {
                        it.copy(
                            historyState = HistoryUiState.Error(
                                throwable.userMessage("Unable to load history")
                            )
                        )
                    }
                }
                .collect { summaries ->
                    _uiState.update {
                        it.copy(historyState = HistoryUiState.Loaded(summaries))
                    }
                }
        }
    }

    private suspend fun copyModelToPrivateStorage(uri: Uri): File = withContext(Dispatchers.IO) {
        val reader = GgufMetadataReader.create()
        require(reader.ensureSourceFileFormat(appContext, uri)) { "Selected file is not a GGUF model." }

        val displayName = appContext.contentResolver.displayName(uri)
            ?: "model-${System.currentTimeMillis()}.gguf"
        val safeName = displayName.toSafeModelFileName()
        val modelsDir = File(appContext.filesDir, MODELS_DIR).also { it.mkdirs() }
        val destination = File(modelsDir, safeName)
        val temp = File.createTempFile("import-", ".gguf.tmp", modelsDir)

        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected model." }
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            if (destination.exists()) destination.delete()
            check(temp.renameTo(destination)) { "Unable to finish model copy." }
            destination
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun android.content.ContentResolver.displayName(uri: Uri): String? {
        val cursor: Cursor? = query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        return cursor?.use {
            if (it.moveToFirst()) {
                it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else {
                null
            }
        }
    }

    private fun String.toSafeModelFileName(): String {
        val withExtension = if (endsWith(".gguf", ignoreCase = true)) this else "$this.gguf"
        return withExtension.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }

    private fun Throwable.userMessage(prefix: String): String =
        message?.let { "$prefix: $it" } ?: prefix

    private fun isCurrentRequest(requestId: Long): Boolean =
        requestId == activeSummaryRequestId

    private inline fun updateIfCurrent(
        requestId: Long,
        crossinline transform: (PocketAiUiState) -> PocketAiUiState,
    ) {
        _uiState.update { current ->
            if (isCurrentRequest(requestId)) transform(current) else current
        }
    }

    private inline fun updateDetailIfCurrent(
        summaryId: Long,
        crossinline transform: (DetailUiState) -> DetailUiState,
    ) {
        _uiState.update { current ->
            val destination = current.destination as? PocketAiDestination.Detail
            if (destination?.summaryId == summaryId) {
                current.copy(detailState = transform(current.detailState))
            } else {
                current
            }
        }
    }

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val container = (application as? PocketAiApplication)?.container
                        ?: PocketAiContainer(application)
                    return MainViewModel(
                        application = application,
                        engine = container.summarizationEngine,
                        historyRepository = container.historyRepository
                    ) as T
                }
            }

        private const val MODELS_DIR = "models"
    }
}

private val ModelReadiness.isChanging: Boolean
    get() = this is ModelReadiness.Importing || this is ModelReadiness.Loading

private val GenerationState.isActive: Boolean
    get() = this is GenerationState.Generating || this is GenerationState.Refining

private fun formatBytesForState(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val mib = bytes / (1024.0 * 1024.0)
    return if (mib >= 1024.0) {
        "%.2f GB".format(mib / 1024.0)
    } else {
        "%.0f MB".format(mib)
    }
}

private const val SAMPLE_PARAGRAPH =
    "PocketAI is preparing an offline Android prototype for field researchers who often work " +
        "without reliable connectivity. The first build focuses on importing a local GGUF model, " +
        "summarizing a pasted paragraph on-device, streaming the result as tokens arrive, and " +
        "letting the user cancel a slow request without losing control of the app."
