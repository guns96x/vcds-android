package com.vag.vcdsandroid.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * Low-level USB OTG transport wrapper for K-Line / KWP2000 communication.
 * Supports FTDI (FT232R/BM), CH340, CP2102, and Prolific USB adapters.
 */
class UsbKwpTransport(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.vag.vcdsandroid.USB_PERMISSION"
        const val KLINE_BAUD_RATE = 10400
        const val FAST_BAUD_RATE = 38400
        const val DEFAULT_TIMEOUT_MS = 250
    }

    private val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var isPortOpen = false

    fun isConnected(): Boolean = isPortOpen && serialPort != null

    fun findAvailableDevice(): UsbDevice? {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            return availableDrivers[0].device
        }
        return null
    }

    fun hasPermission(device: UsbDevice): Boolean {
        return usbManager.hasPermission(device)
    }

    fun requestPermission(device: UsbDevice) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun connect(baudRate: Int = KLINE_BAUD_RATE): Boolean {
        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)
        if (availableDrivers.isEmpty()) {
            return false
        }

        val driver: UsbSerialDriver = availableDrivers[0]
        val connection = usbManager.openDevice(driver.device) ?: return false

        val port = driver.ports[0]
        try {
            port.open(connection)
            port.setParameters(
                baudRate,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.dtr = true
            port.rts = true
            serialPort = port
            isPortOpen = true
            return true
        } catch (e: Exception) {
            try {
                port.close()
            } catch (_: Exception) {}
            isPortOpen = false
            serialPort = null
            return false
        }
    }

    fun disconnect() {
        try {
            serialPort?.close()
        } catch (_: Exception) {}
        serialPort = null
        isPortOpen = false
    }

    /**
     * Sends Fast Init 25ms Break followed by 25ms Mark pulse to wake up EDC16 ECU.
     */
    fun sendFastInitPulse() {
        val port = serialPort ?: return
        try {
            port.setBreak(true)
            Thread.sleep(25)
            port.setBreak(false)
            Thread.sleep(25)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun write(data: ByteArray) {
        val port = serialPort ?: throw IOException("USB port is not open")
        port.write(data, DEFAULT_TIMEOUT_MS)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int {
        val port = serialPort ?: throw IOException("USB port is not open")
        return port.read(buffer, timeoutMs)
    }

    fun purge() {
        val port = serialPort ?: return
        try {
            port.purgeHwBuffers(true, true)
        } catch (_: Exception) {}
    }
}
