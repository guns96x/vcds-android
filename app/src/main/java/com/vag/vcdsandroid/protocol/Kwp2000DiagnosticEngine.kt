package com.vag.vcdsandroid.protocol

import android.content.Context
import com.vag.vcdsandroid.model.FaultCode
import com.vag.vcdsandroid.model.MeasuringGroup
import com.vag.vcdsandroid.usb.UsbKwpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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

    suspend fun connect(targetAddress: Byte = 0x01): Boolean = withContext(Dispatchers.IO) {
        state = DiagState.CONNECTING
        lastError = null

        if (mode == TransportMode.SIMULATOR_DEMO) {
            delay(400) // Emulate fast init delay
            state = DiagState.CONNECTED
            return@withContext true
        }

        try {
            if (!transport.connect(UsbKwpTransport.KLINE_BAUD_RATE)) {
                lastError = "Cannot open USB Serial Port. Check OTG cable & permission."
                state = DiagState.ERROR
                return@withContext false
            }

            // Purge lines
            transport.purge()
            delay(50)

            // Step 1: Send Fast Init 25ms Break / 25ms Mark pulse
            transport.sendFastInitPulse()

            // Step 2: Send StartCommunication request: 0x81, 0x01 (Engine), 0xF1 (Tester), 0x81 (StartComm), CS
            val startComm = buildMessage(targetAddress, 0xF1.toByte(), byteArrayOf(0x81.toByte()))
            transport.write(startComm)

            val respBuffer = ByteArray(64)
            val readBytes = transport.read(respBuffer, 500)
            if (readBytes < 4) {
                lastError = "No response from ECU on K-Line (10400 bps fast init)."
                state = DiagState.ERROR
                transport.disconnect()
                return@withContext false
            }

            // Step 3: Start Diagnostic Session (0x10, 0x89 standard or 0x81)
            val sessionReq = buildMessage(targetAddress, 0xF1.toByte(), byteArrayOf(0x10.toByte(), 0x89.toByte()))
            transport.write(sessionReq)
            transport.read(respBuffer, 300)

            state = DiagState.CONNECTED
            return@withContext true
        } catch (e: Exception) {
            lastError = "Connection error: ${e.message}"
            state = DiagState.ERROR
            transport.disconnect()
            return@withContext false
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

        try {
            // Service 0x21 <GroupNumber>
            val request = buildMessage(0x01.toByte(), 0xF1.toByte(), byteArrayOf(0x21.toByte(), groupNumber.toByte()))
            transport.write(request)

            val buffer = ByteArray(64)
            val count = transport.read(buffer, 250)
            if (count < 6) return@withContext null

            val payload = extractPayload(buffer, count) ?: return@withContext null
            return@withContext MeasuringGroup.decode(payload)
        } catch (e: Exception) {
            lastError = "Read Group $groupNumber failed: ${e.message}"
            return@withContext null
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

        try {
            // Service 0x18 0x02 0xFF 0x00 (Read DTC by status mask)
            val request = buildMessage(0x01.toByte(), 0xF1.toByte(), byteArrayOf(0x18.toByte(), 0x02.toByte(), 0xFF.toByte(), 0x00.toByte()))
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

        try {
            val request = buildMessage(0x01.toByte(), 0xF1.toByte(), byteArrayOf(0x14.toByte(), 0xFF.toByte(), 0x00.toByte()))
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
        
        // Find valid KWP2000 header: Format byte where bits 7..6 == 10b
        var startIdx = -1
        for (i in 0 until count - 3) {
            val fmt = buffer[i].toInt() and 0xFF
            if ((fmt and 0xC0) == 0x80) {
                startIdx = i
                break
            }
        }
        if (startIdx == -1) return null

        val fmt = buffer[startIdx].toInt() and 0xFF
        val length = fmt and 0x3F
        val totalMsgLen = length + 4

        if (startIdx + totalMsgLen > count) {
            // Incomplete frame
            val available = count - startIdx - 4
            if (available <= 0) return null
            val payload = ByteArray(available)
            System.arraycopy(buffer, startIdx + 3, payload, 0, available)
            return payload
        }

        val payload = ByteArray(length)
        System.arraycopy(buffer, startIdx + 3, payload, 0, length)
        return payload
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
