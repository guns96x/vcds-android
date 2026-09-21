# Project Summary — VCDS Android

**Updated:** 2026-09-21

## Current architecture

The previous cable-only refactor was reverted because it left Turbo Fast pre-flight/readiness/logger code tied to ELM while the UI exposed only USB KWP. The project now has isolated pipelines again:

- **Turbo Fast / Bluetooth ELM327** — validated WOT logger.
- **VAG OEM / TP2.0** — measuring-group path with independent OEM pre-flight.
- **USB OEM / KWP** — measuring-group path with independent OEM pre-flight; hardware validation pending.
- **Simulator** — development path.

## Recovery fixes

- Restored the previously validated Turbo Fast UI and control flow.
- OEM logging no longer depends on Mode 01 `010C/010B/0110/010D/0104` readiness.
- OEM `CHECK DATA` validates Groups 011/008/003 before enabling RAW logging.
- Long-press OEM `CHECK DATA` runs a 10-second Group 011 stress test.
- OEM start/stop writes `Event_RAW` and does not run the TurboPair analyzer.
- CSV writer failure is handled by the current pipeline's shutdown path.
- GitHub upload prefers the correct artifact for the current mode.
- USB KWP RX accumulates fragmented serial reads and rejects tester echo.
- Measuring-group decoding is scaler-driven; unsupported scalers remain RAW.
- Expanded OEM context groups are retained without hardcoding unverified engineering units.

## Verified real-car evidence retained

Existing ELM road logs demonstrated:
- 100% valid RPM/MAP pairs in the referenced 58-pair run.
- approximately 215 ms average sequential command latency.
- phone barometer around 1004.4–1004.8 mbar in that session.
- dynamic relative boost calculation.
- Turbo Fast recording cadence suitable for further tuning logs.

These facts validate the **ELM Turbo Fast path only**. They do not validate USB KWP or Ross-Tech intelligent-interface host communication.

## Next hardware acceptance

Before using OEM/USB data for calibration decisions:

1. Connect while stationary with ignition on.
2. Run OEM `CHECK DATA`; Groups 011/008/003 must all pass without `raw` scalers.
3. Long-press `CHECK DATA` and run the 10-second Group 011 stress test.
4. Confirm RPM tracks the tachometer and boost/MAF units are credible.
5. Record a short OEM RAW log and inspect it before any road pull.
6. Only then use OEM data for tuning analysis.

Turbo Fast remains available independently for the established 4th-gear WOT workflow.
