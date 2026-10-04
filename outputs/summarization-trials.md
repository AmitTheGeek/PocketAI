# PocketAI On-Device Summarization Trials

Date: October 4, 2026
Device: OnePlus 8 Pro / IN2021
Model: qwen2.5-1.5b-instruct-q4_k_m.gguf

Input used in the app:

```text
The PocketAI team met on Monday to review the Android prototype. Offline summarization is implemented, but testing on a physical phone is still pending. Amit will test the app on a OnePlus 8 Pro by Friday. Priya will prepare five evaluation samples by Thursday. Cloud integration and saved history are outside the current milestone. The next review will happen after device testing is complete.
```

Note: ADB keyboard input normalized "summarisation" to "summarization" in the app text field before these runs.

## Run 1

Elapsed time: 4.8s

```text
- PocketAI team reviewed Android prototype on Monday.
- Offline summarization is implemented.
- Testing on physical phone is pending.
- Amit will test app on OnePlus 8 Pro on Friday.
- Priya will prepare five samples by Thursday.
- Cloud integration and saved history are not part of current milestone.
- Next review will occur after device testing is complete.
```

## Run 2

Elapsed time: 4.8s

```text
- PocketAI team reviewed Android prototype on Monday
- Offline summarization implemented
- Testing on physical phone still pending
- Amit will test app on OnePlus 8 Pro by Friday
- Priya prepares five evaluation samples by Thursday
- Cloud integration and saved history outside current milestone
- Next review after device testing is complete
```

## Run 3

Elapsed time: 4.4s

```text
- PocketAI team reviewed Android prototype on Monday
- Offline summarization implemented
- Testing on physical phone still pending
- Amit will test app on OnePlus 8 Pro by Friday
- Priya will prepare five samples by Thursday
- Cloud integration and saved history outside current milestone
- Next review after device testing is complete
```

Raw UI hierarchy captures are saved in `work/summarization_trials/run_1.xml`, `run_2.xml`, and `run_3.xml`.
