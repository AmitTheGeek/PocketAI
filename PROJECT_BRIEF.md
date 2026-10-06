# PocketAI Project Brief

## Purpose

PocketAI is an offline Android summarisation prototype for situations where connectivity cannot be assumed. The current target device is a OnePlus 8 Pro with 12 GB RAM on Android 13.

## MVP Scope

- Import a user-downloaded GGUF model through Android's file picker.
- Copy the GGUF to app-private storage.
- Remember the selected app-private model across process death and relaunch.
- Run local CPU inference through llama.cpp.
- Edit one pasted text input on a dedicated Input screen.
- Open a dedicated Result screen immediately after preparation passes.
- Stream output while generation is running.
- Support cancellation and an immediate clean follow-up request.
- Validate summary shape as 1-3 bullets and retry once when the shape is invalid.
- Show elapsed time across initial generation and any retry.
- Explicitly save completed summaries to a local Room database.
- Browse saved summaries, open details, copy saved output, and delete saved records.

## Explicit Exclusions

- No cloud calls or network inference.
- No login.
- No model download inside the app.
- No automatic saving of partial, cancelled, or failed generations.
- No search, cloud sync, or cross-device sync.
- No additional AI tasks beyond summarisation.
- No packaged model files in the APK.
- No extra Gradle modules or DI framework.

## Current Device Evidence

On October 4, 2026, the prototype was installed and run on the connected OnePlus 8 Pro. Offline summary runs took about 4-6 seconds. Initial cancellation followed by a fresh request passed on-device.

The user later confirmed baseline offline/airplane-mode inference worked on the OnePlus 8 Pro. The structural validator, one-retry coordinator, Room-backed save/history logic, and Task 004 lifecycle/import/navigation recovery changes pass JVM unit tests.

Task 007 added remembered-model selection in source. JVM unit tests and a debug APK build passed on October 6, 2026. App-only connected Compose UI tests passed on the OnePlus IN2021 after the device was awake and unlocked.

Task 007C physical-device acceptance verified model recognition after force-stop/relaunch, no re-import, History access without native inference logs, and first post-relaunch inference using the remembered file. Observed elapsed times were 7.1s before force-stop and 9.9s for the first post-relaunch summary. These are individual observations, not a general benchmark.

Remaining phone checks include retry during refinement, cancellation during refinement, lifecycle reopen, failed-load recovery with a real invalid GGUF, same-filename model replacement, system Back routing, second post-relaunch loaded-model reuse, device-restart persistence, and the full manual quality checklist.
