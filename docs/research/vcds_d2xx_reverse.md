# VCDS D2XX Reverse Engineering & Protocol Ledger

**Document Version:** 1.0.0  
**Target Hardware:** Ross-Tech HEX-USB+CAN / B03-V2 clone (`USB\VID_0403&PID_FA24\RT000001`)  
**Microcontroller:** ATmega162 + ATF16V8 / GAL16V8 + FTDI USB-UART bridge  
**Scope Restriction:** This document covers **strictly hardware interoperability and diagnostic transport**. Licensing, activation, dongle cryptography, and bypass routines are excluded from this project.

---

## 1. Executive Summary & Architecture Strategy

Physical testing on the target Android device (`Samsung Galaxy S24 FE` via `UsbManager`) confirmed that sending raw blind pulses or unverified baud rates (500k/250k) returns zero bytes. 

As proven by public prior art (`krzysztofmotas/hexbridge`), `RT-USB.dll` and `RTUS64.dll` are not proprietary protocol layers, but thin export shims forwarding to the FTDI D2XX driver (`FTD2XX.dll`). The actual protocol grammar, framing, and command dispatch reside inside the VCDS application engine (`VCDS.exe` / `VCDS.exeL`) immediately above the D2XX boundary (`FT_Write` / `FT_Read`).

```
┌────────────────────────────────────────────────────────┐
│             VCDS Application (VCDS.exeL)               │
└──────────────────────────┬─────────────────────────────┘
                           │ (FT_Write / FT_Read / FT_SetBaudRate)
                           ▼
┌────────────────────────────────────────────────────────┐
│     D2XX Interoperability Trace Shim (RTUS64.dll)     │ <── tools/vcds/d2xx_trace/
└──────────────────────────┬─────────────────────────────┘
                           │ (Direct 1:1 Ordinal Forwarding)
                           ▼
┌────────────────────────────────────────────────────────┐
│            Genuine D2XX Driver (RTUS64_orig.dll)       │
└──────────────────────────┬─────────────────────────────┘
                           │ (USB Bulk IN/OUT Transfers)
                           ▼
┌────────────────────────────────────────────────────────┐
│       HEX-USB+CAN Adapter (0403:FA24 / ATmega162)      │
└────────────────────────────────────────────────────────┘
```

---

## 2. VCDS Binary & PE Fingerprint (Stage 0 Baseline)

Inspected via `tools/vcds/d2xx_trace/pe_manifest.py`:

| Binary | Machine | File Size | SHA-256 | Export Count | Notes |
| :--- | :---: | :---: | :--- | :---: | :--- |
| `RTUS64.dll` | x64 (`0x8664`) | 260,864 B | `b2a261c16355bc3c1313f5a2f86591ac430ec5ddc7d1ddf24b517a5fb97b48f2` | 87 | Span 1..88 (ordinal 74 omitted) |
| `RT-USB.dll` | x86 (`0x014C`) | 222,464 B | `c4de39fef162cb08e3eb5d648b261b0c0a969f688e1a17951c6c59b20e06ae3d` | 87 | Legacy 32-bit driver |
| `VCDS.exeL` | x64 (`0x8664`) | 3,783,000 B | `ce12b7fa43f7f8dcf514da956ff7fa96efc87cb71a4f00d31c448bb042c16110` | 0 | Imports `RTUS64.DLL` @ Ordinal 6 (`FT_ResetDevice`) |
| `VCDSLoader.exe` | x86 (`0x014C`) | 2,681,344 B | `c72b22ec004b3cf4dbcb3b3206fb7a718b52f67645163cfca2f9fb8dcce32104` | 0 | Multi-stage bootstrap loader |
| `VCDSScan.exe` | x64 (`0x8664`) | 5,512,280 B | `448ecdb2f90a184e9c700346aa86a51d8b9281a708eb1f879bb2aa5a854999d3` | 0 | Diagnostic scan helper |

### D2XX Binding Characteristics
- `VCDS.exeL` binds statically to `RTUS64.DLL` by importing ordinal 6 (`FT_ResetDevice`), ensuring the library is linked and initialized at process creation.
- Dynamic entry points for `FT_Write`, `FT_Read`, `FT_SetBaudRate`, etc. are loaded at runtime.
- By placing our Rust `RTUS64.dll` in the executable folder alongside `RTUS64_orig.dll`, all 87 functions route through the logging shim with zero modification to genuine VCDS code.

---

## 3. Physical Layer & FTDI Configuration

The connection between the FTDI USB-UART bridge and the ATmega162 MCU requires a specific initialization sequence:

1. **Device Reset & Purge**:
   - `FT_ResetDevice(handle)`
   - `FT_Purge(handle, FT_PURGE_RX | FT_PURGE_TX)`
2. **Latency Timer**:
   - `FT_SetLatencyTimer(handle, 1)` (1 ms latency)
3. **Data Characteristics**:
   - `FT_SetDataCharacteristics(handle, 8, 0, 0)` -> **8N1** (8 data bits, 1 stop bit, no parity)
4. **Baud Rate Transition**:
   - Stepped baud transition: `9600 -> 19200 -> 115200` baud.
   - Operating transport baud rate: **115,200 baud**.
5. **Hardware MCU Gating (DTR / RTS)**:
   - `FT_ClrDtr(handle)` -> DTR driven **LOW**.
   - `FT_ClrRts(handle)` -> RTS driven **LOW**.
   - **Critical finding:** The adapter hardware gates the downstream ATmega162 reset/sleep line on DTR/RTS. If DTR/RTS are left floating or HIGH, the ATmega162 is held in reset and will not respond to UART traffic.
6. **Timeouts**:
   - Read / Write timeouts: `1000 ms` / `1000 ms`.

---

## 4. Wire Framing Grammar

All communication between the host PC and the ATmega162 coprocessor uses a unified flat envelope.

### Frame Structure

```
┌──────────────┬──────────────┬──────────────┬────────────────────────────┬──────────────┐
│  SOF Marker  │ Total Length │ Command / Op │       Payload Bytes        │ XOR Checksum │
│   (1 byte)   │   (1 byte)   │   (1 byte)   │         (N bytes)          │   (1 byte)   │
└──────────────┴──────────────┴──────────────┴────────────────────────────┴──────────────┘
```

| Field | Offset | Type | Description | Evidence Level |
| :--- | :---: | :---: | :--- | :---: |
| `SOF Marker` | 0 | `uint8` | `0x53` (`'S'`) for Host -> Cable (OUT)<br>`0x4D` (`'M'`) for Cable -> Host (IN) | `PROVEN_STATIC` |
| `Total Length` | 1 | `uint8` | Total length of the frame including marker, length, opcode, payload, and XOR (`len = 3 + payload.length`) | `PROVEN_STATIC` |
| `Opcode` | 2 | `uint8` | Command identifier (echoed in response) | `PROVEN_STATIC` |
| `Payload` | 3..len-2 | `bytes` | Command parameters or response data (0 to 251 bytes) | `PROVEN_STATIC` |
| `XOR Checksum` | len-1 | `uint8` | XOR sum of all preceding bytes: `0x53 ^ len ^ opcode ^ payload...` | `PROVEN_STATIC` |

### Checksum Verification Rule
A frame is valid if and only if:
$$\bigoplus_{i=0}^{\text{len}-1} \text{byte}[i] == 0$$

---

## 5. Protocol Ledger & Opcode Classification

| Opcode | Direction | Payload Example | Meaning / Semantics | Evidence Status | Safe in Android |
| :---: | :---: | :--- | :--- | :---: | :---: |
| `0x02` | OUT | `[none]` | **Probe / Ping** (checks MCU presence) | `PROVEN_STATIC` | YES (Read-Only) |
| `0x02` | IN | `01 60 44` | Probe acknowledgment / status | `PROVEN_STATIC` | YES |
| `0x04` | OUT | `[none]` | **Identify Query** | `PROVEN_STATIC` | YES (Read-Only) |
| `0x04` | IN | `"ROSSTECH" <ver_bytes>` | Identification string and firmware version | `PROVEN_STATIC` | YES |
| `0x82` | OUT | `[none]` | Status Read Query | `PROVEN_STATIC` | YES (Read-Only) |
| `0x82` | IN | `00 00` | Status Response | `PROVEN_STATIC` | YES |
| `0x0D` | OUT | `[none]` | Mode / Interface Query | `PROVEN_STATIC` | YES (Read-Only) |
| `0x0D` | IN | `02` | Interface Mode Response | `PROVEN_STATIC` | YES |
| `0xA0` | OUT | `[none]` | **Keepalive / Poll Ping** | `PROVEN_STATIC` | YES (Read-Only) |
| `0xB0..0xB5` | OUT | `<filter_params>` | CAN bit-timing and acceptance filter setup (each `0xFE`-acked) | `CORRELATED` | NO (Gated) |
| `0xFE` | IN | `[none]` | Generic ACK for setup frames | `PROVEN_STATIC` | YES |
| `0xB8` | OUT | `[16-byte block]` | Diagnostic Request Envelope (UDS/KWP) | `PROVEN_STATIC` (Framing)<br>`UNKNOWN` (Cipher) | **ZERO-TX** |
| `0xB7` | IN | `[16-byte block]` | Diagnostic Response Envelope (UDS/KWP) | `PROVEN_STATIC` (Framing)<br>`UNKNOWN` (Cipher) | **ZERO-TX** |

---

## 6. Safety Guardrails & Zero-TX Policy

To ensure physical vehicle safety and hardware preservation:
1. **EEPROM Protection**: The D2XX trace shim strictly traps and rejects all EEPROM modification exports (`FT_WriteEE`, `FT_EraseEE`, `FT_EE_Program*`, `FT_EE_UAWrite`, `FT_EE_WriteConfig`).
2. **Typed Commands Only**: Production Android code does NOT expose arbitrary raw transmission. Callers can only execute strongly-typed, read-only commands (`VerifiedB03Command.ProbePing`, `VerifiedB03Command.Identify`).
3. **Diagnostic ZERO-TX**: Any diagnostic transaction request (`01-Engine`, `DTC`, `UDS`) through `HexB03Adapter` returns `AdapterResponse.Unsupported` without transmitting any bytes (`writtenBytes == 0`) until verified dynamic traces confirm the ECU session protocol.
