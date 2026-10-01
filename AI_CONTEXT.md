# AI_CONTEXT — VCDS Mobile for Android

## Current hardware ground truth (2026-09-23)

- Exact cable: `0403:FA24 / RT000001`.
- Exact phone: Samsung Galaxy S24 FE.
- **M1 is physically proven on the phone**: the cable answered the FA24 plaintext probe with `01 60 44` and identify `ROSSTECH A89D010009` in ~20 ms.
- The user's real-car ELM connection traces repeatedly report **ISO 14230-4 / KWP 5BAUD** with `TP2.0 Active: false`. Therefore the Golf 5 BLS has a viable K-Line/KWP path and M2 should use that path first.
- Independent public FA24 research shows that the cable's **intelligent diagnostic path** after plaintext bring-up uses per-session/per-ECU encrypted `B8/B7` transport. Do not spend project time reconstructing clone-auth/genuineness logic. For this cable, the supported interoperability path is **legacy dumb K-Line mode**.
- Legacy HEX-USB+CAN dumb mode is a K-Line pass-through. Until a safe, evidence-backed phone-side mode switch exists, one-time mode selection must be done in Windows VCDS: Test -> disable `Boot in intelligent mode` (or enable `Force Dumb Mode`) -> Test again.
- Current active milestone: **M2 = reliable 01-Engine connection over dumb K-Line**, with UI/features frozen.
- M3 measuring groups remain disabled until M2 produces a checksum-valid KWP reply from ECU source address `0x01`.

## M2 audit (2026-10-01, master 7bb956a) — fixed in code, UNVERIFIED on the car

Audit of the code that actually ran, not of the notes. Master (a consolidation merge) had drifted from the notes above: the notes described the `ccr-6bd6a0fe-368cq3` build, the code still had the older flow.

| # | Finding in master | Evidence | Fix |
|---|---|---|---|
| 1 | The live "01-ENGINE (0x84)" button called a second, older `init5Baud()`. It sent `53 07 84 01 00 03 D2` (payload `[addr,00,03]`) instead of `53 07 84 03 01 00 D2`. XOR is order-blind, so the checksum is equal and the old unit test passed. | code | Removed `init5Baud()` and `CandidateB03Command.Init5Baud`; `Hex5BaudInit` is the only encoder. Test pins the byte order. |
| 2 | The same function demanded >= 6 payload bytes and read a 4-byte LE baud. The documented reply carries 5 (`BH BL KB1 KB2 55`), so a correct reply would have been rejected as "short payload". | code vs spec | Gone with #1. `parseReply` now requires exactly 5 bytes and sync `55`. |
| 3 | A failed probe automatically ran `forceIntelligentModeRescue()`: DTR reset pulse plus blind `SetBoot(2)`. A "RESCUE SMART" button did the same. | code | No code path writes the boot mode. `setLegacyDumbMode`, `setIntelligentMode`, `forceIntelligentModeRescue` return failure unless the adapter is built with `allowBootModeWrites = true` (default false; the app never sets it). |
| 4 | `runSmartEngineWakeUp()` (the correct 0x84 path) was dead code. | grep | The connect flow now calls it. |
| 5 | The screen turned green "Interface Connected" as soon as the serial port was open, with no ECU contact. | code | Green is reserved for a session. The smart path shows yellow and the real result. |
| 6 | The `0x81` second attempt contradicted the KB (RETRACTED, never revive). | KB vs code | Removed; the single canonical request is repeated once, only after total silence. |
| 7 | No separate reason for echo-only, silent cable, bad checksum, unexpected frame. | code | `usb/UsbConnectionState.kt`: `UsbConnectResult` (13 causes), `B03RxClassifier`, `UsbConnectionTracker` (forward-only states, logged as `USB_STATE`). |
| 8 | Stale cable bytes could be matched to the 0x84 reply. | code | The 0x84 exchange flushes the FTDI buffers and the stream decoder first. The proven M1 probe sequence is unchanged (no flush added). |

Checked and found sound: `KLineByteReader` (256-byte packet reads, queue), extended-length parsing, tester-echo rejection (source `F1`), checksum validation, `readKwpPayload` (bounded 512 bytes, deadline-driven, no endless loop), `HexB03StreamDecoder` (drops the S-frame echo because it expects marker `4D`). `KwpFrameParser.extractAll` was added for several frames in one packet.

Open (not changed): HexB03Adapter has no mutex around concurrent commands; the UI serialises them through `connectInProgress` and the disabled button. `readMeasuringGroup` treats `7F xx 78` (response pending) as an unexpected reply; it should wait for the next frame before M3.

Conflicts between notes, resolved toward the canonical KB: ECU 01 address byte `01` (not `81`); `0x55` in the 0x84 reply may be written by the cable itself (UNKNOWN), so `0x55` + plausible key bytes means only "ECU answered 5-baud init" (`ECU_INIT_ANSWERED`), never "connected". Only a checksum-valid KWP frame from source `01` (direct K-Line path) reaches `CONNECTION_ESTABLISHED`.

### Next car test (stationary, ignition ON, engine off)

1. Phone: install the debug APK, open the app, accept the USB permission dialog. Plug the cable into the phone first, then into the OBD port. The app waits for the cable and connects by itself; Connect also works.
2. Watch `adb logcat -s USB_STATE B03_M1 B03_M2 VCDS_DUMB VCDS_SLOW_INIT`, or share the file with "SHARE DIAGNOSTICS" in the result dialog (or long-press the upload button).
3. Expected `B03_M1` lines: `TX 53 04 02 ..`/`RX 4D ..` (probe), identify reply with `ROSSTECH`, then `TX 53 07 84 03 01 00 D2`.
4. Reading the result:
   - `RESULT ECU_INIT_ANSWERED`, `RX 4D 09 84 .. .. KB1 KB2 55 ..` with KB1/KB2 not `00 00`/`FF FF`: PASS for the 0x84 step. Not yet a session.
   - `RESULT ADAPTER_ECHO_ONLY` and then `VCDS_SLOW_INIT`: the cable is transparent; `CONNECTION_ESTABLISHED` with a `VCDS_PROBE ... verified by ECU KWP frame` line is PASS for M2.
   - `INTERFACE_NOT_POWERED`: no byte at all. Plug phone first, then car; ignition ON.
   - `INIT_TIMEOUT`: probe worked, 0x84 got no frame in 3.6 s (twice). FAIL; send the log.
   - `ECU_NOT_RESPONDING`: the cable replied on 0x84 without sync. FAIL; send the log (the reply byte is new evidence).
   - `UNSUPPORTED_PROTOCOL`: KB `01 8A` (KW1281).
   - `INVALID_CHECKSUM` / `UNEXPECTED_ADAPTER_RESPONSE`: FAIL; the raw bytes are in the dialog and the log.
5. Whatever happens, no `0E` (SetBoot) frame may appear in the log. If one does, that is a bug.

## M2 code fixes (2026-10-01) — not yet tested on the car

Found by code review, not by a car log. Status: **UNVERIFIED on hardware**.

1. **Slow init could never see sync 0x55.** It read the FTDI port into a 1-byte buffer. usb-serial-for-android 3.8.0 `FtdiSerialPort.read()` throws `IllegalArgumentException("Read buffer too small")` for buffers of 2 bytes or less, and the exception was swallowed as "0 bytes". Every five-baud init therefore ended at `WAIT_SYNC_55`. Fixed with `protocol/KLineByteReader` (256-byte packet reads plus a byte queue). W4 is measured from the arrival time of the KB2 packet.
2. **The `1A 9B` identity reply was rejected.** VAG identity replies are longer than 63 bytes and use the extended header `80 F1 01 LL ...`. `KwpFrameParser` skipped `length == 0`, so the M2 gate could not pass even with a live ECU. Extended length is now parsed.
3. **A cable that was already in dumb mode was refused.** A transparent cable cannot answer `HC::ReadBoot`, and the code aborted on "no reply". It now continues to the slow init, which is the only proof, and logs the mode as UNVERIFIED. An unknown ReadBoot value is still refused.
4. **DTR.** Neither DTR level is proven for dumb mode. The M1 link that works uses DTR clear, and DTR# is believed to drive ATmega reset (INFERRED). Attempts 1 and 2 now use DTR clear and attempt 3 uses DTR set. The log line `VCDS_SLOW_INIT ... dtr=ON|OFF klineEcho=true|false` records which level was used.
5. Failure messages now separate these cases: no K-Line echo (cable not passing K-Line), echo but no sync (ignition off or ECU not on K-Line), KW1281 keywords `01 8A` (not supported, no retry), and a W4 miss.

### Car runs 2026-10-01 (screenshots, build 7e84ab1 / 36ffc51)

- After phone-side `SetBoot(0)` the smart probe `0x02` stays silent, also after a full re-plug (USB first, then OBD).
- `HC::ReadBoot` gets no frame. RX holds exactly our request `53 04 0D 5A` (115200 baud), so the cable loops our TX back unchanged. The cable is now **transparent (dumb) and persists across re-plug**. Still UNVERIFIED: whether that loopback comes from the real OBD pin-7 K-Line or from inside the cable.
- Five-baud init to `01`, with DTR off/off/on: echo `[00 00]`, which matches the two LOW periods of address `0x01` in 7O1. No `0x55` sync. The control init to `0x33` also gets no sync.
- Open question: does the BREAK waveform reach the car's K-Line? Test it with the cable unplugged from OBD. If echo `[00 00]` and `53 04 0D 5A` still appear, the loopback is internal.

### Change of course (2026-10-01): intelligent mode with opcode 0x84

- The user's own `C:\Ross-Tech\VCDS\VCDS.CFG` (as reported) has `HexIntel=1`, `ForceK=0`, so VCDS drives this cable in **intelligent mode**, not dumb.
- Branch `reverse/vcds-ghidra` (306ba40, `reverse/IMPLEMENTATION_SPEC.md`) documents opcode `0x84` (HC::Init5Baud): the cable MCU performs the 5-baud wake-up itself. Request `53 07 84 03 <addr> 00 xx`, reply `4D 09 84 BH BL KB1 KB2 55 xx`, timeout 3300 ms. Status: **PROVEN_STATIC only**.
- Spec defects found on review: the address-parity rule is self-contradictory (0x01->0x81 but 0x03 unchanged, which is even parity), the canonical KB (VCDS_AI_ENTRYPOINT.md section 5) retracts `0x81`, so the app sends only `0x01`. Opcode `0x85` is named differently in two reverse docs. The group 011 formula types are mislabeled. The app keeps its scaler-driven decoder.
- The app now runs intelligent mode first: probe `0x02/0x04`, then `0x84` for 01, then a read-only `1A 9B` as an S-frame. The phone **never sends SetBoot** anymore. An earlier build's `SetBoot(0)` left this cable booting in dumb mode, and Windows VCDS Options -> Test with "Boot in intelligent mode" ticked restores it.

### Cable power and plug order (2026-10-01, screenshots 13:39)

- User observation: the FA24 is never recognised when it goes into the car first. It has to go into the phone first, then into the car.
- The phone restore attempt ran ~1.5 s after the USB attach, before OBD was connected. It got **no bytes at all** (`rx[none]`) on 115200, 115200+DTR, 9600+DTR and 115200+RTS. A few seconds later, after the cable went into the car, the same ReadBoot echoed `53 04 0D 5A`. INFERRED: the interface MCU/transceiver is powered from OBD pin 16, not from USB. That restore attempt is therefore **inconclusive**, not a failure.
- The app now waits after the attach until the cable answers (a frame or the echo) before it connects.

### Phone cannot leave dumb mode (2026-10-01 13:45, with OBD power)

- Restore attempt with car power: 115200, 115200+DTR pulse, 9600+DTR pulse and 115200+RTS pulse all returned only the echo `53 04 0D 5A`. No ReadBoot frame came back, so no SetBoot was sent.
- The direct K-Line path is unchanged: echo `[00 00]`, no sync from 01 or 33. The echo needs car power. A dumb-mode route to a K-Line the engine is not on (Dual-K cable) is a hypothesis, UNVERIFIED.
- Blocker: intelligent mode must be restored once in Windows VCDS. `tools/vcds/d2xx_trace/capture_vcds_engine_session.ps1` records that restore (D), 01-Engine in intelligent mode (E, wire proof for 0x84) and group 011 (F).

### Spec V2 adopted, "10400 baud bug" claim rejected (2026-10-01)

- The claim that ReadBoot was sent at 10400 baud is FALSE. In 36ffc51 the port opens at 10400, then switches 9600 -> 19200 -> 115200 before ReadBoot. The 13:45 restore screenshot shows 115200 and 9600+DTR returning only the echo.
- Adopted from `audit/vcds-ghidra-proof` `reverse/IMPLEMENTATION_SPEC_V2.md` / `RED_TEAM_REVIEW.md`: the 0x84 address byte for 01 is `0x01` (the `0x81` fallback was removed on 2026-10-01 because the KB lists it as RETRACTED), and the request is `53 07 84 03 01 00 D2`. Sending KWP services as raw adapter opcodes (`21 0B`, `1A 9B`, `3E`) is UNKNOWN and quarantined, so the app no longer sends them. The smart M2 gate is the ECU's own `55` + KB1/KB2 in the 0x84 reply.
- The restore attempt also tries plain 9600 (the VCDS open baud per spec V2).

Next car test, stationary, ignition on: Connect, then save `connection_diagnostics.log` with the `VCDS_DUMB`, `VCDS_SLOW_INIT` and `B03_M2` lines.

Read this before changing the project.

## Ground truth

- Target: VW Golf 5 1.9 TDI BLS, Bosch EDC16U34, PQ35.
- Phone: Samsung Galaxy S24 FE / Android 16.
- The project has **separate acquisition pipelines**. Do not collapse them into one mode.
- The proven road-logging path is **Turbo Fast over Bluetooth ELM327**.
- OEM measuring blocks are a separate path and produce RAW group logs.
- USB KWP is experimental beyond M1, but the physical FA24 phone handshake is proven. M2 is the direct K-Line/KWP validation step.

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
