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
import com.pocketai.offline.inference.LlamaCppSummarizationEngine
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

data class PocketAiUiState(
    val modelReadiness: ModelReadiness = ModelReadiness.NoModel,
    val generationState: GenerationState = GenerationState.Idle,
    val inputText: String = SAMPLE_PARAGRAPH,
    val outputText: String = "",
    val elapsedMs: Long = 0L,
    val formatWarning: String? = null,
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
}

class MainViewModel(
    application: Application,
    private val engine: SummarizationEngine = LlamaCppSummarizationEngine(application),
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(PocketAiUiState())
    val uiState: StateFlow<PocketAiUiState> = _uiState.asStateFlow()

    private val appContext = application.applicationContext
    private val summaryCoordinator = SummaryGenerationCoordinator(engine)
    private var summaryJob: Job? = null
    private var importJob: Job? = null
    private var activeSummaryRequestId: Long = 0L

    fun updateInput(text: String) {
        _uiState.update { it.copy(inputText = text) }
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
                        formatWarning = null
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
        summaryJob = viewModelScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            _uiState.update {
                it.copy(
                    generationState = GenerationState.Generating,
                    outputText = "",
                    elapsedMs = 0L,
                    formatWarning = null
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
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Completed,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    )
                }
            } catch (_: CancellationException) {
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Cancelled,
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    )
                }
            } catch (throwable: Throwable) {
                updateIfCurrent(requestId) {
                    it.copy(
                        generationState = GenerationState.Failed(
                            throwable.userMessage("Summarization failed")
                        ),
                        elapsedMs = SystemClock.elapsedRealtime() - startedAt
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

    override fun onCleared() {
        summaryJob?.cancel()
        importJob?.cancel()
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

    companion object {
        fun factory(application: Application): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(application) as T
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
