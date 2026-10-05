package com.pocketai.offline.summarization

import com.pocketai.offline.inference.SummarizationEngine
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

enum class SummaryAttempt {
    Initial,
    Refinement,
}

sealed interface SummaryGenerationEvent {
    data class Token(val value: String) : SummaryGenerationEvent
    data object Refining : SummaryGenerationEvent
    data class FormatWarning(val message: String) : SummaryGenerationEvent
}

data class PreparedSummaryRequest(
    val sourceText: String,
)

class BlankSummaryInputException : IllegalArgumentException(
    "Enter text to summarize."
)

class PromptTooLongException(
    val promptTokens: Int,
    val availablePromptTokens: Int,
    val contextWindowTokens: Int,
    val reservedOutputTokens: Int,
) : IllegalArgumentException(
    "Input is too long for the $contextWindowTokens-token context after reserving " +
        "$reservedOutputTokens output tokens. The formatted prompt uses $promptTokens tokens; " +
        "the available prompt budget is $availablePromptTokens tokens. Shorten the source text and try again."
)

class SummaryGenerationCoordinator(
    private val engine: SummarizationEngine,
    private val validator: SummaryFormatValidator = SummaryFormatValidator,
) {
    fun summarize(sourceText: String): Flow<SummaryGenerationEvent> = flow {
        emitAll(summarize(prepare(sourceText)))
    }

    suspend fun prepare(sourceText: String): PreparedSummaryRequest {
        if (sourceText.isBlank()) throw BlankSummaryInputException()
        val trimmedSource = sourceText.trim()
        checkPromptBudget(trimmedSource)
        return PreparedSummaryRequest(sourceText = trimmedSource)
    }

    fun summarize(request: PreparedSummaryRequest): Flow<SummaryGenerationEvent> = flow {
        val sourceText = request.sourceText
        val initialOutput = collectAttempt(sourceText, SummaryAttempt.Initial)
        val initialValidation = validator.validate(initialOutput)
        if (initialValidation.isValid) return@flow

        currentCoroutineContext().ensureActive()
        emit(SummaryGenerationEvent.Refining)

        val retryOutput = collectAttempt(sourceText, SummaryAttempt.Refinement)
        val retryValidation = validator.validate(retryOutput)
        if (!retryValidation.isValid) {
            emit(SummaryGenerationEvent.FormatWarning(formatWarning(retryValidation)))
        }
    }

    private suspend fun FlowCollector<SummaryGenerationEvent>.collectAttempt(
        sourceText: String,
        attempt: SummaryAttempt,
    ): String {
        val output = StringBuilder()
        engine.summarize(sourceText, attempt).collect { token ->
            output.append(token)
            emit(SummaryGenerationEvent.Token(token))
        }
        return output.toString()
    }

    private fun formatWarning(validation: SummaryFormatValidation): String =
        "Format warning: ${validation.reason ?: "The model did not follow the requested bullet format."} " +
            "The summary below is unmodified; PocketAI does not validate factual accuracy."

    private suspend fun checkPromptBudget(sourceText: String) {
        val availablePromptTokens = engine.contextWindowTokens - engine.maxGeneratedTokens
        check(availablePromptTokens > 0) {
            "Configured output reserve is larger than the context window."
        }

        val initialTokens = engine.countPromptTokens(sourceText, SummaryAttempt.Initial)
        val refinementTokens = engine.countPromptTokens(sourceText, SummaryAttempt.Refinement)
        val promptTokens = maxOf(initialTokens, refinementTokens)

        if (promptTokens > availablePromptTokens) {
            throw PromptTooLongException(
                promptTokens = promptTokens,
                availablePromptTokens = availablePromptTokens,
                contextWindowTokens = engine.contextWindowTokens,
                reservedOutputTokens = engine.maxGeneratedTokens
            )
        }
    }
}
