# PocketAI Architecture

## Responsibility Boundaries

- `ui`: Compose rendering and `MainViewModel` screen state/user actions.
- `summarization`: prompt-flow policy, structural validation, retry limit, and input-budget errors.
- `inference`: local llama.cpp model execution, prompt construction, token counting, native cancellation, and resource cleanup.
- `work/llama.cpp/examples/llama.android/lib`: generated local checkout of the pinned official llama.cpp Android example with PocketAI-specific native hooks from `patches/llama-cpp-pocketai.patch`.

The app stays in one Android app module. The boundaries are package-level only. The generated `work/llama.cpp` checkout is intentionally not committed; run `scripts/setup-llama-cpp.sh` to recreate it from the pinned upstream commit and patch.

## Data Flow

1. Compose forwards import, text-change, summarise, and cancel actions to `MainViewModel`.
2. `MainViewModel` owns immutable `PocketAiUiState` through `StateFlow`.
3. `SummaryGenerationCoordinator` checks blank input and prompt token budget before generation.
4. The coordinator streams the initial attempt through `SummarizationEngine`.
5. `SummaryFormatValidator` checks only structure: 1-3 non-empty bullet items and no extra prose.
6. If the first attempt is invalid, the coordinator emits `Refining`, clears first output in the UI, and retries once from the original source text.
7. If the retry is still invalid, the retry output is retained and a warning is shown. The warning explicitly avoids claiming factual validation.

## State Model

Model readiness is separate from generation state.

Model readiness:

- `NoModel`
- `Importing`
- `Loading`
- `Ready`
- `ModelError`

Generation state:

- `Idle`
- `Generating`
- `Refining`
- `Completed`
- `Cancelled`
- `Failed`

Import and summary actions are disabled while a model import/load or generation/refinement is active. This prevents replacing the model during inference.

## Model Ownership

`LlamaCppSummarizationEngine` owns the local llama.cpp runtime. It serializes load, token-count, generation, and close operations with one mutex. Loading a different model cleans up the previous native model only after the lock is acquired. Closing waits for in-flight work to release the lock, then destroys native resources off the main thread.

The model file is imported into app-private storage. It is not committed to the repository and is not packaged into the APK.

## Prompt Budget

The native wrapper exposes a runtime token-count call using the same llama.cpp tokenizer and Qwen chat-template formatting path used for generation. The coordinator checks both the initial prompt and the refinement prompt against the configured context window, reserving the configured generation budget. Oversized input is rejected with a clear error instead of truncating source text.

Current settings:

- Context window: 2048 tokens.
- Reserved output: 512 tokens.
- Available formatted prompt budget: 1536 tokens.

## Cancellation

Cancel requests call `SummarizationEngine.cancel()` and cancel the ViewModel job. The same path covers initial generation and refinement. UI updates are guarded by a monotonically increasing request id so cancelled or older jobs cannot append tokens to a newer request.

Native generation also receives a cancellation flag. The unit tests use fakes to verify coordinator cancellation, but those tests do not prove native cleanup safety on their own.

## Retry Trade-Offs

The retry policy intentionally fixes format only. It never truncates bullets or silently discards content to force compliance. This avoids hiding model failures, but it means a retry can still return too many bullets. In that case PocketAI keeps the retry output and shows a format warning.

The validator is structural. It does not verify factual accuracy, names, dates, numbers, or deadlines.
