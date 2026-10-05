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
- Jump to latest restores follow mode and scrolls to the current end.
- Refinement replaces first-attempt output while preserving the reader's follow/not-follow intent for the same source snapshot.
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

- JVM unit tests: attempted on October 5, 2026. The sandbox blocked Gradle access to `~/.gradle`, and the workspace-local Gradle home lacked the wrapper distribution. A direct Gradle binary then failed because the sandbox blocked Gradle's file-lock listener socket. Tests still need to be rerun outside that sandbox blocker.
- Connected Compose UI tests: not run in this pass.
- Long-output Compose regression: covered by `SummaryReadingUiTest.longSummaryFinalMarkerCanBeScrolledIntoView`, pending execution.
- Input draft return regression: covered by `SummaryReadingUiTest.inputDraftSurvivesOpeningResultAndReturning`, pending execution.
- Copy-full-output regression: covered by `SummaryReadingUiTest.copyUsesCompleteOutputText`, pending execution.
- Cancel reachability: covered by `SummaryReadingUiTest.cancelRemainsReachableAtBottomOfLongStreamingOutput`, pending execution.
- Jump/latest and refinement scroll intent: covered by `SummaryReadingUiTest.jumpToLatestAppearsAfterManualScrollAway` and `manualScrollIntentSurvivesRefinementReplacement`, pending execution.
- Manual device acceptance: pending for the split Input/Result flow, keyboard behavior, rotation, copy/save, history, real streaming, Back/cancellation, and the retained model-recovery/evaluation checklist.
