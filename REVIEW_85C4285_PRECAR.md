# REVIEW 85c4285 — PRE-CAR GATE

Target commit reviewed: `85c4285107e976312dc42d4a2d7c82fc50ac8963`

Purpose: remove the remaining failure modes that can waste another car visit. Do **not** add unrelated features, redesign UI, or work on TP2/N75 in this pass.

## Current status

The previous review was substantially implemented: Generic OBD now requires valid RPM+MAP, session BARO exists, stale session samples are cleared, WOT has a dedicated scheduler, stress/preflight jobs are cancellable, success/fail traces are written, actual TX command is logged, and offline tests were added.

However, the current code still has several reliability gaps that can make one road run useless even though the screen initially says READY.

---

# P0 — MUST FIX BEFORE ASKING USER TO GO TO CAR

## 1. `BluetoothElmTransport.kt`: timeout recovery can desynchronize command/response pairing

### Current problem

`sendCommand()` does this after a previous timeout:

```kotlin
if (lastCommandTimedOut.get()) {
    recoverInputBuffer(input, 300L)
    lastCommandTimedOut.set(false)
}
```

`recoverInputBuffer()` waits only 300 ms for `>` and returns nothing. Even if no prompt was recovered, the next PID is transmitted anyway.

A late response from the timed-out command can then arrive after the next request and be consumed as that next request's response. That can create false RPM/MAP values, parse failures, or apparent missing RPM sweep.

### Required implementation

Replace `recoverInputBuffer()` with a function that reports whether the ELM prompt boundary was actually recovered:

```kotlin
private fun recoverToPrompt(input: InputStream, timeoutMs: Long = 1200L): Boolean {
    rxLeftover.setLength(0)
    val start = SystemClock.elapsedRealtime()
    try {
        while (SystemClock.elapsedRealtime() - start < timeoutMs) {
            while (input.available() > 0) {
                val c = input.read().toChar()
                if (c == '>') {
                    rxLeftover.setLength(0)
                    return true
                }
            }
            Thread.sleep(2)
        }
    } catch (_: Exception) {
        return false
    }
    return false
}
```

Then in `sendCommand()`:

```kotlin
if (lastCommandTimedOut.get()) {
    val recovered = recoverToPrompt(input, 1200L)
    lastCommandTimedOut.set(false)
    if (!recovered) {
        val now = SystemClock.elapsedRealtimeNanos()
        return@withLock ElmResponse(
            command = cmd,
            raw = "TRANSPORT_DESYNC",
            promptReceived = false,
            timedOut = true,
            elapsedMs = 1200L,
            txNanos = 0L,
            rxNanos = now
        )
    }
}
```

Important:
- if recovery fails, **do not transmit the next PID**;
- do not silently continue with an unknown stream boundary;
- use `SystemClock.elapsedRealtime()` for timeout timing, not wall clock.

Also clear `rxLeftover` on every failed recovery.

### Add test

Add a transport/recovery unit test or extract prompt recovery into a small testable helper. Required cases:
1. prompt `>` arrives within recovery window -> next command allowed;
2. no prompt -> recovery returns false;
3. delayed payload without `>` -> not considered recovered;
4. stale bytes before `>` are discarded.

---

## 2. Add a core telemetry health watchdog during LIVE and especially RECORDING

### Current problem

`startTurboFastPolling()` loops while `elmEngine.state` is CONNECTED/POLLING. PID timeouts do not change engine state. Therefore the app can remain green/recording while RPM/MAP are repeatedly timing out.

This is unacceptable for a WOT log: the user can finish a run and discover afterwards that acquisition died mid-run.

### Required implementation

Create a small pure class in:

`app/src/main/java/com/vag/vcdsandroid/protocol/CoreTelemetryHealth.kt`

```kotlin
class CoreTelemetryHealth(
    private val maxConsecutiveFailures: Int = 3
) {
    var consecutiveFailures: Int = 0
        private set

    fun onPair(rpm: PidStatus, map: PidStatus): Boolean {
        val healthy = rpm == PidStatus.VALID && map == PidStatus.VALID
        if (healthy) {
            consecutiveFailures = 0
            return true
        }

        val hardFailure = rpm in setOf(PidStatus.TIMEOUT, PidStatus.ERROR, PidStatus.INVALID_FORMAT) ||
                map in setOf(PidStatus.TIMEOUT, PidStatus.ERROR, PidStatus.INVALID_FORMAT)

        if (hardFailure) consecutiveFailures++
        return consecutiveFailures < maxConsecutiveFailures
    }

    fun reset() {
        consecutiveFailures = 0
    }
}
```

If you prefer to count `NO_DATA` too, do so only after documenting it in the test. Do not let one single NO DATA kill the session.

In `MainActivity.queryRpmMapPair()` after both decoders are available:

```kotlin
val linkHealthy = coreTelemetryHealth.onPair(rpmDec.status, mapDec.status)
if (!linkHealthy) {
    handleCoreTelemetryLost()
    return
}
```

Add `handleCoreTelemetryLost()` that:
- stops the active CSV logger safely;
- cancels polling;
- disconnects/invalidates the ELM session;
- shows a red status: `CORE TELEMETRY LOST — LOG STOPPED`;
- tells user to reconnect;
- never leaves `REC ACTIVE` visible after logging actually stopped.

Do not auto-ATZ after one timeout.

### Add test

`CoreTelemetryHealthTest.kt`:
- VALID/VALID resets counter;
- one timeout does not trip;
- two consecutive failures do not trip;
- third consecutive hard failure trips;
- valid pair after failures resets counter;
- auxiliary PID success must not reset core failure counter.

---

## 3. Fix mode-switch / in-flight connection race; `sessionGeneration` is currently unused

### Current problem

`switchConnectionMode()` calls asynchronous `performDisconnect(oldMode)` and immediately changes `connectionMode`.

`startElmConnection()` launches another coroutine but does not capture/validate a generation token. `sessionGeneration` is incremented but never checked.

A stale connection coroutine can finish after a mode change and start polling the wrong mode, while the old disconnect coroutine can close a newly opened shared ELM connection.

### Required implementation

Add:

```kotlin
private var elmConnectJob: Job? = null
```

At the start of `startElmConnection()`:

```kotlin
elmConnectJob?.cancel()
resetTurboSessionState()
val myGeneration = sessionGeneration
val myMode = connectionMode
```

Store the job:

```kotlin
elmConnectJob = lifecycleScope.launch {
    val success = elmEngine.connect(device, forceGeneric = myMode == AppConnectionMode.TURBO_FAST_OBD)

    if (!isActive || myGeneration != sessionGeneration || myMode != connectionMode) {
        if (success) elmEngine.disconnect()
        return@launch
    }

    ... existing success/failure UI logic ...
}
```

In `stopPolling()` / disconnect path:

```kotlin
elmConnectJob?.cancel()
elmConnectJob = null
```

During a connection attempt disable:
- mode toggle;
- connect button;
- CHECK DATA;
- START LOG.

Re-enable only when the current generation's connection attempt completes.

**Do not** let a stale coroutine call `startTurboFastPolling()` after mode change.

### Add testable helper if needed

If Android coroutine/UI testing is awkward, extract a small `ConnectionGenerationGate` pure class and test stale/current token behavior.

---

## 4. Reset connection metadata at every new connect and save every failure trace

### Current problem

At the start of `Elm327DiagnosticEngine.connect()` only `lastError` and `_logHistory` are reset. These can retain stale previous-session metadata:
- `lastConnectStage`
- `obdProtocol`
- `elmVersionString`
- `lastConnectTrace`

Also `dev == null` and RFCOMM failure currently do not call `saveConnectionTrace(false, ...)`.

### Exact fix

At the beginning of `connect()` before device lookup:

```kotlin
lastError = null
lastConnectStage = ""
obdProtocol = ""
elmVersionString = ""
lastConnectTrace = ""
_logHistory.clear()
```

For `dev == null`:

```kotlin
appendLog("ERR: $err")
lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
saveConnectionTrace(false, "NO_PAIRED_DEVICE", "none")
```

For RFCOMM failure:

```kotlin
appendLog("ERR: $err")
lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
saveConnectionTrace(false, "RFCOMM_FAIL", dev.name ?: dev.address)
```

On TP2 success, append the final success marker **before** saving the trace so the file itself proves success.

---

## 5. Pre-flight green state is too permissive for the user's goal

### Current problem

Current PRE-FLIGHT shows `🟢 READY TO LOG` when only:
- RPM valid
- MAP valid
- BARO available

MAF / Speed / Load can all be N/A and the app still tells the user READY. That defeats the explicit goal: see before driving whether the channels needed for the tune log are actually being captured.

### Required behavior

Use three levels:

#### GREEN — `READY TO LOG`
Require:
- RPM
- MAP
- BARO
- MAF
- Speed
- Load

All must be valid in current pre-flight.

Coolant / IAT / Voltage remain optional.

#### AMBER — `CORE READY, AUX MISSING — DO NOT WOT YET`
RPM+MAP+BARO valid but one or more of MAF/Speed/Load missing.
Explicitly list missing channels.

#### RED — `NOT READY`
RPM or MAP or BARO missing.

For the user's current workflow, `START LOG` should only become enabled on GREEN readiness. Do not let a road WOT start with MAF/Speed/Load already known missing.

Store a current-session boolean such as:

```kotlin
private var preflightFullReady = false
```

Reset it in `resetTurboSessionState()`.
Set true only after the GREEN pre-flight condition.

Update `isWotLogReady()`:

```kotlin
return isConnected && hasCore && hasBaro && preflightFullReady
```

If you want START LOG without running CHECK DATA, then replace the boolean with fresh live checks for all 6 channels; do not silently weaken the gate.

---

# P1 — FIX IN SAME PASS IF POSSIBLE

## 6. Recording aux cadence does not match freshness limits

Current recording scheduler:
- MAF every 6th RPM+MAP pair;
- Speed every 12th;
- Load every 12th offset.

At the previously measured ~200–220 ms per ELM command, one RPM+MAP pair takes ~400–450 ms. Therefore approximately:
- MAF updates every ~2.5–3 s;
- Speed/Load every ~5–6 s.

But pair CSV currently accepts:
- MAF only if age <= 1500 ms;
- Speed/Load only if age <= 2000 ms.

Result: many TurboPair rows will have blank MAF/Speed/Load despite those sensors working.

### Required fix

Prefer modestly higher aux cadence without harming core RPM/MAP:

```kotlin
MAF every 4th pair
Speed every 8th pair
Load every 8th pair offset by 4
```

And use age limits that truthfully reflect this schedule:

```kotlin
MAF_MAX_AGE_MS = 2500L
SPEED_MAX_AGE_MS = 4500L
LOAD_MAX_AGE_MS = 4500L
```

Keep the exact age columns in CSV, so downstream analysis can reject stale samples more strictly if needed.

Update `TurboSchedulerTest` accordingly.

---

## 7. RPM stress summary must include maximum latency

The earlier acceptance criteria require mean / median / p95 / **max** latency.

Add:

```kotlin
val maxLatency = sortedLatencies.maxOrNull() ?: 0L
```

and show it in both dialog and logcat summary.

---

## 8. Async logger needs a writer-health signal

If the writer coroutine throws and exits, `isLoggingActive` can remain true. The UI can therefore still show REC ACTIVE even though disk writing has died.

Add to `AsyncCsvLogger`:

```kotlin
private val writerHealthy = AtomicBoolean(false)
@Volatile var lastWriterError: String? = null
    private set

val isWriterHealthy: Boolean
    get() = writerHealthy.get() && writerJob?.isActive == true
```

On start:

```kotlin
lastWriterError = null
writerHealthy.set(true)
```

In writer catch:

```kotlin
lastWriterError = e.message
writerHealthy.set(false)
isLoggingActive.set(false)
```

In finally:

```kotlin
writerHealthy.set(false)
```

During recording UI ticker:
- if logging was expected but writer health becomes false, stop the log state and show RED `LOGGER FAILED`;
- display `lastWriterError` in debug/status.

Add one unit/integration test if practical; at minimum make the state transition deterministic.

---

## 9. Android Bluetooth permissions: check CONNECT and SCAN independently

Current code requests both permissions only when `BLUETOOTH_CONNECT` is missing. If CONNECT is granted but SCAN is denied, `cancelDiscovery()` may throw `SecurityException` and connection quality can degrade.

Change the permission gate to require both on Android 12+:

```kotlin
val missing = mutableListOf<String>()
if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
    missing += Manifest.permission.BLUETOOTH_CONNECT
}
if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
    missing += Manifest.permission.BLUETOOTH_SCAN
}
if (missing.isNotEmpty()) {
    requestPermissions(missing.toTypedArray(), 101)
    return
}
```

Use Activity Result API or `onRequestPermissionsResult()` so a successful grant resumes the intended connect flow, instead of requiring the user to tap Connect again.

---

# CLEANUP — DO NOT LET THIS DISTRACT FROM P0

Remove now-unused private helpers in `Elm327DiagnosticEngine.kt` if they are truly unused:
- `isElmOk()`
- `configureBasicGenericObd()`

Do not refactor unrelated files.

---

# OFFLINE VERIFICATION GATE

Do not ask user to visit the car yet.

Run fresh:

```bat
gradlew.bat clean testDebugUnitTest assembleDebug
```

Required new/updated tests:
- `PidDecoderTest`
- `GenericObdHandshakeTest`
- `SessionBaroResolverTest`
- `TurboSchedulerTest`
- `CoreTelemetryHealthTest`
- transport prompt-recovery/desync test
- generation/stale-connect gate test if extracted

Then report exact:
- total tests / failures;
- APK path;
- changed files;
- proof that the `TRANSPORT_DESYNC` path cannot send another PID before prompt recovery;
- proof that 3 consecutive core pair failures stop logging;
- proof that stale connect generation cannot start polling;
- proof that GREEN pre-flight requires RPM+MAP+BARO+MAF+Speed+Load.

Do **not** write “fixed” based only on successful compilation.

# ONE CAR VISIT AFTER PC GATE

Only after the above passes:

1. Ignition ON, engine OFF.
   - Connect.
   - Verify RPM=0, MAP plausible, BARO source captured.
   - Run CHECK DATA.
   - App must show GREEN only if RPM/MAP/BARO/MAF/Speed/Load are all readable.

2. Start engine at idle.
   - Run 10-second RPM stress test.
   - Save req/s, valid Hz, mean/median/p95/max latency, timeout count.

3. One gentle stationary rev to ~2500–3000 RPM.
   - Verify RAW `010C` follows tachometer.

Only if all three pass: one road WOT log 1300→4000 RPM.

No repeated driving/debug loops.
