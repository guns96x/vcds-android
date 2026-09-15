# AI_CONTEXT: VCDS Mobile for Android
> **Target Audience**: AI coding assistants (ChatGPT, Claude, etc.) inspecting this codebase.
> **Purpose**: Read THIS SINGLE FILE first. It contains the complete architectural map, current ground truth, and recent changes so you do NOT need to re-read the entire codebase.

---

## 1. System Ground Truth
- **Application**: VCDS Mobile (`com.vag.vcdsandroid`)
- **Repository**: [https://github.com/guns96x/vcds-android](https://github.com/guns96x/vcds-android) (`master` branch)
- **Target Car**: VW Golf 5 1.9 TDI BLS (PQ35 Platform, Bosch EDC16U34 ECU, Gateway J533)
- **Target Device**: Samsung Galaxy S24 FE (Android 16) via ADB (`100.105.189.114:5555`)
- **Hardware Interfaces**:
  1. **Bluetooth RFCOMM (Primary)**: V-LINK adapter (ELM327 v2.3 clone, MAC `10:21:3E:4D:2A:93`).
  2. **USB-OTG KKL Cable**: FTDI FT232R / CH340 via `usb-serial-for-android` at 10400 bps.

---

## 2. Architecture & File Responsibilities
Every key feature is isolated in these specific files:

| File | Purpose / Responsibility |
| :--- | :--- |
| `MainActivity.kt` (`app/src/main/java/com/vag/vcdsandroid/ui/MainActivity.kt`) | Core UI controller. Single-card layout for **Mode A (Turbo Fast)** and **Mode B (OEM TP 2.0)**. Polling coroutines, UI ticker (100ms), WOT log trigger. |
| `Elm327DiagnosticEngine.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/Elm327DiagnosticEngine.kt`) | ELM327 Bluetooth state machine. Handles RFCOMM, AT init sequence, multi-stage Mode 01 connection (Functional `7DF` -> Physical `7E0/7E8` -> Auto `ATSP0`), and battery voltage (`ATRV`). |
| `VwTp20Transport.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/VwTp20Transport.kt`) | VW TP 2.0 over CAN transport. Establishes channel on CAN `0x200`, timing handshake `A0/A1`, KWP session `10 89`, and reads VAG Groups 011, 008, 003. |
| `Tp20FrameParser.kt` (`app/src/main/java/com/vag/vcdsandroid/protocol/Tp20FrameParser.kt`) | Parser for multi-frame TP2.0 responses received from ELM. Handles ACKs (`B0`..`BF`), keep-alive (`A3`), and reassembly. |
| `BluetoothElmTransport.kt` (`app/src/main/java/com/vag/vcdsandroid/bluetooth/BluetoothElmTransport.kt`) | Low-level Bluetooth RFCOMM socket transport with coroutine `Mutex` serialization and timed prompt detection. |
| `AsyncCsvLogger.kt` (`app/src/main/java/com/vag/vcdsandroid/logging/AsyncCsvLogger.kt`) | High-speed, non-blocking asynchronous CSV recorder (buffered channel + dedicated I/O coroutine). Writes synchronized turbo pairs and raw events. |
| `UsbKwpTransport.kt` (`app/src/main/java/com/vag/vcdsandroid/usb/UsbKwpTransport.kt`) | USB serial port driver with thread-safe `ioLock` synchronization. |
| `TcpBridgeServer.kt` (`app/src/main/java/com/vag/vcdsandroid/usb/TcpBridgeServer.kt`) | TCP socket bridge allowing PC VCDS / external tools to talk to the USB KKL cable over Wi-Fi. |

---

## 3. Operational Modes
1. **Mode A: Turbo Fast (Generic OBD-II Mode 01)**
   - **Target**: Maximum acquisition rate for RPM (`010C`) and MAP (`010B`) during 4th gear WOT pulls.
   - **Bus**: ISO 15765-4 CAN 11-bit 500k (`ATSP6`).
   - **Addressing**: Functional `ATSH7DF` with automated fallback to Physical `ATSH7E0` / `ATCRA7E8`.
   - **Synchronized Logging**: Pairs RPM and MAP when time delta is under 150ms. Calculates relative Boost = MAP − BARO (`0133`).

2. **Mode B: VAG OEM (VW TP 2.0 CAN + KWP2000)**
   - **Target**: Proprietary Measuring Blocks (Group 011: RPM, Specified Boost, Actual Boost, N75 Duty Cycle %; Group 008: IQ Limits; Group 003: MAF).
   - **Bus**: VW TP 2.0 raw CAN frames via ELM327 with `ATCAF0`.

---

## 4. Incremental Changelog (Latest Commits)

### Commit `3fa6c4d` — 2026-09-15
- **Problem**: In Mode A, ELM327 returned `NO DATA` for `0100`/`010C` after 134ms because default timeout was too short, and Gateway J533 filtered broadcast `7DF`.
- **Solution in `Elm327DiagnosticEngine.kt`**:
  1. Added `ATRV` battery voltage logging on connect.
  2. Added `ATST64` (400ms timeout) for reliable initial gateway wakeup.
  3. Added **Physical Engine addressing fallback** (`ATSH7E0` -> `ATCRA7E8`) if broadcast `7DF` fails.
  4. Added clean `ATSP0` auto-protocol fallback.
- **TP 2.0 Byte Alignment in `VwTp20Transport.kt`**:
  - Removed trailing stray nibbles (` 1`, ` 0`) in channel setup, timing `A0`, ACK `B1`, keep-alive `A3`, and disconnect `A8` to guarantee strict hex byte parity.
- **USB Concurrency in `UsbKwpTransport.kt`**:
  - Added `ioLock` synchronized blocks around `read()`, `write()`, and `purge()` to prevent races with `TcpBridgeServer`.

### Commit `26825e9` — 2026-09-15
- Reverted cluttered multi-card UI into a clean, original single-card layout inside `activity_main.xml`.
- Unified data pipeline: `ELM RAW -> PidDecoder -> DiagnosticSample -> UI & AsyncCsvLogger`.

---

## 5. Current State & Active Focus
- **APK Status**: Latest debug APK built and installed on Samsung Galaxy S24 FE.
- **Active Task**: Verification of in-car connection with ignition ON / engine running.
- **Log Storage**: `/sdcard/Android/data/com.vag.vcdsandroid/files/Documents/VCDS_Logs/`
