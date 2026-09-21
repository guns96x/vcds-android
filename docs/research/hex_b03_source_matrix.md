# B03-V2 / Ross-Tech FTDI Clone Source & Evidence Matrix

**Document Purpose**: Evidence ledger for reverse-engineering the B03-V2 / Ross-Tech-style FTDI clone (`USB\VID_0403&PID_FA24\RT000001`) and designing a clean multi-adapter transport architecture for `vcds-android`.

**Classification Scheme**:
- `PROVEN`: Direct local hardware observation, binary export verification, or official silicon vendor documentation.
- `STRONGLY SUPPORTED`: Consistent across multiple independent hardware teardowns and driver INF definitions.
- `INFERRED`: Plausible architectural hypothesis based on known clones, but requires physical board or protocol confirmation for this specific cable.
- `UNKNOWN`: Unverified; zero guessed assumptions permitted in code.

---

## 1. Primary Source Matrix

### [SRC-01] Local Windows SetupAPI Log & Driver Store
- **URL / Location**: `C:\Windows\inf\setupapi.dev.*.log`, `C:\Ross-Tech\VCDS\RT-USB64.inf`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - `VID_0403`: Hardware vendor is FTDI (Future Technology Devices International Ltd.). (`PROVEN`)
  - `PID_FA24`: Product ID assigned by Ross-Tech to "Ross-Tech Direct USB Interface" (HEX-USB+CAN / Dual-K & CAN). (`PROVEN`)
  - `REV_0600`: Silicon revision is `0x0600`, which FTDI assigns strictly to the **FT232R generation** (FT232RL/FT232RQ). (`PROVEN`)
  - `Serial`: Hardcoded string `RT000001`. (`PROVEN`)
  - Active Service: `RT-USB` loading `RT-USB64.SYS` / `RT-USB.DLL`. (`PROVEN`)
  - Driver failure code `0x27` (`CM_PROB_DRIVER_FAILED_LOAD`, `0xc000007b`): Driver failed signature enforcement under modern 64-bit Windows without test signing. (`PROVEN`)
- **What it Does NOT Prove**:
  - Does not identify the microcontroller behind the FTDI UART pins (e.g. ATmega162 vs STM32 vs PIC).
  - Does not reveal the application-level command protocol between PC and microcontroller.
- **Confidence**: `PROVEN`
- **Relevant Bytes / Strings**:
  - Hardware ID: `USB\VID_0403&PID_FA24&REV_0600`
  - Instance ID: `USB\VID_0403&PID_FA24\RT000001`

---

### [SRC-02] PE Export Analysis of `C:\Ross-Tech\VCDS\RT-USB.DLL`
- **URL / Location**: `C:\Ross-Tech\VCDS\RT-USB.DLL`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - `RT-USB.DLL` exports identical symbols to FTDI's standard D2XX library (`FT_Open`, `FT_Close`, `FT_Read`, `FT_Write`, `FT_SetBaudRate`, `FT_SetDataCharacteristics`, `FT_SetFlowControl`, `FT_SetDtr`, `FT_ClrDtr`, `FT_SetRts`, `FT_ClrRts`, `FT_SetTimeouts`, `FT_Purge`, `FT_SetLatencyTimer`, `FT_SetBitMode`). (`PROVEN`)
  - File metadata reveals `ProductVersion: 2.10.00.1` and `FileVersion: 3.02.08` by Ross-Tech LLC, confirming it is an OEM-branded build of FTDI D2XX. (`PROVEN`)
  - Ross-Tech VCDS uses FTDI direct Bulk IN / Bulk OUT transfer mechanisms, bypassing Windows COM port emulation (`ftser2k.sys`). (`PROVEN`)
- **What it Does NOT Prove**:
  - The internal byte framing of the diagnostic packets sent over `FT_Write` and received over `FT_Read`.
- **Confidence**: `PROVEN`
- **Relevant Functions**:
  - `FT_SetBaudRate`, `FT_SetLatencyTimer`, `FT_Write`, `FT_Read`, `FT_SetDtr`

---

### [SRC-03] FTDI FT232R Device & Silicon Specification
- **URL**: `https://ftdichip.com/wp-content/uploads/2020/08/DS_FT232R.pdf`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - `bcdDevice = 0x0600` is the factory silicon revision code for FT232RL (SSOP-28) and FT232RQ (QFN-32). (`PROVEN`)
  - FT232R includes internal EEPROM, internal clock oscillator (12/24/48 MHz), and USB termination resistors. (`PROVEN`)
  - FTDI Bulk transfer endpoints are 64 bytes max packet size (Full Speed USB 2.0, 12 Mbps). (`PROVEN`)
  - FTDI Bulk IN transfers prepend a 2-byte modem/line status header (`[Status0, Status1]`) to every USB packet received by the host. (`PROVEN`)
- **What it Does NOT Prove**:
  - Any details of the downstream target device wired to TXD/RXD.
- **Confidence**: `PROVEN`

---

### [SRC-04] Hardware Teardown: `pabloaul/vag157-adapter`
- **URL**: `https://github.com/pabloaul/vag157-adapter`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - Independent hardware analysis of a VAG/VCDS clone with `VID_0403&PID_FA24`.
  - Chipset on PCB:
    1. **FTDI FT232RL**: USB-to-UART bridge.
    2. **Microchip/Atmel ATmega162**: Primary 8-bit AVR microcontroller running VCDS adapter firmware.
    3. **Microchip MCP2515**: Standalone SPI CAN controller.
    4. **NXP/Philips TJA1050 / MCP2551**: High-Speed CAN transceiver connected to MCP2515.
    5. **Atmel ATF16V8B / GAL16V8**: Programmable logic device (PLD) routing K/L-line switching and hardware lock signals.
    6. **STC12C2052AD**: Secondary 8051-core microcontroller acting as an anti-brick/firmware-protection watchdog. (`STRONGLY SUPPORTED`)
  - Signal chain: `Host USB` ↔ `FT232RL` ↔ `UART (USART0)` ↔ `ATmega162` ↔ `SPI` ↔ `MCP2515` ↔ `TJA1050` ↔ `OBD-II CAN (Pins 6/14)`.
- **What it Does NOT Prove**:
  - Does not prove our user's specific cable PCB is revision B03-V2 without physical inspection or protocol probe.
- **Confidence**: `STRONGLY SUPPORTED`

---

### [SRC-05] B03-V2 Aftermarket Diagnostic Hardware Teardown Reports
- **URL / Sources**: MHHAuto, LesAmisDuDiag, Allegro, Prom.ua technical listings
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - Cables sold under the designation "B03-V2" or "B03-V2 PLCC" feature:
    - ATmega162 in PLCC-44 socket or TQFP-44 footprint.
    - FTDI FT232RQ or FT232RL with `VID_0403` / `PID_FA24`.
    - ATF16V8B or GAL16V8 PLD.
    - MCP2515 CAN controller + TJA1050 transceiver.
    - L9637D or SI9241A ISO 9141 / K-Line transceiver.
  - Reset line of ATmega162 is hypothesized in community teardowns to be tied to FT232R DTR# (active-low pin). Setting `DTR=true` asserts DTR# LOW (holding RESET# low); setting `DTR=false` releases DTR# HIGH (allowing the MCU to run). However, this specific wiring remains `INFERRED` until confirmed on this user's cable PCB. (`INFERRED`)
- **What it Does NOT Prove**:
  - Exact baud rate used between FT232R and ATmega162 (clones vary between 57600, 115200, 250000, and 500000 baud). Must be extracted from `FTDI_SIO_SET_BAUDRATE` in real capture.
- **Confidence**: `STRONGLY SUPPORTED` for chip list; `INFERRED` for reset pinout; `UNKNOWN` for baud.

---

### [SRC-06] Ross-Tech Public Technical FAQ on USB Direct Drivers
- **URL**: `https://www.ross-tech.com/vag-com/usb/virtual-com-port.php`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - Official Ross-Tech documentation explicitly states:
    > "Our USB interfaces present additional challenges... we found a number of technical advantages to using a 'direct' USB driver which bypasses the Windows Serial drivers entirely. Hence the USB drivers that ship with VCDS do not emulate a serial COM port and cannot be used with applications that expect to communicate via a serial port."
  - Ross-Tech USB interfaces do NOT present a dumb transparent K-Line interface by default; they require an intelligent packet exchange with the internal coprocessor. (`PROVEN`)
- **What it Does NOT Prove**:
  - The proprietary packet opcode definitions used by the firmware.
- **Confidence**: `PROVEN`

---

### [SRC-07] Open-Source VAG K-Line & KWP Implementations
- **URL**: `https://github.com/vwradio/kwp1281_tool`, `https://github.com/domnulvlad/KLineKWP1281Lib`, `https://github.com/muki01/OBD2_KLine_Library`
- **Date Accessed**: 2026-09-22
- **What it Proves**:
  - Pure K-Line (KKL) adapters pass bytes directly to the L9637D/SI9241 transceiver without an intermediary MCU.
  - KWP1281 uses 5-baud address initialization (`0x01` for engine), followed by 7O1 sync byte (`0x55`), key bytes (`0x01`, `0x8A`), and block sequence numbering.
  - KWP2000 uses either 5-baud init or fast pulse (25ms low, 25ms high), followed by StartCommunication (`0x81 0x01 0xF1 0x81 0xF4`).
  - Corroborates that intelligent interfaces (HEX-USB+CAN) wrap these raw line events into higher-level MCU commands rather than bit-banging from the host. (`STRONGLY SUPPORTED`)
- **Confidence**: `PROVEN` for protocol; `STRONGLY SUPPORTED` for adapter distinction.

---

## 2. Summary Evidence Ledger

| Item | Status | Evidence Source | Technical Meaning |
|:---|:---:|:---|:---|
| **USB VID 0x0403** | `PROVEN` | Windows SetupAPI log | FTDI chip present |
| **USB PID 0xFA24** | `PROVEN` | Windows SetupAPI log | Ross-Tech Direct USB Interface VID/PID burned into EEPROM |
| **Silicon Rev 0x0600** | `PROVEN` | Windows SetupAPI log | Specifically FT232R generation (FT232RL or FT232RQ) |
| **Serial RT000001** | `PROVEN` | Windows SetupAPI log | Standard clone serial string in FTDI EEPROM |
| **Driver RT-USB64.SYS** | `PROVEN` | `C:\Ross-Tech\VCDS\RT-USB64.inf` | Rebranded FTDI D2XX kernel driver |
| **Library RT-USB.DLL** | `PROVEN` | PE exports in `C:\Ross-Tech\VCDS\RT-USB.DLL` | 100% FTDI D2XX v2.10.00 API surface |
| **Coprocessor ATmega162** | `STRONGLY SUPPORTED` | `pabloaul/vag157-adapter`, B03-V2 teardown | Typical MCU on FA24 boards, but unverified on user's PCB |
| **CAN Controller MCP2515** | `STRONGLY SUPPORTED` | Hardware teardowns | SPI CAN controller used across VCDS clone hardware |
| **Logic ATF16V8B / GAL16V8**| `INFERRED` | Hardware teardowns | Logic device for K/L line multiplexing |
| **PC↔MCU Command Protocol**| `UNKNOWN` | Requires differential USB capture | Zero guessing allowed in code |
| **MCU Baud Rate** | `UNKNOWN` | Clones vary (57.6k to 500k) | Must be probed safely or extracted from capture |
