# Stage 7: Measuring Blocks & Group 011 Protocol Specification

## 1. Overview
In VCDS, Measuring Blocks is managed by the `BlockDlg` dialog class (`0x140057000` - `0x14005C000`) interacting with the active session object `DAT_140701EC0`.

Group 011 in VAG TDI diesel engines corresponds to **Charge Pressure Control (Boost)**:
- Field 1: Engine Speed (RPM)
- Field 2: Specified MAP (Manifold Absolute Pressure, mbar)
- Field 3: Actual MAP (mbar)
- Field 4: Charge Pressure Control D. Cycle (N75 duty cycle, %)

---

## 2. Wire Protocol Details

### KWP1281 Mode (Older ECUs)
- **Request Frame:**
  - Block Title: `0x29` (`READ_MEASURING_VALUE_BLOCK`)
  - Group Parameter: `0x0B` (decimal 11 for Group 011)
  - Frame layout: `[ 0x03, 0x29, 0x0B, Checksum ]`
- **Response Frame:**
  - Block Title: `0xE7` (`MEASURING_VALUES_RESPONSE`)
  - Length: `0x0F` (15 bytes)
  - Layout: `[ 0xE7, Field1 (3 bytes), Field2 (3 bytes), Field3 (3 bytes), Field4 (3 bytes) ]`
  - Each Field (3 bytes): `[ FormulaType, ValueA, ValueB ]`

### KWP2000 Mode (ISO 14230)
- **Request Service:** `0x21` (`ReadDataByLocalIdentifier`)
- **Record Local Identifier:** `0x0B` (decimal 11)
- **Request Frame:**
  - Length: `2`
  - Service ID: `0x21`
  - Local ID: `0x0B`
  - Frame sent via HEX adapter: `[ 0x53, 0x05, 0x21, 0x0B, Checksum ]`
- **Positive Response:**
  - Response SID: `0x61` (`0x21 + 0x40`)
  - Local ID Echo: `0x0B`
  - Payload: Raw field bytes or 10-byte scaling record.

---

## 3. Field Decoding & Scaling (`FUN_14011feb8`)
In `FUN_14011feb8`, the response buffer is decoded into four 16-bit values stored at `param_1 + 0x420` (`puVar6[0]`, `puVar6[1]`, `puVar6[2]`, `puVar6[3]`):

### Standard VAG Formulas for Group 011 Fields:
1. **Engine Speed (Formula 0x01):**
   $$\text{RPM} = 0.2 \times \text{ValA} \times \text{ValB}$$
2. **Pressure (Formula 0x07 / 0x16):**
   $$\text{Pressure (mbar)} = 0.04 \times \text{ValA} \times \text{ValB}$$
   (or direct 16-bit value in mbar).
3. **Duty Cycle (Formula 0x12 / 0x19):**
   $$\text{Duty Cycle (\%)} = \frac{\text{ValA} \times 100}{255} \quad \text{or} \quad 0.005 \times \text{ValA} \times \text{ValB}$$

---

## 4. Polling & Keepalive Timing
- **Polling Loop:** Continuously requests the active group while the dialog is visible.
- **Inter-frame Delay:** 50 ms to 100 ms between response reception and next request.
- **Session Keepalive:** If no user action occurs, tester sends keepalive frame every 1000 ms to prevent ECU session timeout ($P3_{\text{max}} = 2000 \text{ ms}$).
