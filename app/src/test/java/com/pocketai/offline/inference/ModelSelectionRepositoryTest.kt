package com.pocketai.offline.inference

import android.app.Application
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModelSelectionRepositoryTest {
    @Test
    fun persistedSelectionRestoresReadableModelWithExpectedSize() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = repository(app)
        val modelFile = modelFile(app, "stored.gguf", "fake model")

        val selection = repository.selectionForImportedModel(
            ImportedModelFile(displayName = "Qwen stored.gguf", file = modelFile)
        )
        repository.persistSelection(selection)

        val restored = repository.restoreSelection() as RestoredModelSelection.Available

        assertEquals(modelFile.canonicalPath, restored.selection.file.canonicalPath)
        assertEquals("stored.gguf", restored.selection.relativeFileName)
        assertEquals("Qwen stored.gguf", restored.selection.displayName)
        assertEquals(modelFile.length(), restored.selection.sizeBytes)
        assertTrue(restored.selection.verified)
    }

    @Test
    fun missingPersistedFileRestoresUnavailableState() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = repository(app)
        val modelFile = modelFile(app, "missing.gguf", "fake model")
        val selection = repository.selectionForImportedModel(
            ImportedModelFile(displayName = "missing.gguf", file = modelFile)
        )
        repository.persistSelection(selection)
        assertTrue(modelFile.delete())

        val restored = repository.restoreSelection()

        assertTrue(restored is RestoredModelSelection.Unavailable)
        assertTrue((restored as RestoredModelSelection.Unavailable).message.contains("missing"))
    }

    @Test
    fun sizeMismatchRestoresUnavailableState() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = repository(app)
        val modelFile = modelFile(app, "changed.gguf", "fake model")
        val selection = repository.selectionForImportedModel(
            ImportedModelFile(displayName = "changed.gguf", file = modelFile)
        )
        repository.persistSelection(selection)
        modelFile.writeText("different model bytes")

        val restored = repository.restoreSelection()

        assertTrue(restored is RestoredModelSelection.Unavailable)
        assertTrue((restored as RestoredModelSelection.Unavailable).message.contains("changed size"))
    }

    @Test
    fun singleLegacyGgufIsRecoveredAsUnverifiedWithoutInventingDisplayName() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = repository(app)
        val modelFile = modelFile(app, "legacy-private-name.gguf", "fake model")

        val restored = repository.restoreSelection() as RestoredModelSelection.Available

        assertEquals(modelFile.canonicalPath, restored.selection.file.canonicalPath)
        assertEquals("legacy-private-name.gguf", restored.selection.relativeFileName)
        assertNull(restored.selection.displayName)
        assertEquals(modelFile.length(), restored.selection.sizeBytes)
        assertTrue(!restored.selection.verified)
    }

    @Test
    fun multipleLegacyGgufsAreNotGuessed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = repository(app)
        modelFile(app, "first.gguf", "first")
        modelFile(app, "second.gguf", "second")

        val restored = repository.restoreSelection()

        assertTrue(restored is RestoredModelSelection.Unavailable)
        assertTrue((restored as RestoredModelSelection.Unavailable).message.contains("Multiple"))
    }

    private fun repository(app: Application): DataStoreModelSelectionRepository {
        File(app.filesDir, PRIVATE_MODELS_DIR).deleteRecursively()
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = {
                File(app.filesDir, "model-selection-${UUID.randomUUID()}.preferences_pb")
            }
        )
        return DataStoreModelSelectionRepository(app, dataStore)
    }

    private fun modelFile(app: Application, name: String, contents: String): File {
        val modelsDir = File(app.filesDir, PRIVATE_MODELS_DIR).also { it.mkdirs() }
        return File(modelsDir, name).apply {
            writeText(contents)
            deleteOnExit()
        }
    }
}
