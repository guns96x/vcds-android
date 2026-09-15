# GEMINI: execute these fixes exactly

Work only in the existing repository `guns96x/vcds-android` on `master` (or a single short-lived fix branch if your environment requires it). Do not redesign the app and do not add new TP2.0/N75 features in this pass.

The goal is to make Turbo Fast reliable before the next road log. Apply the changes below file-by-file. Do not reinterpret the task.

## 1) `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt`

### 1.1 Add fixed constants near the top of `MainActivity`
Use these values initially:

```kotlin
private companion object {
    const val TURBO_PID_TIMEOUT_MS = 450L
    const val PREFLIGHT_PID_TIMEOUT_MS = 500L
    const val TURBO_PAIR_MAX_DELTA_MS = 400L
    const val ENGINE_OFF_RPM_MAX = 50.0
    const val ENGINE_OFF_BARO_MIN_MBAR = 800.0
    const val ENGINE_OFF_BARO_MAX_MBAR = 1100.0
}
```

Do not use 200 ms for Turbo Fast PID reads anymore. Real responses from this car/V-LINK were ~202-221 ms.

### 1.2 Replace `onDiagnosticSampleReceived()` with a non-blocking publisher
Current problem: polling calls `withContext(Dispatchers.Main)` and waits for UI before sending the next ELM command.

Replace the data flow with this shape:

```kotlin
private fun publishDiagnosticSample(sample: DiagnosticSample) {
    latestSamples[sample.pid] = sample

    asyncLogger.logRawEvent(
        pid = sample.pid,
        value = sample.value,
        unit = sample.unit,
        raw = sample.rawResponse,
        latencyMs = sample.latencyMs,
        status = sample.status.name,
        rxNanos = sample.monoNanos
    )

    lifecycleScope.launch(Dispatchers.Main.immediate) {
        updateWidgetForSample(sample)
        updateRawDebug(sample)
    }
}
```

Then replace every `withContext(Dispatchers.Main) { onDiagnosticSampleReceived(sample) }` in `startTurboFastPolling()` with a plain:

```kotlin
publishDiagnosticSample(sample)
```

The polling coroutine must remain on `Dispatchers.IO` and must immediately continue to the next ELM request after parsing/log queueing.

For `runPreFlightCheck()` (which already runs on Main), call `publishDiagnosticSample(sample)` too; it is safe.

Do not keep two parsing paths. The same `DiagnosticSample` must feed both UI and RAW CSV.

### 1.3 Add one BARO resolver; delete every numeric BARO fallback
Add:

```kotlin
private data class BaroReading(val valueMbar: Double?, val source: String)

private fun resolveBaro(): BaroReading {
    val pidBaro = latestSamples["0133"]
    if (pidBaro?.status == PidStatus.VALID && pidBaro.value != null) {
        return BaroReading(pidBaro.value, "PID_0133")
    }

    if (calibratedBaroSource == "ENGINE_OFF_MAP" && calibratedBaroMbar != null) {
        return BaroReading(calibratedBaroMbar, "ENGINE_OFF_MAP")
    }

    return BaroReading(null, "UNAVAILABLE")
}
```

Delete all forms of:

```kotlin
?: 1000.0
?: 1013.0
```

from boost/BARO logic.

In `updateWidgetForSample()` for PID `010B`, replace current BARO selection with:

```kotlin
val baro = resolveBaro()
if (sample.status == PidStatus.VALID && sample.value != null && baro.valueMbar != null) {
    val boostMbar = sample.value - baro.valueMbar
    binding.tvHeroBoost.text = String.format(Locale.US, "%.2f bar", boostMbar / 1000.0)
    binding.tvBoostStatusAge.text = "OK | ${baro.source} | Rel"
    binding.tvBoostStatusAge.setTextColor(Color.parseColor("#3FB950"))
} else {
    binding.tvHeroBoost.text = "--- bar"
    binding.tvBoostStatusAge.text = "N/A | BARO ${baro.source}"
    binding.tvBoostStatusAge.setTextColor(Color.parseColor("#8B949E"))
}
```

### 1.4 Capture an engine-off MAP baseline automatically, but only at RPM=0
When a valid MAP sample arrives, check the latest RPM sample. Only if:
- PID 0133 is not valid,
- RPM sample is valid and `rpm <= 50`,
- MAP is valid and in `800..1100 mbar`,

set:

```kotlin
calibratedBaroMbar = mapSample.value
calibratedBaroSource = "ENGINE_OFF_MAP"
```

Never learn BARO from MAP while RPM > 50.

Reset session BARO on a new Bluetooth connection/disconnect:

```kotlin
calibratedBaroMbar = null
calibratedBaroSource = "UNSET"
```

PID 0133, if valid, always overrides engine-off baseline.

### 1.5 Fix all Turbo Fast command timeouts
Inside `startTurboFastPolling()` replace every PID call like:

```kotlin
sendCommand("010C", 200L)
```

with:

```kotlin
sendCommand("010C", TURBO_PID_TIMEOUT_MS)
```

Do this for `010C`, `010B`, `0110`, `010D`, `0104`, `0105`, `010F`, `0142`, `0133`, and the `ATRV` fallback.

Do not add `delay()` between normal Turbo Fast commands.

### 1.6 Fix pair validity; current 150 ms threshold is impossible on this adapter
Current code:

```kotlin
val pairValid = dtMs in 0..150
```

is wrong for a sequential ELM flow where MAP starts only after RPM finishes and a single response is ~220 ms.

Change to:

```kotlin
val pairValid = dtMs in 0..TURBO_PAIR_MAX_DELTA_MS
```

with `TURBO_PAIR_MAX_DELTA_MS = 400L` initially.

The pair timestamp remains the MAP RX timestamp.

When logging the pair, use:

```kotlin
val baro = resolveBaro()
```

and pass:

```kotlin
baroMbar = baro.valueMbar
baroSource = baro.source
```

No hardcoded value.

### 1.7 Do not log stale auxiliary values as if they were fresh
Add a helper:

```kotlin
private fun freshValue(pid: String, maxAgeMs: Long): Double? {
    val s = latestSamples[pid] ?: return null
    if (s.status != PidStatus.VALID || s.value == null) return null
    return if (s.getAgeMs() <= maxAgeMs) s.value else null
}
```

Use these limits when creating `TurboPair`:

```kotlin
mafGs = freshValue("0110", 1500L)
speedKmh = freshValue("010D", 2000L)
loadPct = freshValue("0104", 2000L)
coolantC = freshValue("0105", 10000L)
iatC = freshValue("010F", 10000L)
voltageV = freshValue("0142", 10000L)
```

Do not insert an arbitrarily old value into a new pair row.

### 1.8 Count sample Hz from successful samples, not request attempts
Keep `windowTotalReqs++` for every request.

But move:

```kotlin
windowRpmCount++
windowMapCount++
windowMafCount++
```

so each counter increments only after decoding if `status == PidStatus.VALID`.

Thus:
- `req/s` = all commands sent,
- `RPM Hz`, `MAP Hz`, `MAF Hz` = real valid samples per second.

Add timeout and NO DATA window counters if easy; at minimum do not call a failed request a successful Hz sample.

### 1.9 Simplify Turbo Fast scheduler; keep auxiliary traffic sparse
Do not poll nine categories at equal importance.

Use this deterministic sequence as the main loop:

```text
010C, 010B,
010C, 010B,
010C, 010B,
010C, 010B,
0110,
010C, 010B,
010D,
010C, 010B,
0104,
repeat
```

Move `0105`, `010F`, `0142`, `0133` to slow timed reads, approximately one slow PID every 5-10 seconds, not every main cycle.

The slow read must still use the same serialized `sendCommand()`; do not parallelize ELM commands.

If you choose not to refactor to time-based slow scheduling in this commit, then at minimum reduce auxiliary insertion heavily so RPM/MAP dominate bandwidth.

### 1.10 Make pre-flight deterministic
`runPreFlightCheck()` must not compete with the active polling loop.

Before probing the 9 PIDs:
- remember whether Turbo polling was active,
- cancel/join only the polling job,
- run the pre-flight sequentially,
- restart Turbo Fast polling after the check if it was active.

Use `PREFLIGHT_PID_TIMEOUT_MS` instead of 300 ms.

Do not cancel the pre-flight job from inside itself.

### 1.11 Add RPM-only stress test without redesigning UI
Do not add another large UI panel.

Use a long-press on the existing `CHECK DATA` button:

```kotlin
binding.btnCheckData.setOnLongClickListener {
    runRpmStressTest()
    true
}
```

`runRpmStressTest()`:
- pause normal polling,
- for exactly 10 seconds send only `010C` using `TURBO_PID_TIMEOUT_MS`,
- collect total, valid, timeout, NO DATA, min/max RPM, all latencies,
- compute req/s, valid RPM Hz, mean, median, p95,
- write the summary to Logcat tag `ELM_TURBO_STRESS`,
- show a compact AlertDialog with the same summary,
- restart Turbo Fast polling.

No extra screen.

## 2) `app/src/main/java/com/vag/vcdsandroid/logging/AsyncCsvLogger.kt`

### 2.1 Add real writer counters
Add:

```kotlin
private val rawRowsActuallyWritten = AtomicLong(0L)
private val pairRowsActuallyWritten = AtomicLong(0L)
```

Reset both in `startLogging()`.

Change:

```kotlin
val rowsWritten: Long
    get() = pairSeq.get()
```

to:

```kotlin
val rowsWritten: Long
    get() = pairRowsActuallyWritten.get()

val rawRowsWritten: Long
    get() = rawRowsActuallyWritten.get()
```

Increment immediately after successful writer calls:

```kotlin
localRawWriter.write(line)
rawRowsActuallyWritten.incrementAndGet()
```

and:

```kotlin
localPairWriter.write(line)
pairRowsActuallyWritten.incrementAndGet()
```

Sequence numbers are not writer-progress metrics.

### 2.2 Fix Peak Boost vs Peak MAP
Add:

```kotlin
var peakMapMbarAbs: Double = 0.0
    private set
```

Reset it in `startLogging()`.

In `logTurboPair()` compute boost before updating peaks:

```kotlin
val boostMbar = if (baroMbar != null && baroMbar > 0.0) mapMbarAbs - baroMbar else null
val boostBar = boostMbar?.div(1000.0)

if (mapMbarAbs > peakMapMbarAbs) peakMapMbarAbs = mapMbarAbs
if (boostMbar != null && boostMbar > peakBoostMbar) peakBoostMbar = boostMbar
if (rpm > peakRpm) peakRpm = rpm
```

Delete the current incorrect line:

```kotlin
if (mapMbarAbs > peakBoostMbar) peakBoostMbar = mapMbarAbs
```

### 2.3 Add ages for optional snapshot fields to `TurboPair`
Because MAF/Speed/Load are not sampled at the same instant as MAP, add nullable age columns:

```kotlin
mafAgeMs: Long?
speedAgeMs: Long?
loadAgeMs: Long?
coolantAgeMs: Long?
iatAgeMs: Long?
voltageAgeMs: Long?
```

Add matching CSV headers and values. Pass the ages from `MainActivity` when values are fresh. Leave value+age blank when stale/unavailable.

Do not pretend the auxiliary fields are synchronous with the RPM/MAP pair.

### 2.4 Keep asynchronous writer architecture
Do not replace `Channel` + IO writer with direct disk writes from the polling coroutine.

Keep `Dropped` visible and keep `trySend()` non-blocking.

## 3) `app/src/main/java/com/vag/vcdsandroid/protocol/PidDecoder.kt`

Do not rewrite working formulas.

The current formulas for these are correct and must remain:
- `010C`: `((A*256)+B)/4`
- `010B`: `A*10 mbar`
- `0110`: `((A*256)+B)/100 g/s`
- `0133`: `A*10 mbar`
- `010D`: `A km/h`
- `0104`: `A*100/255`

Only change this file if compilation requires a small helper. No new fake PIDs or formulas.

## 4) `app/src/main/java/com/vag/vcdsandroid/bluetooth/BluetoothElmTransport.kt`

Do not redesign or replace RFCOMM. The current transport already has:
- Secure SPP,
- Insecure SPP,
- reflection only as fallback,
- one command mutex,
- read-until-`>`,
- timeout recovery,
- monotonic TX/RX timestamps.

Leave it alone unless a compile/runtime bug directly requires a minimal fix.

## 5) `app/src/main/res/layout/activity_main.xml`

Do not redesign again.

Keep the current Turbo Fast compact layout. Ensure these currently visible fields remain visible:
- RPM
- MAP absolute
- Boost gauge
- MAF
- Speed
- Load
- BARO
- status/age for each
- req/s, RPM Hz, MAP Hz, latency
- REC / Rows / Queue / Dropped / size
- collapsible RAW DEBUG
- CHECK DATA

Do not add decorative cards/banners unless required for an existing binding.

## 6) Verification sequence — execute, do not just describe

After code edits:

```text
1. gradlew.bat compileDebugKotlin
2. gradlew.bat assembleDebug
3. adb install -r app/build/outputs/apk/debug/app-debug.apk
4. launch app on Samsung S24 FE
5. connect real V-LINK / ELM327
6. CHECK DATA
7. engine stationary: idle -> 1500 -> 2000 -> 2500 -> 3000 rpm
8. confirm raw 010C follows tachometer
9. long-press CHECK DATA -> run 10s RPM stress test
10. START LOG, verify Rows increases and Dropped=0
```

Post the actual results to GitHub issue #1:
- commit SHA,
- build result,
- 10-second stress summary,
- actual max RPM seen in raw 010C,
- actual req/s and RPM/MAP Hz,
- timeout/NO DATA counts,
- BARO source shown on screen,
- whether Dropped remained zero.

Do not mark the task complete if you only changed code. Completion requires the real-device verification evidence above.
