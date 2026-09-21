# B03-V2 / Ross-Tech Clone Reverse Engineering Ledger

**Target Device**: B03-V2 / Ross-Tech Direct USB Interface (`USB\VID_0403&PID_FA24\RT000001`)  
**Repository**: `guns96x/vcds-android`  
**Specification**: GitHub Issue #3  
**Status**: Experimental Multi-Adapter Transport & Evidence-Driven Tooling Complete  

---

## 1. Executive Summary & Evidence Ledger

All claims regarding the adapter hardware, physical link, and protocol framing are strictly classified according to verifiable evidence:

| Item | Status | Evidence / Verification Method | Technical Implications |
|:---|:---:|:---|:---|
| **USB Vendor ID `0x0403`** | `PROVEN` | Windows SetupAPI logs (`setupapi.dev.*.log`), `RT-USB64.inf` | Authentic or cloned FTDI silicon is present on the board. |
| **USB Product ID `0xFA24`** | `PROVEN` | Windows SetupAPI logs, `RT-USB64.inf` | Interface identifies as "Ross-Tech Direct USB Interface" (HEX-USB+CAN). |
| **Silicon Revision `0x0600`** | `PROVEN` | Windows SetupAPI logs (`REV_0600`) | FTDI factory bcdDevice specifically denoting **FT232R generation** (FT232RL/FT232RQ). |
| **Serial String `RT000001`** | `PROVEN` | Windows SetupAPI logs | Factory-programmed clone serial string in FTDI EEPROM. |
| **Driver `RT-USB64.SYS` / `RT-USB.DLL`** | `PROVEN` | PE Export Analysis (`dumpbin` / python PE export scan) | 100% binary match with FTDI D2XX library v2.10.00.1 API surface. |
| **Direct USB (Non-VCP)** | `PROVEN` | Ross-Tech Public Technical FAQ & Driver INF | Driver bypasses Windows `ftser2k.sys` COM port emulation; uses direct Bulk transfers. |
| **Bulk Endpoint Layout (64B)** | `STRONGLY SUPPORTED` | FT232R silicon datasheet & USB descriptor standards | Full-Speed USB 2.0 (12 Mbps); EP 0x02 OUT (Host→MCU), EP 0x81 IN (MCU→Host). |
| **FTDI Bulk IN 2-Byte Header** | `PROVEN` | FTDI D2XX specification & driver behavior | Every Bulk IN packet contains 2 bytes modem/line status (`[Status0, Status1]`). |
| **Coprocessor ATmega162** | `STRONGLY SUPPORTED` | `pabloaul/vag157-adapter`, B03-V2 teardowns | Microchip ATmega162 (PLCC-44 / TQFP-44) running cloned Ross-Tech firmware. |
| **CAN Controller MCP2515** | `STRONGLY SUPPORTED` | Independent B03-V2 teardowns & PCB schematics | Standalone SPI CAN controller paired with TJA1050 / MCP2551 transceiver. |
| **Logic ATF16V8B / GAL16V8** | `INFERRED` | Hardware teardowns | Programmable logic device handling K/L-line switching and security routing. |
| **ATmega Reset on DTR#** | `STRONGLY SUPPORTED` | Hardware teardowns & FTDI pinout traces | MCU reset pin is tied to FTDI DTR# (active low). Reset is released when DTR is pulled low. |
| **Host ↔ MCU Framing Protocol** | `UNKNOWN` | Requires live USBPcap capture differential analysis | Zero guessing allowed in code. Handled fail-safe via `AdapterResponse.Unsupported`. |
| **MCU Operating Baud Rate** | `UNKNOWN` | Clones vary (57600, 115200, 250000, 500000) | Must be confirmed by inspecting FTDI `FTDI_SIO_SET_BAUDRATE` control URBs in capture. |

---

## 2. Confirmed Physical USB Layer

### 2.1 Hardware Identity
- **Device Description**: Ross-Tech Direct USB Interface
- **Hardware IDs**: `USB\VID_0403&PID_FA24&REV_0600`
- **Instance ID**: `USB\VID_0403&PID_FA24\RT000001`
- **Service Name**: `RT-USB`
- **Driver Provider**: Ross-Tech LLC (rebranded FTDI D2XX driver)

### 2.2 Endpoint Mechanics & FTDI Framing
- **Endpoint 0 (Control)**:
  - Standard FTDI Vendor Requests (`bmRequestType = 0x40`):
    - `0x00` (`FTDI_SIO_RESET`): wValue=0 (Reset), 1 (Purge RX), 2 (Purge TX)
    - `0x01` (`FTDI_SIO_MODEM_CTRL`): DTR / RTS control (Reset pin toggle)
    - `0x03` (`FTDI_SIO_SET_BAUDRATE`): Baud rate divisor
    - `0x04` (`FTDI_SIO_SET_DATA`): 8-N-1 framing
    - `0x09` (`FTDI_SIO_SET_LATENCY_TIMER`): Host polling interval (typically 1–2 ms for VCDS)
- **Endpoint 2 (Bulk OUT)**:
  - Carries raw commands from Host PC/Android to FTDI TXD pin.
  - In KKL mode: Raw K-Line UART bytes (10400 baud).
  - In B03-V2 mode: Framed MCU commands to ATmega162 USART0.
- **Endpoint 1 (Bulk IN)**:
  - Full-Speed 64-byte packets.
  - **First 2 bytes**: FTDI modem and line status bytes (`[Status0, Status1]`).
  - **Bytes 2..N**: UART payload from ATmega162 RXD pin. If length == 2, packet is an empty heartbeat poll.

---

## 3. Two-Stage Multi-Adapter Architecture

To support diverse adapters without architectural churn, `vcds-android` cleanly decouples the link layer from the protocol layer:

```
┌─────────────────────────────────────────────────────────────┐
│                      DiagnosticEngine                       │
│    (Measuring Blocks, DTCs, Turbo Fast, Preflight Health)   │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                      AdapterTransport                       │
│  ├── Elm327Adapter     (AT command state machine)           │
│  ├── KklAdapter        (Transparent 10400 UART pass-through)│
│  ├── HexB03Adapter     (Experimental B03-V2 / Ross-Tech)    │
│  ├── HexLegacyAdapter  (Legacy HEX-USB)                     │
│  └── HexV2Adapter      (HEX-V2 STM32 placeholder)           │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                       HardwareDriver                        │
│  ├── UsbFtdiDriver     (FTDI D2XX Bulk & DTR MCU Reset)     │
│  ├── UsbCh34xDriver    (WCH CH340 / CH341 USB-UART)         │
│  ├── UsbCdcDriver      (Standard USB CDC-ACM / CP2102)      │
│  └── BluetoothDriver   (SPP / RFCOMM for wireless dongles)  │
└─────────────────────────────────────────────────────────────┘
```

### Two-Stage Resolution:
1. **Stage 1 (`AdapterRegistry.createHardwareDriver`)**: Inspects USB descriptors and creates the link driver (`UsbFtdiDriver`, `UsbCh34xDriver`, `UsbCdcDriver`).
2. **Stage 2 (`AdapterRegistry.selectAdapter`)**:
   - `0403:FA24` → `HexB03Adapter`
   - `0403:FA20` → `HexLegacyAdapter`
   - `0403:FA30` → `HexV2Adapter`
   - `0403:6001` → `KklAdapter` (10400 baud)
   - `1A86:*`    → `KklAdapter` (CH340)
   - Unknown FTDI → `KklAdapter` fallback (never crashes)

---

## 4. Safety Guardrails & Non-Destructive Policy

In `HexB03Adapter`, safety guardrails are strictly enforced by `assertReadOnlyGuardrails()`:
- **Blocked Services**:
  - `0x2E` (WriteDataByIdentifier)
  - `0x3B` (WriteDataByLocalIdentifier)
  - `0x34` (RequestDownload — ECU flashing)
  - `0x35` (RequestUpload)
  - `0x36` (TransferData — ECU flashing payload)
  - `0x37` (RequestTransferExit)
  - `0x28` (CommunicationControl)
  - `0x31` (RoutineControl — actuation / destructive tests)
- **Zero Guessed Bytes**:
  - Any unverified command opcode immediately returns `AdapterResponse.Unsupported`.
  - No synthetic packet formats or invented magic headers exist in the codebase.

---

## 5. Tooling & Capture Workflow

The following tools have been added to `tools/usb/` and verified:
1. `tools/usb/inspect_ross_tech_usb.ps1`:
   - Inspects connected USB devices on Windows, queries SetupAPI, outputs verified JSON.
2. `tools/usb/capture_vcds_traffic.ps1`:
   - Automates `C:\Program Files\USBPcap\USBPcapCMD.exe` for timed (e.g. 30s) or interactive capture.
3. `tools/usb/parse_usb_pcap.py`:
   - Pure-Python parser for USBPcap `.pcap` files. Decodes FTDI control transfers (baud rate divisor, latency timer, modem control), strips Bulk IN FTDI status headers, and exports JSON/CSV transcripts.
4. `tools/usb/diff_usb_captures.py`:
   - Differential analyzer comparing multi-phase captures (Options/Test vs Engine Connect vs Group 011 vs DTC read) to isolate invariant setups, phase-specific commands, and protocol correlations.
5. `tools/usb/generate_fixtures.py` & `tools/usb/test_pcap_tools.py`:
   - Verified synthetic fixtures and automated test suite.

---

## 6. Real-Car Validation Status & Next Steps

### Completed:
- [x] Full source & evidence matrix documented (`docs/research/hex_b03_source_matrix.md`).
- [x] Windows hardware inspection tool and evidence ledger (`tools/usb/inspect_ross_tech_usb.ps1`).
- [x] Android USB descriptor dumper and profile classifier (`AndroidUsbProbe.kt`).
- [x] Modular two-stage adapter registry (`AdapterRegistry.kt`).
- [x] Clean link-layer drivers (`HardwareDriver`, `UsbFtdiDriver`, `UsbCh34xDriver`, `UsbCdcDriver`).
- [x] Experimental `HexB03Adapter` with read-only guardrails and raw trace listener.
- [x] Pure-Python USBPcap parser and differential analyzer (`tools/usb/parse_usb_pcap.py`, `tools/usb/diff_usb_captures.py`).
- [x] Comprehensive unit test suites for adapter registry, guardrails, and capture parsers.

### Remaining for Future Vehicle Capture Iteration:
- [ ] Connect physical cable to vehicle OBD-II port.
- [ ] Execute the 5-phase capture plan (`docs/research/hex_b03_capture_plan.md`) using `capture_vcds_traffic.ps1`.
- [ ] Run `diff_usb_captures.py` on real `.pcap` files to identify proven coprocessor command IDs.
- [ ] Implement proven coprocessor command IDs in `HexB03Adapter.kt`.
