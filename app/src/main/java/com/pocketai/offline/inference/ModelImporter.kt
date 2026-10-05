package com.pocketai.offline.inference

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import com.arm.aichat.gguf.GgufMetadataReader
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedModelFile(
    val displayName: String,
    val file: File,
)

interface ModelImporter {
    suspend fun import(uri: Uri): ImportedModelFile
    suspend fun deleteImportedFile(file: File)
    suspend fun deleteObsoleteModels(activeModelPath: String)
}

class AppPrivateModelImporter(
    context: Context,
) : ModelImporter {
    private val appContext = context.applicationContext

    override suspend fun import(uri: Uri): ImportedModelFile = withContext(Dispatchers.IO) {
        val reader = GgufMetadataReader.create()
        require(reader.ensureSourceFileFormat(appContext, uri)) {
            "Selected file is not a GGUF model."
        }

        val displayName = appContext.contentResolver.displayName(uri)
            ?: "model-${System.currentTimeMillis()}.gguf"
        val safeDisplayName = displayName.toSafeModelFileName()
        val modelsDir = File(appContext.filesDir, MODELS_DIR).also { it.mkdirs() }
        val destination = File(modelsDir, "${UUID.randomUUID()}-$safeDisplayName")
        val temp = File.createTempFile("import-", ".gguf.tmp", modelsDir)

        try {
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Unable to open selected model." }
                FileOutputStream(temp).use { output -> input.copyTo(output) }
            }
            check(temp.renameTo(destination)) { "Unable to finish model copy." }
            ImportedModelFile(
                displayName = displayName,
                file = destination
            )
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    override suspend fun deleteImportedFile(file: File) {
        withContext(Dispatchers.IO) {
            if (file.exists()) file.delete()
        }
    }

    override suspend fun deleteObsoleteModels(activeModelPath: String) {
        withContext(Dispatchers.IO) {
            val activeFile = File(activeModelPath)
            val modelsDir = activeFile.parentFile ?: return@withContext
            modelsDir.listFiles()
                ?.filter { it.isFile && it.absolutePath != activeFile.absolutePath }
                ?.forEach { it.delete() }
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

    private companion object {
        private const val MODELS_DIR = "models"
    }
}
