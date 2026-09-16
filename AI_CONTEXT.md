# AI_CONTEXT: VCDS Mobile for Android
> **Target Audience**: AI coding assistants (ChatGPT, Claude, etc.) inspecting this codebase.
> **Purpose**: Read THIS SINGLE FILE first. It contains the complete architectural map, current ground truth, and recent changes so you do NOT need to re-read the entire codebase.

---

## 1. System Ground Truth
- **Application**: VCDS Mobile (`com.vag.vcdsandroid`)
- **Repository**: [https://github.com/guns96x/vcds-android](https://github.com/guns96x/vcds-android) (`master` branch)
- **Target Car**: VW Golf 5 1.9 TDI BLS (PQ35 Platform, Bosch EDC16U34 ECU, Gateway J533)
- **Target Device**: Samsung Galaxy S24 FE (Android 16) with hardware Bosch BMP580 Barometer
- **Hardware Interfaces**:
  1. **Bluetooth RFCOMM (Primary)**: V-LINK adapter (ELM327 v2.3 clone, MAC `10:21:3E:4D:2A:93`). Measured performance: ~216 ms average per command, ~4.0-4.5 req/s.
  2. **USB-OTG KKL Cable**: FTDI FT232R / CH340 via `usb-serial-for-android` at 10400 bps.

---

## 2. Architecture & File Responsibilities
Every key feature is isolated in these specific files:

| File | Purpose / Responsibility |
| :--- | :--- |
| `MainActivity.kt` (`app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt`) | Core UI controller. Mode A (Turbo Fast) & Mode B (OEM TP 2.0). Screen wake lock during recording, BARO watchdog, pre-flight check, and 100ms ticker. |
| `Elm327DiagnosticEngine.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/Elm327DiagnosticEngine.kt`) | ELM327 Bluetooth state machine. Deterministic 3-stage handshake (AUTO FIRST -> FIXED SP6 DEFAULT -> PHYSICAL 7E0/7E8). |
| `GenericObdHandshake.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/GenericObdHandshake.kt`) | Pure handshake executor implementing clean ATD resets and strict prompt detection. |
| `TurboScheduler.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/TurboScheduler.kt`) | Pair-first 3/6/6 aux cadence poller: RPM+MAP every cycle, MAF every 3rd pair, Speed every 6th, Load every 6th offset 3. Slow rotation (~2.5s) in LIVE mode only. |
| `TelemetryFreshnessPolicy.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/TelemetryFreshnessPolicy.kt`) | Centralized single source of truth for channel freshness (RPM/MAP: 1000ms, MAF: 2500ms, Speed/Load: 4500ms, Slow: 12000ms, Phone Baro: 5000ms). |
| `SessionBaroResolver.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/SessionBaroResolver.kt`) | Multi-candidate BARO resolver. Priority: `PID_0133 > ENGINE_OFF_MAP > PHONE_BAROMETER > UNAVAILABLE`. No fixed 1000/1013 fallbacks. |
| `PhoneBarometerProvider.kt` & `BarometerMedianFilter.kt` (`app/src/main/java/com/vag/vcdsandroid/sensors/`) | Android `Sensor.TYPE_PRESSURE` provider with rolling 10-sample median filter, 800..1100 mbar bounds check, and monotonic timestamp preservation. |
| `AsyncCsvLogger.kt` (`app/src/main/java/com/vag/vcdsandroid/logging/AsyncCsvLogger.kt`) | High-speed, non-blocking asynchronous CSV recorder (buffered channel + dedicated I/O coroutine). Writes synchronized turbo pairs and raw events. |
| `CoreTelemetryHealth.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/CoreTelemetryHealth.kt`) | Link health watchdog (fails safe on 3 consecutive core timeouts). |

---

## 3. Operational Modes
1. **Mode A: Turbo Fast (Generic OBD-II Mode 01)**
   - **Target**: Maximum acquisition rate for RPM (`010C`) and MAP (`010B`) during 4th gear WOT pulls.
   - **Handshake Order**:
     1. Stage 1: Clean `ATD` -> `ATSP0` (AUTO path).
     2. Stage 2: Clean `ATD` -> `ATSP6` with default CAN header.
     3. Stage 3: Clean `ATD` -> `ATSP6` -> Physical `ATSH7E0` / `ATCRA7E8`.
   - **Synchronized Logging**: Pairs RPM and MAP when time delta is under 550ms (`TURBO_PAIR_MAX_DELTA_MS`).
   - **Boost Calculation**: Relative Boost = MAP − BARO.
   - **BARO Priority**: `PID_0133 > ENGINE_OFF_MAP > PHONE_BAROMETER > UNAVAILABLE`.

2. **Mode B: VAG OEM (VW TP 2.0 CAN + KWP2000)**
   - **Target**: Proprietary Measuring Blocks (Group 011: RPM, Specified Boost, Actual Boost, N75 Duty Cycle %; Group 008: IQ Limits; Group 003: MAF).
   - **Bus**: VW TP 2.0 raw CAN frames via ELM327 with `ATCAF0`.

---

## 4. Current State & Active Focus
- **Build Status**: `./gradlew.bat clean testDebugUnitTest assembleDebug` PASSED (50+ unit tests).
- **APK Status**: Installed and active on phone via ADB.
- **Hardware Validated**: Real car log confirmed 100% valid pairs (`58/58`), live phone barometer resolution (`1004.5 mbar`), relative boost tracking, and stable cadence.
- **Log Storage**: `/sdcard/Android/data/com.vag.vcdsandroid/files/Documents/VCDS_Logs/`
