# AI_CONTEXT — VCDS Mobile for Android

Read this before changing the project.

## Ground truth

- Target: VW Golf 5 1.9 TDI BLS, Bosch EDC16U34, PQ35.
- Phone: Samsung Galaxy S24 FE / Android 16.
- The project has **separate acquisition pipelines**. Do not collapse them into one mode.
- The proven road-logging path is **Turbo Fast over Bluetooth ELM327**.
- OEM measuring blocks are a separate path and produce RAW group logs.
- USB KWP is experimental until validated on the target car.

## Modes

### A — Turbo Fast OBD-II

Transport: `Elm327DiagnosticEngine` / Bluetooth RFCOMM.

Purpose: synchronized WOT logging.

Core:
- `010C` RPM
- `010B` MAP

BARO resolution priority:
`PID_0133 > ENGINE_OFF_MAP > PHONE_BAROMETER > UNAVAILABLE`

Never reintroduce a fixed 1000/1013 mbar fallback.

Logging:
- cycle-boundary START/STOP
- `Turbo_Pair_*.csv` + raw events
- pre-flight is based on Mode 01 samples
- post-log analyzer operates on Turbo Pair CSV

Real-car evidence from the existing test logs showed stable sequential ELM latency around 200–220 ms and valid synchronized pairs. Preserve this pipeline unless a new real-car log disproves it.

### B — VAG OEM over ELM / TP2.0

Purpose: proprietary measuring groups.

Must use OEM pre-flight, not Turbo Fast readiness.

### C — USB OEM / KWP

Files:
- `usb/UsbKwpTransport.kt`
- `protocol/Kwp2000DiagnosticEngine.kt`
- `protocol/KwpFrameParser.kt`

Important invariants:
- a USB serial `read()` is not a KWP message boundary
- accumulate chunks until a complete checksum-valid ECU frame appears or the deadline expires
- reject K-Line tester echo (`source == 0xF1`)
- validate positive service/group before decoding
- do not hold the FTDI/ATmega interface MCU in reset while probing it

Direct Ross-Tech intelligent-interface CAN probing is experimental, not proven host-protocol support.

### D — Simulator

Development-only.

## OEM measuring blocks

Primary acceptance groups:
- 011
- 008
- 003

Additional rotation:
007, 010, 004, 015, 001, 009, 013, 023, 020, 062, 006, 002.

`MeasuringGroup.decode()` interprets the scaler byte. Unsupported scalers must remain `raw`. Do not invent units based only on the group number or UI label.

Group 008 labels are intentionally unit-neutral until the returned scaler proves the engineering unit.

## Logger ownership

- Turbo Fast owns WOT readiness, TurboPair creation and TurboPair post-analysis.
- OEM modes own OEM pre-flight and `Event_RAW` group logging.
- OEM logger start/stop must never require ELM Mode 01 samples.
- On writer failure, stop the logger belonging to the current mode.
- GitHub upload must prefer `Turbo_Pair_` in Turbo Fast and `Event_RAW_` in OEM modes.

## Tests

Keep pure JVM tests for:
- KWP echo rejection / framing
- measuring-block scaler decoding
- Turbo scheduler / PID decoding / pre-flight policies
- logger helpers where possible

CI command:
`./gradlew testDebugUnitTest assembleDebug --no-daemon`

A green CI proves compilation/unit behavior only. Hardware transports still require stationary testing on the car.
