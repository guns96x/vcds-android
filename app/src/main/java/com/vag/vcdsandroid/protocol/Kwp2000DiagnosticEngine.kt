package com.vag.vcdsandroid.protocol

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import com.vag.vcdsandroid.model.FaultCode
import com.vag.vcdsandroid.model.MeasuringGroup
import com.vag.vcdsandroid.usb.FiveBaudSlowInitResult
import com.vag.vcdsandroid.usb.UsbKwpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.sin

enum class DiagState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    POLLING,
    ERROR
}

enum class TransportMode {
    USB_HARDWARE,
    SIMULATOR_DEMO
}

class Kwp2000DiagnosticEngine(
    private val context: Context,
    private val transport: UsbKwpTransport
) {
    var state: DiagState = DiagState.DISCONNECTED
        private set
    var mode: TransportMode = TransportMode.USB_HARDWARE
        private set
    var lastError: String? = null
        private set
    var lastEcuIdentityPayload: ByteArray? = null
        private set
    var lastEcuVerificationPayload: ByteArray? = null
        private set
    var lastSlowInitResult: FiveBaudSlowInitResult? = null
        private set

    private var targetEcuAddress: Byte = 0x01
    private val commMutex = Mutex()
    private val dtcLookup = HashMap<String, Pair<String, String>>()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var keepAliveJob: Job? = null
    @Volatile private var lastDiagnosticActivityNs: Long = 0L

    private companion object {
        /**
         * KWP2000 physical address of the diagnostic tester (us). Any received
         * frame whose SOURCE byte equals this is our own K-Line transmission
         * echoed back by the interface, never a reply from the ECU.
         */
        const val TESTER_ADDRESS = 0xF1
    }

    // Mock engine state for simulation mode
    private var mockRpm = 1400.0
    private var mockSimTime = 0.0
    private var mockWotActive = true

    init {
        loadDtcDatabase()
    }

    private fun loadDtcDatabase() {
        try {
            val stream = context.assets.open("dtc_codes.json")
            val reader = InputStreamReader(stream)
            val jsonStr = reader.readText()
            reader.close()

            val root = JSONObject(jsonStr)
            val keys = root.keys()
            while (keys.hasNext()) {
                val code = keys.next()
                val item = root.getJSONObject(code)
                val en = item.optString("en", "Unknown fault code")
                val uk = item.optString("uk", "Невідомий код несправності")
                dtcLookup[code] = Pair(en, uk)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setMode(newMode: TransportMode) {
        mode = newMode
        if (state != DiagState.DISCONNECTED) {
            disconnect()
        }
    }

    suspend fun connect(
        targetDevice: UsbDevice? = null,
        targetAddress: Byte = 0x01,
        allowRossTechDumbMode: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        commMutex.withLock {
            targetEcuAddress = targetAddress
            state = DiagState.CONNECTING
            lastError = null
            lastEcuIdentityPayload = null
            lastEcuVerificationPayload = null
            lastSlowInitResult = null
            stopKeepAlive()

            if (mode == TransportMode.SIMULATOR_DEMO) {
                delay(400) // Emulate fast init delay
                state = DiagState.CONNECTED
                return@withContext true
            }

            // Auto-retry loop: Samsung S24 FE can kill OTG VBUS mid-operation
            for (attempt in 1..3) {
                try {
                    Log.i("VCDS_PROBE", "=== Connection attempt $attempt/3 ===")

                    // Derive adapter identity from targetDevice or available device before connecting
                    val effectiveDevice = targetDevice ?: transport.findAvailableDevice()
                    val targetInfo = effectiveDevice?.let { UsbKwpTransport.identifyDevice(it) }
                    val isTargetRossTech = targetInfo?.isRossTechIntelligent == true
                    val initialBaud = UsbKwpTransport.KLINE_BAUD_RATE

                    // Smart FA24 traffic has its own proven S/M transport and must not be
                    // opened here with guessed UART settings. MainActivity handles that path
                    // through HexB03Adapter. This engine is used only after dumb K-Line is requested.
                    if (isTargetRossTech && !allowRossTechDumbMode) {
                        lastError = "Ross-Tech smart mode active; direct K-Line was not requested."
                        state = DiagState.ERROR
                        return@withContext false
                    }

                    // Reconnect if port died
                    if (!transport.isConnected()) {
                        // On retry, disconnect cleanly first to release stale handles
                        if (attempt > 1) {
                            Log.i("VCDS_PROBE", "Reconnecting USB (attempt $attempt)...")
                            try { transport.disconnect() } catch (_: Exception) {}
                            delay(500) // Let Samsung re-enumerate USB
                        }
                        val opened = if (isTargetRossTech && allowRossTechDumbMode) {
                            transport.connectDumbRossTech(effectiveDevice)
                        } else {
                            transport.connect(targetDevice, initialBaud)
                        }
                        if (!opened) {
                            lastError = if (isTargetRossTech && allowRossTechDumbMode) {
                                "Legacy HEX direct K-Line serial path could not be opened."
                            } else {
                                "Cannot open USB Serial Port. Check OTG cable & permission."
                            }
                            if (attempt < 3) {
                                delay(1000)
                                continue
                            }
                            state = DiagState.ERROR
                            return@withContext false
                        }
                    }

                    val connectedInfo = transport.getActiveAdapterInfo()
                    val isRossTech = isTargetRossTech || connectedInfo?.isRossTechIntelligent == true

                    if (isRossTech && allowRossTechDumbMode) {
                        // Ground truth from this exact car says ISO 14230-4 / KWP 5BAUD.
                        // Do not try direct StartCommunication or fast-init first: those can
                        // disturb the ECU timing state and make the known-good slow init fail.
                        if (attempt > 1) {
                            Log.i(
                                "VCDS_SLOW_INIT",
                                "Quiet gap before slow-init retry: ${KwpSlowInit.RETRY_QUIET_MS} ms"
                            )
                            delay(KwpSlowInit.RETRY_QUIET_MS)
                        }

                        val slow = transport.performFiveBaudSlowInit(targetEcuAddress.toInt() and 0xFF)
                        lastSlowInitResult = slow

                        val ignoredHex = slow.ignoredBeforeSync.joinToString(" ") {
                            "%02X".format(it.toInt() and 0xFF)
                        }
                        Log.i(
                            "VCDS_SLOW_INIT",
                            "attempt=$attempt success=${slow.success} stage=${slow.failureStage ?: "OK"} " +
                                "sync=${slow.syncByte?.let { "%02X".format(it) } ?: "--"} " +
                                "kb1=${slow.keyByte1?.let { "%02X".format(it) } ?: "--"} " +
                                "kb2=${slow.keyByte2?.let { "%02X".format(it) } ?: "--"} " +
                                "addrComp=${slow.addressComplement?.let { "%02X".format(it) } ?: "--"} " +
                                "w4=${slow.w4SendDelayMs ?: -1}ms elapsed=${slow.elapsedMs}ms " +
                                "ignored=[$ignoredHex]"
                        )

                        if (slow.success) {
                            // The five-baud handshake proves that something answered the
                            // initialization sequence, but it is not yet enough to claim
                            // "01-Engine connected". Require one checksum-valid KWP frame
                            // whose source address is exactly the requested ECU.
                            delay(55) // P3 minimum before first tester request.

                            val verification = readEcuIdentificationRaw(targetEcuAddress)
                            lastEcuVerificationPayload = verification
                            lastEcuIdentityPayload = verification?.takeIf {
                                it.isNotEmpty() &&
                                    (it[0] == 0x5A.toByte() || it[0] == 0x61.toByte())
                            }

                            if (verification != null) {
                                noteDiagnosticActivity()
                                state = DiagState.CONNECTED
                                startIdleKeepAlive()

                                Log.i(
                                    "VCDS_PROBE",
                                    "01-Engine verified by ECU KWP frame=" +
                                        verification.joinToString(" ") {
                                            "%02X".format(it.toInt() and 0xFF)
                                        }
                                )
                                return@withContext true
                            }

                            lastError =
                                "Five-baud init completed, but no checksum-valid KWP reply " +
                                    "was received from ECU address 01."

                            if (attempt < 3) {
                                continue
                            }

                            state = DiagState.ERROR
                            return@withContext false
                        }

                        lastError =
                            "01-Engine slow init failed at ${slow.failureStage ?: "UNKNOWN"} " +
                                "(sync=${slow.syncByte?.let { "%02X".format(it) } ?: "--"}, " +
                                "KB1=${slow.keyByte1?.let { "%02X".format(it) } ?: "--"}, " +
                                "KB2=${slow.keyByte2?.let { "%02X".format(it) } ?: "--"})."

                        if (attempt < 3) {
                            // Keep the same USB handle/OBD power. The next iteration waits
                            // the required quiet interval before trying 0x01 again.
                            continue
                        }

                        state = DiagState.ERROR
                        return@withContext false
                    }

                    // Generic K-Line adapters keep the older fallback sequence.
                    transport.purge()
                    delay(60)
                    var ok = false
                    if (transport.isConnected()) {
                        transport.setBaudRate(UsbKwpTransport.KLINE_BAUD_RATE)
                        delay(50)
                        ok = tryInitDirect(targetEcuAddress)
                        if (!ok) {
                            delay(80)
                            ok = tryInitDirectId(targetEcuAddress)
                        }
                        if (!ok) {
                            delay(80)
                            ok = tryInitFastPulse(targetEcuAddress)
                        }
                    }

                    if (ok) {
                        try {
                            val sessionReq = buildMessage(
                                targetEcuAddress,
                                0xF1.toByte(),
                                byteArrayOf(0x10.toByte(), 0x89.toByte())
                            )
                            transport.write(sessionReq)
                            val respBuffer = ByteArray(64)
                            transport.read(respBuffer, 200)
                        } catch (_: Exception) {}

                        state = DiagState.CONNECTED
                        noteDiagnosticActivity()
                        startIdleKeepAlive()
                        return@withContext true
                    }

                    if (attempt < 3 && !transport.isConnected()) {
                        Log.i("VCDS_PROBE", "Port dead after attempt $attempt, retrying...")
                        delay(800)
                        continue
                    }

                    // Last attempt: report failure but keep USB open
                    lastError = if (isRossTech) {
                        "Ross-Tech adapter online (TCP bridge :9999 ready). ECU did not reply: ensure ignition is ON and adapter LED is lit."
                    } else {
                        "ECU did not respond on K-Line. Turn ignition ON (Terminal 15)!"
                    }
                    state = DiagState.ERROR
                    // DO NOT disconnect: keep USB open to preserve OTG VBUS power and TCP Bridge!
                    return@withContext false

                } catch (e: Exception) {
                    Log.e("VCDS_PROBE", "Connection attempt $attempt error: ${e.message}")
                    if (attempt < 3) {
                        delay(800)
                        continue
                    }
                    lastError = "Connection error: ${e.message}"
                    state = DiagState.ERROR
                    return@withContext false
                }
            }

            lastError = "All connection attempts exhausted"
            state = DiagState.ERROR
            return@withContext false
        }
    }

    private suspend fun tryRossTechCanInit(target: Byte): Boolean {
        Log.w("VCDS_PROBE", "Ross-Tech B03 ZERO-TX enforced: physical ECU probe disabled pending live trace verification")
        return false
    }

    suspend fun bruteForceSweep(): String = withContext(Dispatchers.IO) {
        val sb = StringBuilder()
        if (!transport.isConnected()) {
            val msg = "USB adapter is not connected or open."
            Log.w("VCDS_PROBE", msg)
            return@withContext msg
        }
        val info = transport.getActiveAdapterInfo()
        if (info?.isRossTechIntelligent == true) {
            val msg = "ZERO-TX enforced: bruteForceSweep is disabled for Ross-Tech adapter pending live trace verification."
            Log.w("VCDS_PROBE", msg)
            return@withContext msg
        }
        val bauds = listOf(500000, 250000, 115200, 38400, 10400)
        val probes = listOf(
            Pair("Ross-Tech Ping 0x55", byteArrayOf(0x55)),
            Pair("Ross-Tech Ping 0xAA", byteArrayOf(0xAA.toByte())),
            Pair("Ross-Tech Connect 0x21 0x55", byteArrayOf(0x21, 0x55)),
            Pair("Ross-Tech Query Version 0x22", byteArrayOf(0x22)),
            Pair("CAN TP2.0 Channel Setup (0x200)", byteArrayOf(0x01, 0xC0.toByte(), 0x00, 0x10, 0x00, 0x03, 0x01)),
            Pair("KWP StartComm Addr 01", byteArrayOf(0x81.toByte(), 0x01, 0xF1.toByte(), 0x81.toByte(), 0xF4.toByte())),
            Pair("KWP Read ECU ID 1A 9B", byteArrayOf(0x82.toByte(), 0x01, 0xF1.toByte(), 0x1A, 0x9B.toByte(), 0x69))
        )

        try {
            for (baud in bauds) {
                transport.setBaudRate(baud)
                transport.setDtr(true)
                transport.purge()
                delay(50)
                Log.i("VCDS_PROBE", "=== Testing baud $baud ===")
                sb.append("=== BAUD $baud ===\n")

                for ((name, packet) in probes) {
                    try {
                        transport.purge()
                        transport.write(packet)
                        val hexTx = packet.joinToString(" ") { "%02X".format(it) }
                        Log.i("VCDS_PROBE", "TX [$name]: $hexTx")

                        val buf = ByteArray(128)
                        val readCount = transport.read(buf, 250)
                        if (readCount > 0) {
                            val hexRx = buf.take(readCount).joinToString(" ") { "%02X".format(it) }
                            Log.i("VCDS_PROBE", "  -> RX ($readCount bytes): $hexRx")
                            sb.append("  $name: RX $hexRx\n")
                        } else {
                            Log.i("VCDS_PROBE", "  -> RX: (timeout/silence)")
                            sb.append("  $name: (no reply)\n")
                        }
                    } catch (e: Exception) {
                        Log.w("VCDS_PROBE", "Probe $name error: ${e.message}")
                        sb.append("  $name: ERR ${e.message}\n")
                    }
                    delay(30)
                }
            }
        } catch (e: Exception) {
            Log.e("VCDS_PROBE", "bruteForceSweep fatal: ${e.message}")
            sb.append("Sweep aborted: ${e.message}\n")
        }
        return@withContext sb.toString()
    }

    private fun noteDiagnosticActivity() {
        lastDiagnosticActivityNs = System.nanoTime()
    }

    /**
     * Keeps an established KWP session alive only while it is otherwise idle.
     * Active measuring-group traffic naturally refreshes P3, so no extra packet
     * is injected during polling.
     */
    private fun startIdleKeepAlive() {
        keepAliveJob?.cancel()
        noteDiagnosticActivity()

        keepAliveJob = engineScope.launch {
            while (isActive) {
                delay(1_000)
                if (state != DiagState.CONNECTED && state != DiagState.POLLING) continue
                if (!transport.isConnected() || transport.isBridgeActive()) continue

                val idleMs = (System.nanoTime() - lastDiagnosticActivityNs) / 1_000_000L
                if (idleMs < 2_000L) continue

                commMutex.withLock {
                    try {
                        val request = buildMessage(
                            targetEcuAddress,
                            TESTER_ADDRESS.toByte(),
                            byteArrayOf(0x3E.toByte(), 0x00)
                        )
                        transport.write(request)
                        val payload = readKwpPayload(350)

                        // Positive 0x7E, negative 0x7F, or another valid ECU frame all
                        // prove the link is alive and reset the session inactivity timer.
                        if (payload != null) {
                            noteDiagnosticActivity()
                            Log.d(
                                "VCDS_KEEPALIVE",
                                "RX " + payload.joinToString(" ") {
                                    "%02X".format(it.toInt() and 0xFF)
                                }
                            )
                        } else {
                            Log.w("VCDS_KEEPALIVE", "TesterPresent timeout; keeping USB open")
                        }
                    } catch (e: Exception) {
                        Log.w("VCDS_KEEPALIVE", "Keepalive error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    /**
     * Sends one read-only VAG identity request after slow init and returns any
     * checksum-valid reply from the selected ECU. A positive identity response
     * (0x5A/0x61) is ideal, but a valid negative response (0x7F...) still proves
     * that ECU address 01 is alive and that the KWP framing is working.
     */
    private fun readEcuIdentificationRaw(target: Byte): ByteArray? {
        return try {
            transport.purge()
            val idReq = buildMessage(
                target,
                0xF1.toByte(),
                byteArrayOf(0x1A.toByte(), 0x9B.toByte())
            )
            transport.write(idReq)
            val payload = readKwpPayload(700) ?: return null
            if (payload.isNotEmpty()) {
                noteDiagnosticActivity()
                payload
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun tryInitDirect(target: Byte): Boolean {
        try {
            transport.purge()
            val startComm = buildMessage(target, 0xF1.toByte(), byteArrayOf(0x81.toByte()))
            transport.write(startComm)
            val payload = readKwpPayload(450) ?: return false
            return payload.isNotEmpty() && (payload[0] == 0xC1.toByte() || payload[0] == 0x50.toByte())
        } catch (_: Exception) {
            return false
        }
    }

    private fun tryInitDirectId(target: Byte): Boolean {
        try {
            transport.purge()
            val idReq = buildMessage(target, 0xF1.toByte(), byteArrayOf(0x1A.toByte(), 0x9B.toByte()))
            transport.write(idReq)
            val payload = readKwpPayload(600) ?: return false
            return payload.isNotEmpty() && (payload[0] == 0x5A.toByte() || payload[0] == 0x61.toByte())
        } catch (_: Exception) {
            return false
        }
    }

    private fun tryInitFastPulse(target: Byte): Boolean {
        try {
            transport.purge()
            transport.sendFastInitPulse()
            val startComm = buildMessage(target, 0xF1.toByte(), byteArrayOf(0x81.toByte()))
            transport.write(startComm)
            val payload = readKwpPayload(450) ?: return false
            return payload.isNotEmpty() && (payload[0] == 0xC1.toByte() || payload[0] == 0x50.toByte())
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Accumulates USB serial chunks until a complete checksum-valid ECU KWP frame
     * is present or the deadline expires. A serial read is not a message boundary:
     * K-Line echo and the ECU response may arrive in separate USB packets.
     */
    private fun readKwpPayload(timeoutMs: Int, maxBytes: Int = 512): ByteArray? {
        val aggregate = ByteArray(maxBytes)
        var used = 0
        val deadlineNs = System.nanoTime() + timeoutMs.toLong() * 1_000_000L

        while (used < maxBytes) {
            val nowNs = System.nanoTime()
            if (nowNs >= deadlineNs) break

            val remainingMs = ((deadlineNs - nowNs) / 1_000_000L).coerceAtLeast(1L).toInt()
            val chunk = ByteArray(minOf(128, maxBytes - used))
            val count = transport.read(chunk, minOf(80, remainingMs))

            if (count > 0) {
                System.arraycopy(chunk, 0, aggregate, used, count)
                used += count

                val payload = extractPayload(aggregate, used)
                if (payload != null) return payload
            }
        }
        return null
    }

    fun disconnect() {
        stopKeepAlive()
        if (mode == TransportMode.USB_HARDWARE) {
            transport.disconnect()
        }
        state = DiagState.DISCONNECTED
    }

    /**
     * Polls a VAG Measuring Group (001 - 255) via KWP2000 Service 0x21 (ReadDataByLocalIdentifier).
     */
    suspend fun readMeasuringGroup(groupNumber: Int): MeasuringGroup? = withContext(Dispatchers.IO) {
        if (state != DiagState.CONNECTED && state != DiagState.POLLING) {
            return@withContext null
        }

        if (mode == TransportMode.SIMULATOR_DEMO) {
            return@withContext generateMockGroup(groupNumber)
        }

        if (transport.isBridgeActive()) {
            delay(100)
            return@withContext null
        }

        commMutex.withLock {
            try {
                // Service 0x21 <GroupNumber>
                transport.purge()
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x21.toByte(), groupNumber.toByte()))
                transport.write(request)

                val payload = readKwpPayload(350) ?: return@withContext null
                if (payload.size < 2 || payload[0] != 0x61.toByte() ||
                    (payload[1].toInt() and 0xFF) != groupNumber
                ) {
                    lastError = "Unexpected reply while reading Group $groupNumber"
                    return@withContext null
                }
                noteDiagnosticActivity()
                return@withContext MeasuringGroup.decode(payload)
            } catch (e: Exception) {
                lastError = "Read Group $groupNumber failed: ${e.message}"
                return@withContext null
            }
        }
    }

    /**
     * Reads Diagnostic Trouble Codes (DTC) via KWP2000 Service 0x18.
     */
    suspend fun readFaultCodes(): List<FaultCode> = withContext(Dispatchers.IO) {
        if (state != DiagState.CONNECTED && state != DiagState.POLLING) {
            return@withContext emptyList()
        }

        if (mode == TransportMode.SIMULATOR_DEMO) {
            // Return realistic EDC16 turbo & vacuum fault codes for demo
            return@withContext listOf(
                FaultCode.parseFromBytes(0x02, 0x99, 0xA2, dtcLookup), // P0299 Boost control range not reached
                FaultCode.parseFromBytes(0x01, 0x01, 0x20, dtcLookup)  // P0101 MAF implausible signal
            )
        }

        if (transport.isBridgeActive()) {
            delay(100)
            return@withContext emptyList()
        }

        commMutex.withLock {
            try {
                // Service 0x18 0x02 0xFF 0x00 (Read DTC by status mask)
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x18.toByte(), 0x02.toByte(), 0xFF.toByte(), 0x00.toByte()))
                transport.write(request)

                val payload = readKwpPayload(600) ?: return@withContext emptyList()

                if (payload.isEmpty() || payload[0] != 0x58.toByte()) {
                    return@withContext emptyList()
                }
                noteDiagnosticActivity()

                val dtcList = ArrayList<FaultCode>()

                var offset = 2
                while (offset + 2 < payload.size) {
                    val high = payload[offset].toInt() and 0xFF
                    val low = payload[offset + 1].toInt() and 0xFF
                    val status = payload[offset + 2].toInt() and 0xFF

                    if (high != 0 || low != 0) {
                        dtcList.add(FaultCode.parseFromBytes(high, low, status, dtcLookup))
                    }
                    offset += 3
                }

                return@withContext dtcList
            } catch (e: Exception) {
                lastError = "Scan DTC failed: ${e.message}"
                return@withContext emptyList()
            }
        }
    }

    /**
     * Clears all DTCs via KWP2000 Service 0x14.
     */
    suspend fun clearFaultCodes(): Boolean = withContext(Dispatchers.IO) {
        if (state != DiagState.CONNECTED && state != DiagState.POLLING) {
            return@withContext false
        }

        if (mode == TransportMode.SIMULATOR_DEMO) {
            delay(200)
            return@withContext true
        }

        if (transport.isBridgeActive()) {
            delay(100)
            return@withContext false
        }

        commMutex.withLock {
            try {
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x14.toByte(), 0xFF.toByte(), 0x00.toByte()))
                transport.write(request)

                val payload = readKwpPayload(500) ?: return@withContext false

                return@withContext payload.isNotEmpty() && payload[0] == 0x54.toByte()
            } catch (e: Exception) {
                lastError = "Clear DTC failed: ${e.message}"
                return@withContext false
            }
        }
    }

    private fun buildMessage(target: Byte, source: Byte, payload: ByteArray): ByteArray {
        val length = payload.size
        val format = (0x80 or (length and 0x3F)).toByte()
        val msg = ByteArray(length + 4)
        msg[0] = format
        msg[1] = target
        msg[2] = source
        System.arraycopy(payload, 0, msg, 3, length)

        var cs = 0
        for (i in 0 until msg.size - 1) {
            cs += (msg[i].toInt() and 0xFF)
        }
        msg[msg.size - 1] = (cs and 0xFF).toByte()
        return msg
    }

    /** Visible for unit tests: K-Line echo rejection is safety-relevant. */
    /**
     * Delegates to [KwpFrameParser], which is Android-free and unit tested.
     *
     * The rules live there because they are safety relevant: on K-Line our own
     * transmission is echoed back as a checksum-valid frame, and accepting it
     * would feed the request we just sent back to the caller as live ECU data.
     */
    internal fun extractPayload(buffer: ByteArray, count: Int): ByteArray? =
        KwpFrameParser.extractPayload(
            buffer,
            count,
            expectedSource = targetEcuAddress.toInt() and 0xFF
        )

    /**
     * Realistic EDC16U34 4th-gear WOT acceleration simulation.
     */
    private fun generateMockGroup(group: Int): MeasuringGroup {
        mockSimTime += 0.08
        if (mockWotActive) {
            mockRpm += 35.0
            if (mockRpm > 4200.0) {
                mockWotActive = false
            }
        } else {
            mockRpm -= 70.0
            if (mockRpm < 1400.0) {
                mockRpm = 1400.0
                mockWotActive = true
            }
        }

        val rpm = mockRpm
        val isSpooling = rpm < 2200.0

        // Realistic boost curve: Target 2350 mbar, Actual lagging during spool (1500-2000 RPM)
        val boostTarget = if (rpm < 1600.0) 1300.0 + (rpm - 1400.0) * 4.0 else 2350.0
        val boostActual = if (rpm < 1800.0) {
            1050.0 + (rpm - 1400.0) * 1.8 // Spool lag
        } else if (rpm < 2250.0) {
            1770.0 + (rpm - 1800.0) * 1.3
        } else {
            2340.0 + sin(mockSimTime * 2) * 20.0
        }

        // N75: 80% when spooling (closed vanes), drops to 58-62% when target reached
        val n75Duty = if (rpm < 2100.0) 79.5 else (60.0 - sin(mockSimTime) * 3.5)

        // IQ: Driver Wish 60 mg, Torque Limiter 56 mg, Smoke Limiter restricted by low boost
        val driverWish = 60.0
        val torqueLim = 56.5 - (if (rpm > 3500) (rpm - 3500) * 0.015 else 0.0)
        val smokeLim = if (isSpooling) 36.0 + (rpm - 1400.0) * 0.02 else 55.0
        val mafActual = boostActual * 0.42

        val mockPayload = ByteArray(14)
        mockPayload[0] = 0x61.toByte()
        mockPayload[1] = group.toByte()

        when (group) {
            11 -> {
                // Group 011: RPM (Formula 1), Boost Req (Formula 8), Boost Act (Formula 8), N75 (Formula 2)
                encodeFormula(mockPayload, 2, 1, rpm / 0.2)
                encodeFormula(mockPayload, 5, 8, boostTarget / 0.1)
                encodeFormula(mockPayload, 8, 8, boostActual / 0.1)
                encodeFormula(mockPayload, 11, 2, n75Duty / 0.002)
            }
            8 -> {
                // Group 008: RPM, Driver Wish, Torque Limit, Smoke Limit (Formula 49)
                encodeFormula(mockPayload, 2, 1, rpm / 0.2)
                encodeFormula(mockPayload, 5, 49, driverWish / 0.025)
                encodeFormula(mockPayload, 8, 49, torqueLim / 0.025)
                encodeFormula(mockPayload, 11, 49, smokeLim / 0.025)
            }
            3 -> {
                // Group 003: RPM, MAF Req (850), MAF Act, EGR Duty (4.8%)
                encodeFormula(mockPayload, 2, 1, rpm / 0.2)
                encodeFormula(mockPayload, 5, 39, 850.0 * 256.0 / 100.0)
                encodeFormula(mockPayload, 8, 39, mafActual * 256.0 / 100.0)
                encodeFormula(mockPayload, 11, 2, 4.8 / 0.002)
            }
            else -> {
                encodeFormula(mockPayload, 2, 1, rpm / 0.2)
                encodeFormula(mockPayload, 5, 5, 88.0 * 10.0 + 100.0) // Coolant 88°C
                encodeFormula(mockPayload, 8, 7, 85.0 / 0.01) // 85 km/h
                encodeFormula(mockPayload, 11, 6, 14.1 / 0.001) // 14.1V
            }
        }

        return MeasuringGroup.decode(mockPayload) ?: MeasuringGroup(
            groupNumber = group,
            title = "Group $group (Simulated)",
            values = emptyList()
        )
    }

    private fun encodeFormula(buffer: ByteArray, offset: Int, type: Int, rawValue: Double) {
        buffer[offset] = type.toByte()
        val intVal = rawValue.toInt().coerceIn(0, 65535)
        buffer[offset + 1] = ((intVal shr 8) and 0xFF).toByte()
        buffer[offset + 2] = (intVal and 0xFF).toByte()
    }
}
