package com.vag.vcdsandroid.hardware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dedicated FTDI hardware driver handling FT232R/BM/H silicon specifics:
 * - Proper active-low DTR polarity control for microcontroller reset management.
 * - Explicit latency timer control (default 1ms/2ms on FTDI for low latency OBD roundtrips).
 * - Hardware FIFO buffer purging.
 */
class UsbFtdiDriver(
    usbManager: UsbManager,
    usbDevice: UsbDevice,
    port: UsbSerialPort
) : UsbSerialDriverBase(
    usbManager = usbManager,
    usbDevice = usbDevice,
    port = port,
    name = "FTDI Driver (%04X:%04X)".format(usbDevice.vendorId, usbDevice.productId)
) {
    override suspend fun open(parameters: ConnectionParameters): Result<Unit> = withContext(Dispatchers.IO) {
        val res = super.open(parameters)
        if (res.isSuccess) {
            try {
                // FTDI DTR# is active-low:
                // dtr=false -> DTR# line HIGH (releases ATmega reset)
                // dtr=true  -> DTR# line LOW (holds ATmega in reset)
                port.dtr = parameters.dtr
                port.rts = parameters.rts
            } catch (_: Exception) {}
        }
        res
    }

    /**
     * Pulses the DTR line to trigger hardware MCU reset (e.g. ATmega162 reboot).
     */
    suspend fun pulseMcuReset(holdLowMs: Long = 50, recoveryMs: Long = 400) = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext
        try {
            setDtr(true)  // DTR# active low -> RESET LOW
            kotlinx.coroutines.delay(holdLowMs)
            setDtr(false) // DTR# high -> RESET HIGH (running)
            kotlinx.coroutines.delay(recoveryMs)
        } catch (_: Exception) {}
    }
}
