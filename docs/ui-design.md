# PocketAI UI Design Notes

## Layout

The summariser screen now uses a Material 3 `Scaffold` with a top app bar for PocketAI and History. The main content order is:

1. Compact model status with an import/change action.
2. Source text editor.
3. Primary Summarise or Cancel action.
4. Summary reader with Copy and Save actions.

Model and runtime implementation details stay out of ordinary labels. The UI says "model" and "local summarisation"; setup docs still describe the GGUF requirement.

## Scroll Ownership

The main summariser page has one vertical scroll owner. Summary output is rendered at content height with no fixed-height container, no `maxLines`, and no ellipsis. Long output is selectable and copyable before saving.

During streaming, the page follows new output only while the reader is already at the end. If the reader scrolls upward during generation, auto-follow stops and a Jump to latest action appears. Refinement replaces first-attempt output instead of appending, preserving the bounded retry behaviour from Task 004.

## Expanded Editor

The compact source editor grows to a bounded height and then scrolls internally. Expand opens a full-screen editor using the same ViewModel draft. Done and system Back close the editor without discarding edits. Because the draft is held in `PocketAiUiState`, text edits survive Activity recreation and configuration changes.

The expanded editor uses safe drawing and IME padding so the keyboard does not cover the editing surface. Token-budget validation remains unchanged; a larger editor does not increase the model context window.

## Accessibility

Controls use Material components for touch target sizing and adapt into vertical rows on narrow screens. Source, summary, copy, save, import, cancel, and navigation actions have meaningful labels. Streaming text is not marked as a live region, so screen readers are not asked to announce every token; progress/status bands expose useful generation, refinement, warning, error, save, and cancellation states.

Saved source and summary detail text is selectable and unconstrained inside the detail screen scroll.

## Previews

Compose previews cover:

- Empty state.
- Long input and long summary.
- Generating.
- Refining.
- Format warning.
- Large font on a narrow screen.
- Dark theme.

Previews are design checks only; they do not prove device behaviour.

## Regression Checklist

- JVM unit tests: passed on October 5, 2026 with `JAVA_HOME=work/jdk17/Contents/Home ./gradlew testDebugUnitTest`.
- Connected Compose UI tests: passed on October 5, 2026 with `JAVA_HOME=work/jdk17/Contents/Home ./gradlew :app:connectedDebugAndroidTest --no-daemon` on connected `IN2021 - 13`. These tests verify rendered UI behaviours only; they do not validate real model quality or native inference safety.
- Long-output Compose regression: passed by scrolling a long synthetic summary to a unique final-line marker and asserting the final line is displayed.
- Expanded-editor reopen regression: passed by editing the shared draft, closing, reopening, and checking the inserted text remains.
- Copy-full-output regression: passed by copying a long output with an end marker and checking the full string.
- Debug APK build: passed with `JAVA_HOME=work/jdk17/Contents/Home ./gradlew assembleDebug`; refreshed artifact at `outputs/PocketAI-debug.apk`.
- Artifact checks: no `INTERNET` permission found in the debug app manifest; `outputs/PocketAI-debug.apk` contains no `gguf` or `qwen` entries; tracked files contain no `.gguf` or `.safetensors` model assets.
- Manual device acceptance: pending for keyboard behaviour, large fonts, landscape/narrow layouts, streaming manual-scroll behaviour during real generation, copy/save after real generation, cancellation during refinement, and the Task 004 lifecycle/model-recovery checklist.
