package com.vag.vcdsandroid.usb

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProbeTable
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

/**
 * Information describing a detected diagnostic USB adapter.
 */
data class AdapterInfo(
    val device: UsbDevice,
    val displayName: String,
    val isRossTechIntelligent: Boolean,
    val vidPidHex: String
)

/**
 * Low-level USB OTG transport wrapper for K-Line / KWP2000 communication.
 * Supports Ross-Tech VCDS (FTDI 0xFA24/0xFA20/0xFA23), standard FTDI (FT232R/BM),
 * CH340, CP2102, and Prolific USB adapters.
 */
class UsbKwpTransport(private val context: Context) {

    companion object {
        const val ACTION_USB_PERMISSION = "com.vag.vcdsandroid.USB_PERMISSION"
        const val KLINE_BAUD_RATE = 10400
        const val FAST_BAUD_RATE = 38400
        const val DEFAULT_TIMEOUT_MS = 250

        fun createProber(): UsbSerialProber {
            val table = UsbSerialProber.getDefaultProbeTable()
            // Ross-Tech VCDS / HEX-USB+CAN PIDs
            table.addProduct(0x0403, 0xFA24, FtdiSerialDriver::class.java) // HEX-USB+CAN / Dual-K & CAN
            table.addProduct(0x0403, 0xFA20, FtdiSerialDriver::class.java) // HEX-USB
            table.addProduct(0x0403, 0xFA23, FtdiSerialDriver::class.java) // Micro-CAN
            table.addProduct(0x0403, 0xFA25, FtdiSerialDriver::class.java) // HEX-COM
            table.addProduct(0x0403, 0xFA30, FtdiSerialDriver::class.java) // HEX-NET
            table.addProduct(0x0403, 0x6001, FtdiSerialDriver::class.java) // FT232R / KKL
            table.addProduct(0x0403, 0x6010, FtdiSerialDriver::class.java) // FT2232
            table.addProduct(0x0403, 0x6011, FtdiSerialDriver::class.java) // FT4232
            table.addProduct(0x0403, 0x6014, FtdiSerialDriver::class.java) // FT232H
            table.addProduct(0x1A86, 0x7523, Ch34xSerialDriver::class.java) // CH340
            table.addProduct(0x1A86, 0x5523, Ch34xSerialDriver::class.java) // CH341
            table.addProduct(0x10C4, 0xEA60, Cp21xxSerialDriver::class.java) // CP2102
            table.addProduct(0x067B, 0x2303, ProlificSerialDriver::class.java) // PL2303
            return UsbSerialProber(table)
        }

        fun identifyDevice(device: UsbDevice): AdapterInfo {
            val vid = device.vendorId
            val pid = device.productId
            val vidPidHex = String.format("%04X:%04X", vid, pid)

            val (name, isRossTech) = when {
                vid == 0x0403 && pid == 0xFA24 -> "Ross-Tech HEX-USB+CAN (Вася)" to true
                vid == 0x0403 && pid == 0xFA20 -> "Ross-Tech HEX-USB" to true
                vid == 0x0403 && pid == 0xFA23 -> "Ross-Tech Micro-CAN" to true
                vid == 0x0403 && pid == 0xFA25 -> "Ross-Tech HEX-COM" to true
                vid == 0x0403 && pid == 0xFA30 -> "Ross-Tech HEX-NET" to true
                vid == 0x0403 && pid == 0x6001 -> "FTDI FT232R KKL Cable" to false
                vid == 0x0403 -> "FTDI Serial Cable (${device.productName ?: vidPidHex})" to false
                vid == 0x1A86 && pid == 0x7523 -> "QinHeng CH340 KKL Cable" to false
                vid == 0x1A86 -> "QinHeng CH34x Adapter" to false
                vid == 0x10C4 -> "Silicon Labs CP210x Adapter" to false
                vid == 0x067B -> "Prolific PL2303 Adapter" to false
                else -> (device.productName ?: "USB Serial Device ($vidPidHex)") to false
            }

            return AdapterInfo(
                device = device,
                displayName = name,
                isRossTechIntelligent = isRossTech,
                vidPidHex = vidPidHex
            )
        }
    }

    val usbManager: UsbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var currentDevice: UsbDevice? = null
    private var isPortOpen = false

    fun isConnected(): Boolean = isPortOpen && serialPort != null

    fun getActiveAdapterInfo(): AdapterInfo? {
        val dev = currentDevice ?: findAvailableDevice() ?: return null
        return identifyDevice(dev)
    }

    fun findAvailableDevice(): UsbDevice? {
        val prober = createProber()
        val availableDrivers = prober.findAllDrivers(usbManager)
        if (availableDrivers.isNotEmpty()) {
            return availableDrivers[0].device
        }
        // Direct scan of all USB devices attached to phone
        for (device in usbManager.deviceList.values) {
            if (device.vendorId == 0x0403 || device.vendorId == 0x1A86 ||
                device.vendorId == 0x10C4 || device.vendorId == 0x067B) {
                return device
            }
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
        // Explicit package is required for Android 14+ with RECEIVER_NOT_EXPORTED
        val intent = Intent(ACTION_USB_PERMISSION).apply {
            setPackage(context.packageName)
        }
        val permissionIntent = PendingIntent.getBroadcast(
            context, 0, intent, flags
        )
        usbManager.requestPermission(device, permissionIntent)
    }

    fun connect(targetDevice: UsbDevice? = null, baudRate: Int = KLINE_BAUD_RATE): Boolean {
        val deviceToOpen = targetDevice ?: findAvailableDevice() ?: return false
        currentDevice = deviceToOpen

        val prober = createProber()
        val driver: UsbSerialDriver = prober.probeDevice(deviceToOpen) ?: run {
            when (deviceToOpen.vendorId) {
                0x0403 -> FtdiSerialDriver(deviceToOpen)
                0x1A86 -> Ch34xSerialDriver(deviceToOpen)
                0x10C4 -> Cp21xxSerialDriver(deviceToOpen)
                0x067B -> ProlificSerialDriver(deviceToOpen)
                else -> return false
            }
        }

        val connection = usbManager.openDevice(driver.device) ?: return false

        if (driver.ports.isEmpty()) {
            try {
                connection.close()
            } catch (_: Exception) {}
            return false
        }

        val port = driver.ports[0]
        try {
            port.open(connection)
            port.setParameters(
                baudRate,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.dtr = false
            port.rts = false
            serialPort = port
            isPortOpen = true
            return true
        } catch (e: Exception) {
            try {
                port.close()
            } catch (_: Exception) {}
            try {
                connection.close()
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
        currentDevice = null
    }

    /**
     * Sends Fast Init 25ms Break followed by 25ms Mark pulse to wake up EDC16 ECU.
     * Uses precise millisecond timing loop instead of coarse Thread.sleep.
     */
    fun sendFastInitPulse() {
        val port = serialPort ?: return
        try {
            port.setBreak(true)
            val breakStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - breakStart < 25) {
                Thread.yield()
            }
            port.setBreak(false)
            val markStart = System.currentTimeMillis()
            while (System.currentTimeMillis() - markStart < 25) {
                Thread.yield()
            }
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
