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
import com.vag.vcdsandroid.protocol.KwpSlowInit
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

data class FiveBaudSlowInitResult(
    val success: Boolean,
    val address: Int,
    val syncByte: Int? = null,
    val keyByte1: Int? = null,
    val keyByte2: Int? = null,
    val addressComplement: Int? = null,
    val sessionBaud: Int = KwpSlowInit.PRIMARY_SESSION_BAUD,
    val failureStage: String? = null,
    val ignoredBeforeSync: ByteArray = byteArrayOf(),
    val w4SendDelayMs: Long? = null,
    val elapsedMs: Long = 0L
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
    /**
     * Opens a legacy Ross-Tech HEX interface as a plain K-Line pass-through and
     * verifies that the interface is really in dumb mode before any ECU request is sent.
     *
     * Verification follows the long-established legacy HEX/VCP behaviour used by
     * third-party KWP2000 tools: 10400 baud, 8N1, DTR asserted, RTS clear, then a
     * single 0xF0 byte must echo back unchanged. 0xF0 is deliberately not a KWP
     * start byte; this is only a cable/transceiver loopback check.
     *
     * On success the port stays OPEN at 10400 so Kwp2000DiagnosticEngine can
     * immediately perform the real 01-Engine init on the same handle.
     */
    fun connectDumbRossTech(targetDevice: UsbDevice? = null): Boolean {
        disconnect()

        var deviceToOpen = targetDevice ?: findAvailableDevice() ?: return false
        deviceToOpen = refreshDevice(deviceToOpen) ?: deviceToOpen

        if (deviceToOpen.vendorId != 0x0403 ||
            deviceToOpen.productId !in setOf(0xFA20, 0xFA24, 0xFA25)
        ) {
            android.util.Log.w(
                "VCDS_DUMB",
                "Dumb-mode probe refused for non-legacy Ross-Tech VID:PID=%04X:%04X"
                    .format(deviceToOpen.vendorId, deviceToOpen.productId)
            )
            return false
        }

        if (!usbManager.hasPermission(deviceToOpen)) {
            android.util.Log.w("VCDS_DUMB", "No USB permission for dumb-mode probe")
            return false
        }

        currentDevice = deviceToOpen
        val prober = createProber()
        val serialDriver = prober.probeDevice(deviceToOpen) ?: FtdiSerialDriver(deviceToOpen)
        if (serialDriver.ports.isEmpty()) return false

        val connection = try {
            usbManager.openDevice(serialDriver.device)
        } catch (e: Exception) {
            android.util.Log.e("VCDS_DUMB", "openDevice failed: ${e.message}")
            null
        } ?: return false

        val port = serialDriver.ports[0]
        try {
            port.open(connection)
            port.setParameters(
                KLINE_BAUD_RATE,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )

            if (port is FtdiSerialDriver.FtdiSerialPort) {
                try {
                    port.setLatencyTimer(2)
                } catch (e: Exception) {
                    android.util.Log.w("VCDS_DUMB", "Could not set FTDI latency: ${e.message}")
                }
            }

            // Legacy HEX dumb/VCP mode: DTR asserted enables receive, RTS stays clear.
            port.dtr = true
            port.rts = false
            try { port.setBreak(false) } catch (_: Exception) {}
            port.purgeHwBuffers(true, true)

            val marker = byteArrayOf(0xF0.toByte())
            port.write(marker, 100)
            val echo = ByteArray(8)
            val count = try {
                port.read(echo, 150)
            } catch (_: Exception) {
                0
            }

            val confirmed = count > 0 && echo[0] == marker[0]
            val echoHex = if (count > 0) {
                echo.take(count).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            } else {
                "(none)"
            }
            android.util.Log.i(
                "VCDS_DUMB",
                "FA24 dumb echo: confirmed=$confirmed count=$count echo=$echoHex"
            )

            port.purgeHwBuffers(true, true)

            if (!confirmed) {
                try { port.close() } catch (_: Exception) {}
                try { connection.close() } catch (_: Exception) {}
                currentDevice = null
                return false
            }

            serialPort = port
            isPortOpen = true
            android.util.Log.i(
                "VCDS_DUMB",
                "K-LINE RAW ECHO DETECTED at $KLINE_BAUD_RATE baud; ECU slow-init still required"
            )
            return true
        } catch (e: Exception) {
            android.util.Log.e("VCDS_DUMB", "Dumb-mode probe failed: ${e.message}")
            try { port.close() } catch (_: Exception) {}
            try { connection.close() } catch (_: Exception) {}
            serialPort = null
            isPortOpen = false
            currentDevice = null
            return false
        }
    }


    /**
     * Performs the ISO 9141 / ISO 14230 five-baud wake-up on an already-open
     * transparent K-Line port.
     *
     * This implements the missing connection primitive proven necessary by the
     * user's own real-car trace (ELM reports ISO 14230-4 / KWP 5BAUD):
     *  - address is emitted as 7O1 using BREAK line levels at 200 ms/bit;
     *  - RX garbage/echo caused by BREAK transitions is ignored until ECU sync 0x55;
     *  - exactly two key bytes are collected;
     *  - the complement of key byte 2 is sent inside the W4 25-50 ms window;
     *  - tester echo is tolerated while waiting for the ECU address complement.
     *
     * No diagnostic service is sent here. A successful result proves the physical
     * ECU at [address] completed the slow-init handshake.
     */
    fun performFiveBaudSlowInit(address: Int = 0x01): FiveBaudSlowInitResult = synchronized(ioLock) {
        val port = serialPort ?: return@synchronized FiveBaudSlowInitResult(
            success = false,
            address = address,
            failureStage = "PORT_NOT_OPEN"
        )

        val startedNs = System.nanoTime()
        val ignored = ArrayList<Byte>(16)

        fun elapsedMs(): Long = (System.nanoTime() - startedNs) / 1_000_000L

        fun failure(
            stage: String,
            sync: Int? = null,
            key1: Int? = null,
            key2: Int? = null,
            w4: Long? = null
        ): FiveBaudSlowInitResult {
            try { port.setBreak(false) } catch (_: Exception) {}
            return FiveBaudSlowInitResult(
                success = false,
                address = address,
                syncByte = sync,
                keyByte1 = key1,
                keyByte2 = key2,
                sessionBaud = KwpSlowInit.PRIMARY_SESSION_BAUD,
                failureStage = stage,
                ignoredBeforeSync = ignored.toByteArray(),
                w4SendDelayMs = w4,
                elapsedMs = elapsedMs()
            )
        }

        fun waitUntilNs(deadlineNs: Long) {
            while (true) {
                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) return

                // Sleep while there is comfortable headroom, then yield/spin near
                // the edge. The 200 ms address bits tolerate scheduler jitter; W4
                // is handled separately with the same monotonic clock.
                if (remainingNs > 3_000_000L) {
                    val sleepMs = (remainingNs / 1_000_000L - 1L).coerceAtLeast(1L)
                    Thread.sleep(sleepMs)
                } else {
                    Thread.yield()
                }
            }
        }

        fun readOneUntil(deadlineNs: Long): Int? {
            val one = ByteArray(1)
            while (System.nanoTime() < deadlineNs) {
                val remainingMs = ((deadlineNs - System.nanoTime()) / 1_000_000L)
                    .coerceAtLeast(1L)
                    .coerceAtMost(5L)
                    .toInt()
                val n = try {
                    port.read(one, remainingMs)
                } catch (_: Exception) {
                    0
                }
                if (n > 0) return one[0].toInt() and 0xFF
            }
            return null
        }

        try {
            if (address !in 0..0x7F) return@synchronized failure("INVALID_7BIT_ADDRESS")

            // Incoming sync/key bytes use the normal session UART parameters.
            port.setParameters(
                KwpSlowInit.PRIMARY_SESSION_BAUD,
                8,
                UsbSerialPort.STOPBITS_1,
                UsbSerialPort.PARITY_NONE
            )
            port.dtr = true
            port.rts = false
            port.setBreak(false)
            port.purgeHwBuffers(true, true)

            // ISO W0: bus idle/high before starting the five-baud address.
            val idleUntil = System.nanoTime() + 5_000_000L
            waitUntilNs(idleUntil)

            val bits = KwpSlowInit.addressBits7O1(address)
            var bitEndNs = System.nanoTime()

            // Start + D0..D6 + parity: each is held for a full 200 ms.
            // The stop level stays HIGH while we immediately begin W1 sync wait.
            for (i in 0 until bits.lastIndex) {
                port.setBreak(!bits[i]) // BREAK asserted = K-Line LOW
                bitEndNs += KwpSlowInit.BIT_TIME_MS * 1_000_000L
                waitUntilNs(bitEndNs)
            }
            port.setBreak(false) // stop / idle HIGH

            // Do NOT purge here: ECU 0x55 may already be on its way. BREAK
            // transitions can create local echo bytes, so scan until real sync.
            val syncDeadline = System.nanoTime() + KwpSlowInit.W1_SYNC_MAX_MS * 1_000_000L
            var sync: Int? = null
            while (System.nanoTime() < syncDeadline) {
                val b = readOneUntil(syncDeadline) ?: break
                if (b == 0x55) {
                    sync = b
                    break
                }
                if (ignored.size < 32) ignored.add(b.toByte())
            }
            if (sync != 0x55) return@synchronized failure("WAIT_SYNC_55")

            // Read one byte at a time so no key byte is accidentally consumed in
            // the same USB read as the sync byte. Low FTDI latency is essential.
            val key1 = readOneUntil(
                System.nanoTime() + (KwpSlowInit.W2_KEY1_MAX_MS + 10L) * 1_000_000L
            ) ?: return@synchronized failure("WAIT_KEY1", sync = sync)

            val key2 = readOneUntil(
                System.nanoTime() + (KwpSlowInit.W3_KEY2_MAX_MS + 10L) * 1_000_000L
            ) ?: return@synchronized failure("WAIT_KEY2", sync = sync, key1 = key1)

            // W4 is the critical part. Do no logging/string formatting here.
            val lastKeyReadNs = System.nanoTime()
            val earliestComplementNs =
                lastKeyReadNs + KwpSlowInit.W4_COMPLEMENT_MIN_MS * 1_000_000L
            val latestComplementNs =
                lastKeyReadNs + KwpSlowInit.W4_COMPLEMENT_MAX_MS * 1_000_000L

            waitUntilNs(earliestComplementNs)
            if (System.nanoTime() > latestComplementNs) {
                return@synchronized failure(
                    "W4_MISSED",
                    sync = sync,
                    key1 = key1,
                    key2 = key2,
                    w4 = (System.nanoTime() - lastKeyReadNs) / 1_000_000L
                )
            }

            val keyComplement = KwpSlowInit.byteComplement(key2)
            port.write(byteArrayOf(keyComplement.toByte()), 50)
            val w4DelayMs = (System.nanoTime() - lastKeyReadNs) / 1_000_000L

            // Dumb K-Line adapters echo our own byte. Ignore that echo and any
            // unrelated transition byte until the ECU returns ~address.
            val expectedAddressComplement = KwpSlowInit.expectedAddressComplement(address)
            val complementDeadline =
                System.nanoTime() + KwpSlowInit.ADDRESS_COMPLEMENT_TIMEOUT_MS * 1_000_000L
            var ecuComplement: Int? = null
            while (System.nanoTime() < complementDeadline) {
                val b = readOneUntil(complementDeadline) ?: break
                if (b == expectedAddressComplement) {
                    ecuComplement = b
                    break
                }
                // keyComplement is normally the local tester echo; ignore it.
            }

            if (ecuComplement != expectedAddressComplement) {
                return@synchronized failure(
                    "WAIT_ADDRESS_COMPLEMENT",
                    sync = sync,
                    key1 = key1,
                    key2 = key2,
                    w4 = w4DelayMs
                )
            }

            FiveBaudSlowInitResult(
                success = true,
                address = address,
                syncByte = sync,
                keyByte1 = key1,
                keyByte2 = key2,
                addressComplement = ecuComplement,
                sessionBaud = KwpSlowInit.PRIMARY_SESSION_BAUD,
                ignoredBeforeSync = ignored.toByteArray(),
                w4SendDelayMs = w4DelayMs,
                elapsedMs = elapsedMs()
            )
        } catch (e: Exception) {
            android.util.Log.e("VCDS_SLOW_INIT", "Five-baud init exception: ${e.message}")
            failure("EXCEPTION_${e.javaClass.simpleName}")
        }
    }

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
