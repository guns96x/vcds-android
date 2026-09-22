# Stage 2: FTDI D2XX & RTUS64 Hardware Binding

## 1. D2XX Function Pointer Table
In `VCDS.EXE`, all FTDI D2XX functions are invoked via an indirect pointer table residing contiguously in `.rdata` from `0x14018C810` to `0x14018C8E0`.

| Table Address in VCDS | D2XX Function Target | Export Address in RTUS64 | Description |
| :--- | :--- | :--- | :--- |
| `0x000000014018C810` | `FT_ResetDevice` | `0x0000000180002CF0` | Resets USB chip state |
| `0x000000014018C818` | `FT_W32_CloseHandle`| `0x00000001800047B0` | Closes Win32 overlapped handle |
| `0x000000014018C820` | `FT_SetDataCharacteristics` | `0x0000000180002DC0` | Configures 8N1 serial parameters |
| `0x000000014018C828` | `FT_Close` | `0x0000000180002B40` | Closes device handle |
| `0x000000014018C830` | `FT_GetDeviceInfo` | `0x00000001800044A0` | Queries device serial & description |
| `0x000000014018C838` | `FT_ClrRts` | `0x0000000180002F10` | Clears RTS line |
| `0x000000014018C840` | `FT_Purge` | `0x0000000180002FE0` | Flushes RX/TX FIFOs |
| `0x000000014018C848` | `FT_SetTimeouts` | `0x0000000180003020` | Configures read/write timeouts |
| `0x000000014018C850` | `FT_SetBreakOff` | `0x0000000180003230` | Clears break condition |
| `0x000000014018C858` | `FT_Read` | `0x0000000180002BB0` | Reads bytes from interface |
| `0x000000014018C860` | `FT_Write` | `0x0000000180002C10` | Writes bytes to interface |
| `0x000000014018C868` | `FT_SetDtr` | `0x0000000180002E50` | Sets DTR line |
| `0x000000014018C870` | `FT_SetRts` | `0x0000000180002ED0` | Sets RTS line |
| `0x000000014018C878` | `FT_SetLatencyTimer` | `0x0000000180004320` | Configures FTDI USB latency timer |
| `0x000000014018C880` | `FT_EE_UASize` | `0x00000001800037B0` | Reads EEPROM user area size |
| `0x000000014018C888` | `FT_EE_UAWrite` | `0x0000000180006D40` | Writes EEPROM user area |
| `0x000000014018C890` | `FT_GetQueueStatus` | `0x0000000180003060` | Checks number of bytes in RX FIFO |
| `0x000000014018C898` | `FT_ReadEE` | `0x00000001800032F0` | Reads EEPROM word |
| `0x000000014018C8A0` | `FT_GetModemStatus` | `0x0000000180002F50` | Reads modem status lines |
| `0x000000014018C8A8` | `FT_Open` | `0x0000000180005830` | Opens device by device index |
| `0x000000014018C8B0` | `FT_GetLibraryVersion` | `0x0000000180005200` | Queries D2XX driver version |
| `0x000000014018C8B8` | `FT_SetBaudRate` | `0x0000000180002D30` | Sets FTDI UART baud rate |
| `0x000000014018C8C0` | `FT_CreateDeviceInfoList` | `0x0000000180007940` | Enumerates connected devices |
| `0x000000014018C8C8` | `FT_ClrDtr` | `0x0000000180002E90` | Clears DTR line |
| `0x000000014018C8D0` | `FT_GetDeviceInfoList` | `0x0000000180004FC0` | Populates node list for all devices |
| `0x000000014018C8D8` | `FT_GetDriverVersion` | `0x00000001800051B0` | Queries driver version |
| `0x000000014018C8E0` | `FT_SetBreakOn` | `0x00000001800031F0` | Sets break condition on TX line |

---

## 2. Low-Level Wrapper Functions & Call Sites

### Device Discovery & Open (`FUN_140111f40`)
```c
// 1. Enumerate connected FTDI devices
FT_CreateDeviceInfoList(&numDevs);
nodes = malloc(numDevs * sizeof(FT_DEVICE_LIST_INFO_NODE));
FT_GetDeviceInfoList(nodes, &numDevs);

// 2. Filter for Ross-Tech Vendor/Product ID
// VID = 0x0403 (FTDI), PID range = 0xFA20 .. 0xFA2F (Ross-Tech custom VID/PID)
if (((node.Flags & 0xffff0000) == 0x04030000) && ((node.Flags & 0xffff) - 0xfa20 < 0x10)) {
    // Valid Ross-Tech HEX hardware found
}

// 3. Open device by index
FT_Open(deviceIndex, &ftHandle);

// 4. Reset device
FT_ResetDevice(ftHandle);
FT_Close(ftHandle);
FT_Open(deviceIndex, &ftHandle);

// 5. Configure Latency Timer (CRITICAL: set to 1 ms for minimal USB round-trip latency)
FT_SetLatencyTimer(ftHandle, 1);

// 6. Set Timeouts
// ReadTimeout = 1 ms (non-blocking fast polling with FT_GetQueueStatus)
// WriteTimeout = 100 ms
FT_SetTimeouts(ftHandle, 1, 100);
```

### Port Configuration (`FUN_140112160`)
```c
// 1. Data Characteristics: 8 data bits, 1 stop bit, no parity (8N1)
FT_SetDataCharacteristics(ftHandle, FT_BITS_8=8, FT_STOP_BITS_1=0, FT_PARITY_NONE=0);

// 2. Baud Rate: Initial handshake baud rate (9600 bps or 115200 bps)
FT_SetBaudRate(ftHandle, baudRate);

// 3. Purge RX and TX buffers
FT_Purge(ftHandle, FT_PURGE_RX | FT_PURGE_TX = 3);
```

### Buffer Write (`FUN_14011185c`)
```c
int vcds_ftdi_write_buffer(byte *buffer, int length) {
    DWORD bytesWritten = 0;
    FT_STATUS status = (*DAT_14018c860)(ftHandle, buffer, length, &bytesWritten);
    return (status == FT_OK) ? 0 : -1;
}
```

### Single Byte Read (`FUN_1401117bc`)
```c
int vcds_ftdi_read_byte(void) {
    DWORD rxQueueBytes = 0;
    (*DAT_14018c890)(ftHandle, &rxQueueBytes); // FT_GetQueueStatus
    if (rxQueueBytes > 0) {
        byte b = 0;
        DWORD bytesRead = 0;
        FT_STATUS status = (*DAT_14018c858)(ftHandle, &b, 1, &bytesRead); // FT_Read
        if (status == FT_OK && bytesRead == 1) {
            return (int)b;
        }
    }
    return -1; // No byte available or timeout
}
```
