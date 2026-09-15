# REVIEW AFTER 26d58e6 — DO THESE FIXES NEXT

Target: `guns96x/vcds-android`, current commit `26d58e60b6d7d39ad772c10704d8d09bb81431b0`.

This is a code review + exact repair plan. Do not redesign the app. Do not add TP2.0/N75 features until Mode A is reliably connected and logging.

## A. Root cause area: Generic OBD connection regression

The screenshot shows RFCOMM/ELM reached the point where `Elm327DiagnosticEngine` reports **ECU Mode 01 (0100/010C) did not answer**. This is not the Bluetooth pairing screen failure. The failing layer is the Generic OBD handshake after ELM initialization.

The older known-good generic connection path at commit `26825e9` was simple:

- basic ELM init
- `ATCAF1`
- `ATCFC1`
- `ATV0`
- `ATAR`
- try `ATSP6`, then `0100`, then `010C`
- fallback `ATSP0`, then `0100`, then `010C`

Commit `3fa6c4d` changed this into manual CAN-header/filter manipulation:

- `ATSP6`
- `ATSH7DF`
- `ATCRA`
- `ATAR`
- probe
- then `ATSH7E0`
- `ATCRA7E8`
- probe
- then `ATCRA`, `ATAR`, `ATSP0`

Problem: the final branch is called “Clean Auto-Protocol”, but it is **not clean**. `ATSH7E0` remains active until ELM defaults/reset (`ATD`, `ATWS`, or `ATZ`). `ATCRA` resets receive filtering but does not reset the transmit header. Therefore the later `ATSP0` fallback inherits an explicitly assigned transmit header from the failed physical-address attempt.

Also, this adapter is a V-LINK/ELM-compatible device. Advanced commands like `ATSH`, `ATCRA`, `ATCS` must not be mandatory for the basic Mode 01 path because clone/compatible adapters may partially implement them.

### A1. File: `app/src/main/java/com/vag/vcdsandroid/protocol/Elm327DiagnosticEngine.kt`

Replace the current Generic Mode 01 connection block with a deterministic 3-stage sequence.

### Stage 1 — KNOWN-GOOD AUTO PATH (MUST RUN FIRST IN TURBO_FAST)

For `forceGeneric == true`, after ELM basic init, do this BEFORE any `ATSH` or `ATCRAxxx`:

```kotlin
private suspend fun configureBasicGenericObd(): Boolean {
    val required = listOf(
        "ATD" to 1500L,      // clear any stale SH/filter/timing experiments
        "ATE0" to 1000L,
        "ATL0" to 1000L,
        "ATS0" to 1000L,
        "ATH0" to 1000L,
        "ATCAF1" to 1000L,
        "ATCFC1" to 1000L,
        "ATR1" to 1000L,
        "ATAT1" to 1000L,
        "ATSP0" to 2000L
    )

    for ((cmd, timeout) in required) {
        val r = transport.sendCommand(cmd, timeout)
        appendLog("GENERIC INIT $cmd -> ${formatRx(r)}")
        if (r.timedOut || r.raw.contains("?")) {
            appendLog("WARN: $cmd not cleanly accepted")
        }
    }

    return true
}
```

Do **not** use `ATSH`, `ATCRA7E8`, or `ATCS` in this first path.

After `ATSP0`, primary ECU probe must be **010C first**, because it is a short single-PID response and was already proven to work on this car.

```kotlin
val rpmProbe = transport.sendCommand("010C", 5000L)
appendLog("AUTO PROBE 010C -> ${formatRx(rpmProbe)}")
val rpmClean = cleanHexResponse(rpmProbe.raw)

if (rpmClean.contains("410C")) {
    markObdConnected("AUTO/010C")
    return true
}

val mapProbe = transport.sendCommand("010B", 5000L)
appendLog("AUTO PROBE 010B -> ${formatRx(mapProbe)}")
val mapClean = cleanHexResponse(mapProbe.raw)
if (mapClean.contains("410B")) {
    markObdConnected("AUTO/010B")
    return true
}

val pidProbe = transport.sendCommand("0100", 7000L)
appendLog("AUTO PROBE 0100 -> ${formatRx(pidProbe)}")
if (cleanHexResponse(pidProbe.raw).contains("4100")) {
    markObdConnected("AUTO/0100")
    return true
}
```

Do not make `0100` the first/only health check.

### Stage 2 — FIXED CAN 11/500 WITH DEFAULT HEADER

Only if Stage 1 fails:

1. `ATD`
2. reapply `ATE0 ATL0 ATS0 ATH0 ATCAF1 ATCFC1 ATR1 ATAT1`
3. `ATSP6`
4. **do not issue ATSH yet**
5. probe `010C`, then `010B`, then `0100`

If any response contains `410C`, `410B`, or `4100`, connection is successful.

### Stage 3 — PHYSICAL 7E0/7E8 EXPERIMENTAL FALLBACK

Only if Stages 1 and 2 fail:

1. `ATD`
2. reapply basic init
3. `ATSP6`
4. `ATSH7E0`
5. `ATCRA7E8`
6. probe `010C`
7. probe `010B`

After this stage, if another fallback is attempted, **ATD/ATZ is mandatory before it** so the manual header does not leak into another protocol attempt.

### A2. Do not blindly ignore AT command failures

Create helper:

```kotlin
private fun isElmOk(resp: ElmResponse): Boolean {
    val s = resp.raw.uppercase(Locale.ROOT)
    return resp.promptReceived && !resp.timedOut && !s.contains("?") && !s.contains("ERROR")
}
```

Log every init command result. Optional clone-specific commands may fail, but the failure must be visible. Do not silently continue as though every command was accepted.

### A3. Add one method to mark success

```kotlin
private fun markObdConnected(source: String) {
    isTp20Active = false
    elmState = ElmDiagnosticState.OBD_READY
    state = DiagState.CONNECTED
    appendLog("==> OBD_READY via $source")
}
```

### A4. Preserve the failure transcript

Current code disconnects immediately after Mode 01 failure and UI only shows a generic sentence. Before disconnecting, preserve:

- adapter name/version (`ATI`)
- `ATRV`
- each init command and response
- probe mode (`AUTO`, `SP6`, `PHYSICAL`)
- exact RAW `010C`, `010B`, `0100`
- `ATDP`
- `ATDPN`
- promptReceived
- timedOut
- elapsedMs

Add:

```kotlin
var lastConnectTrace: String = ""
    private set
```

At the end of failed connect:

```kotlin
lastConnectTrace = logHistory.takeLast(80).joinToString("\n")
```

Do not erase this trace on `disconnect()`.

## B. MainActivity connection/error UX

File: `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt`

### B1. On connection failure show the actual layer and RAW trace

In `startElmConnection()` when `success == false`:

- keep current error headline
- show a dialog `CONNECTION DEBUG`
- body = `elmEngine.lastError + "\n\n" + elmEngine.lastConnectTrace`
- add a `COPY` button if trivial; otherwise at least make the text selectable / visible in RAW DEBUG

The current generic text “check ignition/adapter” is not enough.

### B2. Disable logging when ECU is not connected

Currently screenshot shows green `START 4TH GEAR WOT LOG` even while status is `Connection Error`.

Fix `updateStatusUI()`:

```kotlin
val ecuConnected = elmEngine.state == DiagState.CONNECTED || elmEngine.state == DiagState.POLLING
binding.btnToggleLog.isEnabled = ecuConnected
binding.btnCheckData.isEnabled = ecuConnected
```

When disabled, visually show that state.

Do not allow creating empty logs while ECU connection failed.

### B3. Show ALL values that are actually logged

Current Turbo Fast card displays:
- RPM
- Boost
- MAP
- MAF
- Speed
- Load
- BARO

But `TurboPair` also logs:
- coolant `0105`
- IAT `010F`
- voltage `0142` / `ATRV`

Add one compact row under MAF/Speed/Load/BARO:

- COOLANT °C + status/age
- IAT °C + status/age
- VOLTAGE V + status/age

Wire them in `updateWidgetForSample()` and `refreshAgesAndStatuses()`.

If you do not want to display them, remove them from `TurboPair`; UI and CSV must have parity.

## C. Turbo Fast scheduler bug

File: `MainActivity.kt`, `startTurboFastPolling()`.

Current code says “one slow PID every ~8 seconds”, but there are 4 slow PIDs. Therefore each individual slow PID is sampled only every ~32 seconds.

At the same time CSV freshness limits for coolant/IAT/voltage are 10 seconds, so these fields are empty/stale for most of the log.

Fix exactly:

```kotlin
const val SLOW_SLOT_INTERVAL_MS = 2500L
const val SLOW_VALUE_MAX_AGE_MS = 12000L
```

Every 2.5 seconds poll one item from:

```kotlin
listOf("0105", "010F", "0142", "0133")
```

Thus each slow PID refreshes about every 10 seconds.

Use `SLOW_VALUE_MAX_AGE_MS` for coolant/IAT/voltage freshness. BARO may be retained longer because atmospheric pressure changes slowly, but its source must remain explicit.

Do not increase slow PID frequency beyond this until RPM/MAP throughput is measured.

## D. Pair timing constant

Current:

```kotlin
TURBO_PID_TIMEOUT_MS = 450L
TURBO_PAIR_MAX_DELTA_MS = 400L
```

A valid MAP response is allowed to arrive close to 450 ms, so a valid sequential RPM→MAP pair can still be marked invalid by the 400 ms rule.

Replace with:

```kotlin
const val TURBO_PID_TIMEOUT_MS = 450L
const val TURBO_PAIR_MAX_DELTA_MS = TURBO_PID_TIMEOUT_MS + 100L // 550 ms
```

Keep logging exact `dt_map_rpm_ms` so we can tighten later from real data.

## E. AsyncCsvLogger review

File: `app/src/main/java/com/vag/vcdsandroid/logging/AsyncCsvLogger.kt`

The fixes in 26d58e6 are mostly correct:

- actual writer counters are better than sequence counters
- Peak MAP and Peak Boost are now separated correctly
- age columns are useful
- Channel + IO writer keeps disk I/O out of acquisition path

Keep them.

But do these cleanups:

1. Rename UI label `Rows` to `Pairs` if it displays `pairRowsActuallyWritten`; optionally also show `Raw:` with `rawRowsWritten`.
2. `startLogging(scope: CoroutineScope)` does not use `scope`; remove the unused parameter or actually use it. Prefer remove it and keep the logger-owned `SupervisorJob`.
3. `stopLogging()` uses `runBlocking` and can block the UI for up to 3 seconds. Do not call blocking join on Main. Make a suspend `stopLoggingAndWait()` or invoke stop on `Dispatchers.IO`, then update UI on Main.

Do not rewrite the logger architecture.

## F. publishDiagnosticSample review

Current fix removes `withContext(Main)` from acquisition, which is good.

However it launches one Main coroutine per sample:

```kotlin
lifecycleScope.launch(Dispatchers.Main.immediate) { ... }
```

At current ~4–5 req/s this is acceptable, but for future higher rate use a conflated/latest-value UI path (`StateFlow`, single ticker, or atomic latest sample). Do NOT refactor this now unless profiling shows queue buildup. Priority is connection + real-car logging.

## G. Build config cleanup

Commit 3fa6c4d enabled Jetpack Compose and added many Compose dependencies, but the app uses XML/ViewBinding and there are no Compose UI files in the current project.

File: `app/build.gradle.kts`

Unless there is an actual Compose source file, remove:

```kotlin
compose = true
composeOptions { ... }
compose BOM
aandroidx.compose.* dependencies
activity-compose
lifecycle-viewmodel-compose
lifecycle-runtime-compose
```

Keep ViewBinding.

This is not the connection root cause, but it is unnecessary build weight and contradicts “remove extra”.

## H. Verification sequence — DO NOT CLAIM FIXED BEFORE THIS

### H1. Static/build

Run:

```powershell
./gradlew.bat clean testDebugUnitTest assembleDebug
```

### H2. Install

Install the new debug APK through ADB.

### H3. Connection test with engine/ignition ON

Capture logcat filtered to:

```text
ELM_BT
ELM_ENGINE
```

Success evidence must contain one of:

```text
AUTO PROBE 010C -> ...410C...
AUTO PROBE 010B -> ...410B...
AUTO PROBE 0100 -> ...4100...
```

Report which stage connected:

```text
AUTO
SP6_DEFAULT_HEADER
PHYSICAL_7E0
```

### H4. Stationary live test

With engine running, before driving verify screen live updates:

- RPM
- MAP
- MAF
- Speed (0 is valid while stationary)
- Load
- BARO/source
- Coolant
- IAT
- Voltage

Raise engine gently idle -> ~1500 -> ~2000 -> ~2500-3000 rpm and confirm RAW `010C` follows.

### H5. Stress test

Long-press CHECK DATA, run 10-second RPM stress test. Save:

- total requests
- valid samples
- timeouts
- Hz
- mean/median/p95 latency
- max RPM

### H6. Only then road log

Do not do another 1300→4000 WOT run until H3-H5 are successful.

## Priority order

1. Fix Generic Mode 01 connection (`Elm327DiagnosticEngine.kt`).
2. Add connection trace to UI (`MainActivity.kt`).
3. Disable logging while disconnected.
4. Fix slow PID interval / display all logged channels.
5. Fix pair delta constant.
6. Logger small cleanups.
7. Remove unused Compose dependencies.
8. Build + ADB + real-car proof.

No TP2.0/N75 work in this pass.