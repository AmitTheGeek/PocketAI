# PocketAI Architecture

## Responsibility Boundaries

- `ui`: Compose rendering and `MainViewModel` screen state/user actions.
- `summarization`: prompt-flow policy, structural validation, retry limit, and input-budget errors.
- `inference`: app-private model import, local llama.cpp model execution, prompt construction, token counting, native cancellation, model unload, and app-owned runtime shutdown.
- `history`: Room entity/DAO/database, saved-summary domain models, and the repository between ViewModels and Room.
- `PocketAiContainer`: simple application container for constructor injection of the engine and history repository.
- `work/llama.cpp/examples/llama.android/lib`: generated local checkout of the pinned official llama.cpp Android example with PocketAI-specific native hooks from `patches/llama-cpp-pocketai.patch`.

The app stays in one Android app module. The generated `work/llama.cpp` checkout is intentionally not committed; run `scripts/setup-llama-cpp.sh` to recreate it from the pinned upstream commit and tracked patch.

## Native Source Reproducibility

The native runtime remains pinned to llama.cpp commit `1537a0a8b2f8711d840878b0a0677ab2213c882c`. PocketAI changes to the official Android example are stored in `patches/llama-cpp-pocketai.patch`.

`scripts/setup-llama-cpp.sh`:

- Clones llama.cpp when `work/llama.cpp` is absent.
- Checks out the pinned commit.
- Applies the tracked zero-context patch with `git apply --unidiff-zero` when the checkout is clean.
- Accepts an existing checkout only when its zero-context diff matches the tracked patch exactly.
- Fails if local ignored native changes differ from the tracked patch.

Task 004 rechecked patch application against the local pinned llama.cpp checkout after converting the tracked patch to zero-context format. That check did not perform a fresh network clone.

## Generation Data Flow

1. Compose forwards import, text-change, summarise, cancel, save, history, detail, copy, and delete actions to `MainViewModel`.
2. `MainViewModel` owns immutable `PocketAiUiState` through `StateFlow`.
3. `SummaryGenerationCoordinator` checks blank input and prompt token budget before generation.
4. The coordinator streams the initial attempt through `SummarizationEngine`.
5. `SummaryFormatValidator` checks only structure: 1-3 non-empty bullet items and no extra prose.
6. If the first attempt is invalid, the coordinator emits `Refining`, clears first output in the UI, and retries once from the original source text.
7. If the retry is still invalid, the retry output is retained and a warning is shown. The warning explicitly avoids claiming factual validation.
8. On completion, the ViewModel captures the source snapshot, final summary, elapsed time, refinement flag, warning, and available model identification for optional saving.

## History Data Flow

1. `MainViewModel` observes `SummaryHistoryRepository.observeSummaries()` independently of model readiness.
2. The repository maps Room entities to `SavedSummary` domain objects before they reach UI state.
3. Compose renders only domain objects and never receives Room entities.
4. Saving inserts a `NewSavedSummary` only for the completed displayed result.
5. Detail observes a single saved summary by ID and deletion calls the repository.

History browsing and detail screens do not load the model. Save failures do not discard the generated result and can be retried.

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

Additional UI state tracks destination (`Summarizer`, `History`, `Detail`), history loading/error/loaded state, detail loading/error/not-found/loaded state, completed-result snapshot, and save state (`Idle`, `Saving`, `Saved`, `Error`).

Import and summary actions are disabled while a model import/load or generation/refinement is active. This prevents replacing the model during inference. Save is explicit and separate; duplicate save taps for the same displayed result are ignored.

## Room Schema

Database: `PocketAiDatabase`, version 1, schema exported to `app/schemas`.

Table: `saved_summaries`

- `id`: autoincrement primary key.
- `source_text`: original source text snapshot from the completed request.
- `summary_text`: exact final summary text displayed to the user.
- `created_at_epoch_ms`: local save timestamp.
- `duration_ms`: total generation duration across the initial attempt and any retry.
- `refinement_occurred`: whether the retry path ran.
- `format_warning`: final structural-format warning, if any.
- `model_name`: imported GGUF filename when available.
- `model_size_bytes`: imported GGUF byte size when available.

No model binaries are stored in the database.

## Backup Policy

The manifest currently sets `android:allowBackup="false"`. With that setting, app-private files and the Room database are not opted into Android Auto Backup by this app. Data is still stored locally in app-private storage and is removed when app data is cleared or the app is uninstalled. PocketAI has no cloud sync code and declares no `INTERNET` permission.

## Model Ownership

`PocketAiContainer` is the app-process owner of the local llama.cpp runtime. It lazily creates one `LlamaCppSummarizationEngine` and keeps it shared across `MainViewModel` instances. `MainViewModel.onCleared()` cancels only its active request/job references; it does not permanently close the native runtime. This prevents Activity recreation from leaving future ViewModels with a destroyed upstream singleton.

The upstream `AiChat.getInferenceEngine(...)` returns a process singleton. Because `destroy()` cancels that singleton's internal coroutine scope and there is no public replacement API, permanent shutdown is reserved for the app container/test teardown through `PocketAiContainer.closeNativeRuntime()`. That method closes only if the engine was initialized, so browsing history alone does not initialize native code just to clean it up.

Runtime operations are intentionally separate:

- Request cancellation: `SummarizationEngine.cancel()` requests cancellation of the current token generation loop.
- Model unload: `SummarizationEngine.unloadModel()` frees the currently loaded model while keeping the native backend initialized.
- Permanent shutdown: `SummarizationEngine.close()` destroys the app-owned runtime and should not be called from ViewModel disposal.

`LlamaCppSummarizationEngine` serializes load, token-count, generation, unload, and close operations with one mutex. Loading a different model first unloads the previous native model under that lock. A failed load resets the upstream `Error` state through `cleanUp()` before a later load is allowed. PocketAI also patches the pinned upstream Android example so native `unload()` is safe after partial load failures.

The model file is imported into app-private storage with a unique internal filename. The original display filename is retained for UI and saved-summary metadata. This lets two selected files share the same display name while still forcing the newly selected file to become the active model. Failed imports delete their newly copied file, and successful imports delete obsolete app-private model copies only after the new model has loaded. The active model file is not deleted while native inference may still be using it.

## Prompt Budget

The native wrapper exposes a runtime token-count call using the same llama.cpp tokenizer and Qwen chat-template formatting path used for generation. The coordinator checks both the initial prompt and the refinement prompt against the configured context window, reserving the configured generation budget. Oversized input is rejected with a clear error instead of truncating source text.

Current settings:

- Context window: 2048 tokens.
- Reserved output: 512 tokens.
- Available formatted prompt budget: 1536 tokens.

## Cancellation

Cancel requests call `SummarizationEngine.cancel()` and cancel the ViewModel job. The same path covers initial generation and refinement. UI updates are guarded by a monotonically increasing request id so cancelled or older jobs cannot append tokens to a newer request.

Native generation also receives a cancellation flag. The unit tests use fakes to verify coordinator and ViewModel cancellation behaviours, but those tests do not prove native cleanup safety on their own.

## Model-Load Recovery

A failed model load may leave the upstream singleton in `InferenceEngine.State.Error`. The wrapper treats that state as recoverable for future imports: before loading, and again after a failed load, it calls upstream `cleanUp()` to return to `Initialized`. The tracked native patch makes `unload()` null-safe so cleanup can run even when model loading failed partway through resource setup.

This recovery path is covered with fake upstream-engine JVM tests. It still needs physical-device acceptance with an actual invalid GGUF followed by a valid import.

## Retry Trade-Offs

The retry policy intentionally fixes format only. It never truncates bullets or silently discards content to force compliance. This avoids hiding model failures, but it means a retry can still return too many bullets. In that case PocketAI keeps the retry output and shows a format warning.

The validator is structural. It does not verify factual accuracy, names, dates, numbers, or deadlines.
