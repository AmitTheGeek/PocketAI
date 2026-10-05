package com.pocketai.offline.inference

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.arm.aichat.InferenceEngine
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LlamaCppSummarizationEngineTest {
    @Test
    fun cleanupWithoutUseDoesNotInitializeNativeEngine() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        var providerCalls = 0
        val engine = LlamaCppSummarizationEngine(
            context = app,
            engineProvider = {
                providerCalls += 1
                FakeInferenceEngine()
            }
        )

        engine.cancel()
        engine.unloadModel()
        engine.close()

        assertEquals(0, providerCalls)
    }

    @Test
    fun failedLoadResetsErrorAndLaterLoadCanSucceed() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val upstream = FakeInferenceEngine(failFirstLoad = true)
        val engine = LlamaCppSummarizationEngine(
            context = app,
            engineProvider = { upstream }
        )
        val badModel = tempModelFile("bad")
        val goodModel = tempModelFile("good")

        val failure = runCatching {
            engine.loadModel(badModel)
        }.exceptionOrNull()
        engine.loadModel(goodModel)

        assertTrue(failure is IOException)
        assertEquals(1, upstream.cleanUpCount)
        assertEquals(
            listOf(badModel.absolutePath, goodModel.absolutePath),
            upstream.loadedPaths
        )
        assertTrue(upstream.state.value is InferenceEngine.State.ModelReady)
    }

    private fun tempModelFile(label: String): File =
        File.createTempFile("pocketai-engine-$label-", ".gguf").apply {
            writeText("fake-$label")
            deleteOnExit()
        }

    private class FakeInferenceEngine(
        private val failFirstLoad: Boolean = false,
    ) : InferenceEngine {
        private val mutableState = MutableStateFlow<InferenceEngine.State>(
            InferenceEngine.State.Initialized
        )
        override val state = mutableState
        val loadedPaths = mutableListOf<String>()
        var cleanUpCount = 0
        private var loadCount = 0

        override suspend fun loadModel(pathToModel: String) {
            loadCount += 1
            loadedPaths += pathToModel
            if (failFirstLoad && loadCount == 1) {
                val exception = IOException("load failed")
                mutableState.value = InferenceEngine.State.Error(exception)
                throw exception
            }
            mutableState.value = InferenceEngine.State.ModelReady
        }

        override suspend fun setSystemPrompt(systemPrompt: String) {
            check(state.value is InferenceEngine.State.ModelReady)
        }

        override fun sendUserPrompt(message: String, predictLength: Int): Flow<String> = emptyFlow()

        override suspend fun countPromptTokens(message: String): Int = 100

        override suspend fun contextWindowTokens(): Int = 2_048

        override fun cancelGeneration() = Unit

        override suspend fun bench(pp: Int, tg: Int, pl: Int, nr: Int): String = ""

        override fun cleanUp() {
            cleanUpCount += 1
            mutableState.value = InferenceEngine.State.Initialized
        }

        override fun destroy() {
            mutableState.value = InferenceEngine.State.Uninitialized
        }
    }
}
