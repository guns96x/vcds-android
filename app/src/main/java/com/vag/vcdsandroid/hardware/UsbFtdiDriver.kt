package com.vag.vcdsandroid.hardware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware driver for FTDI USB-to-UART bridges (FT232R, FT232BM, FT2232, etc.).
 * Provides standard serial port communication with DTR/RTS modem control and FIFO purging.
 *
 * Note: Downstream pin wiring (e.g. DTR tied to MCU reset or transceiver enable)
 * is board-specific and must not be assumed for generic FTDI devices.
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
                port.dtr = parameters.dtr
                port.rts = parameters.rts
            } catch (_: Exception) {}
        }
        res
    }

    /**
     * Utility to pulse the DTR modem line for hardware boards that wire DTR#
     * to an external reset or control circuit.
     */
    suspend fun pulseDtr(assertDurationMs: Long = 50, recoveryMs: Long = 200) = withContext(Dispatchers.IO) {
        if (!isConnected) return@withContext
        try {
            setDtr(true)
            kotlinx.coroutines.delay(assertDurationMs)
            setDtr(false)
            kotlinx.coroutines.delay(recoveryMs)
        } catch (_: Exception) {}
    }
}
