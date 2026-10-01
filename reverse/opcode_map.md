# Stage 5: Opcode Dispatch & Protocol Map

All communication opcodes exchanged between VCDS and the Ross-Tech HEX-USB+CAN adapter operate inside the `'S'`/`'M'` framing layer.

| Opcode | Name | Direction | Request Layout | Response Layout | Handler Address | Evidence Status |
| :---: | :--- | :---: | :--- | :--- | :--- | :---: |
| `0x02` | `HEX_CMD_GET_VERSION` | Host $\to$ Adapter | `[0x01, 0x02]` | `[0x02, Major, Minor, ModelChar]` (e.g. `[0x02, 0x01, 0x60, 'D']` for v1.96 Dual-K/CAN) | `0x14007E988` | `PROVEN_STATIC` |
| `0x03` | `HEX_CMD_SET_COM_BAUD` | Host $\to$ Adapter | `[0x05, 0x03, B0, B1, B2, B3]` (Baud rate uint32 LE, e.g. `0x0001C200` = 115200) | `[0xFE]` (Adapter Ack), then host switches PC FTDI baud and awaits `[0xFD]` (Ready) | `0x14007EC2C` | `PROVEN_STATIC` |
| `0x85` | `HEX_CMD_TURBO_BAUD` | Host $\to$ Adapter | `[0x05, 0x85, B0, B1, B2, B3]` (Turbo baud rate uint32 LE) | `[0xFE]` (Ack) | `0x14007E630` | `PROVEN_STATIC` |
| `0x84` | `HEX_CMD_5BAUD_INIT` | Host $\to$ Adapter | `[0x04, 0x84, 0x03, ECU_Addr, Flags]` (ECU Addr with 7-bit odd parity) | `[0x84, BaudHi, BaudLo, KB1, KB2, 0x55]` | `0x14007E3B4` | `PROVEN_STATIC` |
| `0x0B` | `HEX_CMD_KLINE_BLOCK` | Host $\to$ Adapter | `[0x03, 0x0B, BlockNum, SubIndex]` | `[0x0B, 32-byte block data]` | `0x14007F1D8` | `PROVEN_STATIC` |
| `0xA0` | `HEX_CMD_TEST_KMODE` | Host $\to$ Adapter | `[0x01, 0xA0]` | `[0xA0, Status]` | `0x14007FFC8` | `PROVEN_STATIC` |
| `0xFE` | `HEX_CMD_CONFIRM` | Bidirectional | `[0x01, 0xFE]` | None / Execution Ack | `0x14007EC2C` | `PROVEN_STATIC` |
| `0xFD` | `HEX_RESP_READY` | Adapter $\to$ Host | None | `[0xFD]` (Adapter ready for next transmission) | `0x14007EC2C` | `PROVEN_STATIC` |

---

## Detailed Opcode Specifications

### 1. `0x02` — `HEX_CMD_GET_VERSION`
- **Purpose:** Query firmware version, hardware revision, and CAN capability.
- **Request Frame:**
  - Length: `4` (`0x04`)
  - Payload: `0x02`
  - Wire bytes: `53 04 02 55`
- **Response Handling (`FUN_14007e988`):**
  - Expected response opcode: `0x02`.
  - Bytes returned: `[0x02, Major, Minor, TypeChar]`.
  - Type characters:
    - `'C'` = CAN capable single-K interface.
    - `'D'` = Dual-K + CAN interface (`HEX-USB+CAN`).
    - `'F'` = Fast-HEX interface.

### 2. `0x03` — `HEX_CMD_SET_COM_BAUD`
- **Purpose:** Switch UART communication speed between PC (FTDI) and HEX adapter microcontroller.
- **Request Frame:**
  - Length: `8` (`0x08`)
  - Opcode: `0x03`
  - Baud rate: 4 bytes Little Endian (`0x00, 0xC2, 0x01, 0x00` for 115200).
- **Handshake Sequence:**
  1. PC sends `0x03` command at current baud rate (default 9600 bps).
  2. Adapter acknowledges with `0xFE`.
  3. PC sets FTDI baud rate to 115200 via `FT_SetBaudRate(0x1C200)`.
  4. Adapter transmits `0xFD` at 115200 to confirm new line speed.
  5. PC sends `0xFE` confirmation back to adapter.

### 3. `0x84` — `HEX_CMD_5BAUD_INIT`
- **Purpose:** Instruct HEX adapter microcontroller to autonomously execute hardware 5-baud wake-up on the K-line.
- **Request Frame:**
  - Length: `7` (`0x07`)
  - Opcode: `0x84`
  - Subcommand: `0x03` (K-Line init)
  - ECU Address: `Target_Addr` with 7-bit odd parity.
    - Engine (0x01): Odd parity calculation sets bit 7 $\to$ `0x81`.
    - Auto-Trans (0x02): Odd parity sets bit 7 $\to$ `0x82`.
    - ABS (0x03): `0x03` (already has odd parity).
  - Flags: `0x00` (standard timings).
- **Autonomous Adapter Processing:**
  - The HEX microcontroller pulls K-Line low for 200 ms (start bit), sends the 8 address bits at 5 baud (200 ms/bit), pulls high for 1 stop bit (total ~2000 ms).
  - Waits for ECU sync byte `0x55`.
  - Reads Key Words `KB1` and `KB2`.
  - Computes ECU baud rate from pulse widths.
  - Inverts `KB2` and transmits it back to ECU within W4 time window.
- **Response Frame:**
  - Opcode: `0x84`
  - Bytes returned: `[0x84, Baud_Hi, Baud_Lo, KB1, KB2, 0x55]`.
  - VCDS verifies `Sync == 0x55` and stores `KB1`, `KB2`, and the detected baud rate.
