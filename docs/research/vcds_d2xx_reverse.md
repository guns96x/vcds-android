# VCDS D2XX Reverse Engineering & Protocol Ledger

**Document Version:** 1.1.0  
**Target Hardware:** Ross-Tech HEX-USB+CAN / B03-V2 clone (`USB\VID_0403&PID_FA24\RT000001`)  
**Microcontroller:** Candidate MCU (ATmega162 unconfirmed on this PCB) + CPLD + FTDI USB-UART bridge  
**Scope Restriction:** This document covers **strictly hardware interoperability and diagnostic transport**. Licensing, activation, dongle cryptography, and bypass routines are excluded from this project.

---

## 1. Executive Summary & Architecture Strategy

Physical testing on the target Android device (`Samsung Galaxy S24 FE` via `UsbManager`) confirmed that sending raw blind pulses or unverified baud rates (500k/250k) returns zero bytes. 

Per unverified third-party prior art (`krzysztofmotas/hexbridge`), `RT-USB.dll` and `RTUS64.dll` are not proprietary protocol layers, but thin export shims forwarding to the FTDI D2XX driver (`FTD2XX.dll`). The actual protocol grammar, framing, and command dispatch reside inside the VCDS application engine (`VCDS.exe` / `VCDS.exeL`) immediately above the D2XX boundary (`FT_Write` / `FT_Read`).

```
┌────────────────────────────────────────────────────────┐
│             VCDS Application (VCDS.exeL)               │
└──────────────────────────┬─────────────────────────────┘
                           │ (FT_Write / FT_Read / FT_SetBaudRate)
                           ▼
┌────────────────────────────────────────────────────────┐
│     D2XX Interoperability Trace Shim (RTUS64.dll)     │ <── tools/vcds/d2xx_trace/ (SAFE_TRACE)
└──────────────────────────┬─────────────────────────────┘
                           │ (Direct 1:1 Ordinal Forwarding)
                           ▼
┌────────────────────────────────────────────────────────┐
│            Genuine D2XX Driver (RTUS64_orig.dll)       │
└──────────────────────────┬─────────────────────────────┘
                           │ (USB Bulk IN/OUT Transfers)
                           ▼
┌────────────────────────────────────────────────────────┐
│   HEX-USB+CAN Adapter (0403:FA24 / Candidate MCU)      │
└────────────────────────────────────────────────────────┘
```

---

## 2. VCDS Binary & PE Fingerprint (Stage 0 Baseline)

Canonical manifest extracted via `tools/vcds/d2xx_trace/pe_manifest.py` (committed in `pe_manifest.json`):

| Binary | Machine | File Size | SHA-256 | Export Count | Notes |
| :--- | :---: | :---: | :--- | :---: | :--- |
| `RTUS64.dll` | x64 (`0x8664`) | 260,864 B | `b2a261c16355bc3c1313f5a2f86591ac430ec5ddc7d1ddf24b517a5fb97b48f2` | 87 | Span 1..88 (ordinal 74 omitted) |
| `RT-USB.dll` | x86 (`0x014C`) | 222,464 B | `5a42313f5b7e4380e1a7b0fb8d1abc97f9321ce383c2cade85199892c550a9eb` | 87 | Legacy 32-bit driver |
| `VCDS.exeL` | x64 (`0x8664`) | 3,783,000 B | `cc7f81cc08222a14a6317abf5ebdf059e5a8853ea885524c562e0602b19733e3` | 0 | Imports `RTUS64.DLL` @ Ordinal 6 (`FT_ResetDevice`) |
| `VCDSLoader.exe` | x86 (`0x014C`) | 2,681,344 B | `8f661f16c87169fefc4dc7e612521ad8498c016a0153c51dae67af0b984adaac` | 0 | 32-bit bootstrap launcher |
| `VCDSScan.exe` | x86 (`0x014C`) | 5,512,280 B | `6e28bce88bd024fd203ee34471c315ac0840b448f0bc8183f99e175e471823f8` | 0 | Diagnostic scan helper |

### D2XX Binding Characteristics
- `VCDS.exeL` binds statically to `RTUS64.DLL` by importing ordinal 6 (`FT_ResetDevice`), ensuring the library is linked and initialized at process creation.
- Dynamic entry points for `FT_Write`, `FT_Read`, `FT_SetBaudRate`, etc. are loaded at runtime.
- By placing our Rust `RTUS64.dll` in the executable folder alongside `RTUS64_orig.dll`, all 87 functions route through the logging shim with zero modification to genuine VCDS code.

---

## 3. Physical Layer & FTDI Configuration (HYPOTHESIS / CANDIDATE)

The initial connection sequence between the FTDI USB-UART bridge and the candidate MCU is hypothesized based on third-party teardowns and prior art:

1. **Device Reset & Purge**:
   - `FT_ResetDevice(handle)`
   - `FT_Purge(handle, FT_PURGE_RX | FT_PURGE_TX)`
2. **Latency Timer**:
   - `FT_SetLatencyTimer(handle, 1)` (1 ms latency)
3. **Data Characteristics**:
   - `FT_SetDataCharacteristics(handle, 8, 0, 0)` -> **8N1** (8 data bits, 1 stop bit, no parity)
4. **Baud Rate**:
   - Initial transport hypothesis: stepped `9600 -> 19200 -> 115200` baud, or static `115,200 baud` (Status: `HYPOTHESIS / UNVERIFIED`).
5. **Modem Control Lines (DTR / RTS)**:
   - `FT_ClrDtr(handle)` / `FT_ClrRts(handle)`.
   - Hardware reset hypothesis: MCU reset line is tied to FTDI control lines (Status: `HYPOTHESIS / INFERRED`).
6. **Timeouts**:
   - Read / Write timeouts: `1000 ms` / `1000 ms`.

---

## 4. Wire Framing Grammar (HYPOTHESIS / UNVERIFIED)

Third-party prior art suggests that host PC and adapter communicate using a flat envelope:

### Frame Structure

```
┌──────────────┬──────────────┬──────────────┬────────────────────────────┬──────────────┐
│  SOF Marker  │ Total Length │ Command / Op │       Payload Bytes        │ XOR Checksum │
│   (1 byte)   │   (1 byte)   │   (1 byte)   │         (N bytes)          │   (1 byte)   │
└──────────────┴──────────────┴──────────────┴────────────────────────────┴──────────────┘
```

| Field | Offset | Type | Description | Evidence Level |
| :--- | :---: | :---: | :--- | :---: |
| `SOF Marker` | 0 | `uint8` | `0x53` (`'S'`) for Host -> Cable (OUT)<br>`0x4D` (`'M'`) for Cable -> Host (IN) | `HYPOTHESIS` (Prior art only) |
| `Total Length` | 1 | `uint8` | Total length of the frame: `totalLen = 4 + payload.size` (1 byte SOF + 1 byte Len + 1 byte Opcode + N bytes Payload + 1 byte Checksum) | `HYPOTHESIS` |
| `Opcode` | 2 | `uint8` | Command identifier (echoed in response) | `HYPOTHESIS` |
| `Payload` | 3..len-2 | `bytes` | Command parameters or response data (0 to 251 bytes) | `HYPOTHESIS` |
| `XOR Checksum` | len-1 | `uint8` | XOR sum of all preceding bytes: `0x53 ^ len ^ opcode ^ payload...` | `HYPOTHESIS` |

### Checksum Verification Rule
A frame is valid if and only if:
$$\bigoplus_{i=0}^{\text{len}-1} \text{byte}[i] == 0$$

---

## 5. Protocol Ledger & Opcode Classification (ALL UNVERIFIED HYPOTHESES)

> [!CAUTION]
> Every opcode below is categorized as `HYPOTHESIS / UNVERIFIED` until backed by a real committed D2XX trace (`vcds_d2xx_trace.log`) or static xref. No physical transmission is permitted from Android.

| Opcode | Direction | Payload Example | Meaning / Semantics | Evidence Status | Physical TX Allowed |
| :---: | :---: | :--- | :--- | :---: | :---: |
| `0x02` | OUT | `[none]` | Candidate Probe / Ping | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0x02` | IN | `01 60 44` | Candidate Probe acknowledgment | `HYPOTHESIS` | N/A |
| `0x04` | OUT | `[none]` | Candidate Identify Query | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0x04` | IN | `"ROSSTECH" <ver_bytes>` | Candidate Identify Response | `HYPOTHESIS` | N/A |
| `0x82` | OUT | `[none]` | Candidate Status Read | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0x82` | IN | `00 00` | Candidate Status Response | `HYPOTHESIS` | N/A |
| `0x0D` | OUT | `[none]` | Candidate Mode Query | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0x0D` | IN | `02` | Candidate Mode Response | `HYPOTHESIS` | N/A |
| `0xA0` | OUT | `[none]` | Candidate Keepalive Ping | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0xB0..0xB5` | OUT | `<filter_params>` | Candidate CAN setup frames | `HYPOTHESIS` | **NO (ZERO-TX)** |
| `0xFE` | IN | `[none]` | Candidate Generic ACK | `HYPOTHESIS` | N/A |
| `0xB8` | OUT | `[16-byte block]` | Candidate Diagnostic Request Envelope | `HYPOTHESIS` (Cipher `UNKNOWN`) | **NO (ZERO-TX)** |
| `0xB7` | IN | `[16-byte block]` | Candidate Diagnostic Response Envelope | `HYPOTHESIS` (Cipher `UNKNOWN`) | **NO (ZERO-TX)** |

---

## 6. Safety Guardrails, SAFE_TRACE & Zero-TX Policy

To ensure physical vehicle safety and hardware preservation:
1. **SAFE_TRACE Mode**: The D2XX trace shim strictly traps and rejects all EEPROM modification exports (`FT_WriteEE`, `FT_EraseEE`, `FT_EE_Program*`, `FT_EE_UAWrite`, `FT_EE_WriteConfig`, `FT_EEPROM_Program`) with `FT_OTHER_ERROR`. This is documented as `SAFE_TRACE` mode. Any trace run where an EEPROM call was blocked must be flagged so that no protocol sequence is inferred from blocked calls.
2. **Offline Codec Only**: The packet codec in Android is strictly for offline trace parsing.
3. **Strict ZERO-TX Policy**: Any transaction request (`transact()` or `executeCandidateCommand()`) through `HexB03Adapter` strictly returns `AdapterResponse.Unsupported` without writing any bytes to the physical hardware (`writtenBytes == 0`) until verified dynamic traces promote a command to `PROVEN_DYNAMIC`.
