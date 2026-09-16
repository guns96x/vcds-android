# Review — `a3df9eb` + latest road log `112035`

Scope:
- current master commit `a3df9ebafd32665ced13e493d055f06f97d98b39`;
- latest road log `test_logs/20260916/Turbo_Pair_20260916_112035.csv`;
- matching `Event_RAW_20260916_112035.csv`;
- latest connection trace and Android CI.

## Confirmed good — latest real-car run

The `112035` run is now a proper acceptance-quality 4th-gear pull.

Observed from the checked-in data:
- `48/48` RPM+MAP pairs are `pair_valid=true`.
- BARO stays on `PHONE_BAROMETER`, about `1004.7..1005.5 mbar`, i.e. very stable.
- RPM/MAP delta remains roughly `199..297 ms`, safely below the `550 ms` pair-validity limit.
- Main pull covers roughly `~1280/1313 -> 4072 RPM`.
- Fresh speed/RPM points give a stable 4th-gear ratio:
  - 1741 RPM / 60 km/h = ~34.46 km/h/1000 RPM
  - 2553 / 90 = ~35.25
  - 3214 / 113 = ~35.16
  - 3678 / 131 = ~35.62
  - 4011 / 143 = ~35.65
- Early spool is captured under real load:
  - 1386 RPM: ~0.774 bar gauge, Load ~96.9%
  - 1459 RPM: ~0.884 bar
  - 1515 RPM: ~0.914 bar
  - 1573 RPM: ~1.014 bar
  - 1741 RPM: ~1.225 bar
  - 1815 RPM: ~1.275 bar
  - 1906 RPM: ~1.315 bar
- Peak observed boost is about `1.325 bar gauge` around ~1906-2153 RPM.
- MAF rises to about `108.5 g/s` near 4011 RPM.
- Engine was warm: pre-log snapshot shows coolant ~91 C, IAT ~35 C, voltage ~13.2 V.

### Raw-session boundary is now proven in the car

The matching RAW file starts exactly as intended:

```text
SESSION_START
SESSION_SNAPSHOT...
010C
010B
```

There is no leaked LIVE aux command before the first recording core pair.

At the end it finishes with a complete RPM+MAP pair, then aux, then:

```text
SESSION_STOP
```

There is no orphan `010C` after STOP. The cycle-boundary START/STOP implementation is therefore real-car validated, not merely unit-tested.

### Connection path remains healthy

The latest connection evidence still shows:
- Secure SPP RFCOMM success;
- ELM327 responding;
- AUTO path;
- ISO 14230-4 / KWP 5BAUD (`A4` reported by ELM);
- raw `410C` and `410B` success;
- phone BARO fresh and valid;
- Bluetooth MAC redacted.

Do not rewrite handshake/RFCOMM.

### CI

Android CI on `a3df9eb` is green: `testDebugUnitTest + assembleDebug` completed successfully.

---

# Remaining code-review findings

## P1 — Analyzer can still false-PASS a partial-throttle 1300→4000 run

**File**: `app/src/main/java/com/vag/vcdsandroid/analysis/LogQualityAnalyzer.kt`

The current PASS gate only proves:
- enough valid pairs;
- 4th gear;
- RPM coverage;
- stable BARO;
- at least one qualifying fresh high-load sample in the 1400..1900 spool zone.

It does **not** require sustained high load through the same main pull. `longestPull` itself is segmented using RPM movement only, despite the comment describing a pull under load/boost.

Therefore this pathological run can currently pass:
1. full throttle briefly at 1500-1700 RPM;
2. then 30-50% throttle from 2000 to 4000 RPM;
3. same gear and valid telemetry throughout.

That is not a valid calibration acceptance pull.

### Required fix

Tie load validation to the detected primary pull segment. Require fresh high-load evidence in multiple RPM bands of the same segment, for example:
- low: 1400..1900 RPM;
- mid: 2000..3000 RPM;
- high: 3000..3900 RPM;

A small deterministic rule is sufficient: at least one fresh `Load >= 80%` sample in each band, with `loadAgeMs <= 2200` (or a better cadence-derived threshold).

The latest `112035` log should still PASS this stronger rule because it shows ~97-100% load throughout the acceleration.

Add a negative unit test where only the low band is WOT and the rest is partial throttle; it must NOT return `PASS_ACCEPTANCE_PULL`.

---

## P1 — Gear verification is computed from the whole file, not the detected primary pull

Gear ratio observations currently include all valid rows in the file with fresh speed, even before/after the main acceleration segment.

A multi-gear file can therefore mix ratios and produce a misleading median. For the current `112035` run this does not hurt because the fresh ratio observations in the acceleration are consistently ~35 km/h/1000 RPM.

### Required fix

After primary-pull detection, perform gear classification from speed/RPM observations belonging to that same pull segment only.

Also require at least 3 consistent fresh observations if available, and report spread/dispersion. Do not allow unrelated pre/post-pull gear data to decide the acceptance gear.

---

## P1 — “Acceptance PASS” ignores engine temperature

The analyzer parses coolant, but coolant is not part of the final acceptance gate. A cold-engine WOT run could therefore be labelled `IDEAL / ACCEPTANCE PASS`.

The current `112035` run is warm (~91 C in the snapshot), so it is fine. The gate is still incomplete for future use.

### Required fix

Add a warm-engine precondition to the report:
- if a fresh coolant value is available, require at least a reasonable warm threshold (e.g. >=70-80 C) for an acceptance pull;
- if coolant is unavailable/stale, show a warning rather than silently claiming an ideal log.

Keep the threshold centralized/configurable instead of embedding it in UI text.

---

## P2 — “IDEAL” wording overstates what the analyzer proves

Current UI/report strings use:

```text
ІДЕАЛЬНИЙ ЗВІТ / ACCEPTANCE PASS
ЗВІТ ЗАЇЗДУ: ІДЕАЛЬНО
```

The analyzer proves **log suitability**, not that the ECU calibration, turbo, AFR/smoke, EGT, N75 control, or requested-vs-actual boost are ideal. Generic Mode 01 cannot prove most of those.

### Required fix

Rename the green verdict to something factual, e.g.:

```text
ЛОГ ПРИЙНЯТО / ACCEPTANCE LOG PASS
```

and add one short line:

```text
Це оцінка якості/повноти логу, а не оцінка калібровки чи стану турбіни.
```

---

## P2 — abnormal immediate stop has no explicit abort marker

Normal user START/STOP is now good and proven by `112035`.

But `stopWotLog(immediate = true)` closes the logger directly. On telemetry loss/disconnect/onStop it can end without a `SESSION_ABORT`/reason marker.

### Required fix

Before an immediate forced close, if the writer is healthy, append something like:

```text
SESSION_ABORT reason=CORE_TELEMETRY_LOST
SESSION_ABORT reason=BARO_LOST
SESSION_ABORT reason=ACTIVITY_STOPPED
```

Do not wait for another ECU command; this is local metadata only.

---

## P2 — project summary contains stale/unverified protocol ground truth

`project_summary.md` currently mixes some assumptions with verified facts. In particular:
- it describes the adapter as `ELM327 v1.5 / PIC18F25K80`, while the real trace reports `ATI ELM327 v2.3`;
- it mentions `ATSP5`, while the proven connection path is AUTO `ATSP0` and the adapter reports ISO 14230-4 KWP 5BAUD (`A4`);
- it hard-codes an older “latest commit” identifier;
- gearbox-code/tire assumptions should be clearly labelled if they are not directly verified.

Update documentation only; do not alter the working transport because of these text issues.

---

## P2 — add `112035` as the second real-car acceptance fixture

There is a real-car acceptance test for `111930`, which is good. Add `Turbo_Pair_20260916_112035.csv` as another fixture because it proves:
- full ~1300→4000 coverage;
- stable 4th-gear ratio;
- warm engine snapshot context;
- sustained high load;
- new cycle-boundary recording behavior in its matching RAW file.

Also add a RAW-boundary fixture assertion:
- first non-snapshot acquisition after START is `010C`, then `010B`;
- final session marker is `SESSION_STOP`;
- there is no unmatched `010C` after the final complete pair.

---

# Current disposition

The logger and normal recording flow are now functioning well enough that **another road trip is not needed to debug basic logging**.

The latest `112035` run is suitable as real calibration-analysis evidence for generic OBD channels: RPM, absolute MAP/boost, MAF, speed, load, BARO and timing quality.

Do not change RFCOMM, handshake, pair-validity threshold, or the real-car-validated RECORDING 4/8/8 cadence in this pass.

Next development pass should only strengthen `LogQualityAnalyzer` semantics and metadata/documentation, all testable offline.