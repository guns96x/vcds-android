# Implementation Specification v2: Ross-Tech HEX Transport Protocol

> **CRITICAL COMPLIANCE NOTICE:**
> This specification has been strictly audited against x64 disassembled assembly and decompiled C AST of `VCDS.EXE` (version `26.3.0.0`, SHA256 `CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3`) and `RTUS64.dll` (SHA256 `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2`).
>
> **Every item in the main specification is strictly marked `PROVEN_STATIC`.**
> Any unverified or conflicting items are quarantined in the Appendix and MUST NOT be used as production instructions for Android transport implementation until proven.

---

## 1. Physical & D2XX Transport Layer

### 1.1 Device Enumeration and Filtering
- **STATIC ADDRESS:** `0x140111F40` (`FUN_140111f40`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **MECHANISM:**
  1. Call `FT_CreateDeviceInfoList` (`0x14018C8C0`) to get device count.
  2. Allocate `device_count * 0x68` bytes (104 bytes per node).
  3. Call `FT_GetDeviceInfoList` (`0x14018C8D0`).
  4. Inspect each `FT_DEVICE_LIST_INFO_NODE` at offset `+8` (`ID` field):
     - Check: `(ID & 0xFFFF0000) == 0x04030000` (FTDI Vendor ID `0x0403`).
     - Check: `((ID & 0x0000FFFF) - 0xFA20) < 0x10` (PID in range `0xFA20` to `0xFA2F`).
  5. If exactly one matching Ross-Tech device is present, open index via `FT_Open` (`0x14018C8A8`).

### 1.2 Channel Configuration Parameters
- **STATIC ADDRESS:** `0x140111F40`, `0x140112160`
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **INITIALIZATION SEQUENCE:**
  1. `FT_ResetDevice(ftHandle)` (`0x14018C810`).
  2. `FT_SetLatencyTimer(ftHandle, 1)` (`0x14018C878`) — Configures FTDI USB packet flush latency to 1 ms.
  3. `FT_SetTimeouts(ftHandle, 1, 100)` (`0x14018C848`) — Read timeout: 1 ms, Write timeout: 100 ms.
  4. `FT_SetDataCharacteristics(ftHandle, 8, 0, 0)` (`0x14018C820`) — 8 data bits, 1 stop bit, no parity (8N1).
  5. `FT_SetBaudRate(ftHandle, 9600)` (`0x14018C8B8`) — Initial UART baud rate is 9600 bps.
  6. `FT_Purge(ftHandle, 3)` (`0x14018C840`) — Purges both RX FIFO (1) and TX FIFO (2).

---

## 2. Wire Packet Framing & Integrity

### 2.1 Host-to-Adapter Frame Builder (Requests)
- **FUNCTION:** `vcds_adapter_send_frame` (`FUN_14007e734`)
- **STATIC ADDRESS:** `0x14007E734` (Slot `0x108` in vtable `0x1401AD3C0`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** FTDI device open and initialized. Payload length $N \le 80$ (`0x50`).
- **WIRE FORMAT:**
  ```
  [ Byte 0 ] [ Byte 1 ] [ Byte 2 ] [ Bytes 3 .. N+1 ] [ Byte N+2 ]
    0x53       L = N+3     Opcode    Command Payload     XOR Checksum
  ```
- **LENGTH SEMANTICS:** Byte 1 ($L$) is the **total physical byte count of the entire wire frame** ($L = N_{\text{payload}} + 3$).
- **CHECKSUM FORMULA:**
  $$\text{Checksum} = 0x53 \oplus L \oplus \bigoplus_{i=0}^{N-1} \text{Payload}[i]$$
- **ERROR CONDITIONS:** If payload length $> 80$, returns `-1` (`0xFFFFFFFF`).

### 2.2 Adapter-to-Host Frame Parser (Responses)
- **FUNCTION:** `vcds_adapter_read_frame` (`FUN_14007e824`)
- **STATIC ADDRESS:** `0x14007E824` (Slot `0x110` in vtable `0x1401AD3C0`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **WIRE FORMAT:**
  ```
  [ Byte 0 ] [ Byte 1 ] [ Byte 2 ] [ Bytes 3 .. L-2 ] [ Byte L-1 ]
    0x4D       L (Total)   Opcode    Response Payload    XOR Checksum
  ```
- **LENGTH SEMANTICS:** Byte 1 ($L$) is the total physical byte count of the response frame ($3 \le L \le 48$).
- **VALIDATION & ERROR CODES:**
  - `Byte 0 != 0x4D ('M')` $\to$ Returns `-1` (`0xFFFFFFFF`).
  - `L < 3` $\to$ Returns `-2` (`0xFFFFFFFE`).
  - `L >= 0x31 (49 decimal)` $\to$ Returns `-3` (`0xFFFFFFFD`).
  - RX timeout while reading remaining $L - 2$ bytes $\to$ Returns `-4` (`0xFFFFFFFC`).
  - Cumulative XOR sum over all $L$ bytes != `0x00` $\to$ Returns `-5` (`0xFFFFFFFB`).
  - Success $\to$ Copies $L - 3$ payload bytes (from index 2 to $L-2$) to destination and returns `0`.

---

## 3. Proven Adapter Protocol Operations

### 3.1 Operation: `HC_GET_VERSION`
- **FUNCTION:** `FUN_14007e988`
- **STATIC ADDRESS:** `0x14007E988`
- **DYNAMIC TRACE:** N/A (Static proof)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** Channel initialized at 9600 baud.
- **INPUT:** None.
- **OUTPUT:** Firmware major/minor version (e.g. `1.96`), hardware model character (`'D'` for Dual-K+CAN).
- **WIRE INPUT (TX):** `53 04 02 55`
  - Sync: `0x53`
  - Length: `0x04`
  - Opcode: `0x02`
  - Checksum: `0x55` ($0x53 \oplus 0x04 \oplus 0x02$)
- **WIRE OUTPUT (RX):** `4D 07 02 <Major> <Minor> <ModelChar> <Checksum>`
  - Example: `4D 07 02 01 60 44 6D` (v1.96, model 'D', Checksum `0x6D`).
- **TIMEOUT:** 750 ms (`0x2EE`).
- **RETRY:** 1 retry if interface index == 8.
- **STATE TRANSITION:** Validates connected hardware capability.
- **ERROR CONDITIONS:** Returns `-1` on timeout, `-2` if response opcode != `0x02`.

---

### 3.2 Operation: `HC_SET_COM_BAUD` (115,200 Handshake)
- **FUNCTION:** `FUN_14007ec2c`
- **STATIC ADDRESS:** `0x14007EC2C`
- **DYNAMIC TRACE:** N/A (Static proof)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** Adapter version queried; currently communicating at 9600 baud.
- **INPUT:** Target baud rate: 115200 bps (`0x0001C200` LE: `00 C2 01 00`).
- **OUTPUT:** Successful baud rate transition to 115200 on both host and adapter.
- **WIRE TRANSACTIONS (4-WAY HANDSHAKE):**
  1. **Phase 1 (Host $\to$ Adapter):**
     - Wire TX: `53 08 03 00 C2 01 00 9B`
     - Description: Opcode `0x03`, 4-byte LE baud rate `115200`.
  2. **Phase 2 (Adapter $\to$ Host):**
     - Wire RX: `4D 04 FE B7`
     - Description: Opcode `0xFE` (Adapter ACK acknowledging baud switch request).
     - Timeout: 750 ms (`0x2EE`).
  3. **Phase 3 (Local Host Action):**
     - Host calls `FT_SetBaudRate(ftHandle, 115200)` (`FUN_140111ed0`).
  4. **Phase 4 (Adapter $\to$ Host):**
     - Wire RX: `4D 04 FD B4`
     - Description: Opcode `0xFD` (Adapter Ready confirming its UART is running at 115200).
     - Timeout: 750 ms (`0x2EE`).
  5. **Phase 5 (Host $\to$ Adapter Confirm):**
     - Wire TX: `53 04 FE A9`
     - Description: Opcode `0xFE` (Host confirmation ACK).
- **TIMEOUT:** 750 ms per phase.
- **RETRY:** None.
- **STATE TRANSITION:** Host and adapter UART baud rates switched from 9600 to 115200.
- **ERROR CONDITIONS:**
  - Phase 2 timeout $\to$ returns `-1`.
  - Phase 2 response != `0xFE` $\to$ returns `-2`.
  - Phase 4 timeout $\to$ returns `-3`.
  - Phase 4 response != `0xFD` $\to$ returns `-4`.

---

### 3.3 Operation: `HC_KLINE_TEST`
- **FUNCTION:** `FUN_14007ed84`
- **STATIC ADDRESS:** `0x14007ED84`
- **DYNAMIC TRACE:** N/A (Static proof)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** Adapter in communication mode.
- **INPUT:** None.
- **OUTPUT:** Physical K-Line electrical line status (`0x00` = Line OK / no short).
- **WIRE INPUT (TX):** `53 04 82 D5`
  - Opcode: `0x82`
- **WIRE OUTPUT (RX):** `4D 05 82 00 D4`
  - Status byte `0x00` indicates line is operational.
- **TIMEOUT:** 750 ms (`0x2EE`).
- **RETRY:** None.
- **STATE TRANSITION:** Hardware self-test.
- **ERROR CONDITIONS:** Returns `-1` on timeout.

---

### 3.4 Operation: `HC_RESET`
- **FUNCTION:** `FUN_14007f758`
- **STATIC ADDRESS:** `0x14007F758`
- **DYNAMIC TRACE:** N/A (Static proof)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** Adapter connected.
- **INPUT:** None.
- **OUTPUT:** Hardware state reset to defaults.
- **WIRE INPUT (TX):** `53 04 08 5F`
  - Opcode: `0x08`
- **WIRE OUTPUT (RX):** `4D 04 FE B7`
  - Opcode `0xFE` (Execution ACK).
- **TIMEOUT:** 750 ms (`0x2EE`).
- **RETRY:** None.
- **STATE TRANSITION:** Clears session state and resets adapter internal microcontroller logic.
- **ERROR CONDITIONS:** Returns `-1` on timeout, `-2` if response != `0xFE`.

---

### 3.5 Operation: `HC_INIT_5BAUD` (ECU 01-Engine Session Wake-Up)
- **FUNCTION:** `FUN_14007e3b4`
- **STATIC ADDRESS:** `0x14007E3B4`
- **DYNAMIC TRACE:** N/A (Static proof)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **PRECONDITIONS:** Adapter communication established at 115200 baud.
- **INPUT:** ECU target address (`0x01` for 01-Engine).
- **PARITY ENCODING ALGORITHM (Lines 20–33 of `FUN_14007e3b4`):**
  - Schema: **ODD PARITY** with parity bit in bit 7 (`0x80`).
  - Bit 7 is set **only if the initial count of 1-bits is EVEN**.
  - Address `0x01` (`0000 0001`b) has 1 set bit (already ODD) $\to$ Bit 7 is NOT set $\to$ **Encoded byte is `0x01`**.
  - Address `0x33` is exempt (`addr != 0x33`).
- **OUTPUT:** Detected ECU communication baud rate, KeyBytes `KB1`, `KB2`, sync confirmation `0x55`.
- **WIRE INPUT (TX):** `53 07 84 03 01 00 D2`
  - Sync: `0x53`
  - Length: `0x07`
  - Opcode: `0x84`
  - Sub-function: `0x03` (K-Line 5-baud init)
  - Target Address: `0x01` (Parity-encoded address for ECU 01)
  - Options/Flags: `0x00`
  - Checksum: `0xD2` ($0x53 \oplus 0x07 \oplus 0x84 \oplus 0x03 \oplus 0x01 \oplus 0x00$)
- **WIRE OUTPUT (RX):** `4D 09 84 <BaudHi> <BaudLo> <KB1> <KB2> 55 <Checksum>`
  - Sync: `0x4D`
  - Length: `0x09` (9 bytes total)
  - Opcode: `0x84`
  - BaudRate (16-bit BE): e.g. `0x28A0` (10400 bps)
  - `KB1`: KeyByte 1 (e.g. `0x08`)
  - `KB2`: KeyByte 2 (e.g. `0x89` for KWP2000, `0x01` for KWP1281)
  - Sync Byte: `0x55`
  - Checksum: Cumulative XOR of all 9 bytes == `0x00`.
- **TIMEOUT:** **3300 ms** (`0xCE4`).
- **RETRY:** None.
- **STATE TRANSITION:** Adapter handles line timing autonomously and transitions physical K-line into active diagnostic session.
- **ERROR CONDITIONS:**
  - Timeout $\to$ returns `-1` (`0xFFFFFFFF`).
  - Response opcode != `0x84` $\to$ returns error.
  - Invalid sync byte != `0x55` $\to$ init rejected.

---

## Appendix: Non-Proven / Unknown / Conflict Items

> [!WARNING]
> The following items are currently **UNPROVEN** or in **CONFLICT**. They MUST NOT be implemented in production Android transport code until evidenced.

### Item A: Group 011 Wire Frame & KWP Encapsulation
- **EVIDENCE STATUS:** `UNKNOWN`
- **DESCRIPTION:** The exact wire packet bridging the high-level measuring blocks dialog (`BlockDlg` @ `0x14005911c`) to `vcds_adapter_send_frame` (`0x14007E734`) is not proven in decompiled AST. KWP Service `0x21` (`ReadDataByLocalIdentifier`) is an ISO 14230 constant and is NOT proven to be a raw Ross-Tech adapter opcode.
- **REQUIRED FOR RESOLUTION:** Dynamic bus trace (RTUS64 logging shim) of a live Group 011 query.

### Item B: Keepalive Opcode
- **EVIDENCE STATUS:** `UNKNOWN`
- **DESCRIPTION:** No call site constructing an adapter packet with opcode `0x3E` has been identified in `VCDS.EXE`. ISO 14230 `TesterPresent` (`0x3E`) is a diagnostic service, not proven as an adapter-level opcode.
- **REQUIRED FOR RESOLUTION:** Dynamic bus trace of an idle session.

### Item C: Formula Decoder Association
- **EVIDENCE STATUS:** `CONFLICT`
- **DESCRIPTION:** Initial draft claim that `FUN_14011FEB8` decodes Group 011 formulas was false. Decompilation proves `FUN_14011feb8` is an adapter security challenge validator. Formulas for Group 011 ($0.2 \times A \times B$ RPM, $0.04 \times A \times B$ mbar) are standard VAG formula decoders, but their internal VCDS dispatch function remains unmapped.
