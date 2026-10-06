# PocketAI Engineering Case Study

## Problem And Target Device

PocketAI started as a focused question: can a small Android app summarise text fully offline on a real phone, without a login, cloud inference, or bundled model?

The target device for the prototype is a OnePlus 8 Pro / IN2021 with 12 GB RAM on Android 13. That target shaped the first scope: CPU inference, one imported GGUF, one source text, streamed output, cancellation, and no extra product surface beyond saved local summaries.

## Runtime And Model Choice

The app uses the official llama.cpp Android example instead of a hand-written native bridge. The native source is pinned to llama.cpp commit `1537a0a8b2f8711d840878b0a0677ab2213c882c`, then patched reproducibly through `patches/llama-cpp-pocketai.patch`.

The chosen model for the MVP is `Qwen/Qwen2.5-1.5B-Instruct-GGUF`, specifically `qwen2.5-1.5b-instruct-q4_k_m.gguf`. The app does not download or package it. The user imports the downloaded GGUF through Android's file picker, and PocketAI copies it to app-private storage.

This route kept the first prototype small enough for CPU feasibility while still exercising the real constraints: Android storage, native lifecycle, prompt formatting, token budget, cancellation, and UI streaming.

## Initial Prototype And Feasibility

The first prototype proved that the app could load the model, summarise a short paragraph, stream tokens, and cancel generation on the OnePlus. Real summaries were reported around 4-6 seconds in early manual runs, and a later Task 007C acceptance pass observed 7.1s before force-stop and 9.9s for the first post-relaunch summary.

Those timings are useful feasibility observations, not benchmarks. They came from a small number of manual runs on one device with one model and source text.

## Output Format Failure And Bounded Retry

The summary instruction asks for at most 3 concise bullets while preserving names, numbers, deadlines, negations, and uncertainty. Device testing showed the model could preserve key facts but still return too many bullets.

PocketAI added a structural validator and coordinator:

- Validate only the completed output shape.
- Accept 1-3 non-empty bullet items with no extra prose.
- Retry at most once from the original source when the shape is invalid.
- During retry, replace the first output instead of appending to it.
- If the retry still fails, keep the retry output and show a format warning.

The trade-off is intentional. The app never truncates or silently discards bullets to force compliance, but the validator does not prove semantic quality or factual accuracy.

## Lifecycle And Model Replacement Findings

The upstream Android example exposes a process-level inference singleton. An early design let `MainViewModel.onCleared()` close the engine, which could leave later ViewModels in the same app process pointing at a destroyed runtime.

The app now separates ownership:

- `PocketAiContainer` owns the app-process native runtime.
- `MainViewModel` owns request/session state and cancellation.
- Request cancellation, model unload, and permanent runtime shutdown are distinct operations.
- History browsing does not initialize native inference just to clean it up later.

Model replacement also needed sharper identity. Replacing a GGUF by display filename alone was not reliable because two files can share the same visible name. Imports now use unique app-private filenames while retaining the original display name for UI and saved metadata.

Failed model loads created another recovery case. The wrapper now treats upstream error state as recoverable by cleaning up and allowing a later valid import/load, while preserving coroutine cancellation semantics.

## UI Reading Improvements

The combined edit/result screen was workable for a prototype but cramped for real reading. A previous summary section also used a fixed text height, which clipped long output even when the overall page could scroll.

Task 005 removed fixed-height output clipping and improved editor/readability behavior. Task 006 then split the flow into:

- A dedicated Input screen with model status, a large source editor, and a bottom Summarise action.
- A dedicated Result screen that opens after preparation, streams output, keeps Cancel reachable, and exposes Copy, Save, and Edit source after completion.

Streaming scroll ownership became its own design problem. The Result screen follows output only while the reader is already at the bottom, stops following when the reader scrolls away, and exposes Jump to latest outside the scrolling content.

## Model Persistence Across Process Death

Task 007 addressed a practical usability issue: after relaunch, an imported model should still be recognised without selecting and copying the GGUF again.

PocketAI now stores durable model-selection metadata in Preferences DataStore:

- App-private relative filename.
- Original display name when known.
- File size.

The app does not store model bytes in DataStore or Room and does not store a `Ready` boolean. On startup it validates the saved file reference without loading llama.cpp. History remains browsable without loading the model. The first summary after relaunch loads the remembered private file on demand.

Task 007C verified model recognition after force-stop, no re-import, History access without native inference logs, and a successful first post-relaunch summary. A second post-relaunch reuse check and device-restart persistence remain unverified.

## What Was Learned

- Native runtime ownership should be app-level when upstream exposes singleton behavior.
- Durable model selection and in-memory readiness are different states and should be modeled separately.
- Runtime tokenizer checks are safer than character-based guesses for prompt limits.
- A structural output validator can improve UX, but it must not be described as factual validation.
- Streaming UI needs explicit scroll ownership; otherwise useful output can be hidden or the reader can be pulled away from what they are reading.
- Saved summaries should store immutable source/result snapshots, not mutable editor state.

## Remaining Limits

- CPU inference has only been exercised on one target phone and one small GGUF.
- The app does not download models, manage multiple active models, or run cloud sync.
- The validator does not assess factual accuracy.
- Full five-case quality evaluation, restart persistence, second post-relaunch model reuse, invalid-model recovery on hardware, same-filename replacement on hardware, and some lifecycle/accessibility checks remain pending in the acceptance documentation.
