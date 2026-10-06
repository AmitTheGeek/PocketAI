# PocketAI Acceptance Report

Date: October 6, 2026

## Task 006 Scope

Task 006 replaces the combined summariser with separate Compose Input and Result destinations. It preserves local llama.cpp inference, bounded retry, cancellation, Room history, and app-owned runtime behavior.

## Automated Checks

- Source scan for removed Task 005 combined-screen and expanded-editor symbols: passed locally.
- `testDebugUnitTest`: attempted, not completed. The wrapper was blocked from `~/.gradle` by sandbox permissions. The workspace-local Gradle home lacked the Gradle 8.14.3 distribution and could not download it because network is blocked. Running the already-installed Gradle binary directly also failed because the sandbox blocked Gradle's file-lock listener socket.
- `assembleDebug`: not run after Task 006 because Gradle execution is blocked as above.
- Connected Compose UI tests: not run after Task 006.
- APK refresh: pending; `outputs/PocketAI-debug.apk` still reflects the previous successful baseline unless a later build is run outside the blocker.
- `INTERNET` permission and packaged-model APK checks: pending for the Task 006 APK because no refreshed APK was produced.

## Task 006B Checks

Source changes:

- Moved Jump to latest into the Result `Scaffold` floating action area so it stays reachable while the reader is above the latest text.
- Replaced the delayed `maxValue` auto-follow effect with a conflated, bounded-rate scroll collector. Continuous content-height updates no longer restart the wait indefinitely.
- Added `activeRequestId` to UI state so follow intent resets for a new request even when the source text is unchanged.
- Added/updated connected Compose regressions for Jump visibility, continuous synthetic streaming follow, manual-scroll persistence through refinement, and Cancel reachability.

Local checks on October 5, 2026:

- `git diff --check`: passed.
- Source manifest scan with `rg "INTERNET|uses-permission" app/src/main`: no matches.
- Tracked model asset scan excluding `.git`, `work`, and `outputs`: no `.gguf`, `.safetensors`, or `*qwen*.bin` files found.

Gradle verification attempt:

- Exact command: `JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew testDebugUnitTest`
- Result: failed before compilation started.
- Relevant error excerpt: `java.io.FileNotFoundException: /Users/batcomputer/.gradle/wrapper/dists/gradle-8.14.3-bin/.../gradle-8.14.3-bin.zip.lck (Operation not permitted)`.
- Escalated rerun: requested for the same command so Gradle could use the existing wrapper cache; rejected by the execution policy.
- `assembleDebug`: not attempted after the unit-test Gradle wrapper failure because the same wrapper/cache access is required and repeated workaround attempts were stopped.
- Connected UI tests: not run.
- Refreshed `outputs/PocketAI-debug.apk`: pending.
- Tested commit and APK checksum: pending until a successful build produces a refreshed APK.

Commands for Amit to run in a normal local terminal:

```sh
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew testDebugUnitTest
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew assembleDebug
cp app/build/outputs/apk/debug/app-debug.apk outputs/PocketAI-debug.apk
shasum -a 256 outputs/PocketAI-debug.apk
```

If a device is connected and authorized:

```sh
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew connectedDebugAndroidTest
```

## Device Checks

Not performed after Task 006 in this environment.

Pending on the connected OnePlus 8 Pro:

- Install refreshed APK over the existing app without clearing model/history data.
- Confirm the existing imported model is still available after Activity recreation.
- Input screen: edit non-personal text, show keyboard, rotate, return from History, and confirm draft preservation.
- Summarise: validate that Result opens after preparation and streams before completion.
- Back during active generation: verify it requests cancellation, returns to Input, shows Stopping until cleanup finishes, and then allows a fresh request.
- Cancel button on Result: verify it stops work while remaining on Result and leaves partial output copyable but not saveable.
- Copy completed output and cancelled partial output.
- Save a completed summary, reopen it from History, force-stop/relaunch, reopen again, copy, delete, and confirm deletion persists.
- History/detail Back routing: Detail -> History -> Input -> system behavior.
- Airplane mode with Wi-Fi and mobile data off.
- Same-filename model replacement.
- Failed model load followed by valid import.
- Oversized input error.
- Refinement path, including cancellation during refinement followed by a fresh request.
- Five rubric evaluation cases in `docs/evaluation-cases.md`.

## Task 007 Checks

Source changes:

- Added Preferences DataStore-backed model selection metadata for the app-private GGUF filename, original display name when known, and file size.
- Added startup restore that validates the remembered file without loading llama.cpp.
- Added `AvailableOnDisk` readiness so Summarise can load the remembered file just in time.
- Kept History browsing independent of model loading.
- Preserved safe replacement ordering: import/copy, load, persist selection, then delete obsolete files.
- Added legacy recovery for existing app-private installs with exactly one plausible `.gguf`; multiple candidates are not guessed.

Automated checks on October 6, 2026:

- `git diff --check`: passed.
- `JAVA_HOME=$PWD/work/jdk17/Contents/Home ./gradlew testDebugUnitTest`: passed.
- `JAVA_HOME=$PWD/work/jdk17/Contents/Home ./gradlew assembleDebug`: passed.
- `outputs/PocketAI-debug.apk`: refreshed.
- APK SHA-256: `06872938c76c2dc8b414af351ad299765b833ecdaa49a1b67f411023815dfa92`.
- APK model-asset scan: no `.gguf`, `.safetensors`, Qwen, or model binary assets found.
- Source permission scan: no `INTERNET` or `uses-permission` declarations found under `app/src/main`.
- Built APK permissions: only `com.pocketai.offline.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`; no `android.permission.INTERNET`.
- `JAVA_HOME=$PWD/work/jdk17/Contents/Home ./gradlew connectedDebugAndroidTest`: failed in `:llama-android-lib:connectedDebugAndroidTest` before app tests. The library test APK crashed because `androidx.test.runner.AndroidJUnitRunner` was not found in `com.arm.aichat.test`.
- `JAVA_HOME=$PWD/work/jdk17/Contents/Home ./gradlew :app:connectedDebugAndroidTest`: started 7 tests on the OnePlus IN2021 / Android 13. Multiple `SummaryReadingUiTest` cases failed with `No compose hierarchies found in the app`; the run then stopped making progress and was interrupted. Treat connected UI test validation as failed/pending, not accepted.

Task 007 pending device acceptance:

- [ ] Import/select once and summarise.
- [ ] Force-stop and relaunch without clearing app data.
- [ ] Confirm the model is recognised without opening the file picker.
- [ ] Open History without loading the model.
- [ ] Summarise successfully using the remembered file.
- [ ] Repeat after device restart if available.

## Task 007B Connected Test Diagnosis

Root cause:

- The aggregate `connectedDebugAndroidTest` failure remains separate: Gradle reaches `:llama-android-lib:connectedDebugAndroidTest`, whose upstream test APK lacks `androidx.test.runner.AndroidJUnitRunner`. The app-only command is the correct connected UI test command for PocketAI.
- The app-only `No compose hierarchies found` failures were caused by the connected phone being locked/asleep, not by DataStore, model restore, or app startup. The earliest failing log showed `SummaryReadingUiTest.longSummaryFinalMarkerCanBeScrolledIntoView` starting, `androidx.activity.ComponentActivity` reaching `RESUMED`, then immediately moving to `PAUSED`/`STOPPED`. Device state inspection during diagnosis showed `mWakefulness=Dozing`, `screenState=SCREEN_STATE_OFF`, secure keyguard showing, and the Compose host `ComposeView` measured at `0x0`.
- After waking/unlocking the phone, device state showed `mWakefulness=Awake`, keyguard not showing, and `SCREEN_STATE_ON`. No product or test-code changes were required.

Exact commands and results on October 6, 2026:

```sh
/Users/batcomputer/Library/Android/sdk/platform-tools/adb -s 204885a6 shell dumpsys power
/Users/batcomputer/Library/Android/sdk/platform-tools/adb -s 204885a6 shell dumpsys window
```

- Before unlock: confirmed asleep/locked state.
- After unlock: confirmed awake/unlocked state.

```sh
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.pocketai.offline.ui.SummaryReadingUiTest#longSummaryFinalMarkerCanBeScrolledIntoView \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

- Result: passed. Gradle reported `Starting 1 tests on IN2021 - 13`, `Finished 1 tests on IN2021 - 13`, `BUILD SUCCESSFUL in 1m 2s`.

```sh
JAVA_HOME="$PWD/work/jdk17/Contents/Home" ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true
```

- Result: passed. Gradle reported `Starting 7 tests on IN2021 - 13`, `Finished 7 tests on IN2021 - 13`, `BUILD SUCCESSFUL in 56s`.
- XML report: `SummaryReadingUiTest`, 7 tests, 0 failures, 0 errors, 0 skipped, total test time 8.69s.
- Passing cases: long-summary final marker scroll, draft preservation, complete copy text, manual-scroll intent during refinement replacement, Cancel reachability, continuous synthetic streaming follow, and Jump to latest visibility.

Actual device results:

- OnePlus IN2021 / Android 13 app-only connected Compose suite: passed once the device was awake and unlocked.
- No app source changes, skipped tests, sleeps, or weakened assertions were needed.
- No model-quality or manual restoration checks were performed as part of this connected-test diagnosis.

Task 007 manual model-restoration checks remain pending:

- [ ] Import/select once and summarise.
- [ ] Force-stop and relaunch without clearing app data.
- [ ] Confirm the model is recognised without opening the file picker.
- [ ] Open History without loading the model.
- [ ] Summarise successfully using the remembered file.
- [ ] Repeat after device restart if available.

## Task 007C Model Persistence Device Acceptance

Date: October 6, 2026

Source and APK:

- Source commit: `3311f58`.
- Application ID resolved from Gradle: `com.pocketai.offline`.
- Version: `0.1.0`, versionCode `1`.
- Local APK: `outputs/PocketAI-debug.apk`.
- Local APK SHA-256: `06872938c76c2dc8b414af351ad299765b833ecdaa49a1b67f411023815dfa92`.
- Installed base APK SHA-256: `06872938c76c2dc8b414af351ad299765b833ecdaa49a1b67f411023815dfa92`.
- Device: OnePlus IN2021, Android 13, serial `204885a6`.

Preparation results:

- Device awake/unlocked: pass. `dumpsys window policy` showed `screenState=SCREEN_STATE_ON`, `interactiveState=INTERACTIVE_STATE_AWAKE`, and keyguard `showing=false`.
- Installed app matches latest successful local debug APK: pass. The installed base APK checksum matched `outputs/PocketAI-debug.apk`.
- Data-preserving reinstall: not performed because the installed APK already matched.
- Existing imported model available: fail. The visible app state showed `No model selected`, and private app storage inspection showed no `files/models` directory and no `.gguf` under `files`.

Evidence:

- Screenshot: [task007c-no-model-selected.png](screenshots/task007c-no-model-selected.png).
- Private file inspection with `run-as com.pocketai.offline find . -maxdepth 3 -type f` found `./files/profileInstalled` plus Room database files, but no DataStore model-selection file and no private GGUF.
- `logcat -d -s InferenceEngineImpl:I` returned no native inference entries during this blocked attempt, which is consistent with no model load being attempted.

Actions and pass/fail:

- Establish baseline real summary: not run. Blocked because no existing imported model was available.
- Save acceptance test record: not run.
- Force-stop/relaunch model recognition: not run.
- History record inspection after saved baseline: not run.
- History access without native load: unverified. The required saved acceptance record could not be created first.
- Post-relaunch inference: not run.
- Subsequent loaded-model reuse: unverified.
- Reboot persistence: not run.

Observed issue:

- The imported model expected for Task 007C is no longer present in app-private storage on the connected device. A likely contributing factor is that earlier connected-test runs installed/reinstalled test APKs during Task 007B; however, this acceptance report records only observed state, not a proven deletion cause.
- Per the task instruction, no file picker import was started and no model/history files were deleted or modified.

## Known Limitations

- The UI tests use synthetic Compose state and fakes. They do not validate native llama.cpp behavior or model quality.
- Structural validation checks only bullet format. It does not validate factual accuracy.
- Process-death restoration for an active generation is not implemented or claimed.
- Task 006 build/device acceptance remains pending until Gradle can run outside the current sandbox blocker.
