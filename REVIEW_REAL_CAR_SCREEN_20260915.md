# Real-car review after 056f806 — fix before any WOT road test

This review is based on the real-car screen captured on 2026-09-15 after commit `056f806`.

## Real-car evidence now confirmed

The connection problem is no longer the current blocker:

- ELM/ECU status: CONNECTED
- RPM: ~830 rpm and live
- MAP: ~1000 mbar absolute and live
- bus: ~4.5 req/s
- RPM: ~2.3 Hz
- MAP: ~2.3 Hz
- average latency: ~216 ms
- MAF/Speed/Load/Coolant/IAT/Voltage all return plausible real values

This matches the known V-LINK performance envelope. Do not rewrite the working handshake or RFCOMM path now.

## P0-1 — BARO must not require an engine-off ritual on this phone

Observed on the real screen:

- PID 0133 = unavailable
- engine running at ~830 rpm, therefore ENGINE_OFF_MAP baseline cannot be learned
- BARO remains unavailable
- Boost remains N/A
- START WOT LOG remains disabled

The current behavior is logically safe, but operationally bad: it forces the user to stop/restart/reconnect only to obtain one atmospheric-pressure value.

### Required implementation: Android phone barometer fallback

The target phone has an Android pressure sensor. Do not hardcode a device model. Detect `Sensor.TYPE_PRESSURE` at runtime.

Create:

`app/src/main/java/com/vag/vcdsandroid/sensors/PhoneBarometerProvider.kt`

Required behavior:

1. Obtain `SensorManager` from `Context.SENSOR_SERVICE`.
2. `getDefaultSensor(Sensor.TYPE_PRESSURE)`.
3. If absent, expose `available = false` and do nothing else.
4. Register a `SensorEventListener` while the Activity is active.
5. `event.values[0]` is pressure in hPa. Numerically hPa == mbar.
6. Reject values outside `800.0..1100.0 mbar`.
7. Maintain a rolling window of at least 10 valid readings.
8. Expose the MEDIAN, not one instantaneous sample.
9. Keep `monoNs = SystemClock.elapsedRealtimeNanos()` for the last accepted sample.
10. Reading is fresh only when age <= 5000 ms.
11. No internet/weather API and no fixed 1000/1013 fallback.

Suggested public model:

```kotlin
data class PhoneBaroReading(
    val valueMbar: Double?,
    val available: Boolean,
    val fresh: Boolean,
    val ageMs: Long
)
```

Lifecycle:

- start provider in `onStart()` or `onResume()`
- stop provider in `onStop()` or `onPause()`
- do not leak the Activity

### SessionBaroResolver priority

Extend `SessionBaroResolver` with phone pressure as a third real source.

Priority MUST be:

1. `PID_0133`
2. `ENGINE_OFF_MAP`
3. `PHONE_BAROMETER`
4. `UNAVAILABLE`

Reason: ECU-derived BARO/engine-off MAP is preferred when available; phone pressure is a real ambient-pressure fallback, not a guessed constant.

Add an API similar to:

```kotlin
fun onPhoneBaro(valueMbar: Double?, monoNs: Long, fresh: Boolean)
```

Rules:

- phone BARO may populate the resolver only if there is no current PID_0133 or ENGINE_OFF_MAP source;
- a later valid PID_0133 replaces PHONE_BAROMETER;
- a later valid ENGINE_OFF_MAP replaces PHONE_BAROMETER;
- phone timeout/staleness must not overwrite a better ECU source;
- if PHONE_BAROMETER itself becomes stale and no better source exists, BARO becomes unavailable.

UI must show the source explicitly, for example:

`BARO 998 mbar | PHONE`

CSV `baro_source` must contain `PHONE_BAROMETER` when used.

WOT readiness may accept `PHONE_BAROMETER` only when the reading is fresh.

## P0-2 — LIVE scheduler is making MAF stale by design

Real screen evidence:

- MAF = 11.5 g/s
- displayed `STALE`
- age ~3862 ms
- RPM/MAP remain ~2.3 Hz

This is not an ECU failure. The current LIVE 9-step schedule queries MAF too sparsely relative to `MAF_MAX_AGE_MS = 2500`.

Do not hide the problem by simply increasing the stale threshold.

### Required scheduler change

Make LIVE mode use the same pair-first structure as RECORDING, while still allowing slow sensors:

Every live loop:

```text
010C RPM
010B MAP
```

Then use deterministic aux cadence:

- MAF `0110`: every 4th RPM/MAP pair
- Speed `010D`: every 8th pair
- Load `0104`: every 8th pair, offset from Speed by 4 pairs

Keep slow rotation separately:

- Coolant 0105
- IAT 010F
- Voltage 0142 / ATRV fallback
- BARO 0133

one slow PID approximately every 2500 ms.

Do not poll slow PIDs during active WOT recording.

Acceptance for LIVE mode on the real V-LINK:

- MAF should not repeatedly become STALE during a stable 60-second idle session;
- RPM/MAP rate must remain visible and should not regress catastrophically;
- Speed/Load age should stay within their defined freshness windows.

## P0-3 — Pre-flight must evaluate EFFECTIVE/FRESH status, not raw historical status

Current pre-flight calls `PreflightEvaluator.evaluate()` using checks equivalent to:

```kotlin
sample?.status == PidStatus.VALID
```

That is insufficient because a sample can have raw status VALID while UI correctly shows it as STALE based on age.

The real screen demonstrates this exact case for MAF.

Create one helper and use it everywhere readiness is evaluated:

```kotlin
private fun isFreshValid(pid: String, maxAgeMs: Long, nowNs: Long): Boolean {
    val s = latestSamples[pid] ?: return false
    return s.status == PidStatus.VALID &&
           s.value != null &&
           s.getAgeMs(nowNs) <= maxAgeMs
}
```

Use freshness limits consistently:

- RPM: <= 1000 ms
- MAP: <= 1000 ms
- MAF: <= 2500 ms
- Speed: <= 4500 ms
- Load: <= 4500 ms
- slow channels: existing slow freshness policy
- BARO: resolver source must be currently valid/fresh according to source rules

`PreflightEvaluator` must receive actual fresh booleans, not raw historical status.

`START WOT LOG` must never enable from stale MAF/Speed/Load merely because their last raw status was VALID.

## P1 — Make BARO acquisition automatic immediately after connect

After Generic OBD handshake succeeds and before normal polling settles:

1. Try 0133 once.
2. Start/use PhoneBarometerProvider immediately.
3. If RPM <= 50 and MAP is valid, learn ENGINE_OFF_MAP.
4. Otherwise use fresh PHONE_BAROMETER fallback.
5. Render BARO/Boost as soon as any real source resolves.

The user should not need to press CHECK DATA just to populate BARO.

## P1 — preserve proof in logs

Add the following to the session/connection trace header:

- `BARO source`
- `BARO value`
- `Phone pressure sensor available = true/false`
- `Phone pressure current value/age` when available

This avoids another car visit just to determine why Boost was N/A.

## Unit tests required

Add tests for:

### `PhoneBarometerProvider` logic

Keep the median/filter logic in a pure class so it can be unit tested without Android SensorManager.

Test:

- rejects <800 and >1100 mbar;
- median calculation;
- stale after 5 s;
- no sample => unavailable.

### `SessionBaroResolver`

Test source priority:

1. PHONE_BAROMETER alone resolves.
2. ENGINE_OFF_MAP replaces PHONE_BAROMETER.
3. PID_0133 replaces both.
4. stale PHONE_BAROMETER becomes unavailable when it is the only source.
5. stale phone reading does not erase PID_0133 or ENGINE_OFF_MAP.

### pre-flight freshness

Test that:

- MAF raw `VALID` but older than 2500 ms does NOT yield GREEN;
- fresh RPM+MAP+BARO+MAF+Speed+Load yields GREEN;
- missing BARO yields RED;
- phone BARO fresh may satisfy BARO requirement.

### live scheduler

Verify over at least 32 pairs:

- MAF exactly every 4th pair;
- Speed every 8th pair;
- Load every 8th pair offset 4;
- no slow PID injected through aux cadence;
- slow PID rotation remains independent.

## PC gate

Before asking for another car test run:

```powershell
./gradlew.bat clean testDebugUnitTest assembleDebug
```

Report exact test count and failures.

## Real-car acceptance — only ONE short stationary session

Do not request a road pull yet.

With engine running at idle:

1. Connect.
2. BARO must resolve automatically via PID_0133, ENGINE_OFF_MAP if already available, or PHONE_BAROMETER.
3. Boost must stop showing `---`.
4. Run for 60 seconds.
5. RPM/MAP stay live.
6. MAF must not repeatedly go STALE because of scheduler design.
7. Speed and Load stay valid/fresh at their expected cadence.
8. CHECK DATA must become GREEN if all required channels are fresh.
9. Only then enable START WOT LOG.

No road WOT until this stationary acceptance passes.
