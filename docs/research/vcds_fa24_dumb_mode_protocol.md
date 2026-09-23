# Ross-Tech HEX-USB+CAN (0403:FA24) Legacy Dumb Mode Reverse Engineering & Implementation Spec

## 1. Executive Summary

This document specifies the exact protocol mechanism used by Ross-Tech HEX interfaces (`VID 0403 / PID FA24`) to transition from **Intelligent (Smart) Mode** into **Legacy Dumb K-Line Mode**, discovered via binary reverse engineering of `VCDS 26.3 x64` (`VCDS_unpacked.exe`).

In **Smart Mode** (`0x02`), the on-board microcontroller (ATmega162) intercepts all UART communication over the FTDI FT232R bridge and requires proprietary `0x53` ('S') framed packets. Raw UART bytes (such as KWP 5-baud address bytes or direct 10400-baud ISO 9141/14230 frames) are dropped or intercepted by the MCU.

In **Legacy Dumb Mode** (`0x00`), the MCU reconfigures its internal multiplexer / transceiver routing to function as a transparent level-shifter (KKL pass-through mode). The host computer communicates directly with the vehicle ECU over OBD pin 7 (K-Line) at arbitrary baud rates (standard 10400 baud, 5-baud break pulses, etc.).

---

## 2. Binary Reverse Engineering Evidence (VCDS 26.3 x64)

### 2.1 String Artifacts in `.rdata`
The following diagnostic error and function trace strings were located in the binary:
- `0x1401AD238`: `"HC::SetBoot -1"`
- `0x1401AD248`: `"HC::SetBoot -2"`
- `0x1401AD258`: `"HC::ReadBoot -1"`
- `0x1401AD268`: `"HC::ReadBoot -2"`
- `0x1401AD200`: `"HC::Echo10400 -1"`
- `0x1401AD218`: `"HC::Echo10400 -2"`
- `0x1401ACE18`: `"HC::KLineTest -1"`

### 2.2 Virtual Method Table (`HC` class at `0x1401AD3C0`)
The `HC` (Hardware Controller) class defines the low-level adapter transport interface:
- `vtable[0x60]: 0x14007E630` (K-Line Break and Echo Init, Opcode `0x85`)
- `vtable[0x90]: 0x1400821EC` (`HC::Echo10400`, Opcode `0x9A`)
- `vtable[0x108]: 0x14007E734` (`vcds_adapter_send_frame`)
- `vtable[0x110]: 0x14007E824` (`vcds_adapter_read_frame`)

### 2.3 `HC::SetBoot` Implementation (`0x140083208`)
The function sets the boot / operating mode of the cable:
```x86asm
0x140083208: push rbx
0x14008320a: sub rsp, 0x120
0x140083211: mov rax, qword ptr [rcx]       ; load vtable
0x140083214: mov byte ptr [rsp + 0x22], dl  ; dl = mode parameter (0x00 = dumb, 0x02 = smart)
0x140083218: lea rdx, [rsp + 0x20]
0x14008321d: mov rbx, rcx
0x140083220: mov byte ptr [rsp + 0x20], 2    ; payload length = 2 bytes
0x140083225: mov byte ptr [rsp + 0x21], 0x0E ; opcode = 0x0E (SetBoot)
0x14008322a: call qword ptr [rax + 0x108]    ; call send_frame(0x0E, mode)
...
0x140083245: call qword ptr [rax + 0x110]    ; call read_frame()
0x14008324b: test eax, eax
0x14008324d: jns 0x140083273
...
0x140083273: cmp byte ptr [rsp + 0x20], 0xFE ; verify ACK response (0xFE)
0x140083278: je 0x1400832a0
0x14008327a: lea rdx, "HC::SetBoot -2"       ; error on missing ACK
...
0x1400832a0: mov rcx, rbx
0x1400832a3: call 0x1400832B4                ; call HC::ReadBoot to verify transition!
0x1400832a8: xor eax, eax                    ; return 0 (success)
0x1400832aa: add rsp, 0x120
0x1400832b1: pop rbx
0x1400832b2: ret
```

### 2.4 `HC::ReadBoot` Implementation (`0x1400832B4`)
The function reads the current boot / operating mode:
```x86asm
0x1400832B4: push rbx
0x1400832B6: sub rsp, 0x120
0x1400832BD: mov rax, qword ptr [rcx]
0x1400832C0: lea rdx, [rsp + 0x20]
0x1400832C5: mov rbx, rcx
0x1400832C8: mov byte ptr [rsp + 0x20], 1    ; payload length = 1 byte
0x1400832CD: mov byte ptr [rsp + 0x21], 0x0D ; opcode = 0x0D (ReadBoot)
0x1400832D2: call qword ptr [rax + 0x108]    ; send_frame(0x0D)
...
0x1400832F2: call qword ptr [rax + 0x110]    ; read_frame()
...
0x14008331A: cmp byte ptr [rsp + 0x20], 0x0D ; verify reply opcode == 0x0D
0x14008331F: je 0x140083344
...
0x140083344: movzx eax, byte ptr [rsp + 0x21] ; return mode payload byte (e.g. 0x00 or 0x02)
0x140083349: add rsp, 0x120
0x140083350: pop rbx
0x140083351: ret
```

### 2.5 Caller Decision Logic (`0x14007D4F0` - `0x14007D530`)
This function evaluates the adapter state during the Port Test / Options dialog:
```x86asm
0x14007D4F2: mov rcx, rsi
0x14007D4F5: call 0x14007ED84                ; HC::KLineTest (Opcode 0x82)
0x14007D4FA: mov rcx, rsi
0x14007D4FD: call 0x1400832B4                ; HC::ReadBoot (Opcode 0x0D)
0x14007D502: lea edx, [r13 + 2]              ; edx = 2 (Smart Mode)
0x14007D506: cmp eax, edx                    ; is ReadBoot result == 2?
0x14007D508: jne 0x14007D51F
0x14007D50A: cmp dword ptr [rip + 0x17605B], r13d ; check "Boot in Intelligent Mode" setting
0x14007D511: jne 0x14007D538
0x14007D513: xor edx, edx                    ; dl = 0 (Mode 0: Legacy Dumb Mode!)
0x14007D515: mov rcx, rsi
0x14007D518: call 0x140083208                ; HC::SetBoot(this, 0)
0x14007D51D: jmp 0x14007D538
0x14007D51F: cmp dword ptr [rip + 0x176046], r13d ; check if Smart Mode requested
0x14007D526: je 0x14007D538
0x14007D528: mov rcx, rsi
0x14007D52B: call 0x140083208                ; HC::SetBoot(this, 2)
```

### 2.6 Direct K-Line Baud Switch (`0x1400C28B0`)
When Dumb Mode is active (`[rdx + 0x4C] == 0`), VCDS skips intelligent echo frames and configures the FTDI baud rate directly to **10400 baud** (`0x28A0`):
```x86asm
0x1400C28B0: cmp byte ptr [rdx + 0x4C], 0     ; is adapter in Dumb Mode?
0x1400C28B4: jne 0x1400C28C8                  ; if not, use HC::Echo10400 (opcode 0x9A)
0x1400C28B6: mov edx, 0x28A0                  ; edx = 10400 baud!
0x1400C28BB: mov ecx, dword ptr [rip + 0x4E2D23]
0x1400C28C1: call 0x1400A14B8                ; set_baud_rate(10400)
0x1400C28C6: jmp 0x1400C28FC
```

---

## 3. Wire Protocol Specification

### 3.1 Packet Framing
Format: `[Marker][Length][Opcode][Payload...][XOR_Checksum]`
- **Marker**: `0x53` ('S') Host to Cable; `0x4D` ('M') Cable to Host.
- **Length**: Total frame length in bytes (`4 + payload.length`).
- **Opcode**: Command / response identifier.
- **XOR Checksum**: XOR sum of all preceding frame bytes (`0` to `Length - 2`).

### 3.2 Command: `HC::SetBoot` (Opcode `0x0E`)
- **Request (Switch to Dumb Mode)**:
  `[0x53, 0x05, 0x0E, 0x00, 0x58]`
  - Checksum calculation: `0x53 ^ 0x05 ^ 0x0E ^ 0x00 = 0x58`
- **Request (Switch to Smart Mode)**:
  `[0x53, 0x05, 0x0E, 0x02, 0x5A]`
  - Checksum calculation: `0x53 ^ 0x05 ^ 0x0E ^ 0x02 = 0x5A`
- **Expected Cable Response (ACK)**:
  `[0x4D, 0x04, 0xFE, 0xB7]`
  - Opcode: `0xFE` (ACK)
  - Checksum calculation: `0x4D ^ 0x04 ^ 0xFE = 0xB7`

### 3.3 Query: `HC::ReadBoot` (Opcode `0x0D`)
- **Request**:
  `[0x53, 0x04, 0x0D, 0x5A]`
  - Checksum calculation: `0x53 ^ 0x04 ^ 0x0D = 0x5A`
- **Response (Dumb Mode Active)**:
  `[0x4D, 0x05, 0x0D, 0x00, 0x45]`
  - Payload byte: `0x00`
- **Response (Smart Mode Active)**:
  `[0x4D, 0x05, 0x0D, 0x02, 0x47]`
  - Payload byte: `0x02`

---

## 4. Android Implementation Summary

1. **`HexB03Packet.kt`**:
   - Added constants: `OPCODE_SET_BOOT (0x0E)`, `OPCODE_READ_BOOT (0x0D)`, `OPCODE_ACK (0xFE)`, `BOOT_MODE_LEGACY_DUMB (0x00)`, `BOOT_MODE_SMART (0x02)`.
   - Added command objects: `CandidateB03Command.SetBootDumb`, `CandidateB03Command.SetBootSmart`, `CandidateB03Command.Echo10400`.

2. **`HexB03Adapter.kt`**:
   - Added `readBootMode(timeoutMs): Result<Byte>`.
   - Added `setLegacyDumbMode(timeoutMs): Result<Boolean>`: Automated sequence verifying current mode, transmitting `0x0E 0x00`, checking `0xFE` ACK, and confirming `0x00` transition.
   - Added `setIntelligentMode(timeoutMs): Result<Boolean>` to restore smart mode if needed.

3. **`UsbKwpTransport.kt`**:
   - Updated `connectDumbRossTech()`: Automatically checks if adapter is in Smart mode (`0x02`), issues `0x0E 0x00` (`HC::SetBoot(0)`), awaits ACK, and transitions port to 10400 baud.

4. **Unit Tests**:
   - `HexB03PacketTest.kt`: Validates wire-level framing for `SetBootDumb` (`53 05 0E 00 58`), `SetBootSmart` (`53 05 0E 02 5A`), and `Echo10400`.
   - `HexB03AdapterTest.kt`: Validates state machine transitions, ACK matching, no-op when already dumb, timeout handling, and smart mode restoration.
