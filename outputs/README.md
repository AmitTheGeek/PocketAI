# PocketAI Offline Android Prototype

Offline summarisation prototype for Android 13 on arm64 devices such as the OnePlus 8 Pro.

## Runtime Pin

- llama.cpp official Android example: `examples/llama.android`
- llama.cpp tag: `b11379`
- llama.cpp commit: `1537a0a8b2f8711d840878b0a0677ab2213c882c`
- Source reference: https://github.com/ggml-org/llama.cpp/tree/1537a0a8b2f8711d840878b0a0677ab2213c882c/examples/llama.android

The native wrapper is based on the official Android example and is patched for this prototype to use a 2048-token context, Qwen GGUF chat-template formatting through llama.cpp's Jinja chat formatter, per-request state reset so generation does not keep chat history, runtime prompt-token counting, explicit rejection of over-budget prompts, and cancellation support.

## Model

Download this exact GGUF before testing:

https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf

Do not place the model in the repository, `assets/`, `res/`, or the APK. The app imports the file through Android's file picker and copies it to app-private storage.

## Build Dependencies

- JDK 17. Use a real JDK 17 runtime; the Android Studio bundled runtime on this machine reported Java `25.0.3` and failed Gradle/Kotlin script evaluation.
- Gradle wrapper 8.14.3
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.0
- compileSdk 36, targetSdk 36, minSdk 33
- Android NDK 29.0.13113456
- CMake 3.31.6
- Compose UI/Foundation 1.7.3
- Material 3 1.3.0
- Lifecycle ViewModel 2.8.3
- Kotlin coroutines 1.10.2

## Build

After cloning the repository, fetch and patch the pinned llama.cpp Android example:

```sh
./scripts/setup-llama-cpp.sh
```

This creates `work/llama.cpp` at commit `1537a0a8b2f8711d840878b0a0677ab2213c882c` and applies `patches/llama-cpp-pocketai.patch`. The `work/` directory is generated local state and is not committed.

```sh
JAVA_HOME=/path/to/jdk17 ./gradlew testDebugUnitTest
JAVA_HOME=/path/to/jdk17 ./gradlew assembleDebug
```

The checked workspace includes a local JDK used for the latest build:

```sh
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew testDebugUnitTest
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The refreshed handoff APK is copied to:

```text
outputs/PocketAI-debug.apk
```

## Setup On Device

1. Download `qwen2.5-1.5b-instruct-q4_k_m.gguf` from the link above to local device storage.
2. Install the debug APK.
3. Launch PocketAI.
4. Tap Import GGUF and select the downloaded model.
5. Wait for the import and load state to finish.
6. Edit or keep the sample paragraph, then tap Summarize.
7. Use Cancel to stop an in-progress generation.
8. Tap Save after a completed result.
9. Open History, open the saved detail, copy the summary if needed, and delete saved records when testing deletion.

## Airplane-Mode Test

1. Install the APK while online if needed.
2. Enable airplane mode and leave Wi-Fi and mobile data off.
3. Launch PocketAI.
4. Import the already-downloaded GGUF from local storage.
5. Summarize the sample paragraph.
6. Confirm streamed output appears, elapsed time updates, and Cancel stops generation.
7. Save the completed result and reopen it from History.
8. Confirm no network prompt or login is required.

## Local Persistence

Saved summaries use Room database `PocketAiDatabase`, version 1. Schemas are exported under `app/schemas`.

Saved fields:

- ID.
- Original source text snapshot.
- Exact final summary text.
- Local save timestamp.
- Total generation duration.
- Whether refinement occurred.
- Final structural-format warning, if any.
- Imported model filename and byte size when available.

Model binaries are not stored in Room. The manifest currently sets `android:allowBackup="false"`, so this app is not opted into Android Auto Backup. Saved summaries are still local app-private data and are removed if app data is cleared or the app is uninstalled.

## Evaluation Cases

Five short rubric-based evaluation cases are documented in `docs/evaluation-cases.md`. They cover deadlines, negation, numerical facts, uncertain plans, and instruction-like text embedded inside source text. The same document includes a device checklist for airplane mode, all five cases, refinement, cancellation during refinement, oversized input, saving, relaunch persistence, history without model loading, deletion, and warned-result retention.

## Architecture Notes

See `PROJECT_BRIEF.md` for scope and exclusions, and `ARCHITECTURE.md` for responsibilities, state, cancellation, prompt budget handling, Room persistence, backup policy, and retry trade-offs.

## Actual Build Result

`JAVA_HOME=work/jdk17/Contents/Home ./gradlew testDebugUnitTest` succeeded on October 4, 2026 after Task 003 changes.

`JAVA_HOME=work/jdk17/Contents/Home ./gradlew assembleDebug` succeeded on October 4, 2026 after Task 003 changes. The build took 14m 58s and refreshed `outputs/PocketAI-debug.apk`.

Physical-device smoke testing was previously performed on the connected OnePlus 8 Pro / IN2021 on October 4, 2026: APK install succeeded, the app launched, the GGUF was imported into app-private storage, and real summary runs took about 4-6 seconds. Initial cancellation followed by a fresh request passed on-device.

The structural validator, one-retry coordinator, Room persistence, and ViewModel save orchestration pass JVM unit tests. The retry path, saved-history flow, cancellation during refinement, oversized input, and the full five-case checklist have not yet been validated on the phone. A full airplane-mode test was not performed.

The source manifest declares no permissions. The merged app manifest contains AndroidX's generated app-private dynamic receiver permission, but no `android.permission.INTERNET`. An APK archive check found no GGUF or Qwen model files packaged in `outputs/PocketAI-debug.apk`.
