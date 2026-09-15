# PRE-CAR REVIEW — commit d2cbe72

Goal: remove software-side ambiguity **before another road test**. Do not add TP2.0/N75 features and do not redesign the UI. Fix/test everything below on PC first. The user should need one short stationary car session, not repeated road runs.

## Ground truth

Current commit reviewed: `d2cbe72b176d499396588ef5273e9e502bbc22fc`.

Known priorities:
- Mode A / Turbo Fast must be reliable first.
- Core data is `010C RPM` + `010B MAP`.
- BARO must be real (`0133` or measured engine-off MAP), never 1000/1013 fallback.
- No fake N75 / Specified Boost.
- Bluetooth/RFCOMM transport is already basically working; do not rewrite it without evidence.

---

# P0 — FIX BEFORE ANY MORE CAR TESTING

## 1. Clear all telemetry/session state on reconnect

### File
`app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt`

### Problem
`latestSamples` is never cleared on disconnect or before a new ELM session. That means a VALID PID from an old session can survive into the next one.

This is especially dangerous for BARO because `resolveBaro()` currently trusts `latestSamples["0133"]` by `status == VALID` without checking session identity. An old BARO can therefore be reused after reconnect.

The bus counters also survive a partial previous window (`windowTotalReqs`, RPM/MAP/MAF counters, latency sum).

### Required implementation
Create exactly one helper, for example:

```kotlin
private fun resetTurboSessionState() {
    latestSamples.clear()

    calibratedBaroMbar = null
    calibratedBaroSource = "UNSET"

    busReqRate = 0.0
    busRpmHz = 0.0
    busMapHz = 0.0
    busMafHz = 0.0
    busAvgLatency = 0L

    windowStart = 0L
    windowTotalReqs = 0
    windowRpmCount = 0
    windowMapCount = 0
    windowMafCount = 0
    windowLatencySum = 0L
}
```

Call it:
1. immediately before starting a NEW ELM connection in `startElmConnection()`;
2. after polling/logging has stopped in `performDisconnect()`;
3. on mode switch before displaying Turbo Fast data from a new session.

Also reset the visible telemetry fields to `---` / `IDLE`, so values from a previous session never remain visible.

### Acceptance test (PC)
Add a unit-testable session-state helper or equivalent test proving:
- Session A has valid BARO/RPM/MAP.
- New session begins.
- No Session A value is visible/resolvable in Session B before new responses arrive.

---

## 2. Turbo Fast connection success MUST require BOTH RPM and MAP

### File
`app/src/main/java/com/vag/vcdsandroid/protocol/Elm327DiagnosticEngine.kt`

### Current bug
The new 3-stage handshake marks the ECU CONNECTED if **any one** of these succeeds:
- `010C`, OR
- `010B`, OR
- `0100`.

That is too weak for Turbo Fast. Turbo Fast cannot function correctly unless **both `010C` and `010B` are actually readable**.

The current logic can therefore show `Connected` and enable logging even when MAP or RPM is missing.

### Required implementation
For each connection stage, probe both core PIDs before calling `markObdConnected()`.

Stage 1:
```text
ATD -> basic init -> ATSP0
010C
010B
```

Success only when:
```text
010C: promptReceived && !timedOut && contains 410C
AND
010B: promptReceived && !timedOut && contains 410B
```

`0100` may be queried for diagnostic evidence/supported-PID info, but **0100 alone must never make Turbo Fast CONNECTED**.

Same rule for Stage 2 and Stage 3.

Do not accept a partial payload with `timedOut=true` as successful connection even if the string happens to contain `410C`/`410B`.

Recommended helper:

```kotlin
private fun validPidReply(resp: ElmResponse, marker: String): Boolean {
    return resp.promptReceived &&
           !resp.timedOut &&
           cleanHexResponse(resp.raw).contains(marker)
}
```

Then:

```kotlin
val rpmOk = validPidReply(rpmResp, "410C")
val mapOk = validPidReply(mapResp, "410B")
if (rpmOk && mapOk) markObdConnected(...)
```

### Acceptance tests (PC; mandatory)
Extract the stage probe decision into a pure/internal helper so it can be tested with fake `ElmResponse` values.

Test cases:
1. `010C OK + 010B OK` => success.
2. `010C OK + 010B NO DATA` => NOT success.
3. `010C TIMEOUT but raw contains 410C + 010B OK` => NOT success.
4. only `0100 OK` => NOT success.
5. Stage 1 fails, Stage 2 has RPM+MAP => Stage 2 success.
6. Stages 1/2 fail, Stage 3 has RPM+MAP => Stage 3 success.

---

## 3. BARO must be SESSION-CACHED and must survive a transient 0133 failure

### Files
- `MainActivity.kt`
- optionally extract a small pure `SessionBaroResolver.kt`

### Current bug
Current flow does this:
- valid `0133` is stored in `calibratedBaroMbar`, source `PID_0133`;
- later a single `0133` timeout/NO DATA overwrites `latestSamples["0133"]` with invalid status;
- `resolveBaro()` only falls back to `calibratedBaroMbar` when source is `ENGINE_OFF_MAP`, not when source is `PID_0133`;
- result: Boost can suddenly become `N/A` after one transient BARO read failure.

There is also the cross-session stale-data problem from P0.1.

### Required implementation
Maintain one explicit session BARO cache that is updated **only by valid measurements**:

```kotlin
private var sessionBaroMbar: Double? = null
private var sessionBaroSource: String = "UNAVAILABLE"
private var sessionBaroMonoNs: Long = 0L
```

Rules:
1. Valid `0133` => update cache and source `PID_0133`.
2. If no valid `0133` has been obtained in this session, valid MAP with a **fresh** valid RPM <= 50 can set `ENGINE_OFF_MAP`.
3. TIMEOUT / NO DATA / INVALID_FORMAT must NOT erase the last valid session BARO.
4. New connection/disconnect clears the cache.
5. No 1000/1013 fallback.
6. `resolveBaro()` reads this session cache only; it must not directly trust an arbitrary old `latestSamples["0133"]`.

For engine-off MAP fallback, require RPM age <= ~1000 ms, not only `status == VALID`.

### Acceptance tests (PC)
- valid 0133 = 990 => resolver returns 990 PID_0133.
- next 0133 TIMEOUT => still returns 990 PID_0133.
- new session => returns UNAVAILABLE.
- fresh RPM 0 + MAP 990 => ENGINE_OFF_MAP 990.
- stale RPM 0 from old timestamp + MAP 1000 => must NOT calibrate.
- RPM > 50 + MAP 1000 => must NOT calibrate.

---

## 4. START LOG must require fresh core telemetry, not merely CONNECTED state

### File
`MainActivity.kt`

### Current bug
`updateStatusUI()` enables `btnToggleLog` whenever ECU state is CONNECTED/POLLING.

That is insufficient. A connection can exist while current RPM/MAP samples are absent/stale. The user can start a road log that is already unusable.

### Required implementation
Add:

```kotlin
private fun isCoreTelemetryReady(nowNs: Long = SystemClock.elapsedRealtimeNanos()): Boolean {
    val rpm = latestSamples["010C"] ?: return false
    val map = latestSamples["010B"] ?: return false
    return rpm.status == PidStatus.VALID &&
           map.status == PidStatus.VALID &&
           rpm.getAgeMs(nowNs) <= 1000L &&
           map.getAgeMs(nowNs) <= 1000L
}
```

For a **Boost/WOT-ready** verdict also require `resolveBaro().valueMbar != null`.

Button rule when not already recording:
```text
START LOG enabled only if:
ECU connected
AND RPM fresh
AND MAP fresh
AND BARO source available
```

If RPM/MAP are connected but BARO is unavailable, show explicitly:
`RAW TELEMETRY OK — BOOST NOT READY: engine off + ignition on once for BARO baseline`

Do not send the user onto the road with a green START LOG button when core data is stale/missing.

---

## 5. Pre-flight must use REQUIRED vs OPTIONAL channels and real fallbacks

### File
`MainActivity.kt`, function `runPreFlightCheck()`

### Current issue
It reports `x/9 sensors`, but:
- `0133` may legitimately be unsupported even though engine-off MAP is a valid BARO source;
- `0142` may be unsupported while `ATRV` can provide adapter/control voltage information;
- all 9 are treated equally, even though RPM/MAP are essential and coolant is not essential for a turbo WOT log.

### Required categories

**REQUIRED FOR WOT:**
- 010C RPM
- 010B MAP
- real BARO source (`0133` OR session `ENGINE_OFF_MAP`)

**RECOMMENDED:**
- 0110 MAF
- 010D Speed
- 0104 Load

**OPTIONAL:**
- 0105 Coolant
- 010F IAT
- voltage (`0142`, fallback `ATRV`)

### Voltage preflight
If `0142` is not valid, immediately test `ATRV` and decode it with `decodeVoltage()` before declaring voltage unavailable.

### BARO preflight
If `0133` is unavailable but the session has a valid engine-off MAP baseline, BARO = PASS (`ENGINE_OFF_MAP`).

### Verdicts
Only show `READY TO LOG` when required WOT items pass.

Example:
```text
READY TO LOG
RPM OK | MAP OK | BARO 990 ENGINE_OFF_MAP
MAF OK | SPEED OK | LOAD OK
COOLANT OK | IAT OK | VOLTAGE ATRV
```

If core fails:
```text
NOT READY — DO NOT DRIVE
RPM OK | MAP TIMEOUT
```

---

## 6. Do not let CHECK DATA / RPM STRESS modify an active WOT log

### File
`MainActivity.kt`

### Current bug
`runPreFlightCheck()` and `runRpmStressTest()` do not reject execution while `asyncLogger.isLogging`.

A tap/long-press during recording can pause the normal scheduler and pollute the CSV with a diagnostic sequence.

### Required implementation
At the first line of both functions:

```kotlin
if (asyncLogger.isLogging) {
    Toast.makeText(this, "Stop the current log first", Toast.LENGTH_SHORT).show()
    return
}
```

While logging:
- disable `CHECK DATA`;
- disable mode switching;
- preferably disable Disconnect, or make Disconnect first await a clean logger stop.

Re-enable after `stopWotLog()` and call `updateStatusUI()`.

---

## 7. Track/cancel the RPM stress-test coroutine

### File
`MainActivity.kt`

### Current bug
`runRpmStressTest()` launches an untracked `lifecycleScope.launch`.
`stopPolling()` cancels `pollingJob` and `preFlightJob`, but it cannot cancel the stress test.

If the user disconnects/switches mode during the 10 s test, the stress coroutine can keep sending `010C` against a closed transport and later pop a stale result dialog.

### Required implementation
Add:

```kotlin
private var stressJob: Job? = null
```

Assign the 10 s coroutine to it and cancel it from `stopPolling()` / disconnect / mode switch.

---

## 8. Reset logging UI/state on disconnect

### File
`MainActivity.kt`

### Current bug
If Disconnect or mode switch occurs while logging:
- logger stop is launched asynchronously;
- button text/color may remain `STOP LOG` even after logging has ended;
- reconnect can therefore display state inconsistent with `asyncLogger.isLogging`.

### Required implementation
Create one helper:

```kotlin
private fun renderLoggingState() { ... }
```

It must derive text/color/enabled from `asyncLogger.isLogging` + readiness, never from stale button state.

After every:
- start log,
- stop log,
- disconnect,
- mode switch,
call the same helper.

Do not manually leave button text in multiple code paths.

---

# P1 — PERFORMANCE FIXES BEFORE ROAD WOT

## 9. Current scheduler still wastes WOT bandwidth

### File
`MainActivity.kt`, `startTurboFastPolling()`

### Current implementation
The 9-step loop includes six RPM+MAP pairs plus separate MAF/Speed/Load steps, and slow PIDs are injected every 2.5 s. This is acceptable for live dashboard use, but it still wastes scarce ELM bandwidth during the short 1300→4000 RPM WOT sweep.

With a real adapter around ~200+ ms per command, every extra coolant/IAT/voltage/BARO request can remove a core RPM/MAP sample from the acceleration window.

### Required implementation: TWO schedules

#### LIVE / NOT RECORDING
Keep full dashboard updates (current behavior is acceptable after fixes).

#### RECORDING / TURBO CORE
While `asyncLogger.isLogging == true`:
1. every loop ALWAYS query `010C` then `010B`;
2. every 6th pair query `0110` MAF;
3. every 12th pair query `010D` Speed;
4. every 12th pair, offset by 6, query `0104` Load;
5. DO NOT poll `0105`, `010F`, `0142`, `0133` during the short WOT recording; keep the last pre-flight values + ages in CSV/UI.

Example:

```kotlin
var pairCounter = 0
while (logging) {
    readRpmMapPair()
    pairCounter++

    if (pairCounter % 6 == 0) readMaf()
    if (pairCounter % 12 == 0) readSpeed()
    if (pairCounter % 12 == 6) readLoad()
}
```

When logging stops, return to LIVE/full scheduler.

### Required metric
Show actual `RPM Hz` and `MAP Hz` during recording. Do not claim theoretical rates.

---

## 10. RAW DEBUG must show the actual transmitted command

### Files
- `MainActivity.kt`
- `DiagnosticSample` model (currently local in MainActivity)
- optionally CSV raw event

### Current bug
For voltage fallback, the app may actually transmit `ATRV`, but the generated sample has PID `0142`, so RAW DEBUG can display:

```text
TX: 0142
RX: 14.2V
```

That is false diagnostic evidence.

### Required implementation
Add `requestCommand` to `DiagnosticSample`, defaulting to PID for normal Mode 01 reads.

For ATRV fallback:
```text
pid = 0142
requestCommand = ATRV
```

RAW DEBUG must render `requestCommand`.

Recommended RAW CSV columns:
```text
pid,request,tx_mono_ns,rx_mono_ns,value,unit,raw,latency_ms,status
```

This makes future debugging possible from one saved log without another car visit.

---

## 11. Save connection trace on SUCCESS too, not only failure

### Files
- `Elm327DiagnosticEngine.kt`
- `MainActivity.kt`

### Current issue
`lastConnectTrace` is populated on failure paths, but a successful connection trace is exactly what we need to prove which stage/protocol worked and avoid repeating experiments.

### Required implementation
After connection success and after `ATDP/ATDPN`, set:

```kotlin
lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
```

Also store the selected success source (`AUTO`, `SP6_DEFAULT`, `PHYSICAL_7E0`) in a field like `lastConnectStage`.

Automatically save every attempt to:

```text
Documents/VCDS_Logs/ConnectionTrace_YYYYMMDD_HHmmss_SUCCESS.txt
Documents/VCDS_Logs/ConnectionTrace_YYYYMMDD_HHmmss_FAIL.txt
```

Include:
- ELM ATI string;
- selected Bluetooth device;
- each connection stage;
- RAW 010C/010B probes;
- ATDP/ATDPN;
- final stage/verdict.

This is much better than requiring the user to stand by the car and copy logcat.

---

# P1 — OFFLINE TESTS: MUST PASS BEFORE INSTALLING ANOTHER APK

## 12. Add PidDecoderTest.kt using known real responses

Create:
`app/src/test/java/com/vag/vcdsandroid/protocol/PidDecoderTest.kt`

Mandatory cases:

```text
410C0000 -> 0 rpm
410C3E80 -> 4000 rpm
410B63   -> 990 mbar absolute
410BF3   -> 2430 mbar absolute
4110001E -> 0.30 g/s
411022AB -> 88.75 g/s
410400   -> 0.0 % load
410D57   -> 87 km/h
NO DATA  -> NO_DATA
TimedOut=true even with payload -> TIMEOUT
ATRV raw "14.2V" -> 14.2 V
```

Do not go back to the car until these pass.

---

## 13. Add GenericObdHandshakeTest.kt with fake responses

Do not mock Android Bluetooth. Extract only the handshake decision/order into a small pure/internal component that accepts a send lambda:

```kotlin
suspend (cmd: String, timeoutMs: Long) -> ElmResponse
```

Then unit-test exact stages and exact success criteria.

Mandatory cases:
- AUTO RPM+MAP valid => success AUTO; Stage2/3 never run.
- AUTO only RPM => not ready; next stage runs.
- AUTO only MAP => not ready; next stage runs.
- AUTO only 0100 => not ready.
- AUTO timeout with partial payload => not ready.
- Stage2 RPM+MAP valid => success Stage2.
- Stage3 RPM+MAP valid => success Stage3.
- all fail => deterministic failure + trace.

Also assert command order so a future edit cannot accidentally place `ATSH/ATCRA` before AUTO again.

---

## 14. Add session BARO tests

Extract only enough BARO state to test it without Android UI.

Mandatory tests listed in P0.3.

---

## 15. Add scheduler test

Extract the schedule decision from Android UI code or expose a pure helper.

Assert over a sufficiently long generated sequence:
- in RECORDING mode every cycle contains RPM then MAP;
- no `0105/010F/0142/0133` while recording;
- MAF every 6 pairs;
- Speed every 12 pairs;
- Load every 12 pairs offset by 6;
- in LIVE mode slow sensors still occur.

---

# P2 — OTHER REAL BUGS FOUND IN THIS REVIEW

## 16. Mode B must never display fake OEM zeros after TP2.0 failure

### Files
- `MainActivity.kt`, `startOemPolling()`
- `Elm327DiagnosticEngine.kt`, generic group fallback methods

### Current bug
If Mode B TP2.0 fails but Generic OBD connects, `readGroup011Generic()` returns:
- Specified Boost `rawValue = 0.0`, formatted `N/A`
- N75 `rawValue = 0.0`, formatted `N/A`

But `startOemPolling()` reads `rawValue` and displays/graphs `0` as though it were real OEM data.

This violates the no-fake-data requirement.

### Required behavior
If `connectionMode == VAG_OEM_TP20` and `elmEngine.isTp20Active == false`:
- do NOT start the OEM Group 011/008/003 graph loop;
- show `TP2.0 unavailable — use Mode A Generic OBD`;
- Specified Boost/N75/Driver Wish/Torque/Smoke remain N/A;
- never graph a synthetic/zero placeholder as ECU data.

---

## 17. DTC buttons use the wrong engine in Bluetooth Mode B

### File
`MainActivity.kt`, listeners for `btnScanDtc` and `btnClearDtc`

### Current bug
They always call the USB `engine.readFaultCodes()` / `engine.clearFaultCodes()`.

In Bluetooth VAG OEM mode they must call `elmEngine.readFaultCodes()` / `elmEngine.clearFaultCodes()`.

Route by `connectionMode` explicitly.

---

## 18. Generic MAF unit is mislabeled in OEM fallback UI

`readGroup003Generic()` supplies actual MAF in `g/s`, while `startOemPolling()` formats it as `mg/s`.

Either remove generic fallback from Mode B as required above, or preserve the real unit from `MeasuringValue.unit`.

---

# REQUIRED PC VERIFICATION BEFORE THE USER RETURNS TO THE CAR

Run and paste exact output into Issue #1:

```text
gradlew.bat clean testDebugUnitTest assembleDebug
```

Required:
- all old TP2 parser tests pass;
- all new PidDecoder tests pass;
- all handshake tests pass;
- all BARO session tests pass;
- scheduler tests pass;
- APK assembles.

Also run a project-wide search and report ZERO unsafe matches in production code for:

```text
?: 1000.0
?: 1013.0
N75 hardcoded numeric placeholder shown as real
specifiedBoost hardcoded numeric placeholder shown as real
```

Do not write `verified on real car` from unit/build success.

---

# ONE SHORT CAR SESSION AFTER ALL PC TESTS PASS

The goal is to finish all hardware-dependent evidence in one visit.

## Phase A — ignition ON, engine OFF (no driving)
1. Plug V-LINK.
2. Ignition ON, engine OFF.
3. Open Mode A and Connect once.
4. App must automatically save `ConnectionTrace_*`.
5. Required live state:
   - RPM = 0 and VALID;
   - MAP approximately atmospheric and VALID;
   - BARO = PID_0133 or ENGINE_OFF_MAP;
   - START LOG only becomes enabled when required data is ready.
6. Run CHECK DATA once.

If this phase fails, **do not start engine and do not road-test**. Send only the saved connection/preflight report.

## Phase B — engine idle, stationary
1. Start engine.
2. Verify RPM ~idle updates and MAP/MAF update.
3. Long-press CHECK DATA: 10 s RPM stress test at idle.
4. Automatically save stress report.
5. Confirm timeouts are zero or clearly quantified.

Still no road test.

## Phase C — one stationary gentle rev
Only if A+B are green:
- gently raise RPM to ~2500–3000 once;
- confirm live RAW 010C follows it and max RPM in stress/live evidence is plausible.

## Phase D — road WOT
Only after A/B/C pass. Then one 1300→4000 log should be enough.

---

# FINAL ACCEPTANCE CRITERIA

Do not mark this review complete until:

1. New sessions cannot inherit any previous PID/BARO value.
2. Turbo Fast connection requires BOTH valid 010C and 010B.
3. A transient 0133 failure does not erase a valid session BARO.
4. START LOG is impossible while RPM/MAP/BARO readiness is not satisfied.
5. Preflight distinguishes required/recommended/optional channels and uses ATRV/BARO fallback correctly.
6. Preflight/stress cannot run during a WOT recording.
7. Stress test is cancellable on disconnect/mode switch.
8. Logging UI cannot remain stuck in STOP/REC state after disconnect.
9. Recording scheduler prioritizes RPM/MAP and suspends slow sensors.
10. RAW debug/CSV identifies the actual command (including ATRV fallback).
11. Connection trace is saved on both success and failure.
12. PidDecoder + handshake + BARO + scheduler unit tests pass on PC.
13. Mode B cannot display fake N75/Specified/IQ zeros after TP2 failure.
14. DTC buttons route to the correct engine for the selected transport.
15. Only after all above: one short stationary hardware session, then one road log if green.

Do not add unrelated features until these acceptance criteria are satisfied.