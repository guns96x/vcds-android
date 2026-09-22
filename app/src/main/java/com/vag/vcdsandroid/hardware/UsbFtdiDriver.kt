package com.vag.vcdsandroid.hardware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

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
     * Opens Ross-Tech-style 0403:FA24 interfaces using the transport setup recovered
     * from public live USB captures of the same interface family:
     * reset/open -> purge -> latency 1 ms -> 8N1 -> 9600 -> 19200 -> 115200 -> DTR/RTS clear.
     *
     * This only prepares the FTDI/cable link. It does not send ECU diagnostic traffic.
     */
    suspend fun openRossTechFa24(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!usbManager.hasPermission(usbDevice)) {
                return@withContext Result.failure(
                    SecurityException("Missing USB permission for ${usbDevice.deviceName}")
                )
            }

            val conn = usbManager.openDevice(usbDevice)
                ?: return@withContext Result.failure(IOException("usbManager.openDevice returned null"))
            connection = conn

            port.open(conn) // FTDI driver performs RESET_ALL and clears DTR/RTS on open.
            port.purgeHwBuffers(true, true)

            val ftdiPort = port as? FtdiSerialDriver.FtdiSerialPort
                ?: throw IOException("Expected FTDI serial port for 0403:FA24")
            ftdiPort.setLatencyTimer(1)

            for (baud in intArrayOf(9_600, 19_200, 115_200)) {
                port.setParameters(
                    baud,
                    8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
            }

            // Equivalent to FT_ClrDtr / FT_ClrRts.
            port.dtr = false
            port.rts = false

            isOpen = true
            Result.success(Unit)
        } catch (e: Exception) {
            close()
            Result.failure(e)
        }
    }

    /**
     * FTDI latency timer control used by intelligent HEX interfaces.
     * usb-serial-for-android exposes this on the concrete FTDI port.
     */
    suspend fun setLatencyTimerMs(latencyMs: Int): Boolean = withContext(Dispatchers.IO) {
        val ftdiPort = port as? FtdiSerialDriver.FtdiSerialPort ?: return@withContext false
        return@withContext try {
            ftdiPort.setLatencyTimer(latencyMs)
            true
        } catch (_: Exception) {
            false
        }
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
