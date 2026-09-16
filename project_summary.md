# Project Summary: VCDS-ANDROID (VCDS Mobile for Android)
**Last Updated**: 2026-09-16 | **Status**: Real-Car Validated on Road Pulls (58/58 pairs, 1.405 bar boost, ~1.85 Hz pair rate)

## 1. Hardware & Configuration
- **Application**: VCDS Mobile for Android (`com.vag.vcdsandroid`).
- **Target OS**: Android 8.0+ (Tested on Samsung Galaxy S24 FE via ADB and Bluetooth RFCOMM).
- **Target Vehicle & ECU**: VW Golf 5 1.9 TDI BLS, Bosch EDC16U34, 0A4 5-speed manual gearbox (GQQ / JCR).
- **Interface Adapters**:
  - Wireless: ELM327 Bluetooth (v1.5 / PIC18F25K80) via RFCOMM SPP UUID `00001101-0000-1000-8000-00805F9B34FB` with auto-protocol negotiation (ISO 14230-4 KWP 5BAUD / `ATSP5`). Redacted MAC `XX:XX:XX:XX:XX:XX`.
  - Wired: Direct Android USB Host via `usb-serial-for-android` at 10400 bps (8N1) for FTDI (FT232R), CH340, and KKL K-Line cables.
- **Log Files Directory**:
  - Android Device: `/storage/emulated/0/Android/data/com.vag.vcdsandroid/files/Documents/VCDS_Logs/`
  - PC Repository: `test_logs/YYYYMMDD/`

## 2. Verified Invariants & Ground Truth (🟢)
- **ELM327 KWP Fast Polling (Turbo Fast)**:
  - Nominal KWP command round-trip latency is ~200–220 ms (measured average: 215.1 ms).
  - Core pair queries: `010C` (RPM) immediately followed by `010B` (MAP). $\Delta t$ is ~198–257 ms (well within 550 ms validity threshold).
  - Split Cadence Policy:
    - **RECORDING**: Cadence 4/8/8 (MAF every 4th pair, Speed every 8th, Load every 8th offset by 4). Slow PIDs strictly OFF during WOT. Core pair rate: ~1.85 Hz (58 pairs in 30.9 s).
    - **LIVE**: Cadence 3/6/6 (MAF every 3rd pair, Speed every 6th, Load every 6th offset 3). Slow PIDs (Coolant `0105`, IAT `010F`, Battery `0142`/`ATRV`, Baro `0133`) scheduled strictly on clean cycles (`auxPids.isEmpty()`).
- **Phone Barometer Baseline**:
  - Real hardware barometer (`PhoneBarometerProvider` + `BarometerMedianFilter`) provides dynamic ambient pressure (measured stable at 1004.4–1004.8 mbar).
  - Absolute boost gauge calculated dynamically: $\text{Boost} = \text{MAP}_{\text{abs}} - \text{BARO}_{\text{phone}}$.
- **Gear Ratios for Golf 5 1.9 TDI BLS (0A4 GQQ/JCR Manual, ~1.94m tire)**:
  - 1st gear: ~8.5 km/h / 1000 RPM
  - 2nd gear: ~15.4 km/h / 1000 RPM
  - 3rd gear: ~24.8 km/h / 1000 RPM (typical range: 23.0..27.5)
  - 4th gear: ~35.4 km/h / 1000 RPM (typical range: 32.5..38.5) — **Target gear for WOT pull**
  - 5th gear: ~46.0 km/h / 1000 RPM (typical range: 43.0..50.0)
- **Post-Log Quality Analyzer (`LogQualityAnalyzer.kt`)**:
  - Pure Kotlin analyzer evaluating valid pair %, RPM range (1300→4000), peak MAP & boost, BARO stability, aux ages, gear verification via `kmh_per_1000rpm` with fresh speed ($\le 1000$ ms), and early spool load (1500–1900 RPM $\ge 80\%$).
  - Produces human-readable dialog on app stop with instant feedback and copy-to-clipboard button.
- **Cycle-Boundary START/STOP Transitions**:
  - Atomic flags `recordingStartRequested` / `recordingStopRequested` applied at the top of the polling loop.
  - Guarantees log starts with `[SESSION_START]` -> `SESSION_SNAPSHOT` -> `010C` -> `010B`. Zero leaked LIVE aux commands before pair 1.
  - Guarantees log stops after completing a full pair with `[SESSION_STOP]`. Zero orphan `010C` events.
  - Immediate flush (`immediate = true`) guarantees file closure on unexpected disconnect or telemetry loss.

## 3. Current Project Status
- Latest commit on `master`: `49d3c49` (pushed to `origin/master`).
- GitHub Actions CI: **SUCCESS** (`Android CI` running `testDebugUnitTest` + `assembleDebug`).
- APK compiled and installed on target phone (`100.105.189.114`).
- All 60 unit tests passing.

## 4. Key Decisions Made
- **Pair-First Polling**: Split RPM and MAP into individual single-PID queries rather than grouped commands to minimize inter-command latency jitter on standard ELM327 clones.
- **Cycle-Boundary Recording Transitions**: Move recording-mode transitions to the polling-cycle boundary instead of asynchronous UI-thread interruptions.
- **Strict Post-Log Auxiliary Age Gate**: Use strict $\le 1000$ ms age gate for gear classification and $\le 1200$ ms for spool load analysis, while keeping $\le 4500$ ms channel health window for UI rendering.

## 5. Real-Car Log Evidence (2026-09-16 10:23:49, 58 Pairs)
- 58/58 pairs valid (100%), $\Delta t$ avg 215.1 ms.
- Peak MAP: 2410 mbar, Peak relative boost: +1.405 bar.
- Ambient BARO: 1004.4..1004.8 mbar from real phone barometer.
- Run was identified as 3rd gear (~24.7–25.1 km/h / 1000 RPM).
- WOT pedal was pressed late (~1998 RPM), missing early spool (1500–1900 RPM), and ended early at 3344 RPM. Driver instructed to pull on 4th gear from 1300 to 4000 RPM.

## 6. Discarded Hypotheses (🔴 Anti-Memory / Do Not Repeat)
- **Discarded: Changing RECORDING cadence away from 4/8/8.** Real car proved 4/8/8 maintains MAF age $\le 2050$ ms and Speed/Load $\le 4070$ ms without dropping pair rate.
- **Discarded: Polling slow PIDs during active WOT recording.** Exceeds KWP bus timing budget.
- **Discarded: Hardcoding ambient pressure to 1013 mbar.** Real elevation/weather shifts baseline significantly (e.g. 1004.6 mbar); dynamic phone/OBD BARO resolver is mandatory.
- **Discarded: Asynchronous UI start/stop logger interruption.** Leaked LIVE commands before pair 1 and left orphan RPM queries at log stop.
