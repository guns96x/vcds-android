# Differential USB Capture Analysis Report

> [!NOTE]
> **EVIDENCE STATUS NOTICE**: This report reflects comparative analysis of input capture transcripts.
> If generated against synthetic/mock fixtures, all payloads represent unit-test vectors only and MUST NOT
> be interpreted as confirmed Ross-Tech or B03-V2 protocol evidence.

**Analyzed Captures**: Capture_A, Capture_B, Capture_C

## 1. FTDI Link Layer Setup Sequence

| Capture | Step | Command | Details | Value / Index |
|---|---|---|---|---|
| Capture_A | 1 | `FTDI_SIO_RESET` | SIO_RESET (Purge buffers & reset UART) | `0x0000` / `0x0000` |
| Capture_A | 2 | `FTDI_SIO_SET_BAUDRATE` | Div=0x001A (~115384 baud) | `0x001A` / `0x0000` |
| Capture_A | 3 | `FTDI_SIO_SET_LATENCY_TIMER` | 1 ms | `0x0001` / `0x0000` |
| Capture_A | 4 | `FTDI_SIO_MODEM_CTRL` | DTR=HIGH, RTS=HIGH | `0x0303` / `0x0000` |
| Capture_B | 1 | `FTDI_SIO_RESET` | SIO_RESET (Purge buffers & reset UART) | `0x0000` / `0x0000` |
| Capture_B | 2 | `FTDI_SIO_SET_BAUDRATE` | Div=0x001A (~115384 baud) | `0x001A` / `0x0000` |
| Capture_B | 3 | `FTDI_SIO_SET_LATENCY_TIMER` | 1 ms | `0x0001` / `0x0000` |
| Capture_C | - | *None* | - | - |

## 2. Invariant Command Sequences (Common to ALL Captures)
Commands observed across all captures represent adapter handshake, reset, or protocol initialization:

| Hex Payload | Length | Probable Role |
|---|---|---|
| `53594e54485f50494e475f303031` | 14 B | Handshake / Reset Invariant |

## 3. Phase-Specific Unique Commands (Differential Analysis)

### Capture: `Capture_A`
*No unique commands detected.*

### Capture: `Capture_B`
| Unique Hex Command | Length | Protocol Correlation |
|---|---|---|
| `021a9a00` | 4 B | KWP2000 ReadECUIdentification (0x1A) at byte 1 |
| `8101f181f4` | 5 B | KWP2000 StartCommunication (0x81 0x01 0xF1 0x81 0xF4) |

### Capture: `Capture_C`
| Unique Hex Command | Length | Protocol Correlation |
|---|---|---|
| `031800001b` | 5 B | KWP2000 ReadDiagnosticTroubleCodes (0x18) at byte 1 |

## 4. Correlated Protocol Transactions (KWP / TP2 / CAN)

### `Capture_B`
| Request Hex | Request Protocol | Response Hex | Response Protocol | Latency |
|---|---|---|---|---|
| `8101f181f4` | KWP2000 StartCommunication (0x81 0x01 0xF1 0x81 0xF4) | `83f101c1ea8f00` | *Custom Framing* | 35.0 ms |
| `021a9a00` | KWP2000 ReadECUIdentification (0x1A) at byte 1 | `501a3033473930363031364142` | KWP2000 PositiveResponse (0x50) at byte 0; KWP2000 ReadECUIdentification (0x1A) at byte 1 | 25.0 ms |

### `Capture_C`
| Request Hex | Request Protocol | Response Hex | Response Protocol | Latency |
|---|---|---|---|---|
| `031800001b` | KWP2000 ReadDiagnosticTroubleCodes (0x18) at byte 1 | `5800` | *Custom Framing* | 25.0 ms |
