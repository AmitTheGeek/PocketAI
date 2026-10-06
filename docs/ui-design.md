# PocketAI UI Design Notes

## Layout

Task 006 separates editing and reading into two Compose destinations inside the same app module.

Input screen:

- Top app bar with PocketAI and History.
- Compact model readiness row with Import model or Change model.
- A spacious multiline source editor that fills remaining space.
- A bottom Summarise action outside the editor and above keyboard/navigation insets.
- Blank-input, readiness, import, load, and prompt-budget feedback stays on this screen.

Result screen:

- Top app bar with Back and Summary.
- Preparing, generating, refining, completed, cancelled, failed, warning, save, and copy feedback.
- Selectable summary text in a content-height scroll area.
- Cancel remains in the bottom action area while work is active.
- Completed results expose Copy, Save, and Edit source. Cancelled partial text can be copied but cannot be saved.

The removed expanded-editor flow is intentionally not replaced. The main editor is now the editing surface, and the draft remains in `PocketAiUiState` while moving between Input, Result, History, and Detail.

## Scroll Ownership

Input and Result own separate scroll behavior.

- The source editor scrolls internally within the Input screen.
- The Result screen owns a `ScrollState` for summary reading.
- Output is rendered without fixed-height clipping, `maxLines`, or ellipsis.
- Auto-follow is enabled only while the reader is already at the bottom.
- User scroll gestures turn auto-follow off.
- Jump to latest lives in the Result scaffold's floating action area, outside the scrolling text, so it remains reachable while the reader is above the latest output.
- Jump to latest restores follow mode and scrolls to the current end.
- Streaming follow uses conflated content-height updates with a bounded delay after each scroll. Continuous token updates do not restart the delay forever.
- Refinement replaces first-attempt output while preserving the reader's follow/not-follow intent for the same request.
- A new active request id resets follow intent even when the source text matches a previous request.
- Bottom actions are outside scrolling text, so Cancel/Copy/Save/Edit remain reachable.

## Accessibility

The screens use Material 3 components for touch target sizing and adapt actions vertically on narrow widths. Source, summary, copy, save, import, cancel, edit, history, and navigation controls have content descriptions. Streaming tokens are not exposed as a live region; status bands communicate meaningful progress changes such as Preparing, Summarising, Refining, Stopping, warnings, errors, and completion-related feedback.

Safe drawing, navigation-bar, and IME padding keep bottom actions from covering the last input/output line. Large-font and narrow-screen previews are design checks only; they do not prove physical-device behavior.

## Previews

Compose previews cover:

- Input empty state.
- Input with long source text.
- Result with long summary.
- Generating.
- Refining.
- Format warning.
- Large font on a narrow screen.
- Dark theme.

## Regression Checklist

- JVM unit tests: passed on October 6, 2026 with `JAVA_HOME=$PWD/work/jdk17/Contents/Home ./gradlew testDebugUnitTest`.
- Connected Compose UI tests: passed on October 6, 2026 on the OnePlus IN2021 / Android 13 with `:app:connectedDebugAndroidTest` after the phone was awake and unlocked. The earlier `No compose hierarchies found in the app` failure was traced to the device being asleep/locked.
- Long-output Compose regression: covered by `SummaryReadingUiTest.longSummaryFinalMarkerCanBeScrolledIntoView`, passed in the app-only connected suite.
- Input draft return regression: covered by `SummaryReadingUiTest.inputDraftSurvivesOpeningResultAndReturning`, passed in the app-only connected suite.
- Copy-full-output regression: covered by `SummaryReadingUiTest.copyUsesCompleteOutputText`, passed in the app-only connected suite.
- Cancel reachability: covered by `SummaryReadingUiTest.cancelRemainsReachableAtBottomOfLongStreamingOutput`, passed in the app-only connected suite.
- Jump/latest, continuous streaming follow, and refinement scroll intent: covered by `SummaryReadingUiTest.jumpToLatestVisibleWhileReadingEarlierContent`, `continuousSyntheticStreamingFollowsBeforeGenerationEnds`, and `manualScrollIntentSurvivesRefinementReplacement`, passed in the app-only connected suite.
- Manual device acceptance: remembered-model recognition after force-stop, History without native load, saved detail inspection, and first post-relaunch inference passed in Task 007C. Remaining manual checks include split Input/Result keyboard behavior, rotation, real streaming Back/cancellation, model-recovery, second post-relaunch model reuse, restart persistence, and the retained five-case evaluation checklist.
