package com.pocketai.offline.inference

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class PersistedModelSelection(
    val file: File,
    val relativeFileName: String,
    val displayName: String?,
    val sizeBytes: Long,
    val verified: Boolean,
) {
    val label: String
        get() = displayName ?: relativeFileName
}

sealed interface RestoredModelSelection {
    data object None : RestoredModelSelection
    data class Available(val selection: PersistedModelSelection) : RestoredModelSelection
    data class Unavailable(val message: String) : RestoredModelSelection
}

interface ModelSelectionRepository {
    suspend fun restoreSelection(): RestoredModelSelection
    suspend fun selectionForImportedModel(importedModelFile: ImportedModelFile): PersistedModelSelection
    suspend fun persistSelection(selection: PersistedModelSelection)
}

class DataStoreModelSelectionRepository(
    context: Context,
    private val dataStore: DataStore<Preferences> = context.applicationContext.modelSelectionDataStore,
) : ModelSelectionRepository {
    private val appContext = context.applicationContext
    private val modelsDirectory: File
        get() = File(appContext.filesDir, PRIVATE_MODELS_DIR)

    override suspend fun restoreSelection(): RestoredModelSelection = withContext(Dispatchers.IO) {
        val preferences = dataStore.data.first()
        val relativeFileName = preferences[RELATIVE_FILE_NAME_KEY]
        val expectedSize = preferences[SIZE_BYTES_KEY]
        val displayName = preferences[DISPLAY_NAME_KEY]

        if (relativeFileName.isNullOrBlank() || expectedSize == null) {
            return@withContext recoverLegacySelection()
        }

        val file = resolveStoredFile(relativeFileName)
            ?: return@withContext RestoredModelSelection.Unavailable(
                "Remembered model reference is invalid. Import the GGUF again."
            )
        if (!file.isFile || !file.canRead()) {
            return@withContext RestoredModelSelection.Unavailable(
                "Remembered model file is missing. Import the GGUF again."
            )
        }
        if (file.length() != expectedSize) {
            return@withContext RestoredModelSelection.Unavailable(
                "Remembered model file changed size. Import the GGUF again."
            )
        }

        RestoredModelSelection.Available(
            PersistedModelSelection(
                file = file,
                relativeFileName = relativeFileName,
                displayName = displayName,
                sizeBytes = expectedSize,
                verified = true
            )
        )
    }

    override suspend fun selectionForImportedModel(
        importedModelFile: ImportedModelFile,
    ): PersistedModelSelection = withContext(Dispatchers.IO) {
        val file = importedModelFile.file.canonicalFile
        val relativeFileName = relativeFileNameFor(file)
        PersistedModelSelection(
            file = file,
            relativeFileName = relativeFileName,
            displayName = importedModelFile.displayName,
            sizeBytes = file.length(),
            verified = true
        )
    }

    override suspend fun persistSelection(selection: PersistedModelSelection) {
        withContext(Dispatchers.IO) {
            dataStore.edit { preferences ->
                preferences[RELATIVE_FILE_NAME_KEY] = selection.relativeFileName
                preferences[SIZE_BYTES_KEY] = selection.sizeBytes
                if (selection.displayName.isNullOrBlank()) {
                    preferences.remove(DISPLAY_NAME_KEY)
                } else {
                    preferences[DISPLAY_NAME_KEY] = selection.displayName
                }
            }
        }
    }

    private fun recoverLegacySelection(): RestoredModelSelection {
        val candidates = modelsDirectory
            .listFiles()
            ?.filter { it.isFile && it.extension.equals("gguf", ignoreCase = true) }
            .orEmpty()

        return when (candidates.size) {
            0 -> RestoredModelSelection.None
            1 -> {
                val file = candidates.single().canonicalFile
                RestoredModelSelection.Available(
                    PersistedModelSelection(
                        file = file,
                        relativeFileName = relativeFileNameFor(file),
                        displayName = null,
                        sizeBytes = file.length(),
                        verified = false
                    )
                )
            }
            else -> RestoredModelSelection.Unavailable(
                "Multiple private GGUF files were found. Import or change model to choose one."
            )
        }
    }

    private fun resolveStoredFile(relativeFileName: String): File? {
        if (relativeFileName.contains(File.separatorChar)) return null
        if (File(relativeFileName).isAbsolute) return null

        val directory = modelsDirectory.canonicalFile
        val file = File(directory, relativeFileName).canonicalFile
        return if (file.parentFile?.canonicalPath == directory.canonicalPath) file else null
    }

    private fun relativeFileNameFor(file: File): String {
        val directory = modelsDirectory.also { it.mkdirs() }.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.parentFile?.canonicalPath == directory.canonicalPath) {
            "Model file is outside app-private model storage."
        }
        return canonicalFile.name
    }

    private companion object {
        val RELATIVE_FILE_NAME_KEY = stringPreferencesKey("model_relative_file_name")
        val DISPLAY_NAME_KEY = stringPreferencesKey("model_display_name")
        val SIZE_BYTES_KEY = longPreferencesKey("model_size_bytes")
    }
}

internal const val PRIVATE_MODELS_DIR = "models"

private val Context.modelSelectionDataStore by preferencesDataStore(name = "model_selection")
