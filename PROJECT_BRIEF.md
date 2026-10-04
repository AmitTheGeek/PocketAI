# PocketAI Project Brief

## Purpose

PocketAI is an offline Android summarisation prototype for situations where connectivity cannot be assumed. The current target device is a OnePlus 8 Pro with 12 GB RAM on Android 13.

## MVP Scope

- Import a user-downloaded GGUF model through Android's file picker.
- Copy the GGUF to app-private storage.
- Run local CPU inference through llama.cpp.
- Summarise one pasted text input.
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

The structural validator, one-retry coordinator, and Room-backed save/history logic pass JVM unit tests. The retry path, saved-history flow, and Task 003 acceptance checklist have not yet been validated on the phone.
