# Review — `c2290ac` code + real-car logs (2026-09-16 10:23)

Scope:
- current `master` code through `db02947` / `c2290ac`;
- `ConnectionTrace_20260916_102338_406_SUCCESS.txt`;
- `Event_RAW_20260916_102349.csv`;
- `Turbo_Pair_20260916_102349.csv`.

## What is now confirmed good

### Previous review fixes are actually implemented
- LIVE and RECORDING cadences are split:
  - LIVE = 3/6/6 + slow PIDs only on clean cycles;
  - RECORDING = 4/8/8, slow PIDs OFF.
- `SESSION_SNAPSHOT` now checks age/freshness and writes event timestamps at snapshot time while preserving source timestamp/age in metadata.
- MAC redaction is applied to current tracked traces and the transport/engine logging path.
- Android CI exists and the current `c2290ac` push passed `testDebugUnitTest assembleDebug`.

Do not rewrite the working RFCOMM/ELM handshake. The real trace again confirms AUTO / ISO 14230-4 (KWP 5BAUD) works reliably.

### Logger/data-path quality is substantially better
The new pair log contains **58 RPM+MAP pairs and all 58 are `pair_valid=true`**. There are no TIMEOUT/NO DATA/INVALID core samples in the run.

Observed pair synchronization is approximately **198–257 ms**, comfortably inside the 550 ms validity limit.

From first to last pair the run spans about **30.9 s**, so the effective RPM+MAP pair rate is about **1.84 pairs/s**. This is consistent with the measured ~200–260 ms KWP/ELM command latency.

BARO is stable from the real phone sensor at about **1004.4–1004.8 mbar** for the entire run. Boost calculation is therefore based on a real pressure reference, not a fixed constant.

RECORDING 4/8/8 remains valid in real data:
- MAF age stays roughly <= 2.05 s;
- Speed age stays roughly <= 4.07 s;
- Load age stays roughly <= 4.05 s.

Keep RECORDING 4/8/8 unless new real evidence shows a problem.

---

# Log review — what this run actually proves

## Useful boost sections
The cleanest high-load section is approximately pairs 45–53:

| Pair | RPM | MAP abs mbar | Boost gauge bar | MAF g/s | Load % (with age) |
|---|---:|---:|---:|---:|---:|
| 45 | 1998 | 2130 | 1.125 | 59.89 | 96.5 @ 423 ms |
| 46 | 2136 | 2410 | 1.405 | 59.89 | 96.5 @ 848 ms |
| 47 | 2283 | 2320 | 1.315 | 59.89 | 96.5 @ 1268 ms |
| 48 | 2447 | 2330 | 1.325 | 59.89 | 96.5 @ 1684 ms |
| 49 | 2755 | 2400 | 1.395 | 86.14 | 96.5 @ 2520 ms |
| 50 | 2911 | 2380 | 1.375 | 86.14 | 96.5 @ 2953 ms |
| 51 | 3076 | 2350 | 1.345 | 86.14 | 96.5 @ 3416 ms |
| 52 | 3222 | 2310 | 1.306 | 86.14 | 96.5 @ 3844 ms |
| 53 | 3344 | 1720 | 0.716 | 100.91 | 91.8 @ 412 ms |

The sharp MAP drop at pair 53 should **not** automatically be treated as turbo control failure: the next pair drops from 3344 RPM to 2651 RPM, so this is at/around a lift or gear-change transition.

The logger captured real boost dynamics clearly enough for offline analysis.

---

# P0 — this is still not the requested acceptance pull

The project acceptance target is a continuous approximately **1300 -> 4000 RPM** usable pull. This log reaches only **3344 RPM**.

More importantly, the critical low-RPM spool region is not cleanly captured under verified high load:
- the early 1500–1750 RPM section still carries an older low `0104` load sample (`6.3%`) until the next load query;
- the first clearly fresh ~95%+ load sample in the later clean pull appears around **1998 RPM**.

Therefore this file is useful for ~2000–3200 RPM boost behavior, but it is **not sufficient evidence for the 1500–1900 RPM spool/lag problem**, and it does not satisfy the full-range 1300–4000 acceptance criterion.

Do not tune the 1500–1900 region solely from this run.

---

# P0 — the run is not proven to be 4th gear; data strongly suggests a lower fixed gear

The button/file intent says `4TH GEAR WOT LOG`, but the log does not contain an actual gear channel.

Two fresh Speed/RPM observations are very consistent with each other:
- around pair 33: 3270 RPM and 82 km/h (speed age ~479 ms) -> ~25.1 km/h per 1000 RPM;
- around pair 49: 2755 RPM and 68 km/h (speed age ~431 ms) -> ~24.7 km/h per 1000 RPM.

That ratio is stable, so the vehicle is clearly in one fixed gear during those sections, but it appears materially shorter than the intended 4th-gear test. Exact gear identification depends on the actual gearbox/final-drive/tire combination, so do not hardcode “3rd” without the gearbox data; however **the app must stop calling the run 4th gear without validating it**.

### Required improvement before another road session
Add a lightweight log-quality/gear validation layer using only already logged RPM + fresh Speed:

1. Compute `kmh_per_1000rpm = speed_kmh * 1000 / rpm` only when Speed age is <= ~1000 ms and RPM is stable/nonzero.
2. Cluster several samples from the same acceleration segment.
3. Show/log `observed_ratio` and an estimated gear/confidence if target gearbox ratios are configured.
4. At minimum, after STOP show `GEAR NOT VERIFIED` unless enough fresh speed samples support the requested gear.

This can be implemented and unit-tested offline. It directly prevents wasting another car run in the wrong gear.

---

# P1 — START/STOP recording transition is not cycle-atomic

The new raw log gives direct evidence:

### At START
Rows 1–7 are the `SESSION_SNAPSHOT`, but row 8 is a live `010D` Speed request **before the first recording RPM/MAP pair** (rows 9–10).

This means the user pressed START while the polling coroutine already had a LIVE auxiliary command in flight/selected. `turboScheduler.reset()` in the UI thread does not cancel that already-started command.

This is mostly harmless, but a leaked slow request (especially `0142 -> ATRV`) could delay the first RPM/MAP pair and make the logger miss the beginning of a 1300 RPM pull.

### At STOP
The raw file ends with an extra RPM event (`010C`, ~901 RPM) without its matching MAP/pair. The pair CSV remains clean at 58 pairs, but RAW ends in a partial acquisition cycle.

### Required fix
Move recording-mode transitions to the polling-cycle boundary instead of changing them asynchronously from the UI thread.

Lowest-risk approach:
- UI sets `startRecordingRequested` / `stopRecordingRequested`;
- polling loop applies the request only at the top/bottom of a complete RPM+MAP cycle;
- reset scheduler at that boundary;
- write `SESSION_START` / `SESSION_STOP` metadata markers;
- then enable/disable CSV acceptance.

Do **not** cancel an ELM command already in flight.

Acceptance test: first non-snapshot acquisition after `SESSION_START` must be `010C` then `010B`; final session must end after a complete pair or explicitly mark `partial_pair`.

---

# P1 — pair CSV freshness is adequate for channel health, not instantaneous WOT classification

The current `Speed/Load <= 4500 ms` freshness windows are acceptable for answering “is this PID alive?” and for keeping the UI from flickering STALE.

They are **too loose for interpreting instantaneous driver/load state** during a fast pull. Example: a pair can legitimately carry a Load value 3–4 s old while RPM/MAP are current.

Do not change the existing general freshness policy just to solve this.

Instead, any future `LogQualityAnalyzer` / WOT detector should use a stricter analysis-age gate, e.g.:
- Load/Speed used for segment classification only when age <= ~1000 ms;
- MAF preferably <= ~1000–1500 ms;
- RPM/MAP remain pair-synchronous as now.

The age columns already make this possible without changing the CSV schema.

---

# P2 — scheduler stress test still has a dead branch

`TurboSchedulerTest.testRecordingSchedulerDrivenSimulationUnderRealLatency()` loops over `216 ms` and `260 ms`, but assertions are executed only when `cmdLatencyMs == 216L`.

So the `260 ms` branch performs calculations and proves nothing.

Fix one of two ways:
1. remove the 260 ms branch if it is informational only; or
2. define explicit expected behavior at 260 ms and assert it.

Better: add a fixture/replay test using the checked-in real RAW file and verify the observed 4/8/8 ages and pair cadence from actual event timing instead of only synthetic fixed-latency arithmetic.

---

# Recommended no-car work now

Before requesting another drive:
1. Add cycle-boundary START/STOP recording transition.
2. Add post-log `LogQualityAnalyzer` that reports at minimum:
   - pair count / valid pair percentage;
   - min/max RPM;
   - max MAP / max boost;
   - BARO source continuity;
   - max MAF/Speed/Load ages;
   - observed speed-per-1000-RPM ratio / gear verification status;
   - whether a continuous usable segment covers 1300–4000 RPM;
   - whether the 1500–1900 region has sufficiently fresh load evidence.
3. Fix/remove the dead 260 ms test branch.
4. Keep current working handshake, BARO resolver, 4/8/8 RECORDING cadence, and CI.

The app should tell the user immediately after STOP whether the run is usable, so the next car visit is one deliberate acceptance pull rather than another diagnostic trip.
