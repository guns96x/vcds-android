# Review findings — commit fb85849 vs parent 5f2b6b1

## P0 — 4/8/8 cadence still cannot reliably satisfy the declared freshness windows on the real V-LINK

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/protocol/TurboScheduler.kt:22-27`
- `app/src/main/java/com/vag/vcdsandroid/protocol/TelemetryFreshnessPolicy.kt:4-9`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:1496-1509`

**Why this is still broken**

The real-car screen already measured about **216 ms average per ELM command**. Between two Speed samples at pair 8 and pair 16, the current scheduler executes 20 normal commands before/including the next Speed response: 16 RPM/MAP commands plus MAF x2 + Load x1 + Speed x1. That is about `20 * 216 = 4320 ms` before any slow sensor work.

LIVE mode also injects a slow PID every ~2500 ms. One normal slow request raises that interval to about **4536 ms**, already beyond `SPEED_MAX_AGE_MS = 4500` / `LOAD_MAX_AGE_MS = 4500`. When the slow slot is `0142` and it falls back to `ATRV`, two slow requests can push it to about **4752 ms**.

MAF has the same edge case: every 4 pairs is near the 2500 ms limit, and an `0142 -> ATRV` slow slot can push a MAF interval past `MAF_MAX_AGE_MS = 2500`.

So the new scheduler can still reproduce the exact symptom it was meant to fix: a valid channel periodically becoming STALE at idle.

**Smallest concrete fix**

Use a safer pair cadence for this measured adapter:

```kotlin
if (pairCount % 3L == 0L) pids.add("0110")  // MAF
if (pairCount % 6L == 0L) pids.add("010D")  // Speed
if (pairCount % 6L == 3L) pids.add("0104")  // Load
```

Keep slow PID rotation only in LIVE mode exactly as now. Do not increase freshness thresholds to hide missed cadence.

Update `TurboSchedulerTest` to assert the 3/6/6 cadence and add a timing-budget test using 216 ms/request plus one slow request and the `0142 -> ATRV` two-request case. The computed worst-case sample ages must remain below the declared freshness limits.

---

## P0 — PHONE_BAROMETER can disappear during an active WOT log when the Activity stops, while logging continues

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:230-238`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:1389-1419`
- `app/src/main/java/com/vag/vcdsandroid/sensors/PhoneBarometerProvider.kt:45-55`

**Why this matters**

`onStop()` unconditionally unregisters the pressure sensor and clears the filter. If PHONE_BAROMETER is the only BARO source, `SessionBaroResolver` expires it after 5 seconds. Nothing in the active WOT path stops or invalidates the recording when this happens: `queryRpmMapPair()` simply keeps writing pairs with `baroMbar = null` / `baroSource = UNAVAILABLE`.

A screen timeout, power-button press, app switch, or lifecycle stop during a road log can therefore silently turn a good tune log into a partially unusable one.

**Smallest concrete fix**

1. While `asyncLogger.isLogging`, keep the Activity screen on:
   - set `FLAG_KEEP_SCREEN_ON` in `startWotLog()`;
   - clear it in `stopWotLog()` and disconnect/error paths.
2. Do not silently continue a WOT log after BARO loss. In the recording path, if the active BARO source becomes unavailable for more than a short grace period (e.g. 2 consecutive RPM/MAP pairs), stop the log and show `BARO LOST — LOG STOPPED`.
3. If the implementation intentionally supports background logging, do not unregister the pressure sensor in `onStop()` while PHONE_BAROMETER is the active source and a log is active; otherwise explicitly stop the log on `onStop()`.
4. Add a unit/state test: start with fresh PHONE_BAROMETER, advance >5 s without phone samples while logging, verify the session cannot continue as a valid WOT log.

---

## P1 — Boost can remain green/numeric while MAP or PHONE_BAROMETER is already stale

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:750-788`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:988-1004`

**Why this matters**

`updateBaroUi()` accepts MAP when `mapSample.status == VALID` but does not check MAP age. A historical VALID MAP older than the freshness limit can therefore still produce a green numeric Boost value.

Separately, when PHONE_BAROMETER ages out because sensor events stop, `refreshAgesAndStatuses()` updates the BARO row to `UNAVAILABLE`, but it never clears/recomputes `tvHeroBoost` / `tvBoostStatusAge`. The last green `OK | PHONE | Rel` boost can remain on screen even though BARO has already expired.

This creates contradictory live instrumentation exactly where the user is relying on the screen to decide whether logging works before driving.

**Smallest concrete fix**

- In `updateBaroUi()`, require `isFreshValid("010B", TelemetryFreshnessPolicy.MAP_MAX_AGE_MS, nowNs)` before calculating Boost.
- Call `updateBaroUi()` from the 100 ms ticker (or on BARO/MAP readiness state changes), so a phone-BARO timeout actively clears Boost even when no further pressure event arrives.
- When BARO or MAP is stale/unavailable, set Boost to `--- bar` and status to an explicit `STALE/UNAVAILABLE`, never green `OK`.
- Add a regression test/state test for: valid MAP + phone BARO -> advance beyond phone freshness without new sensor events -> BARO and Boost both become unavailable.

---

## P1 — Phone pressure freshness is re-stamped with “now” instead of preserving the sensor sample timestamp

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/sensors/PhoneBarometerProvider.kt:58-63`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:212-222`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:509-511`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:672-677`

**Why this matters**

`PhoneBarometerProvider` has the real `SensorEvent.timestamp`, but `PhoneBaroReading` does not carry it. `MainActivity` then calls `onPhoneBaro(..., SystemClock.elapsedRealtimeNanos(), ...)`, replacing the actual sample time with callback/reset/connect time.

Example: a sensor reading is 4.9 s old but still barely fresh. A reconnect/session reset re-inserts it with a new current timestamp, effectively giving that old reading another ~5 s of validity. That defeats the strict freshness guarantee added in this commit.

**Smallest concrete fix**

- Add `monoNs` (last accepted sensor sample timestamp) to `PhoneBaroReading`.
- `BarometerMedianFilter.getMedianReading()` must return the original `lastSampleMonoNs`.
- Pass `reading.monoNs` to `SessionBaroResolver.onPhoneBaro()` everywhere. Never replace it with current time.
- Add regression test: sample at `t0`, read/reinsert at `t0+4.9s`, resolve at `t0+5.1s` must be `UNAVAILABLE`, not extended to `t0+9.9s`.

---

## P2 — The new “single” freshness policy is not actually the single source of truth

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/protocol/TelemetryFreshnessPolicy.kt:3-21`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:77-87`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:96-105`

**Why this matters**

The new policy says RPM/MAP = 1000 ms and slow channels = 12000 ms, while `DiagnosticSample.getEffectiveStatus()` still hardcodes RPM/MAP = 800 ms and slow channels = 14000 ms. MainActivity also keeps duplicate local MAF/Speed/Load/slow constants.

The result is multiple competing definitions of “fresh”: UI can show RPM/MAP STALE while WOT readiness still accepts them, and UI can show slow channels VALID while CSV freshness has already dropped them.

**Smallest concrete fix**

- Replace the `when(pid)` in `DiagnosticSample.getEffectiveStatus()` with `TelemetryFreshnessPolicy.getMaxAgeMs(pid)`.
- Remove/alias the duplicated `MAF_MAX_AGE_MS`, `SPEED_MAX_AGE_MS`, `LOAD_MAX_AGE_MS`, and `SLOW_VALUE_MAX_AGE_MS` constants in MainActivity and use `TelemetryFreshnessPolicy` in CSV `freshValue/freshAge` calls too.
- Add one test asserting UI effective-status and readiness policy use the same threshold for every logged PID.

---

## P2 — Every successful Mode A connection now writes two SUCCESS trace files

**Files / lines**
- `app/src/main/java/com/vag/vcdsandroid/protocol/Elm327DiagnosticEngine.kt:283-289`
- `app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt:509-521`

**Why this matters**

The engine still writes a SUCCESS connection trace immediately after the OBD handshake. MainActivity then writes another SUCCESS trace after the BARO auto-probe to add phone/BARO fields. This leaves two success files for one connection; the first one lacks the BARO evidence that this commit was specifically supposed to preserve.

For troubleshooting, two near-identical files with different completeness make it easy to inspect the wrong trace.

**Smallest concrete fix**

Have a single owner for successful trace persistence. For Mode A, save the SUCCESS trace only after MainActivity has enriched it with BARO/phone data. Keep failure trace saving inside the engine. Mode B can still save its success trace in the engine if no BARO enrichment is needed.

---

## P2 — `AI_CONTEXT.md` now instructs future agents to reintroduce already-fixed behavior

**File / lines**
- `AI_CONTEXT.md:35-40`
- `AI_CONTEXT.md:47-65`

**Why this matters**

This file says it is the single ground-truth file for AI coding assistants, but it still says Mode A uses functional `ATSH7DF` first, RPM/MAP pairs under 150 ms, and Boost BARO from `0133` only. The current code instead uses the deterministic AUTO/SP6/physical handshake, 550 ms pair limit, and `PID_0133 > ENGINE_OFF_MAP > PHONE_BAROMETER`.

The next Gemini/Codex session is explicitly told to read this stale document first, so it can undo the reliability work.

**Smallest concrete fix**

Update the operational-mode section and current changelog in the same commit as the code fixes. At minimum document:
- AUTO -> SP6 default -> physical 7E0/7E8 handshake order;
- `TURBO_PAIR_MAX_DELTA_MS = 550`;
- phone barometer fallback and exact BARO priority;
- current pair-first live scheduler;
- current freshness policy and real measured ~216 ms/request envelope.

Do not change unrelated documentation.
