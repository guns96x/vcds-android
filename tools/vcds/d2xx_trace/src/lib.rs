//! RTUS64.dll -- Transparent FTDI D2XX Logging Shim for VCDS Reverse Engineering.
//!
//! Forwards calls to RTUS64_orig.dll, intercepts diagnostic transport I/O
//! (FT_Write, FT_Read, baud rates, control lines), logs timestamped byte buffers,
//! and guards against EEPROM write corruption.

use std::ffi::{c_char, c_void, CStr};
use std::fs::{File, OpenOptions};
use std::io::Write;
use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::{Mutex, OnceLock};
use std::time::Instant;

use windows_sys::Win32::Foundation::{HMODULE, MAX_PATH};
use windows_sys::Win32::System::LibraryLoader::{
    GetModuleFileNameW, GetModuleHandleExW, GetProcAddress, LoadLibraryW,
    GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS, GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
};
use windows_sys::Win32::System::Threading::GetCurrentThreadId;

pub type FtHandle = *mut c_void;
pub type FtStatus = u32;

pub const FT_OK: FtStatus = 0;
pub const FT_INVALID_HANDLE: FtStatus = 1;
pub const FT_DEVICE_NOT_FOUND: FtStatus = 2;
pub const FT_DEVICE_NOT_OPENED: FtStatus = 3;
pub const FT_IO_ERROR: FtStatus = 4;
pub const FT_INSUFFICIENT_RESOURCES: FtStatus = 5;
pub const FT_INVALID_PARAMETER: FtStatus = 6;
pub const FT_INVALID_BAUD_RATE: FtStatus = 7;
pub const FT_DEVICE_NOT_OPENED_FOR_ERASE: FtStatus = 8;
pub const FT_DEVICE_NOT_OPENED_FOR_WRITE: FtStatus = 9;
pub const FT_FAILED_TO_WRITE_DEVICE: FtStatus = 10;
pub const FT_EEPROM_READ_FAILED: FtStatus = 11;
pub const FT_EEPROM_WRITE_FAILED: FtStatus = 12;
pub const FT_EEPROM_ERASE_FAILED: FtStatus = 13;
pub const FT_EEPROM_NOT_PRESENT: FtStatus = 14;
pub const FT_EEPROM_NOT_PROGRAMMED: FtStatus = 15;
pub const FT_INVALID_ARGS: FtStatus = 16;
pub const FT_NOT_SUPPORTED: FtStatus = 17;
pub const FT_OTHER_ERROR: FtStatus = 18;

static START_TIME: OnceLock<Instant> = OnceLock::new();
static SEQ_COUNTER: AtomicUsize = AtomicUsize::new(1);
static LOG_FILE: OnceLock<Mutex<File>> = OnceLock::new();

fn get_start_time() -> Instant {
    *START_TIME.get_or_init(Instant::now)
}

fn log_line(msg: &str) {
    let now = get_start_time().elapsed();
    let millis = now.as_millis();
    let tid = unsafe { GetCurrentThreadId() };
    let seq = SEQ_COUNTER.fetch_add(1, Ordering::Relaxed);

    let formatted = format!("[{millis:09}ms][seq:{seq:06}][tid:{tid:05}] {msg}\n");

    let logger = LOG_FILE.get_or_init(|| {
        let log_path = get_dll_directory()
            .map(|p| p.join("vcds_d2xx_trace.log"))
            .unwrap_or_else(|| PathBuf::from("vcds_d2xx_trace.log"));
        let f = OpenOptions::new()
            .create(true)
            .append(true)
            .open(&log_path)
            .unwrap_or_else(|_| File::create("vcds_d2xx_trace.log").expect("create fallback log"));
        Mutex::new(f)
    });

    if let Ok(mut lock) = logger.lock() {
        let _ = lock.write_all(formatted.as_bytes());
        let _ = lock.flush();
    }
}

fn hex_format(buf: &[u8]) -> (String, String) {
    let hex = buf.iter().map(|b| format!("{b:02X}")).collect::<Vec<_>>().join(" ");
    let ascii: String = buf
        .iter()
        .map(|&b| if (0x20..=0x7E).contains(&b) { b as char } else { '.' })
        .collect();
    (hex, ascii)
}

fn get_dll_directory() -> Option<PathBuf> {
    unsafe {
        let mut path_buf = [0u16; MAX_PATH as usize];
        let addr = get_dll_directory as *const ();
        let mut hmod: HMODULE = std::ptr::null_mut();
        let ok = GetModuleHandleExW(
            GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS | GET_MODULE_HANDLE_EX_FLAG_UNCHANGED_REFCOUNT,
            addr as *const u16,
            &mut hmod,
        );
        if ok != 0 {
            let len = GetModuleFileNameW(hmod, path_buf.as_mut_ptr(), path_buf.len() as u32);
            if len > 0 {
                let s = String::from_utf16_lossy(&path_buf[..len as usize]);
                let p = PathBuf::from(s);
                return p.parent().map(|d| d.to_path_buf());
            }
        }
    }
    None
}

struct OrigDll {
    module: HMODULE,
}

unsafe impl Send for OrigDll {}
unsafe impl Sync for OrigDll {}

static ORIG_DLL: OnceLock<OrigDll> = OnceLock::new();

fn get_orig_dll() -> &'static OrigDll {
    ORIG_DLL.get_or_init(|| {
        let mut target_name = "RTUS64_orig.dll\0".encode_utf16().collect::<Vec<_>>();
        if let Some(dir) = get_dll_directory() {
            let full = dir.join("RTUS64_orig.dll");
            let mut full_w = full.as_os_str().to_string_lossy().encode_utf16().collect::<Vec<_>>();
            full_w.push(0);
            let h = unsafe { LoadLibraryW(full_w.as_ptr()) };
            if h != std::ptr::null_mut() {
                log_line(&format!("Loaded genuine D2XX from: {}", full.display()));
                return OrigDll { module: h };
            }
        }

        let h = unsafe { LoadLibraryW(target_name.as_mut_ptr()) };
        if h == std::ptr::null_mut() {
            log_line("CRITICAL: Failed to load RTUS64_orig.dll!");
        } else {
            log_line("Loaded RTUS64_orig.dll from PATH");
        }
        OrigDll { module: h }
    })
}

unsafe fn get_proc<T>(name: &[u8]) -> Option<T> {
    let orig = get_orig_dll();
    if orig.module == std::ptr::null_mut() {
        return None;
    }
    let p = GetProcAddress(orig.module, name.as_ptr() as *const u8);
    if p.is_none() {
        let name_str = CStr::from_bytes_with_nul(name).map(|s| s.to_string_lossy()).unwrap_or_default();
        log_line(&format!("WARNING: Missing symbol in RTUS64_orig: {name_str}"));
        None
    } else {
        Some(std::mem::transmute_copy(&p))
    }
}

// ============================================================================
// INTERCEPTED D2XX CALLS
// ============================================================================

#[no_mangle]
pub unsafe extern "system" fn FT_Open(device_number: u32, handle: *mut FtHandle) -> FtStatus {
    log_line(&format!("FT_Open(dev_num: {device_number})"));
    type FnType = unsafe extern "system" fn(u32, *mut FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Open\0") {
        let status = f(device_number, handle);
        let h_val = if !handle.is_null() { *handle } else { std::ptr::null_mut() };
        log_line(&format!("  -> status: {status}, handle: {h_val:p}"));
        status
    } else {
        FT_DEVICE_NOT_FOUND
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_OpenEx(arg1: *const c_void, flags: u32, handle: *mut FtHandle) -> FtStatus {
    let arg_desc = if (flags & 1) != 0 || (flags & 2) != 0 {
        if arg1.is_null() {
            "null".to_string()
        } else {
            CStr::from_ptr(arg1 as *const c_char).to_string_lossy().into_owned()
        }
    } else {
        format!("{arg1:p}")
    };
    log_line(&format!("FT_OpenEx(arg: \"{arg_desc}\", flags: 0x{flags:X})"));
    type FnType = unsafe extern "system" fn(*const c_void, u32, *mut FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_OpenEx\0") {
        let status = f(arg1, flags, handle);
        let h_val = if !handle.is_null() { *handle } else { std::ptr::null_mut() };
        log_line(&format!("  -> status: {status}, handle: {h_val:p}"));
        status
    } else {
        FT_DEVICE_NOT_FOUND
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_Close(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_Close(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Close\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ResetDevice(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_ResetDevice(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ResetDevice\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_Purge(handle: FtHandle, mask: u32) -> FtStatus {
    let mut rx = false;
    let mut tx = false;
    if (mask & 1) != 0 { rx = true; }
    if (mask & 2) != 0 { tx = true; }
    log_line(&format!("FT_Purge(handle: {handle:p}, mask: 0x{mask:X} [RX:{rx}, TX:{tx}])"));
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Purge\0") {
        f(handle, mask)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetBaudRate(handle: FtHandle, baud: u32) -> FtStatus {
    log_line(&format!("FT_SetBaudRate(handle: {handle:p}, baud: {baud})"));
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetBaudRate\0") {
        f(handle, baud)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetDivisor(handle: FtHandle, divisor: u32) -> FtStatus {
    log_line(&format!("FT_SetDivisor(handle: {handle:p}, divisor: 0x{divisor:X})"));
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetDivisor\0") {
        f(handle, divisor)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetDataCharacteristics(
    handle: FtHandle,
    word_len: u8,
    stop_bits: u8,
    parity: u8,
) -> FtStatus {
    let par_str = match parity {
        0 => "NONE",
        1 => "ODD",
        2 => "EVEN",
        3 => "MARK",
        4 => "SPACE",
        _ => "UNKNOWN",
    };
    log_line(&format!(
        "FT_SetDataCharacteristics(handle: {handle:p}, word_len: {word_len}, stop_bits: {stop_bits}, parity: {par_str})"
    ));
    type FnType = unsafe extern "system" fn(FtHandle, u8, u8, u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetDataCharacteristics\0") {
        f(handle, word_len, stop_bits, parity)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetFlowControl(
    handle: FtHandle,
    flow_ctrl: u16,
    xon: u8,
    xoff: u8,
) -> FtStatus {
    log_line(&format!(
        "FT_SetFlowControl(handle: {handle:p}, flow_ctrl: 0x{flow_ctrl:X}, xon: 0x{xon:02X}, xoff: 0x{xoff:02X})"
    ));
    type FnType = unsafe extern "system" fn(FtHandle, u16, u8, u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetFlowControl\0") {
        f(handle, flow_ctrl, xon, xoff)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetDtr(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_SetDtr(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetDtr\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ClrDtr(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_ClrDtr(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ClrDtr\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetRts(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_SetRts(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetRts\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ClrRts(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_ClrRts(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ClrRts\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetLatencyTimer(handle: FtHandle, latency: u8) -> FtStatus {
    log_line(&format!("FT_SetLatencyTimer(handle: {handle:p}, latency: {latency} ms)"));
    type FnType = unsafe extern "system" fn(FtHandle, u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetLatencyTimer\0") {
        f(handle, latency)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetLatencyTimer(handle: FtHandle, latency: *mut u8) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetLatencyTimer\0") {
        let st = f(handle, latency);
        let val = if !latency.is_null() { *latency } else { 0 };
        log_line(&format!("FT_GetLatencyTimer(handle: {handle:p}) -> val: {val} ms"));
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetTimeouts(
    handle: FtHandle,
    read_timeout: u32,
    write_timeout: u32,
) -> FtStatus {
    log_line(&format!(
        "FT_SetTimeouts(handle: {handle:p}, read: {read_timeout} ms, write: {write_timeout} ms)"
    ));
    type FnType = unsafe extern "system" fn(FtHandle, u32, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetTimeouts\0") {
        f(handle, read_timeout, write_timeout)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetUSBParameters(
    handle: FtHandle,
    in_transfer_size: u32,
    out_transfer_size: u32,
) -> FtStatus {
    log_line(&format!(
        "FT_SetUSBParameters(handle: {handle:p}, in: {in_transfer_size}, out: {out_transfer_size})"
    ));
    type FnType = unsafe extern "system" fn(FtHandle, u32, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetUSBParameters\0") {
        f(handle, in_transfer_size, out_transfer_size)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetBitMode(handle: FtHandle, mask: u8, mode: u8) -> FtStatus {
    log_line(&format!("FT_SetBitMode(handle: {handle:p}, mask: 0x{mask:02X}, mode: 0x{mode:02X})"));
    type FnType = unsafe extern "system" fn(FtHandle, u8, u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetBitMode\0") {
        f(handle, mask, mode)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetBitMode(handle: FtHandle, mode: *mut u8) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetBitMode\0") {
        let st = f(handle, mode);
        let val = if !mode.is_null() { *mode } else { 0 };
        log_line(&format!("FT_GetBitMode(handle: {handle:p}) -> 0x{val:02X}"));
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetQueueStatus(handle: FtHandle, rx_bytes: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetQueueStatus\0") {
        let st = f(handle, rx_bytes);
        let count = if !rx_bytes.is_null() { *rx_bytes } else { 0 };
        if count > 0 {
            log_line(&format!("FT_GetQueueStatus(handle: {handle:p}) -> rx_queue: {count} bytes"));
        }
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetStatus(
    handle: FtHandle,
    rx_bytes: *mut u32,
    tx_bytes: *mut u32,
    event_status: *mut u32,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32, *mut u32, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetStatus\0") {
        let st = f(handle, rx_bytes, tx_bytes, event_status);
        let rx = if !rx_bytes.is_null() { *rx_bytes } else { 0 };
        let tx = if !tx_bytes.is_null() { *tx_bytes } else { 0 };
        let ev = if !event_status.is_null() { *event_status } else { 0 };
        if rx > 0 || tx > 0 {
            log_line(&format!("FT_GetStatus(handle: {handle:p}) -> rx:{rx}, tx:{tx}, ev:0x{ev:X}"));
        }
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetModemStatus(handle: FtHandle, modem_status: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetModemStatus\0") {
        let st = f(handle, modem_status);
        let ms = if !modem_status.is_null() { *modem_status } else { 0 };
        log_line(&format!("FT_GetModemStatus(handle: {handle:p}) -> 0x{ms:04X}"));
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetBreakOn(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_SetBreakOn(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetBreakOn\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetBreakOff(handle: FtHandle) -> FtStatus {
    log_line(&format!("FT_SetBreakOff(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetBreakOff\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

// ----------------------------------------------------------------------------
// DATA TRANSPORT INTERCEPTORS: FT_Write and FT_Read
// ----------------------------------------------------------------------------

#[no_mangle]
pub unsafe extern "system" fn FT_Write(
    handle: FtHandle,
    buffer: *const u8,
    bytes_to_write: u32,
    bytes_written: *mut u32,
) -> FtStatus {
    let payload = if !buffer.is_null() && bytes_to_write > 0 {
        std::slice::from_raw_parts(buffer, bytes_to_write as usize)
    } else {
        &[]
    };
    let (hex, ascii) = hex_format(payload);
    log_line(&format!(
        "FT_Write(handle: {handle:p}, len: {bytes_to_write}) -> HEX: [{hex}] | ASCII: \"{ascii}\""
    ));

    type FnType = unsafe extern "system" fn(FtHandle, *const u8, u32, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Write\0") {
        let st = f(handle, buffer, bytes_to_write, bytes_written);
        let actual = if !bytes_written.is_null() { *bytes_written } else { 0 };
        if st != FT_OK || actual != bytes_to_write {
            log_line(&format!("  -> FT_Write result: status: {st}, written: {actual}/{bytes_to_write}"));
        }
        st
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_Read(
    handle: FtHandle,
    buffer: *mut u8,
    bytes_to_read: u32,
    bytes_returned: *mut u32,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u8, u32, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Read\0") {
        let st = f(handle, buffer, bytes_to_read, bytes_returned);
        let actual = if !bytes_returned.is_null() { *bytes_returned } else { 0 };
        if actual > 0 && !buffer.is_null() {
            let slice = std::slice::from_raw_parts(buffer, actual as usize);
            let (hex, ascii) = hex_format(slice);
            log_line(&format!(
                "FT_Read(handle: {handle:p}, requested: {bytes_to_read}) -> returned: {actual}, HEX: [{hex}] | ASCII: \"{ascii}\""
            ));
        } else if st != FT_OK {
            log_line(&format!("FT_Read(handle: {handle:p}, requested: {bytes_to_read}) -> status: {st}, returned: 0"));
        }
        st
    } else {
        FT_INVALID_HANDLE
    }
}

// ============================================================================
// SAFETY GUARDRAILS: BLOCK EEPROM WRITING CALLS (SAFE_TRACE MODE)
// ============================================================================

#[no_mangle]
pub unsafe extern "system" fn FT_WriteEE(handle: FtHandle, _word_offset: u32, _value: u16) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_WriteEE on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EraseEE(handle: FtHandle) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EraseEE on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_Program(handle: FtHandle, _data: *const c_void) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EE_Program on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_ProgramEx(
    handle: FtHandle,
    _data: *const c_void,
    _s1: *const c_char,
    _s2: *const c_char,
    _s3: *const c_char,
    _s4: *const c_char,
) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EE_ProgramEx on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_UAWrite(handle: FtHandle, _data: *const u8, _data_len: u32) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EE_UAWrite on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_WriteConfig(handle: FtHandle, _addr: u32, _value: u8) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EE_WriteConfig on handle {handle:p}"));
    FT_OTHER_ERROR
}

#[no_mangle]
pub unsafe extern "system" fn FT_EEPROM_Program(
    handle: FtHandle,
    _eeprom_data: *const c_void,
    _eeprom_data_size: u32,
    _manufacturer: *const c_char,
    _manufacturer_id: *const c_char,
    _description: *const c_char,
    _serial_number: *const c_char,
) -> FtStatus {
    log_line(&format!("[SAFE_TRACE GUARD] Blocked FT_EEPROM_Program on handle {handle:p}"));
    FT_OTHER_ERROR
}

// Read-only EEPROM functions are allowed and forwarded
#[no_mangle]
pub unsafe extern "system" fn FT_ReadEE(handle: FtHandle, word_offset: u32, value: *mut u16) -> FtStatus {
    log_line(&format!("FT_ReadEE(handle: {handle:p}, offset: {word_offset})"));
    type FnType = unsafe extern "system" fn(FtHandle, u32, *mut u16) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ReadEE\0") {
        f(handle, word_offset, value)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_Read(handle: FtHandle, data: *mut c_void) -> FtStatus {
    log_line(&format!("FT_EE_Read(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_Read\0") {
        f(handle, data)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_UARead(
    handle: FtHandle,
    data: *mut u8,
    data_len: u32,
    bytes_read: *mut u32,
) -> FtStatus {
    log_line(&format!("FT_EE_UARead(handle: {handle:p}, len: {data_len})"));
    type FnType = unsafe extern "system" fn(FtHandle, *mut u8, u32, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_UARead\0") {
        f(handle, data, data_len, bytes_read)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_UASize(handle: FtHandle, size: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_UASize\0") {
        f(handle, size)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_ReadEx(
    handle: FtHandle,
    data: *mut c_void,
    s1: *mut c_char,
    s2: *mut c_char,
    s3: *mut c_char,
    s4: *mut c_char,
) -> FtStatus {
    log_line(&format!("FT_EE_ReadEx(handle: {handle:p})"));
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void, *mut c_char, *mut c_char, *mut c_char, *mut c_char) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_ReadEx\0") {
        f(handle, data, s1, s2, s3, s4)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_ReadConfig(handle: FtHandle, addr: u32, val: *mut u8) -> FtStatus {
    log_line(&format!("FT_EE_ReadConfig(handle: {handle:p}, addr: {addr})"));
    type FnType = unsafe extern "system" fn(FtHandle, u32, *mut u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_ReadConfig\0") {
        f(handle, addr, val)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EE_ReadECC(handle: FtHandle, option: u8, val: *mut u16) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u8, *mut u16) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EE_ReadECC\0") {
        f(handle, option, val)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_EEPROM_Read(
    handle: FtHandle,
    eeprom_data: *mut c_void,
    eeprom_data_size: u32,
    manufacturer: *mut c_char,
    manufacturer_id: *mut c_char,
    description: *mut c_char,
    serial_number: *mut c_char,
) -> FtStatus {
    log_line(&format!("FT_EEPROM_Read(handle: {handle:p}, data_size: {eeprom_data_size})"));
    type FnType = unsafe extern "system" fn(
        FtHandle,
        *mut c_void,
        u32,
        *mut c_char,
        *mut c_char,
        *mut c_char,
        *mut c_char,
    ) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_EEPROM_Read\0") {
        f(handle, eeprom_data, eeprom_data_size, manufacturer, manufacturer_id, description, serial_number)
    } else {
        FT_INVALID_HANDLE
    }
}

// ----------------------------------------------------------------------------
// PASS-THROUGH FOR OTHER UTILITY FUNCTIONS
// ----------------------------------------------------------------------------

#[no_mangle]
pub unsafe extern "system" fn FT_SetChars(
    handle: FtHandle,
    event_ch: u8,
    event_en: u8,
    error_ch: u8,
    error_en: u8,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u8, u8, u8, u8) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetChars\0") {
        f(handle, event_ch, event_en, error_ch, error_en)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetEventNotification(
    handle: FtHandle,
    mask: u32,
    param: *mut c_void,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u32, *mut c_void) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetEventNotification\0") {
        f(handle, mask, param)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetEventStatus(handle: FtHandle, event_status: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetEventStatus\0") {
        f(handle, event_status)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetWaitMask(handle: FtHandle, mask: u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetWaitMask\0") {
        f(handle, mask)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_WaitOnMask(handle: FtHandle, mask: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_WaitOnMask\0") {
        f(handle, mask)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ListDevices(
    arg1: *mut c_void,
    arg2: *mut c_void,
    flags: u32,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(*mut c_void, *mut c_void, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ListDevices\0") {
        f(arg1, arg2, flags)
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_CreateDeviceInfoList(num_devices: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(*mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_CreateDeviceInfoList\0") {
        let st = f(num_devices);
        let n = if !num_devices.is_null() { *num_devices } else { 0 };
        log_line(&format!("FT_CreateDeviceInfoList() -> count: {n}"));
        st
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetDeviceInfoList(dest: *mut c_void, num_devices: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(*mut c_void, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetDeviceInfoList\0") {
        f(dest, num_devices)
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetDeviceInfoDetail(
    index: u32,
    flags: *mut u32,
    device_type: *mut u32,
    id: *mut u32,
    loc_id: *mut u32,
    serial_number: *mut c_char,
    description: *mut c_char,
    handle: *mut FtHandle,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(u32, *mut u32, *mut u32, *mut u32, *mut u32, *mut c_char, *mut c_char, *mut FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetDeviceInfoDetail\0") {
        let st = f(index, flags, device_type, id, loc_id, serial_number, description, handle);
        if st == FT_OK {
            let ser = if !serial_number.is_null() { CStr::from_ptr(serial_number).to_string_lossy() } else { "".into() };
            let desc = if !description.is_null() { CStr::from_ptr(description).to_string_lossy() } else { "".into() };
            let vid_pid = if !id.is_null() { *id } else { 0 };
            log_line(&format!("FT_GetDeviceInfoDetail(idx: {index}) -> id: 0x{vid_pid:08X}, ser: \"{ser}\", desc: \"{desc}\""));
        } else {
            log_line(&format!("FT_GetDeviceInfoDetail(idx: {index}) -> status {st}"));
        }
        st
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetDeadmanTimeout(handle: FtHandle, timeout: u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetDeadmanTimeout\0") {
        f(handle, timeout)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetDriverVersion(handle: FtHandle, version: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetDriverVersion\0") {
        f(handle, version)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetLibraryVersion(version: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(*mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetLibraryVersion\0") {
        f(version)
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_Rescan() -> FtStatus {
    type FnType = unsafe extern "system" fn() -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Rescan\0") {
        f()
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_Reload(vid: u16, pid: u16) -> FtStatus {
    type FnType = unsafe extern "system" fn(u16, u16) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_Reload\0") {
        f(vid, pid)
    } else {
        FT_OTHER_ERROR
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetComPortNumber(handle: FtHandle, port_number: *mut i32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut i32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetComPortNumber\0") {
        f(handle, port_number)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetQueueStatusEx(handle: FtHandle, rx_bytes: *mut u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetQueueStatusEx\0") {
        f(handle, rx_bytes)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ResetPort(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ResetPort\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_CyclePort(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_CyclePort\0") {
        f(handle)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_IoCtl(
    handle: FtHandle,
    ioctl_code: u32,
    in_buf: *mut c_void,
    in_len: u32,
    out_buf: *mut c_void,
    out_len: u32,
    bytes_returned: *mut u32,
    overlapped: *mut c_void,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u32, *mut c_void, u32, *mut c_void, u32, *mut u32, *mut c_void) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_IoCtl\0") {
        f(handle, ioctl_code, in_buf, in_len, out_buf, out_len, bytes_returned, overlapped)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_GetDeviceInfo(
    handle: FtHandle,
    device_type: *mut u32,
    id: *mut u32,
    serial_number: *mut c_char,
    description: *mut c_char,
    dummy: *mut c_void,
) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32, *mut u32, *mut c_char, *mut c_char, *mut c_void) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_GetDeviceInfo\0") {
        f(handle, device_type, id, serial_number, description, dummy)
    } else {
        FT_INVALID_HANDLE
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_StopInTask(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_StopInTask\0") { f(handle) } else { FT_INVALID_HANDLE }
}

#[no_mangle]
pub unsafe extern "system" fn FT_RestartInTask(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_RestartInTask\0") { f(handle) } else { FT_INVALID_HANDLE }
}

#[no_mangle]
pub unsafe extern "system" fn FT_SetResetPipeRetryCount(handle: FtHandle, count: u32) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_SetResetPipeRetryCount\0") { f(handle, count) } else { FT_INVALID_HANDLE }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ComPortIdle(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ComPortIdle\0") { f(handle) } else { FT_INVALID_HANDLE }
}

#[no_mangle]
pub unsafe extern "system" fn FT_ComPortCancelIdle(handle: FtHandle) -> FtStatus {
    type FnType = unsafe extern "system" fn(FtHandle) -> FtStatus;
    if let Some(f) = get_proc::<FnType>(b"FT_ComPortCancelIdle\0") { f(handle) } else { FT_INVALID_HANDLE }
}

// ----------------------------------------------------------------------------
// Win32 Emulation (FT_W32_*) exports
// ----------------------------------------------------------------------------

#[no_mangle]
pub unsafe extern "system" fn FT_W32_CreateFile(
    name: *const c_char,
    access: u32,
    share_mode: u32,
    security: *mut c_void,
    create: u32,
    attrs: u32,
    template: *mut c_void,
) -> FtHandle {
    type FnType = unsafe extern "system" fn(*const c_char, u32, u32, *mut c_void, u32, u32, *mut c_void) -> FtHandle;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_CreateFile\0") {
        f(name, access, share_mode, security, create, attrs, template)
    } else {
        std::ptr::null_mut()
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_CloseHandle(handle: FtHandle) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_CloseHandle\0") { f(handle) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_ReadFile(
    handle: FtHandle,
    buf: *mut c_void,
    num_bytes: u32,
    bytes_read: *mut u32,
    overlapped: *mut c_void,
) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void, u32, *mut u32, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_ReadFile\0") {
        f(handle, buf, num_bytes, bytes_read, overlapped)
    } else {
        0
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_WriteFile(
    handle: FtHandle,
    buf: *const c_void,
    num_bytes: u32,
    bytes_written: *mut u32,
    overlapped: *mut c_void,
) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *const c_void, u32, *mut u32, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_WriteFile\0") {
        f(handle, buf, num_bytes, bytes_written, overlapped)
    } else {
        0
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetOverlappedResult(
    handle: FtHandle,
    overlapped: *mut c_void,
    bytes_transferred: *mut u32,
    wait: i32,
) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void, *mut u32, i32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetOverlappedResult\0") {
        f(handle, overlapped, bytes_transferred, wait)
    } else {
        0
    }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_ClearCommBreak(handle: FtHandle) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_ClearCommBreak\0") { f(handle) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_ClearCommError(handle: FtHandle, errors: *mut u32, stat: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_ClearCommError\0") { f(handle, errors, stat) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_EscapeCommFunction(handle: FtHandle, func: u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_EscapeCommFunction\0") { f(handle, func) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetCommModemStatus(handle: FtHandle, stat: *mut u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetCommModemStatus\0") { f(handle, stat) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetCommState(handle: FtHandle, dcb: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetCommState\0") { f(handle, dcb) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_SetCommState(handle: FtHandle, dcb: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_SetCommState\0") { f(handle, dcb) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetCommTimeouts(handle: FtHandle, timeouts: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetCommTimeouts\0") { f(handle, timeouts) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_SetCommTimeouts(handle: FtHandle, timeouts: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_SetCommTimeouts\0") { f(handle, timeouts) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetLastError(handle: FtHandle) -> u32 {
    type FnType = unsafe extern "system" fn(FtHandle) -> u32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetLastError\0") { f(handle) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_PurgeComm(handle: FtHandle, flags: u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_PurgeComm\0") { f(handle, flags) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_SetCommBreak(handle: FtHandle) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_SetCommBreak\0") { f(handle) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_SetCommMask(handle: FtHandle, mask: u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_SetCommMask\0") { f(handle, mask) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_GetCommMask(handle: FtHandle, mask: *mut u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_GetCommMask\0") { f(handle, mask) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_SetupComm(handle: FtHandle, in_q: u32, out_q: u32) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, u32, u32) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_SetupComm\0") { f(handle, in_q, out_q) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_WaitCommEvent(handle: FtHandle, mask: *mut u32, overlapped: *mut c_void) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle, *mut u32, *mut c_void) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_WaitCommEvent\0") { f(handle, mask, overlapped) } else { 0 }
}

#[no_mangle]
pub unsafe extern "system" fn FT_W32_CancelIo(handle: FtHandle) -> i32 {
    type FnType = unsafe extern "system" fn(FtHandle) -> i32;
    if let Some(f) = get_proc::<FnType>(b"FT_W32_CancelIo\0") { f(handle) } else { 0 }
}
