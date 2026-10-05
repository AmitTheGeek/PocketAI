package com.pocketai.offline.summarization

import com.pocketai.offline.inference.SummarizationEngine
import com.pocketai.offline.inference.LoadedModelInfo
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SummaryGenerationCoordinatorTest {
    @Test
    fun doesNotRetryWhenInitialSummaryHasValidFormat() = runBlocking {
        val engine = ScriptedEngine(
            SummaryAttempt.Initial to listOf("- One concise fact\n- Second concise fact")
        )
        val coordinator = SummaryGenerationCoordinator(engine)

        val events = coordinator.summarize("source text").toList()

        assertEquals(listOf(SummaryAttempt.Initial), engine.attempts)
        assertEquals(listOf("source text"), engine.sources)
        assertEquals(
            "- One concise fact\n- Second concise fact",
            events.tokens()
        )
        assertFalse(events.any { it is SummaryGenerationEvent.Refining })
    }

    @Test
    fun retriesOnceWithOriginalSourceTextWhenInitialFormatIsInvalid() = runBlocking {
        val engine = ScriptedEngine(
            SummaryAttempt.Initial to listOf(
                "- One\n- Two\n- Three\n- Four"
            ),
            SummaryAttempt.Refinement to listOf(
                "- One and two are combined.\n- Three and four are combined."
            )
        )
        val coordinator = SummaryGenerationCoordinator(engine)

        val events = coordinator.summarize("original source").toList()

        assertEquals(
            listOf(SummaryAttempt.Initial, SummaryAttempt.Refinement),
            engine.attempts
        )
        assertEquals(listOf("original source", "original source"), engine.sources)
        assertTrue(events.any { it is SummaryGenerationEvent.Refining })
        assertEquals(
            "- One and two are combined.\n- Three and four are combined.",
            events.tokensAfterRefining()
        )
        assertFalse(events.any { it is SummaryGenerationEvent.FormatWarning })
    }

    @Test
    fun emitsWarningAfterOneRetryLimitAndKeepsRetryOutputUnmodified() = runBlocking {
        val retryOutput = "- One\n- Two\n- Three\n- Four"
        val engine = ScriptedEngine(
            SummaryAttempt.Initial to listOf("Summary:\n- One"),
            SummaryAttempt.Refinement to listOf(retryOutput)
        )
        val coordinator = SummaryGenerationCoordinator(engine)

        val events = coordinator.summarize("source").toList()

        assertEquals(
            listOf(SummaryAttempt.Initial, SummaryAttempt.Refinement),
            engine.attempts
        )
        assertEquals(retryOutput, events.tokensAfterRefining())
        val warnings = events.filterIsInstance<SummaryGenerationEvent.FormatWarning>()
        assertEquals(1, warnings.size)
        assertTrue(warnings.single().message.contains("Format warning"))
        assertTrue(warnings.single().message.contains("does not validate factual accuracy"))
    }

    @Test
    fun cancellationStopsTheOperationBeforeRetry() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val engine = object : SummarizationEngine {
            override val loadedModel = MutableStateFlow<LoadedModelInfo?>(null)
            override val contextWindowTokens: Int = 2048
            override val maxGeneratedTokens: Int = 512
            val attempts = mutableListOf<SummaryAttempt>()

            override suspend fun loadModel(modelFile: File, displayName: String) = Unit
            override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100

            override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
                attempts += attempt
                started.complete(Unit)
                emit("- Partial")
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            }

            override fun cancel() = Unit

            override suspend fun unloadModel() = Unit

            override suspend fun close() = Unit
        }
        val coordinator = SummaryGenerationCoordinator(engine)

        val job = launch {
            coordinator.summarize("source").collect()
        }
        withTimeout(1_000) { started.await() }

        job.cancelAndJoin()

        withTimeout(1_000) { cancelled.await() }
        assertEquals(listOf(SummaryAttempt.Initial), engine.attempts)
    }

    @Test
    fun cancellationDuringRefinementStopsBeforeLaterOutput() = runBlocking {
        val refinementStarted = CompletableDeferred<Unit>()
        val refinementCancelled = CompletableDeferred<Unit>()
        val engine = object : SummarizationEngine {
            override val loadedModel = MutableStateFlow<LoadedModelInfo?>(null)
            override val contextWindowTokens: Int = 2048
            override val maxGeneratedTokens: Int = 512
            val attempts = mutableListOf<SummaryAttempt>()

            override suspend fun loadModel(modelFile: File, displayName: String) = Unit
            override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100

            override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
                attempts += attempt
                if (attempt == SummaryAttempt.Initial) {
                    emit("- One\n- Two\n- Three\n- Four")
                } else {
                    refinementStarted.complete(Unit)
                    emit("- Retry partial")
                    try {
                        awaitCancellation()
                    } finally {
                        refinementCancelled.complete(Unit)
                    }
                }
            }

            override fun cancel() = Unit
            override suspend fun unloadModel() = Unit
            override suspend fun close() = Unit
        }
        val coordinator = SummaryGenerationCoordinator(engine)
        val events = mutableListOf<SummaryGenerationEvent>()

        val job = launch {
            coordinator.summarize("source").toList(events)
        }
        withTimeout(1_000) { refinementStarted.await() }

        job.cancelAndJoin()

        withTimeout(1_000) { refinementCancelled.await() }
        assertEquals(listOf(SummaryAttempt.Initial, SummaryAttempt.Refinement), engine.attempts)
        assertFalse(events.any { it is SummaryGenerationEvent.FormatWarning })
    }

    @Test
    fun freshRequestAfterCancellationStartsWithCleanOutput() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val engine = RestartableEngine(firstStarted)
        val coordinator = SummaryGenerationCoordinator(engine)
        val firstEvents = mutableListOf<SummaryGenerationEvent>()

        val firstJob = launch {
            coordinator.summarize("first source").toList(firstEvents)
        }
        withTimeout(1_000) { firstStarted.await() }
        firstJob.cancelAndJoin()

        val secondEvents = coordinator.summarize("second source").toList()

        assertEquals("- Clean second result", secondEvents.tokens())
        assertFalse(secondEvents.tokens().contains("Partial stale text"))
    }

    @Test
    fun blankInputFailsBeforeInference() = runBlocking {
        val engine = ScriptedEngine(
            SummaryAttempt.Initial to listOf("- Should not run")
        )
        val coordinator = SummaryGenerationCoordinator(engine)

        val failure = runCatching {
            coordinator.summarize("   ").toList()
        }.exceptionOrNull()

        assertTrue(failure is BlankSummaryInputException)
        assertEquals(emptyList<SummaryAttempt>(), engine.attempts)
    }

    @Test
    fun oversizedInputFailsBeforeInferenceWithTokenBudgetMessage() = runBlocking {
        val engine = ScriptedEngine(
            SummaryAttempt.Initial to listOf("- Should not run"),
            tokenCounts = mapOf(
                SummaryAttempt.Initial to 1_400,
                SummaryAttempt.Refinement to 1_600
            ),
            contextWindowTokens = 2_048,
            maxGeneratedTokens = 512
        )
        val coordinator = SummaryGenerationCoordinator(engine)

        val failure = runCatching {
            coordinator.summarize("large source").toList()
        }.exceptionOrNull()

        assertTrue(failure is PromptTooLongException)
        assertEquals(emptyList<SummaryAttempt>(), engine.attempts)
        assertEquals(1_600, (failure as PromptTooLongException).promptTokens)
        assertEquals(1_536, failure.availablePromptTokens)
    }

    private class ScriptedEngine(
        private vararg val scriptedOutputs: Pair<SummaryAttempt, List<String>>,
        private val tokenCounts: Map<SummaryAttempt, Int> = emptyMap(),
        override val contextWindowTokens: Int = 2048,
        override val maxGeneratedTokens: Int = 512,
    ) : SummarizationEngine {
        override val loadedModel = MutableStateFlow<LoadedModelInfo?>(null)
        val attempts = mutableListOf<SummaryAttempt>()
        val sources = mutableListOf<String>()

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit
        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int =
            tokenCounts[attempt] ?: 100

        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            attempts += attempt
            sources += paragraph
            scriptedOutputs
                .firstOrNull { it.first == attempt }
                ?.second
                .orEmpty()
                .forEach { emit(it) }
        }

        override fun cancel() = Unit

        override suspend fun unloadModel() = Unit

        override suspend fun close() = Unit
    }

    private class RestartableEngine(
        private val firstStarted: CompletableDeferred<Unit>,
    ) : SummarizationEngine {
        override val loadedModel = MutableStateFlow<LoadedModelInfo?>(null)
        override val contextWindowTokens: Int = 2048
        override val maxGeneratedTokens: Int = 512
        private var requestCount = 0

        override suspend fun loadModel(modelFile: File, displayName: String) = Unit
        override suspend fun countPromptTokens(paragraph: String, attempt: SummaryAttempt): Int = 100

        override fun summarize(paragraph: String, attempt: SummaryAttempt): Flow<String> = flow {
            requestCount += 1
            if (requestCount == 1) {
                firstStarted.complete(Unit)
                emit("- Partial stale text")
                awaitCancellation()
            } else {
                emit("- Clean second result")
            }
        }

        override fun cancel() = Unit
        override suspend fun unloadModel() = Unit
        override suspend fun close() = Unit
    }

    private fun List<SummaryGenerationEvent>.tokens(): String =
        filterIsInstance<SummaryGenerationEvent.Token>().joinToString(separator = "") { it.value }

    private fun List<SummaryGenerationEvent>.tokensAfterRefining(): String {
        val refiningIndex = indexOfLast { it is SummaryGenerationEvent.Refining }
        return drop(refiningIndex + 1)
            .filterIsInstance<SummaryGenerationEvent.Token>()
            .joinToString(separator = "") { it.value }
    }
}
