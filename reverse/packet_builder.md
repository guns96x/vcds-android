# Stage 4: Packet Framing & Checksum Specification

## 1. Frame Structure: Host to Ross-Tech Adapter (Request)
Every frame sent from VCDS to the HEX adapter is built by function **`vcds_adapter_send_frame` (`FUN_14007e734` @ `0x14007e734`)**.

```
+------------+------------+------------+------------------------+---------------+
| Byte 0     | Byte 1     | Byte 2     | Bytes 3 .. (L-2)       | Byte (L-1)    |
| Sync 'S'   | Length (L) | Opcode     | Command Payload        | Checksum      |
| 0x53       | N + 3      | CMD_ID     | D[0] .. D[N-2]         | XOR Sum       |
+------------+------------+------------+------------------------+---------------+
```

- **Sync Header Byte:** `0x53` (ASCII `'S'`)
- **Length Byte ($L$):** Total number of bytes in the wire frame ($L = \text{PayloadLength} + 3$).
- **Payload ($N$ bytes):** First byte is always the Command Opcode, followed by parameters.
- **Checksum Byte:** Cumulative XOR checksum of the entire frame.
- **Maximum Length:** Maximum payload length is $0x50$ (80 bytes); total frame size $\le 83$ bytes.

### Exact Checksum Formula
$$\text{Checksum} = 0x53 \oplus L \oplus \text{Opcode} \oplus \bigoplus_{i=0}^{N-2} D[i]$$

### Decompiled Frame Builder (`FUN_14007e734` @ `0x14007e734`)
```c
undefined8 vcds_adapter_send_frame(undefined8 context, byte *payload)
{
    byte payloadLen = *payload; // payload[0] specifies payload length
    byte checksum;
    byte frame[256];
    
    if (payloadLen >= 0x51) {
        // Exceeds max length limit
        vcds_log_error(context, "HC::SendCommand -1");
        return 0xffffffff;
    }
    
    frame[0] = 0x53;                     // 'S' sync byte
    frame[1] = payloadLen + 3;          // Total frame length
    checksum = (payloadLen + 3) ^ 0x53; // Initialize XOR checksum
    
    // Copy payload and compute cumulative XOR
    for (int i = 0; i < payloadLen; i++) {
        byte b = payload[i + 1];
        frame[2 + i] = b;
        checksum ^= b;
    }
    
    frame[payloadLen + 2] = checksum;   // Append checksum at end of frame
    int totalBytes = payloadLen + 3;
    
    if (fastMode == 2) {
        // Bulk write via FT_Write
        vcds_ftdi_write_buffer(frame, totalBytes);
    } else {
        // Byte-by-byte transmission with optional inter-byte pacing
        for (int i = 0; i < totalBytes; i++) {
            if (targetEcu == 0x02) {
                Sleep(10); // 10ms pacing delay for Auto-Trans
            }
            vcds_ftdi_write_byte(frame[i]);
        }
    }
    return 0; // Success
}
```

---

## 2. Frame Structure: Ross-Tech Adapter to Host (Response)
Every response received from the HEX adapter is framed and verified by function **`vcds_adapter_read_frame` (`FUN_14007e824` @ `0x14007e824`)**.

```
+------------+------------+------------+------------------------+---------------+
| Byte 0     | Byte 1     | Byte 2     | Bytes 3 .. (L-2)       | Byte (L-1)    |
| Sync 'M'   | Length (L) | Opcode/ACK | Response Payload       | Checksum      |
| 0x4D       | Total Len  | RESP_ID    | R[0] .. R[N-2]         | XOR Sum       |
+------------+------------+------------+------------------------+---------------+
```

- **Sync Header Byte:** `0x4D` (ASCII `'M'`)
- **Length Byte ($L$):** Total number of bytes in the response frame.
- **Checksum Verification:** The cumulative XOR of all bytes in the frame (including the checksum byte itself) must evaluate to `0x00`.
- **Payload Extraction:** Extracted payload has length $L - 3$ and is copied to the caller's receive buffer.

### Decompiled Response Parser (`FUN_14007e824` @ `0x14007e824`)
```c
int vcds_adapter_read_frame(undefined8 context, void *outPayload)
{
    byte frame[256];
    int b = vcds_read_byte_timeout(0);
    if (b != 0x4D) { // Must start with 'M' (0x4D)
        return -1;   // Header error
    }
    
    frame[0] = 0x4D;
    int len = vcds_read_byte_timeout(0);
    if (len < 0 || len >= 0x31) {
        return -2;   // Length invalid (max response length is 0x30 = 48 bytes)
    }
    frame[1] = (byte)len;
    
    // Read remaining (len - 2) bytes
    int bytesToRead = len - 2;
    for (int i = 0; i < bytesToRead; i++) {
        int val = vcds_read_byte_timeout(0);
        if (val < 0) return -4; // Timeout
        frame[2 + i] = (byte)val;
    }
    
    // Validate cumulative XOR checksum over the entire frame
    byte xorSum = frame[0];
    for (int i = 1; i < len; i++) {
        xorSum ^= frame[i];
    }
    
    if (xorSum != 0) {
        return -5; // Checksum mismatch error
    }
    
    // Copy extracted payload (stripping 'M', length, and checksum)
    memcpy(outPayload, frame + 2, len - 3);
    return 0; // Success
}
```
