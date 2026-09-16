# Review — commit `57ee9ee`

Scope: review the current master after the 2026-09-16 real-car logs and the change that restored 4/8/8 cadence + added `SESSION_SNAPSHOT`.

## What is confirmed good

The real recording evidence in `test_logs/20260916` validates the RECORDING path:
- 58/58 RPM+MAP pairs are `pair_valid=true`.
- No raw PID failures/timeouts are present in the saved run.
- 4/8/8 during recording kept MAF <= ~2.07 s, Speed <= ~4.20 s, Load <= ~4.16 s.
- Phone BARO was stable around 1004.4–1004.5 mbar.
- Therefore **do not change the RECORDING 4/8/8 cadence merely from theoretical timing**.

Do not rewrite RFCOMM/handshake; the real connection works.

---

## P0 — LIVE and RECORDING still use the same 4/8/8 aux cadence, but only RECORDING was validated

### Files
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt` — `startTurboFastPolling()`
- `app/src/main/java/com/vag/vcdsandroid/protocol/TurboScheduler.kt`
- `app/src/test/java/com/vag/vcdsandroid/protocol/TurboSchedulerTest.kt`

### Current problem

`startTurboFastPolling()` always calls the same `turboScheduler.nextAuxPids()` regardless of whether logging is active. The only difference is that slow PIDs are disabled during recording.

The real CSV validates **RECORDING** only. In **LIVE** mode, slow PIDs are still inserted every ~2.5 s on clean cycles.

Using the measured adapter average of ~216 ms/request:
- 4/8/8 recording interval between Speed/Load samples is ~20 commands = ~4320 ms.
- One additional LIVE slow request makes it ~4536 ms, already above the 4500 ms Speed/Load freshness limit.
- If the slow slot is `0142` and it falls back to `ATRV`, two requests can make it ~4752 ms.
- MAF base interval is ~2160 ms; a 2-command slow fallback can push it to ~2592 ms, above the 2500 ms MAF freshness limit.

This means the user can again see healthy MAF/Speed/Load intermittently turn STALE before pressing START LOG, and `isWotLogReady()` can temporarily disable START despite a healthy ECU.

### Required fix

Keep **RECORDING = 4/8/8** exactly as real-car validated.

Add a separate LIVE cadence or deadline-aware schedule. Lowest-risk implementation:

```kotlin
// RECORDING
MAF every 4th pair
Speed every 8th pair
Load every 8th pair offset 4
slow PIDs OFF

// LIVE
MAF every 3rd pair
Speed every 6th pair
Load every 6th pair offset 3
slow PIDs allowed only on clean cycles
```

Do not globally change 4/8/8 again. Make the mode explicit, e.g.:

```kotlin
nextRecordingAuxPids()
nextLiveAuxPids()
```

`startTurboFastPolling()` chooses based on `asyncLogger.isLogging`.

### Tests

The current timing test only hard-codes `20 * 216` and does not simulate LIVE slow PID insertion. Add tests that actually call the scheduler and simulate both modes:
1. RECORDING 4/8/8, no slow PID.
2. LIVE 3/6/6 + one normal slow request.
3. LIVE 3/6/6 + `0142 -> ATRV` two-request slow slot.

Assert MAF/Speed/Load remain under their declared freshness limits.

---

## P1 — `SESSION_SNAPSHOT` records historically VALID samples even when they are currently STALE

### File
`app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt` — `snapshotPreLogTelemetry()`

### Current code problem

The function checks only:

```kotlin
if (sample.status == PidStatus.VALID)
```

`sample.status` is the status when the PID was originally received. It does not turn to `STALE` as time passes. A coolant/IAT/voltage/0133 sample can therefore be many seconds or minutes old and still be written as `SESSION_SNAPSHOT`.

### Required fix

For every snapshot PID, require effective freshness at snapshot time:

```kotlin
val maxAge = TelemetryFreshnessPolicy.getMaxAgeMs(pid)
val ageMs = sample.getAgeMs(nowNs)
if (sample.status == PidStatus.VALID && sample.value != null && ageMs <= maxAge) {
    ...
}
```

Or use the existing centralized fresh helper. Do not snapshot stale data as valid session state.

Also add the source age to the snapshot record so analysis can prove how old it was.

---

## P1 — `SESSION_SNAPSHOT` breaks chronological ordering in `Event_RAW.csv`

### Files
- `MainActivity.kt` — `snapshotPreLogTelemetry()`
- `AsyncCsvLogger.kt` — `logRawEvent()`

### Current problem

A snapshot gets a **new `event_seq` after logging starts**, but the code passes the **old sample's original** `timestampUtcMs` and `monoNanos`.

Result: `event_seq=1..N` can have timestamps from before the log start, while later rows have newer times. Any analyzer that assumes event sequence and timestamp are chronological can mis-order the session.

### Required fix

Treat a snapshot as metadata captured **now**:
- event `timestamp_utc_ms` = snapshot time;
- event `mono_ns` = snapshot time;
- preserve the source sample timestamp/age inside explicit metadata.

Preferred small change: extend raw CSV with optional fields:

```text
source_timestamp_utc_ms,source_mono_ns,source_age_ms
```

For normal live events these can equal the event timestamp/age 0. For `SESSION_SNAPSHOT`, event time is now and source fields identify the original PID sample.

If schema change is undesirable, at minimum put `source_utc_ms=... source_age_ms=...` into the snapshot raw text and use current time for the event columns.

---

## P1 — Current timing-budget test gives false confidence

### File
`app/src/test/java/com/vag/vcdsandroid/protocol/TurboSchedulerTest.kt`

### Current problem

`testTimingBudgetUnderMeasuredAdapterLatency()` creates a `TurboScheduler()` but does not use it. It hard-codes:

```kotlin
recordingCycleCommands = 20
cmdLatencyMs = 216
```

So the test can still pass after the production scheduler changes. It also calls a calculation from the **mean** latency a “worst case”. The real raw log contains individual requests above 216 ms.

### Required fix

Build the timing simulation from actual outputs of `nextRecordingAuxPids()` / `nextLiveAuxPids()`.

Use at least:
- measured mean = 216 ms for nominal calculation;
- a higher stress latency derived from the checked-in real log (p95 or conservative 250–275 ms) to show expected degradation explicitly instead of silently calling mean latency worst-case.

The test should validate scheduler behavior, not duplicated arithmetic constants.

---

## P1 — Public repository exposes the adapter Bluetooth MAC address

### Files currently containing it
- `AI_CONTEXT.md`
- `test_logs/20260916/ConnectionTrace_20260916_091746_781_SUCCESS.txt`
- `test_logs/20260916/ConnectionTrace_20260916_091747_178_SUCCESS.txt`

The repository is PUBLIC and the real adapter MAC is committed in plain text.

### Required fix

1. Replace the MAC in current tracked docs/test fixtures with a redacted form such as:

```text
XX:XX:XX:XX:XX:XX
```

2. In saved/shareable connection traces, redact Bluetooth addresses before writing evidence intended for GitHub.
3. Do not put the real MAC in `AI_CONTEXT.md`; agents do not need it.

Note: editing current files does not remove the value from old Git history. If full removal is desired, history must be rewritten separately. Do not rewrite history automatically in this task.

---

## P2 — No GitHub CI status exists for the latest commit

The latest commit has no GitHub status/check attached. Local reports saying tests passed are useful but not independently visible from GitHub.

To reduce repeated device/car cycles, add one minimal GitHub Actions workflow on push/PR:

```text
./gradlew testDebugUnitTest assembleDebug
```

No emulator/device CI is required yet. This gives every Gemini/Codex change an automatic offline gate before asking the user to install an APK.

---

# Required execution order for Gemini

1. **Do not touch working handshake/RFCOMM.**
2. Split LIVE vs RECORDING scheduler: LIVE 3/6/6, RECORDING 4/8/8.
3. Fix snapshot freshness.
4. Fix snapshot timestamps/metadata ordering.
5. Replace the fake timing-budget test with scheduler-driven simulation.
6. Redact MAC from current public files and future shareable traces.
7. Add minimal Gradle CI.
8. Run `gradlew.bat clean testDebugUnitTest assembleDebug` locally and post exact result.

No new TP2/N75 work and no UI redesign in this pass.

## Car gate

No additional road test is needed for these fixes. They are testable offline. After they are merged and reviewed, the next useful real-car test should be a single warm stationary validation followed, only if green, by one full 1300→4000 RPM road log.
