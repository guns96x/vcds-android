# Independent Audit of VCDS 26.3 Transport Layer Claims

This document provides a line-by-line, fail-closed verification of all claims made in the initial reverse-engineering draft. Every claim is strictly assigned one of the allowed statuses: `PROVEN_STATIC`, `PROVEN_DYNAMIC`, `PROVEN_BOTH`, `INFERRED`, `CONFLICT`, or `UNKNOWN`.

---

### CLAIM 1: Target Executable Hashes & Version
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** PE Headers
- **FUNCTION:** N/A (Image Filesystem & Headers)
- **DECOMPILER SNIPPET:** N/A
- **ASSEMBLY/P-CODE CHECK:** ProductVersion string resource = `26.3.0.0`
- **CALLER:** N/A
- **CALLEE:** N/A
- **DATA FLOW:** Computed SHA256 of `C:\Ross-Tech\VCDS\VCDS.EXE` and `RTUS64.dll`.
- **RUNTIME TRACE:** Verified with PowerShell `Get-FileHash -Algorithm SHA256`.
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed 100%. `VCDS.EXE` = `CC7F81CC08222A14A6317ABF5EBDF059E5A8853EA885524C562E0602B19733E3`, `RTUS64.dll` = `B2A261C16355BC3C1313F5A2F86591AC430EC5DDC7D1DDF24B517A5FB97B48F2`.

---

### CLAIM 2: D2XX Hardware Binding Pointer Table
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14018C810 - 0x14018C8E0` (Section 2 `.rdata`)
- **FUNCTION:** `FUN_140111f40`, `FUN_140112160`, `FUN_1401117bc`, `FUN_14011185c`
- **DECOMPILER SNIPPET:**
  ```c
  DAT_140630f90 = (*DAT_14018c860)(DAT_140630f48, param_1, param_2, local_res18); // FT_Write
  DAT_140630f90 = (*DAT_14018c890)(DAT_140630f48, local_res8); // FT_GetQueueStatus
  iVar1 = (*DAT_14018c858)(DAT_140630f48, local_18, 1, local_res8); // FT_Read
  ```
- **ASSEMBLY/P-CODE CHECK:** Contiguous array of 27 QWORD pointers pointing to `RTUS64.dll` ImageBase `0x180000000`. Cross-referenced with all 87 exported names of `RTUS64.dll`.
- **CALLER:** Direct D2XX hardware wrapper functions in `0x140111xxx`.
- **CALLEE:** `RTUS64.dll` exported entry points.
- **DATA FLOW:** Pointers dereferenced using global device handle `DAT_140630f48`.
- **RUNTIME TRACE:** N/A (Static table in binary image).
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** All 27 table entries mapped and proven against `RTUS64.dll` exports.

---

### CLAIM 3: FT_Write Pointer Location
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14018C860`
- **FUNCTION:** `FUN_140111828` (single byte), `FUN_14011185c` (buffer)
- **DECOMPILER SNIPPET:**
  ```c
  int FUN_14011185c(undefined8 param_1, undefined4 param_2) {
      undefined1 local_res18 [16];
      DAT_140630f90 = (*DAT_14018c860)(DAT_140630f48, param_1, param_2, local_res18);
      return -(uint)(DAT_140630f90 != 0);
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Target VA `0x0000000180002C10` corresponds to `RTUS64.dll!FT_Write` (Export Ordinal 74, RVA `0x02C10`).
- **CALLER:** `FUN_140111e00` (buffer write), `FUN_140111e4c` (byte write), invoked by `FUN_14007e734` (`vcds_adapter_send_frame`).
- **CALLEE:** `RTUS64.dll!FT_Write`.
- **DATA FLOW:** Passes frame buffer and byte length to FTDI driver.
- **RUNTIME TRACE:** N/A (Static binary proof).
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed `0x14018C860` is strictly `FT_Write`.

---

### CLAIM 4: FT_Read Pointer Location
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14018C858`
- **FUNCTION:** `FUN_1401117bc`
- **DECOMPILER SNIPPET:**
  ```c
  ulonglong FUN_1401117bc(void) {
      int local_res8 [8];
      byte local_18 [24];
      DAT_140630f90 = (*DAT_14018c890)(DAT_140630f48, local_res8); // FT_GetQueueStatus
      if (((DAT_140630f90 != 0) || (local_res8[0] != 0)) &&
          ((*DAT_14018c858)(DAT_140630f48, local_18, 1, local_res8) == 0)) {
          return (ulonglong)local_18[0];
      }
      return 0xffffff9c;
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Target VA `0x0000000180002BB0` corresponds to `RTUS64.dll!FT_Read` (Export Ordinal 75, RVA `0x02BB0`).
- **CALLER:** `FUN_140111e70` -> `FUN_1400a11a8` -> `FUN_14007e824` (`vcds_adapter_read_frame`).
- **CALLEE:** `RTUS64.dll!FT_Read`.
- **DATA FLOW:** Reads single bytes from FTDI hardware RX buffer after queue status check.
- **RUNTIME TRACE:** N/A (Static binary proof).
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed `0x14018C858` is strictly `FT_Read`.

---

### CLAIM 5: VID/PID Detection Field in FT_DEVICE_LIST_INFO_NODE
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x140111F40` (`FUN_140111f40`, lines 24-32)
- **FUNCTION:** `FUN_140111f40`
- **DECOMPILER SNIPPET:**
  ```c
  _Memory = malloc((ulonglong)local_res8[0] * 0x68);
  DAT_140630f90 = (*DAT_14018c8d0)(); // FT_GetDeviceInfoList
  puVar6 = (uint *)((longlong)_Memory + 8);
  do {
      if (((*puVar6 & 0xffff0000) == 0x4030000) && ((*puVar6 & 0xffff) - 0xfa20 < 0x10)) {
          iVar2 = iVar2 + 1;
          iVar5 = iVar3;
      }
      iVar3 = iVar3 + 1;
      puVar6 = puVar6 + 0x1a; // 0x1a * 4 = 104 = 0x68 bytes
  } while (iVar3 < (int)local_res8[0]);
  ```
- **ASSEMBLY/P-CODE CHECK:** Offset `+8` of struct size `0x68` (104 bytes) is read and masked.
- **CALLER:** `FUN_140112160` (device initialization).
- **CALLEE:** `FT_CreateDeviceInfoList` (`0x14018C8C0`), `FT_GetDeviceInfoList` (`0x14018C8D0`).
- **DATA FLOW:** Inspects device node list returned by D2XX driver.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** Previous draft stated node->Flags was checked. Corrected: checked field is `ID` at offset `+8` (`(VID << 16) | PID`).
- **FINAL VERDICT:** Confirmed VID is `0x0403`, PID range is `0xFA20..0xFA2F`. Struct field is strictly `ID`.

---

### CLAIM 6: Frame Builder (`0x14007E734`) Architecture & Layout
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E734`
- **FUNCTION:** `FUN_14007e734` (slot `0x108` in vtable `0x1401AD3C0`)
- **DECOMPILER SNIPPET:**
  ```c
  bVar1 = *param_2; // payloadLen
  if (bVar1 < 0x51) {
      abStack_108[0] = 0x53; // 'S'
      abStack_108[1] = bVar1 + 3; // total frame length
      bVar5 = bVar1 + 3 ^ 0x53; // XOR checksum init
      // payload copied into abStack_108 + 2
      // XOR accumulated into bVar5
      abStack_108[bVar1 + 2] = bVar5; // checksum appended
      uVar6 = (ulonglong)(bVar1 + 3); // total bytes sent
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Maximum payload length is strictly `0x50` (80 bytes). Total frame bytes = `bVar1 + 3`.
- **CALLER:** All protocol handlers via `*(param_1 + 0x108)`.
- **CALLEE:** `FUN_140111e00` / `FUN_140111e4c` (`FT_Write` wrappers).
- **DATA FLOW:** Raw payload array `[len, opcode, ...]` -> wire envelope `[0x53, len+3, opcode, ..., checksum]`.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** None in code. Corrected prior markdown documentation discrepancies.
- **FINAL VERDICT:** Confirmed request envelope format and checksum calculation.

---

### CLAIM 7: Frame Length Field Semantics
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E734` (builder), `0x14007E824` (parser)
- **FUNCTION:** `FUN_14007e734`, `FUN_14007e824`
- **DECOMPILER SNIPPET:**
  - Builder: `frame[1] = payloadLen + 3;`
  - Parser: `iVar2 = read_byte(); ... if (iVar2 < 0x31) ... read remaining (iVar2 - 2) bytes;`
- **ASSEMBLY/P-CODE CHECK:** Parser reads `iVar2 - 2` additional bytes. Total bytes read = `2 + (iVar2 - 2) = iVar2`.
- **CALLER:** `FUN_14007e988`, `FUN_14007ec2c`, `FUN_14007e3b4`, etc.
- **CALLEE:** FTDI transport.
- **DATA FLOW:** Direct byte count on wire.
- **RUNTIME TRACE:** Verified with `tools/audit/test_framing_and_parser.py`.
- **CONTRADICTIONS:** Previous draft had example `4D 06 02 01 60 44 checksum` (7 physical bytes with length 06). Proven error: for 4 payload bytes, length byte MUST be `0x07`.
- **FINAL VERDICT:** Length byte strictly equals total physical wire frame size ($L_{\text{wire}} = N_{\text{payload}} + 3$).

---

### CLAIM 8: Response Parser (`0x14007E824`) Architecture
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E824`
- **FUNCTION:** `FUN_14007e824` (slot `0x110` in vtable `0x1401AD3C0`)
- **DECOMPILER SNIPPET:**
  ```c
  bVar1 = read_byte();
  if (bVar1 == 0x4d) { // 'M'
      iVar2 = read_byte(); // Length
      if (iVar2 < 0x31) {
          // reads iVar2 - 2 bytes into abStack_108 + 2
          for (lVar4 = 1; lVar4 < iVar6; lVar4++) bVar1 ^= abStack_108[lVar4];
          if (bVar1 == 0) {
              memcpy(param_2, abStack_108 + 2, (ulonglong)(iVar6 - 3));
              return 0; // SUCCESS
          }
          return 0xfffffffb; // CHECKSUM ERROR (-5)
      }
      return 0xfffffffd; // LENGTH ERROR (-3)
  }
  return 0xffffffff; // HEADER ERROR (-1)
  ```
- **ASSEMBLY/P-CODE CHECK:** Complete error code mapping: `-1` (bad sync), `-2` (length < 0), `-3` (length >= 0x31), `-4` (timeout), `-5` (checksum failure).
- **CALLER:** Protocol response handlers via `*(param_1 + 0x110)`.
- **CALLEE:** Byte reader `FUN_1400a11a8`.
- **DATA FLOW:** Strips `'M'`, length byte, and checksum byte; returns pure response payload (`L - 3` bytes).
- **RUNTIME TRACE:** Verified against 7 test vectors in `test_framing_and_parser.py`.
- **CONTRADICTIONS:** None in binary.
- **FINAL VERDICT:** Confirmed 100%.

---

### CLAIM 9: 5-Baud Address Parity Calculation
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E3B4` (`FUN_14007e3b4`, lines 20-33)
- **FUNCTION:** `FUN_14007e3b4`
- **DECOMPILER SNIPPET:**
  ```c
  bVar7 = false;
  lVar6 = 8;
  bVar4 = (byte)DAT_1401f6818;
  do {
      if ((bVar4 & 1) != 0) {
          bVar7 = (bool)(bVar7 ^ 1);
      }
      bVar4 = (char)bVar4 >> 1;
      lVar6 = lVar6 + -1;
  } while (lVar6 != 0);
  uVar2 = DAT_1401f6818;
  if ((DAT_1401f6818 != 0x33) && (!bVar7)) {
      uVar2 = (uint)(byte)((byte)DAT_1401f6818 | 0x80);
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Loops 8 times over input address; accumulates bit parity in `bVar7`. `!bVar7` means initial set bit count was EVEN. If EVEN and `addr != 0x33`, bit 7 (`0x80`) is set.
- **CALLER:** Session wake-up initiator.
- **CALLEE:** `*(param_1 + 0x108)` (Opcode `0x84`).
- **DATA FLOW:** Input target address in `DAT_1401f6818` -> output encoded byte in `local_107`.
- **RUNTIME TRACE:** Verified with `tools/audit/test_parity.py`.
- **CONTRADICTIONS:** Critical error in previous draft which claimed ECU `0x01` encodes to `0x81`. Address `0x01` has 1 set bit (already ODD); bit 7 is NOT set. Encoded byte for ECU 0x01 is `0x01`.
- **FINAL VERDICT:** Schema is strictly **ODD PARITY**. Output for ECU 0x01 is `0x01`.

---

### CLAIM 10: Opcode 0x02 (`HC::GetVersion`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E988` (`FUN_14007e988`)
- **FUNCTION:** `FUN_14007e988`
- **DECOMPILER SNIPPET:**
  ```c
  local_128 = '\x01';
  local_127 = 2; // Opcode 0x02
  (**(code **)(*param_1 + 0x108))(param_1, &local_128);
  DAT_1401f6e4c = 0x2ee; // 750 ms
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1, &local_128);
  if (local_128 != '\x02') {
      uVar2 = FUN_140001790(local_res20, "HC::GetVersion -2");
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** String references `"HC::GetVersion -1"` (`0x1401ACD78`) and `"HC::GetVersion:KMode failed 1"` (`0x1401ACDB8`).
- **CALLER:** Hardware initialization sequence.
- **CALLEE:** `send_frame` (`*param_1 + 0x108`), `read_frame` (`*param_1 + 0x110`).
- **DATA FLOW:** Request: `[0x53, 0x04, 0x02, 0x55]`. Response: `[0x4D, 0x07, 0x02, Major, Minor, ModelChar, Checksum]`.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed `0x02` is `HC::GetVersion`.

---

### CLAIM 11: Opcode 0x03 (`HC::Com115`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007EC2C` (`FUN_14007ec2c`)
- **FUNCTION:** `FUN_14007ec2c`
- **DECOMPILER SNIPPET:**
  ```c
  local_108[0] = '\x05';
  local_108[1] = 3; // Opcode 0x03
  local_108[2] = 0; local_108[3] = 0xc2; local_108[4] = 1; local_108[5] = 0; // 0x0001C200 (115200)
  (**(code **)(*param_1 + 0x108))(param_1, local_108);
  // Reads response: expects 0xFE (-2)
  // Switches local baud to 115200: FUN_140111ed0(DAT_1405a55e4, 0x1c200)
  // Reads response: expects 0xFD (-3)
  // Sends confirmation: local_108[0]=1, local_108[1]=0xFE
  (**(code **)(*param_1 + 0x108))(param_1, local_108);
  ```
- **ASSEMBLY/P-CODE CHECK:** String references `"HC::Com115 -1"` (`0x1401ACDD8`), `"HC::Com115 -3"` (`0x1401ACDF8`), `"HC::Com115 -4"` (`0x1401ACE08`).
- **CALLER:** Hardware initialization sequence.
- **CALLEE:** `send_frame`, `read_frame`, `FT_SetBaudRate`.
- **DATA FLOW:** 4-phase handshake protocol.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** Corrected checksum vector from `0x97` to `0x9B`.
- **FINAL VERDICT:** Confirmed `0x03` is `HC::Com115`.

---

### CLAIM 12: Opcode 0x82 (`HC::KLineTest`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007ED84` (`FUN_14007ed84`)
- **FUNCTION:** `FUN_14007ed84`
- **DECOMPILER SNIPPET:**
  ```c
  local_118 = '\x01';
  local_117 = -0x7e; // 0x82
  (**(code **)(*param_1 + 0x108))(param_1, &local_118);
  DAT_1401f6e4c = 0x2ee;
  iVar2 = (**(code **)(*param_1 + 0x110))(param_1, &local_118);
  if (iVar2 < 0) {
      uVar5 = FUN_140001790(local_res20, "HC::KLineTest -1");
  }
  else if (local_118 == -0x7e) {
      if (local_117 == '\0') { /* K-Line OK */ }
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Opcode `0x82` sent to test physical line voltage/short.
- **CALLER:** Port test dialogue.
- **CALLEE:** `send_frame`, `read_frame`.
- **DATA FLOW:** Request: `[0x53, 0x04, 0x82, 0xD5]`. Response: `[0x4D, 0x05, 0x82, Status, Checksum]`.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed `0x82` is `HC::KLineTest`.

---

### CLAIM 13: Opcode 0x08 (`HC::Reset`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007F758` (`FUN_14007f758`)
- **FUNCTION:** `FUN_14007f758`
- **DECOMPILER SNIPPET:**
  ```c
  local_108[0] = '\x01';
  local_108[1] = 8; // Opcode 0x08
  (**(code **)(*param_1 + 0x108))(param_1, local_108);
  DAT_1401f6e4c = 0x2ee;
  iVar1 = (**(code **)(*param_1 + 0x110))(param_1, local_108);
  if (local_108[0] != -2) { // Expects 0xFE
      uVar2 = FUN_140001790(local_res8, "HC::Reset -2");
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Error strings `"HC::Reset -1"` and `"HC::Reset -2"`.
- **CALLER:** Session disconnect and adapter re-initialization.
- **CALLEE:** `send_frame`, `read_frame`.
- **DATA FLOW:** Request: `[0x53, 0x04, 0x08, 0x5F]`. Response: `[0x4D, 0x04, 0xFE, 0xB7]`.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** None.
- **FINAL VERDICT:** Confirmed `0x08` is `HC::Reset`.

---

### CLAIM 14: Opcode 0x84 (`HC::Init5Baud`)
- **EVIDENCE STATUS:** `PROVEN_STATIC`
- **VCDS ADDRESS:** `0x14007E3B4` (`FUN_14007e3b4`)
- **FUNCTION:** `FUN_14007e3b4`
- **DECOMPILER SNIPPET:**
  ```c
  local_108 = '\x04';
  local_107 = CONCAT31((uint3)local_107 >> 8, 0x84);
  local_107 = CONCAT13(3, (uint3)local_107);
  DAT_1401f6e4c = 0xce4; // Timeout = 3300 ms
  (**(code **)(*param_1 + 0x108))(param_1, &local_108);
  iVar3 = (**(code **)(*param_1 + 0x110))(param_1);
  if (local_108 == -0x7c) { // Response 0x84
      *(undefined4 *)(param_1 + 2) = 0x55; // Sync byte 0x55
      DAT_14062fb4c = local_103; // KB1
      DAT_14062fb4d = local_102; // KB2
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Assembles 4-byte payload `[0x84, 0x03, ParityAddr, Flags]`. Sets 3300 ms timeout. Extracts detected baud rate, KeyBytes `KB1`, `KB2`, and `0x55`.
- **CALLER:** ECU connection initiation routine.
- **CALLEE:** `send_frame`, `read_frame`.
- **DATA FLOW:** Host sends 5-baud initiation directive; adapter hardware drives lines autonomously.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** Parity byte for ECU 01 corrected from `0x81` to `0x01`.
- **FINAL VERDICT:** Confirmed `0x84` is `HC::Init5Baud`.

---

### CLAIM 15: Opcode 0x85 (`HEX_CMD_TURBO_BAUD`)
- **EVIDENCE STATUS:** `INFERRED`
- **VCDS ADDRESS:** `0x14007E630` (`FUN_14007e630`)
- **FUNCTION:** `FUN_14007e630`
- **DECOMPILER SNIPPET:**
  ```c
  local_108[0] = '\x05';
  local_108[1] = -0x7b; // 0x85
  // Copies uint32 target baud into local_108 + 2
  (**(code **)(*param_1 + 0x108))(param_1, local_108);
  ```
- **ASSEMBLY/P-CODE CHECK:** Sends opcode `0x85` with 4-byte baud rate parameter.
- **CALLER:** Advanced K-line turbo mode handlers.
- **CALLEE:** `send_frame`.
- **DATA FLOW:** Baud rate configuration parameter passed to adapter.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** Semantic name "TurboBaud" inferred from surrounding strings and parameters; exact log string at callsite is missing.
- **FINAL VERDICT:** Payload structure is `PROVEN_STATIC`, semantic name is `INFERRED`.

---

### CLAIM 16: Group 011 Wire Frame via KWP Service 0x21
- **EVIDENCE STATUS:** `CONFLICT` / `UNKNOWN`
- **VCDS ADDRESS:** `0x14005911c` (`BlockDlg`), `0x14007E734` (`send_frame`)
- **FUNCTION:** `BlockDlg::OnGraph`
- **DECOMPILER SNIPPET:**
  `BlockDlg` creates a named pipe `MB-PIPE.TXT` and communicates via virtual member functions. No direct call to `0x14007E734` exists with raw `0x21` in its payload.
- **ASSEMBLY/P-CODE CHECK:** The constant `0x21` does not appear as an opcode in any `CALL qword ptr [RAX + 0x108]` site.
- **CALLER:** GUI thread.
- **CALLEE:** Session communication object.
- **DATA FLOW:** **UNPROVEN BREAKPOINT.** Data flow from `BlockDlg` to `send_frame` is mediated by polymorphic session classes.
- **RUNTIME TRACE:** Missing dynamic trace of Group 011 request.
- **CONTRADICTIONS:** Prior draft claimed `53 04 21 0B ...` was the wire packet. This was an ungrounded assumption conflating ISO 14230 service IDs with Ross-Tech adapter framing.
- **FINAL VERDICT:** Status is `UNKNOWN`. Wire frame MUST NOT be generated until dynamic trace or complete AST bridging is established.

---

### CLAIM 17: Keepalive Opcode 0x3E
- **EVIDENCE STATUS:** `CONFLICT` / `UNKNOWN`
- **VCDS ADDRESS:** Unknown
- **FUNCTION:** Unknown
- **DECOMPILER SNIPPET:** N/A
- **ASSEMBLY/P-CODE CHECK:** No call site constructing `local_buf = { 0x01, 0x3E }` passed to `*(param_1 + 0x108)`.
- **CALLER:** N/A
- **CALLEE:** N/A
- **DATA FLOW:** Unproven.
- **RUNTIME TRACE:** Missing.
- **CONTRADICTIONS:** Prior draft asserted `53 04 3E ...` as adapter keepalive. 0x3E is KWP `TesterPresent`, not proven as a raw adapter opcode.
- **FINAL VERDICT:** Status is `UNKNOWN`. Do not use `0x3E` as raw adapter opcode.

---

### CLAIM 18: Function `FUN_14011FEB8` as Group 011 Formula Decoder
- **EVIDENCE STATUS:** `CONFLICT`
- **VCDS ADDRESS:** `0x14011FEB8` (`FUN_14011feb8`)
- **FUNCTION:** `FUN_14011feb8`
- **DECOMPILER SNIPPET:**
  ```c
  if (((&DAT_1405a4280)[DAT_14020b8e0] ^ *(byte *)(param_1 + 0x1f0)) == 0xaa) {
      if (((&DAT_1405a4280)[DAT_14020b8e1] ^ *(byte *)(param_1 + 0x1f1)) == 0x55) {
          // Decrypts 16-byte challenge block and checks magic bytes
      }
  }
  ```
- **ASSEMBLY/P-CODE CHECK:** Compares XOR results with `0xAA` and `0x55`. Performs security challenge validation on EEPROM block read via opcode `0x0B`.
- **CALLER:** `FUN_14007f1d8` (session init).
- **CALLEE:** `FUN_14011b108`.
- **DATA FLOW:** Validates adapter hardware authorization block.
- **RUNTIME TRACE:** N/A.
- **CONTRADICTIONS:** Prior draft claimed this function decoded Group 011 measuring formulas ($0.2 \times A \times B$, etc.). Decompilation proves this function has nothing to do with measuring blocks; it is an adapter authentication check.
- **FINAL VERDICT:** Status is `CONFLICT`. The claim that `FUN_14011FEB8` decodes Group 011 is completely false and retracted.

---

### CLAIM 19: "100% Full Decompilation"
- **EVIDENCE STATUS:** `CONFLICT` (Retracted)
- **VCDS ADDRESS:** Global binary scope
- **FUNCTION:** Multiple
- **DECOMPILER SNIPPET:** Multiple functions contain unreachable blocks or unresolved indirect calls.
- **ASSEMBLY/P-CODE CHECK:** Section `.text` spans `0x140001000 - 0x14018C000`. Over 1,200 functions identified by Ghidra, but extensive virtual method tables require manual symbol resolution.
- **CONTRADICTIONS:** Claiming "100% full decompilation" was an ungrounded exaggeration.
- **FINAL VERDICT:** Retracted. The analysis is targeted and bounded to transport and session routines with explicitly identified evidence boundaries.
