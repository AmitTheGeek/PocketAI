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

## Explicit Exclusions

- No cloud calls or network inference.
- No login.
- No saved history.
- No model download inside the app.
- No additional AI tasks beyond summarisation.
- No packaged model files in the APK.
- No extra Gradle modules or DI framework.

## Current Device Evidence

On October 4, 2026, the prototype was installed and run on the connected OnePlus 8 Pro. Offline summary runs took about 4-6 seconds. Initial cancellation followed by a fresh request passed on-device.

The structural validator and one-retry coordinator pass JVM unit tests. The retry path has not yet been validated on the phone.
