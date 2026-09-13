package com.vag.vcdsandroid.protocol

import android.content.Context
import android.hardware.usb.UsbDevice
import com.vag.vcdsandroid.model.FaultCode
import com.vag.vcdsandroid.model.MeasuringGroup
import com.vag.vcdsandroid.usb.UsbKwpTransport
import kotlinx.coroutines.Dispatchers
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

    private var targetEcuAddress: Byte = 0x01
    private val commMutex = Mutex()
    private val dtcLookup = HashMap<String, Pair<String, String>>()

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

    suspend fun connect(targetDevice: UsbDevice? = null, targetAddress: Byte = 0x01): Boolean = withContext(Dispatchers.IO) {
        commMutex.withLock {
            targetEcuAddress = targetAddress
            state = DiagState.CONNECTING
            lastError = null

            if (mode == TransportMode.SIMULATOR_DEMO) {
                delay(400) // Emulate fast init delay
                state = DiagState.CONNECTED
                return@withContext true
            }

            try {
                if (!transport.connect(targetDevice, UsbKwpTransport.KLINE_BAUD_RATE)) {
                    lastError = "Cannot open USB Serial Port. Check OTG cable & permission."
                    state = DiagState.ERROR
                    return@withContext false
                }

                // Step 0: Flush stale buffer bytes
                transport.purge()
                delay(60)

                // Strategy 1: Direct KWP2000 StartCommunication (81 01 F1 81 F4) without destructive break pulse
                // Strategy 2: Direct Read ECU ID (82 01 F1 1A 9B 69) as used by EDC16/MPPS
                // Strategy 3: Fast Init 25ms break/mark pulse + StartCommunication (81 01 F1 81 F4)
                // Strategy 4: Functional OBD2 Broadcast (C1 33 F1 81 66)

                var ok = tryInitDirect(targetEcuAddress)
                if (!ok) {
                    delay(80)
                    ok = tryInitDirectId(targetEcuAddress)
                }
                if (!ok) {
                    delay(80)
                    ok = tryInitFastPulse(targetEcuAddress)
                }
                if (!ok) {
                    delay(80)
                    ok = tryInitDirect(0x33.toByte())
                }

                if (!ok) {
                    val info = transport.getActiveAdapterInfo()
                    lastError = if (info?.isRossTechIntelligent == true) {
                        "ECU did not respond on K-Line. ${info.displayName} detected: check ignition ON and adapter LED lit (12V OBD power)!"
                    } else {
                        "ECU did not respond on K-Line. Turn ignition ON (Terminal 15)!"
                    }
                    state = DiagState.ERROR
                    transport.disconnect()
                    return@withContext false
                }

                // Optional diagnostic session setup (0x10 0x89)
                try {
                    val sessionReq = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x10.toByte(), 0x89.toByte()))
                    transport.write(sessionReq)
                    val respBuffer = ByteArray(64)
                    transport.read(respBuffer, 200)
                } catch (_: Exception) {}

                state = DiagState.CONNECTED
                return@withContext true
            } catch (e: Exception) {
                lastError = "Connection error: ${e.message}"
                state = DiagState.ERROR
                transport.disconnect()
                return@withContext false
            }
        }
    }

    private fun tryInitDirect(target: Byte): Boolean {
        try {
            transport.purge()
            val startComm = buildMessage(target, 0xF1.toByte(), byteArrayOf(0x81.toByte()))
            transport.write(startComm)
            val respBuffer = ByteArray(64)
            val readBytes = transport.read(respBuffer, 450)
            if (readBytes <= 0) return false

            val payload = extractPayload(respBuffer, readBytes) ?: return false
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
            val respBuffer = ByteArray(128)
            val readBytes = transport.read(respBuffer, 600)
            if (readBytes <= 0) return false

            val payload = extractPayload(respBuffer, readBytes) ?: return false
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
            val respBuffer = ByteArray(64)
            val readBytes = transport.read(respBuffer, 450)
            if (readBytes <= 0) return false

            val payload = extractPayload(respBuffer, readBytes) ?: return false
            return payload.isNotEmpty() && (payload[0] == 0xC1.toByte() || payload[0] == 0x50.toByte())
        } catch (_: Exception) {
            return false
        }
    }

    fun disconnect() {
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

        commMutex.withLock {
            try {
                // Service 0x21 <GroupNumber>
                transport.purge()
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x21.toByte(), groupNumber.toByte()))
                transport.write(request)

                val buffer = ByteArray(128)
                val count = transport.read(buffer, 350)
                if (count < 6) return@withContext null

                val payload = extractPayload(buffer, count) ?: return@withContext null
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

        commMutex.withLock {
            try {
                // Service 0x18 0x02 0xFF 0x00 (Read DTC by status mask)
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x18.toByte(), 0x02.toByte(), 0xFF.toByte(), 0x00.toByte()))
                transport.write(request)

                val buffer = ByteArray(256)
                val count = transport.read(buffer, 600)
                val payload = extractPayload(buffer, count) ?: return@withContext emptyList()

                if (payload.isEmpty() || payload[0] != 0x58.toByte()) {
                    return@withContext emptyList()
                }

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

        commMutex.withLock {
            try {
                val request = buildMessage(targetEcuAddress, 0xF1.toByte(), byteArrayOf(0x14.toByte(), 0xFF.toByte(), 0x00.toByte()))
                transport.write(request)

                val buffer = ByteArray(32)
                val count = transport.read(buffer, 500)
                val payload = extractPayload(buffer, count) ?: return@withContext false

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

    private fun extractPayload(buffer: ByteArray, count: Int): ByteArray? {
        if (count < 4) return null

        // Pass 1: Search for valid KWP2000 response addressed to Tester (target == 0xF1, source != 0xF1)
        for (i in 0 until count - 3) {
            val fmt = buffer[i].toInt() and 0xFF
            if ((fmt and 0xC0) != 0x80) continue

            val target = buffer[i + 1].toInt() and 0xFF
            val source = buffer[i + 2].toInt() and 0xFF

            // Filter out local transmission echo (where source is Tester 0xF1)
            if (target != 0xF1 || source == 0xF1) continue

            val length = fmt and 0x3F
            val totalMsgLen = length + 4
            if (i + totalMsgLen > count) continue

            var calculatedCs = 0
            for (k in i until i + totalMsgLen - 1) {
                calculatedCs += (buffer[k].toInt() and 0xFF)
            }
            calculatedCs = calculatedCs and 0xFF
            val receivedCs = buffer[i + totalMsgLen - 1].toInt() and 0xFF
            if (calculatedCs == receivedCs) {
                val payload = ByteArray(length)
                System.arraycopy(buffer, i + 3, payload, 0, length)
                return payload
            }
        }

        // Pass 2: Fallback for generic frames if address bytes vary
        for (i in 0 until count - 3) {
            val fmt = buffer[i].toInt() and 0xFF
            if ((fmt and 0xC0) != 0x80) continue
            val length = fmt and 0x3F
            val totalMsgLen = length + 4
            if (i + totalMsgLen > count) continue

            var calculatedCs = 0
            for (k in i until i + totalMsgLen - 1) {
                calculatedCs += (buffer[k].toInt() and 0xFF)
            }
            calculatedCs = calculatedCs and 0xFF
            val receivedCs = buffer[i + totalMsgLen - 1].toInt() and 0xFF
            if (calculatedCs == receivedCs) {
                val payload = ByteArray(length)
                System.arraycopy(buffer, i + 3, payload, 0, length)
                return payload
            }
        }

        return null
    }

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
