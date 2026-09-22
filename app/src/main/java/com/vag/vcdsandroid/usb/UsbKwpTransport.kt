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
    private var bridgeServer: TcpBridgeServer? = null

    fun isConnected(): Boolean = isPortOpen && serialPort != null
    fun isBridgeActive(): Boolean = bridgeServer?.isBridgeActive == true

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

    fun connect(targetDevice: UsbDevice? = null, baudRate: Int? = null): Boolean {
        var deviceToOpen = targetDevice ?: findAvailableDevice() ?: return false

        // Re-find the device from current deviceList by VID:PID to avoid stale
        // /dev/bus/usb paths after Samsung USB chooser dialog remounts the device
        deviceToOpen = refreshDevice(deviceToOpen) ?: deviceToOpen
        currentDevice = deviceToOpen

        val info = identifyDevice(deviceToOpen)
        val initialBaud = baudRate ?: if (info.isRossTechIntelligent) 500000 else KLINE_BAUD_RATE

        android.util.Log.i("VCDS_USB", "connect(): VID:PID=${info.vidPidHex} name=${info.displayName} devName=${deviceToOpen.deviceName}")

        val prober = createProber()
        // Re-probe with fresh device reference
        val driver: UsbSerialDriver = prober.probeDevice(deviceToOpen) ?: run {
            when (deviceToOpen.vendorId) {
                0x0403 -> FtdiSerialDriver(deviceToOpen)
                0x1A86 -> Ch34xSerialDriver(deviceToOpen)
                0x10C4 -> Cp21xxSerialDriver(deviceToOpen)
                0x067B -> ProlificSerialDriver(deviceToOpen)
                else -> return false
            }
        }

        // Check permission before trying to open
        if (!usbManager.hasPermission(driver.device)) {
            android.util.Log.w("VCDS_USB", "No USB permission for ${driver.device.deviceName}")
            return false
        }

        val connection = try {
            usbManager.openDevice(driver.device)
        } catch (e: Exception) {
            android.util.Log.e("VCDS_USB", "openDevice() failed: ${e.message}, retrying with fresh scan...")
            // One more attempt: re-scan and get completely fresh UsbDevice
            val freshDev = findAvailableDevice() ?: return false
            currentDevice = freshDev
            val freshDriver = prober.probeDevice(freshDev) ?: FtdiSerialDriver(freshDev)
            try {
                usbManager.openDevice(freshDriver.device)
            } catch (e2: Exception) {
                android.util.Log.e("VCDS_USB", "openDevice() retry also failed: ${e2.message}")
                null
            }
        }

        if (connection == null) {
            android.util.Log.e("VCDS_USB", "openDevice() returned null")
            return false
        }

        // Re-check driver ports from the fresh device if we re-scanned
        val finalDriver = prober.probeDevice(currentDevice!!) ?: run {
            when (currentDevice!!.vendorId) {
                0x0403 -> FtdiSerialDriver(currentDevice!!)
                0x1A86 -> Ch34xSerialDriver(currentDevice!!)
                0x10C4 -> Cp21xxSerialDriver(currentDevice!!)
                0x067B -> ProlificSerialDriver(currentDevice!!)
                else -> {
                    try { connection.close() } catch (_: Exception) {}
                    return false
                }
            }
        }

        if (finalDriver.ports.isEmpty()) {
            try {
                connection.close()
            } catch (_: Exception) {}
            return false
        }

        val port = finalDriver.ports[0]
        try {
            port.open(connection)
            port.setParameters(
                initialBaud,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            // FT232R: DTR# pin is ACTIVE LOW output.
            // setDtr(true) → DTR# LOW → ATmega162 RESET LOW → MCU in reset!
            // setDtr(false) → DTR# HIGH → RESET HIGH → MCU runs!
            port.dtr = false
            port.rts = false
            serialPort = port
            isPortOpen = true
            android.util.Log.i("VCDS_USB", "Serial port opened OK at $initialBaud baud, DTR=false (MCU reset released)")

            // FTDI latency timer: reduce default 16ms buffer delay to 1ms for high-speed diagnostic response
            if (deviceToOpen.vendorId == 0x0403) {
                try {
                    val res = connection.controlTransfer(0x40, 0x09, 1, 1, null, 0, 500)
                    android.util.Log.i("VCDS_USB", "FTDI Latency Timer set to 1ms (result=$res)")
                } catch (e: Exception) {
                    android.util.Log.w("VCDS_USB", "Could not set FTDI latency timer: ${e.message}")
                }
            }

            // Start TCP Bridge server on 127.0.0.1:9999
            if (bridgeServer == null) {
                bridgeServer = TcpBridgeServer(this).also { it.start() }
            }

            return true
        } catch (e: Exception) {
            android.util.Log.e("VCDS_USB", "Port open failed: ${e.message}")
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

    /**
     * Re-find a USB device from the current system device list by matching VID:PID.
     * This is critical on Samsung devices where the /dev/bus/usb path changes
     * after the USB chooser dialog remounts the device.
     */
    private fun refreshDevice(stale: UsbDevice): UsbDevice? {
        for (dev in usbManager.deviceList.values) {
            if (dev.vendorId == stale.vendorId && dev.productId == stale.productId) {
                if (dev.deviceName != stale.deviceName) {
                    android.util.Log.w("VCDS_USB", "USB device path changed: ${stale.deviceName} -> ${dev.deviceName}")
                }
                return dev
            }
        }
        return null
    }

    fun setBaudRate(newBaudRate: Int): Boolean {
        val port = serialPort ?: return false
        return try {
            port.setParameters(newBaudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setDtr(state: Boolean) {
        try {
            serialPort?.dtr = state
        } catch (_: Exception) {}
    }

    fun setRts(state: Boolean) {
        try {
            serialPort?.rts = state
        } catch (_: Exception) {}
    }

    fun disconnect() {
        try {
            bridgeServer?.stop()
            bridgeServer = null
        } catch (_: Exception) {}
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

    private val ioLock = Any()

    fun write(data: ByteArray) = synchronized(ioLock) {
        val port = serialPort ?: throw IOException("USB port is not open")
        port.write(data, DEFAULT_TIMEOUT_MS)
    }

    fun read(buffer: ByteArray, timeoutMs: Int = DEFAULT_TIMEOUT_MS): Int = synchronized(ioLock) {
        val port = serialPort ?: return 0
        try {
            port.read(buffer, timeoutMs)
        } catch (e: IOException) {
            // In usb-serial-for-android, a read timeout (no bytes ready) throws IOException.
            // This is NORMAL in serial communication — return 0 bytes read.
            0
        } catch (e: Exception) {
            0
        }
    }

    fun purge() = synchronized(ioLock) {
        val port = serialPort ?: return
        try {
            port.purgeHwBuffers(true, true)
        } catch (_: Exception) {}
    }
}
