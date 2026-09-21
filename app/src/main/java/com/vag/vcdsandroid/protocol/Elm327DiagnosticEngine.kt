package com.vag.vcdsandroid.protocol

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.vag.vcdsandroid.bluetooth.BluetoothElmTransport
import com.vag.vcdsandroid.bluetooth.ElmResponse
import com.vag.vcdsandroid.model.FaultCode
import com.vag.vcdsandroid.model.MeasuringGroup
import com.vag.vcdsandroid.model.MeasuringValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Locale
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import android.os.Environment

enum class ElmDiagnosticState {
    DISCONNECTED,
    CONNECTING_RFCOMM,
    RFCOMM_CONNECTED,
    ELM_INITIALIZING,
    ELM_READY,
    OBD_CONNECTING,
    OBD_READY,
    ERROR
}

class Elm327DiagnosticEngine(private val context: Context) {

    companion object {
        private const val TAG = "ELM_ENGINE"
    }

    val transport = BluetoothElmTransport(context)
    val tp20Transport = VwTp20Transport(transport) { appendLog(it) }
    private val commMutex = Mutex()

    var isTp20Active: Boolean = false
        private set

    var forceGenericObd: Boolean = false

    var state: DiagState = DiagState.DISCONNECTED
        private set

    var elmState: ElmDiagnosticState = ElmDiagnosticState.DISCONNECTED
        private set

    var lastError: String? = null
        private set

    var elmVersionString: String = ""
        private set

    var lastConnectTrace: String = ""
        private set

    var lastConnectStage: String = ""
        private set

    var obdProtocol: String = ""
        private set

    private val macAddressRegex = Regex("([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})")

    private fun redactMac(text: String): String {
        return text.replace(macAddressRegex, "XX:XX:XX:XX:XX:XX")
    }

    fun saveConnectionTrace(
        isSuccess: Boolean,
        stage: String,
        deviceName: String,
        baroSource: String? = null,
        baroValueMbar: Double? = null,
        phoneBaroAvailable: Boolean? = null,
        phoneBaroValueMbar: Double? = null,
        phoneBaroAgeMs: Long? = null
    ) {
        try {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "VCDS_Logs")
            if (!dir.exists()) dir.mkdirs()
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
            val outcome = if (isSuccess) "SUCCESS" else "FAIL"
            val file = File(dir, "ConnectionTrace_${ts}_${outcome}.txt")
            val safeDeviceName = redactMac(deviceName)
            val content = buildString {
                appendLine("=== VCDS Connection Trace ===")
                appendLine("Outcome: $outcome")
                appendLine("Timestamp: ${Date()}")
                appendLine("Device: $safeDeviceName")
                appendLine("ELM Version: $elmVersionString")
                appendLine("Connect Stage: $stage")
                appendLine("OBD Protocol: $obdProtocol")
                appendLine("TP2.0 Active: $isTp20Active")
                appendLine("Last Error: $lastError")
                if (baroSource != null || baroValueMbar != null) {
                    appendLine("BARO source: ${baroSource ?: "N/A"}")
                    appendLine("BARO value: ${baroValueMbar?.let { String.format(Locale.US, "%.1f mbar", it) } ?: "N/A"}")
                }
                if (phoneBaroAvailable != null) {
                    appendLine("Phone pressure sensor available: $phoneBaroAvailable")
                    if (phoneBaroAvailable) {
                        appendLine("Phone pressure value: ${phoneBaroValueMbar?.let { String.format(Locale.US, "%.1f mbar", it) } ?: "N/A"}")
                        appendLine("Phone pressure age: ${phoneBaroAgeMs ?: 0L} ms")
                    }
                }
                appendLine("\n--- LOG HISTORY ---")
                appendLine(logHistory.joinToString("\n") { redactMac(it) })
            }
            file.writeText(content)
            Log.i(TAG, "Saved connection trace to ${file.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save connection trace: ${e.message}")
        }
    }

    var onLogListener: ((String) -> Unit)? = null

    private val _logHistory = mutableListOf<String>()
    val logHistory: List<String>
        get() = _logHistory.toList()

    private fun markObdConnected(source: String) {
        isTp20Active = false
        elmState = ElmDiagnosticState.OBD_READY
        state = DiagState.CONNECTED
        appendLog("==> OBD_READY via $source")
    }

    private fun appendLog(msg: String) {
        val sanitized = redactMac(msg)
        _logHistory.add(sanitized)
        if (_logHistory.size > 200) {
            _logHistory.removeAt(0)
        }
        Log.i(TAG, sanitized)
        onLogListener?.invoke(sanitized)
    }

    suspend fun connect(targetDevice: BluetoothDevice? = null, forceGeneric: Boolean = forceGenericObd): Boolean = withContext(Dispatchers.IO) {
        this@Elm327DiagnosticEngine.forceGenericObd = forceGeneric
        commMutex.withLock {
            state = DiagState.CONNECTING
            elmState = ElmDiagnosticState.CONNECTING_RFCOMM
            lastError = null
            lastConnectStage = ""
            obdProtocol = ""
            elmVersionString = ""
            lastConnectTrace = ""
            _logHistory.clear()

            val dev = targetDevice ?: transport.findPairedElmDevice()
            if (dev == null) {
                val err = "Не знайдено спареного адаптера (V-LINK / ELM327) у списку Bluetooth! Спаруйте його в налаштуваннях Android."
                appendLog("ERR: $err")
                lastError = redactMac(err)
                lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                saveConnectionTrace(false, "NO_PAIRED_DEVICE", "none")
                return@withContext false
            }

            appendLog("1. Початок RFCOMM зв'язку з ${dev.name} [${dev.address}]...")
            val rfcommOk = transport.connect(dev) { logMsg ->
                appendLog(logMsg)
            }

            if (!rfcommOk) {
                val err = "RFCOMM connect() failed для ${dev.name} [${dev.address}]. Перевірте адаптер!"
                appendLog("ERR: $err")
                lastError = redactMac(err)
                lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                saveConnectionTrace(false, "RFCOMM_FAIL", dev.name ?: dev.address)
                return@withContext false
            }

            elmState = ElmDiagnosticState.RFCOMM_CONNECTED
            appendLog("==> RFCOMM_CONNECTED: Bluetooth сокет відкрито успішно.")
            delay(200)

            // Step 1: Send warmup CR to flush ELM state, then ATZ (Hardware Reset)
            appendLog("2. Скидання та ініціалізація ELM327 (ATZ)...")
            transport.sendCommand("", 500L) // Warmup CR
            delay(100)

            appendLog("TX >> ATZ")
            val atzResp = transport.sendCommand("ATZ", 3500L)
            appendLog("RX << ${formatRx(atzResp)}")
            delay(400) // Hardware reboot settling time

            // Step 2: Query ATI (Interface Info)
            appendLog("TX >> ATI")
            val atiResp = transport.sendCommand("ATI", 2000L)
            appendLog("RX << ${formatRx(atiResp)}")

            if (atiResp.promptReceived) {
                elmVersionString = atiResp.raw.replace("\n", " ").trim()
                appendLog("==> ELM_RESPONDING: Адаптер на зв'язку ($elmVersionString)")
            } else if (atzResp.promptReceived) {
                elmVersionString = atzResp.raw.replace("\n", " ").trim()
                appendLog("==> ELM_RESPONDING: Адаптер на зв'язку через ATZ ($elmVersionString)")
            } else {
                val err = "RFCOMM підключено, але ELM327 не відповів на ATZ/ATI! Перевірте адаптер або увімкніть/вимкніть запалювання."
                lastError = err
                lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                appendLog("ERR: $err")
                saveConnectionTrace(false, "ATZ_INIT_FAIL", dev.name ?: dev.address)
                transport.disconnect()
                return@withContext false
            }

            // Step 2: Init sequence
            elmState = ElmDiagnosticState.ELM_INITIALIZING
            appendLog("3. Ініціалізація ELM327 параметрів...")

            val initSequence = listOf(
                "ATZ" to 4500L,   // Reset (needs 3-4s)
                "ATI" to 1500L,   // Re-check ID
                "ATE0" to 1500L,  // Echo off
                "ATL0" to 1500L,  // Linefeeds off
                "ATS0" to 1500L,  // Spaces off
                "ATH0" to 1500L,  // Headers off
                "ATCAF1" to 1500L,// CAN Auto Formatting on
                "ATR1" to 1500L   // Responses on
            )

            for ((cmd, timeout) in initSequence) {
                appendLog("TX >> $cmd")
                val resp = transport.sendCommand(cmd, timeout)
                appendLog("RX << ${formatRx(resp)}")

                if (cmd == "ATZ") {
                    delay(400) // Hardware reboot settling time
                }

                if (resp.timedOut) {
                    appendLog("WARN: Команда $cmd перевищила таймаут (${resp.elapsedMs}ms)")
                }
            }

            elmState = ElmDiagnosticState.ELM_READY
            appendLog("==> ELM_READY: Конфігурація адаптера завершена.")

            // Step 3: Try VW TP 2.0 over CAN (Golf 5 EDC16U34) for true Group 011 / N75
            if (!forceGenericObd) {
                elmState = ElmDiagnosticState.OBD_CONNECTING
                appendLog("4. Спроба відкриття каналу VW TP 2.0 (CAN ID 0x200 -> 01-Engine)...")
                val canCapOk = tp20Transport.probeCapabilities()
                if (canCapOk) {
                    val channelOk = tp20Transport.setupChannel(0x01)
                    if (channelOk) {
                        isTp20Active = true
                        lastConnectStage = "VW_TP20"
                        state = DiagState.CONNECTED
                        elmState = ElmDiagnosticState.OBD_READY
                        appendLog("==> VW TP 2.0 ПІДКЛЮЧЕНО! Опитуємо справжній VAG Group 011 (Target Boost, Actual, N75 %)")
                        lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                        saveConnectionTrace(true, "VW_TP20", dev.name ?: dev.address)
                        return@withContext true
                    } else {
                        appendLog("WARN: TP 2.0 канал не відповів на 0x200, перемикаємось на Generic OBD-II Mode 01...")
                    }
                } else {
                    appendLog("WARN: Адаптер не підтримує ATV1/Raw CAN, перемикаємось на Generic OBD-II Mode 01...")
                }
            }

            isTp20Active = false

            // Step 4: Generic OBD Mode 01 Handshake (Deterministic 3-Stage per REVIEW_D2CBE72_BEFORE_CAR.md)
            elmState = ElmDiagnosticState.OBD_CONNECTING
            val handshake = GenericObdHandshake(
                sendCmd = { cmd, timeout -> transport.sendCommand(cmd, timeout) },
                log = { appendLog(it) }
            )
            val result = handshake.execute()
            when (result) {
                is HandshakeResult.Success -> {
                    lastConnectStage = result.stage
                    obdProtocol = "${result.protocolName} (${result.protocolNum})"
                    markObdConnected(result.stage)
                    lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                    // In Mode A (forceGeneric == true), trace is saved once in MainActivity after BARO enrichment
                    if (!forceGeneric) {
                        saveConnectionTrace(true, result.stage, dev.name ?: dev.address)
                    }
                    return@withContext true
                }
                is HandshakeResult.Failure -> {
                    lastError = redactMac(result.reason)
                    lastConnectTrace = logHistory.takeLast(120).joinToString("\n")
                    state = DiagState.ERROR
                    elmState = ElmDiagnosticState.ERROR
                    appendLog("ERR: ${result.reason}")
                    saveConnectionTrace(false, "HANDSHAKE_FAIL", dev.name ?: dev.address)
                    transport.disconnect()
                    return@withContext false
                }
            }
        }
    }

    private fun formatRx(resp: ElmResponse): String {
        val promptStr = if (resp.promptReceived) " >" else " [NO PROMPT / TIMEOUT]"
        return "${resp.raw}$promptStr (${resp.elapsedMs}ms)"
    }

    suspend fun readMeasuringGroup(groupNum: Int): MeasuringGroup? = withContext(Dispatchers.IO) {
        commMutex.withLock {
            if (!transport.isConnected || state != DiagState.CONNECTED) return@withContext null

            if (isTp20Active) {
                // VwTp20Transport already implements a generic KWP 0x21 reader.
                // Do not artificially restrict OEM mode to only 011/008/003.
                return@withContext tp20Transport.readMeasuringGroup(groupNum)
            }

            return@withContext when (groupNum) {
                11 -> readGroup011Generic()
                8 -> readGroup008Generic()
                3 -> readGroup003Generic()
                else -> null
            }
        }
    }

    private suspend fun readGroup011Generic(): MeasuringGroup? {
        val rpmResp = transport.sendCommand("010C", 800L)
        val boostResp = transport.sendCommand("010B", 800L)

        val rpmParsed = parsePid010C(rpmResp.raw)
        val boostParsed = parsePid010B(boostResp.raw)

        val values = listOf(
            MeasuringValue(
                fieldIndex = 1,
                title = "Engine Speed",
                rawValue = if (rpmParsed.isValid) rpmParsed.rawDec else 0.0,
                formattedValue = rpmParsed.formatted,
                unit = rpmParsed.unit
            ),
            MeasuringValue(
                fieldIndex = 2,
                title = "Specified Boost",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "mbar"
            ),
            MeasuringValue(
                fieldIndex = 3,
                title = "MAP absolute",
                rawValue = if (boostParsed.isValid) boostParsed.rawDec else 0.0,
                formattedValue = boostParsed.formatted,
                unit = boostParsed.unit
            ),
            MeasuringValue(
                fieldIndex = 4,
                title = "N75 Duty Cycle",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "%"
            )
        )
        return MeasuringGroup(11, "GENERIC OBD — MAP & RPM", values)
    }

    private suspend fun readGroup008Generic(): MeasuringGroup? {
        val loadResp = transport.sendCommand("0104", 800L)
        val loadParsed = parsePid0104(loadResp.raw)

        val values = listOf(
            MeasuringValue(
                fieldIndex = 1,
                title = "Calculated Load",
                rawValue = if (loadParsed.isValid) loadParsed.rawDec else 0.0,
                formattedValue = loadParsed.formatted,
                unit = loadParsed.unit
            ),
            MeasuringValue(
                fieldIndex = 2,
                title = "Driver Wish IQ",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "mg/str"
            ),
            MeasuringValue(
                fieldIndex = 3,
                title = "Torque Limit IQ",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "mg/str"
            ),
            MeasuringValue(
                fieldIndex = 4,
                title = "Smoke Limit IQ",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "mg/str"
            )
        )
        return MeasuringGroup(8, "GENERIC OBD — IQ NOT AVAILABLE", values)
    }

    private suspend fun readGroup003Generic(): MeasuringGroup? {
        val mafResp = transport.sendCommand("0110", 800L)
        val mafParsed = parsePid0110(mafResp.raw)

        val values = listOf(
            MeasuringValue(
                fieldIndex = 1,
                title = "Engine Speed",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "RPM"
            ),
            MeasuringValue(
                fieldIndex = 2,
                title = "MAF (Specified)",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "mg/str"
            ),
            MeasuringValue(
                fieldIndex = 3,
                title = "MAF (Actual)",
                rawValue = if (mafParsed.isValid) mafParsed.rawDec else 0.0,
                formattedValue = mafParsed.formatted,
                unit = mafParsed.unit
            ),
            MeasuringValue(
                fieldIndex = 4,
                title = "EGR Duty Cycle",
                rawValue = 0.0,
                formattedValue = "N/A",
                unit = "%"
            )
        )
        return MeasuringGroup(3, "GENERIC OBD — MAF", values)
    }

    suspend fun readFaultCodes(): List<FaultCode> = withContext(Dispatchers.IO) {
        commMutex.withLock {
            if (!transport.isConnected) return@withContext emptyList()

            if (isTp20Active) {
                val payload = tp20Transport.requestKwp(
                    byteArrayOf(0x18, 0x02, 0xFF.toByte(), 0x00),
                    timeoutMs = 2500L
                ) ?: return@withContext emptyList()

                if (payload.isEmpty() || payload[0] != 0x58.toByte()) {
                    return@withContext emptyList()
                }

                val codes = mutableListOf<FaultCode>()
                var offset = 2
                while (offset + 2 < payload.size) {
                    val high = payload[offset].toInt() and 0xFF
                    val low = payload[offset + 1].toInt() and 0xFF
                    val status = payload[offset + 2].toInt() and 0xFF
                    if (high != 0 || low != 0) {
                        codes.add(FaultCode.parseFromBytes(high, low, status, emptyMap()))
                    }
                    offset += 3
                }
                return@withContext codes
            }

            val resp = transport.sendCommand("03", 2500L)
            val clean = cleanHexResponse(resp.raw)
            val codes = mutableListOf<FaultCode>()

            if (clean.startsWith("43")) {
                var idx = 2
                while (idx + 4 <= clean.length) {
                    val codeHex = clean.substring(idx, idx + 4)
                    if (codeHex == "0000") break
                    val prefix = when (codeHex[0]) {
                        '0' -> "P0"
                        '1' -> "P1"
                        '2' -> "P2"
                        '3' -> "P3"
                        '4' -> "C0"
                        '5' -> "C1"
                        '6' -> "C2"
                        '7' -> "C3"
                        '8' -> "B0"
                        '9' -> "B1"
                        'A' -> "B2"
                        'B' -> "B3"
                        'C' -> "U0"
                        'D' -> "U1"
                        'E' -> "U2"
                        'F' -> "U3"
                        else -> "P"
                    }
                    val dtc = prefix + codeHex.substring(1)
                    codes.add(
                        FaultCode(
                            vagCode = dtc,
                            saeCode = dtc,
                            descriptionEn = "ECU Fault Code $dtc",
                            descriptionUk = "Код помилки ЕБУ $dtc",
                            statusByte = 0,
                            isMilActive = true,
                            isIntermittent = false
                        )
                    )
                    idx += 4
                }
            }
            return@withContext codes
        }
    }

    suspend fun clearFaultCodes(): Boolean = withContext(Dispatchers.IO) {
        commMutex.withLock {
            if (!transport.isConnected) return@withContext false

            if (isTp20Active) {
                val payload = tp20Transport.requestKwp(
                    byteArrayOf(0x14, 0xFF.toByte(), 0x00),
                    timeoutMs = 2000L
                ) ?: return@withContext false
                return@withContext payload.isNotEmpty() && payload[0] == 0x54.toByte()
            }

            val resp = transport.sendCommand("04", 2500L)
            return@withContext resp.raw.contains("44") || resp.raw.contains("OK")
        }
    }

    fun disconnect() {
        if (isTp20Active) {
            try {
                runBlocking(Dispatchers.IO) {
                    withTimeoutOrNull(500L) {
                        tp20Transport.closeChannel()
                    }
                }
            } catch (_: Exception) {}
        }
        isTp20Active = false
        try {
            transport.disconnect()
        } catch (_: Exception) {}
        state = DiagState.DISCONNECTED
        elmState = ElmDiagnosticState.DISCONNECTED
    }

    private fun cleanHexResponse(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT)
        if (upper.contains("NO DATA") || upper.contains("ERROR") || upper.contains("UNABLE TO CONNECT") || upper.contains("STOPPED")) {
            return ""
        }
        return upper.replace(Regex("[^0-9A-F]"), "")
    }

    private fun hexStringToByteArray(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len - 1) {
            val byteVal = hex.substring(i, i + 2).toIntOrNull(16) ?: 0
            data[i / 2] = byteVal.toByte()
            i += 2
        }
        return data
    }

    data class ParsedPid(
        val pid: String,
        val isValid: Boolean,
        val raw: String,
        val rawHex: String,
        val rawDec: Double,
        val formatted: String,
        val unit: String
    )

    private fun parsePid010C(raw: String): ParsedPid {
        val clean = cleanHexResponse(raw)
        val idx = clean.indexOf("410C")
        if (idx >= 0 && idx + 8 <= clean.length) {
            val aHex = clean.substring(idx + 4, idx + 6)
            val bHex = clean.substring(idx + 6, idx + 8)
            val a = aHex.toIntOrNull(16) ?: 0
            val b = bHex.toIntOrNull(16) ?: 0
            val rpm = ((a * 256) + b) / 4.0
            val logLine = "PID 010C | RAW=${raw.trim()} | A=0x$aHex B=0x$bHex | VALUE=${rpm.toInt()} | UNIT=rpm"
            Log.d("ELM_PARSER", logLine)
            appendLog(logLine)
            return ParsedPid("010C", true, raw, clean, rpm, String.format(Locale.US, "%.0f", rpm), "RPM")
        }
        val logLine = "PID 010C | RAW=${raw.trim()} | VALUE=N/A | UNIT=rpm"
        Log.d("ELM_PARSER", logLine)
        appendLog(logLine)
        return ParsedPid("010C", false, raw, clean, Double.NaN, "N/A", "RPM")
    }

    private fun parsePid010B(raw: String): ParsedPid {
        val clean = cleanHexResponse(raw)
        val idx = clean.indexOf("410B")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val aHex = clean.substring(idx + 4, idx + 6)
            val a = aHex.toIntOrNull(16) ?: 0
            val kpa = a.toDouble()
            val mbar = kpa * 10.0
            val logLine = "PID 010B | RAW=${raw.trim()} | A=0x$aHex RAW_DEC=$a | VALUE=$kpa UNIT=kPa absolute (${mbar.toInt()} mbar)"
            Log.d("ELM_PARSER", logLine)
            appendLog(logLine)
            return ParsedPid("010B", true, raw, clean, mbar, String.format(Locale.US, "%.0f", mbar), "mbar")
        }
        val logLine = "PID 010B | RAW=${raw.trim()} | VALUE=N/A | UNIT=mbar absolute"
        Log.d("ELM_PARSER", logLine)
        appendLog(logLine)
        return ParsedPid("010B", false, raw, clean, Double.NaN, "N/A", "mbar")
    }

    private fun parsePid0110(raw: String): ParsedPid {
        val clean = cleanHexResponse(raw)
        val idx = clean.indexOf("4110")
        if (idx >= 0 && idx + 8 <= clean.length) {
            val aHex = clean.substring(idx + 4, idx + 6)
            val bHex = clean.substring(idx + 6, idx + 8)
            val a = aHex.toIntOrNull(16) ?: 0
            val b = bHex.toIntOrNull(16) ?: 0
            val mafGs = ((a * 256) + b) / 100.0
            val logLine = "PID 0110 | RAW=${raw.trim()} | A=0x$aHex B=0x$bHex | VALUE=${String.format(Locale.US, "%.2f", mafGs)} | UNIT=g/s"
            Log.d("ELM_PARSER", logLine)
            appendLog(logLine)
            return ParsedPid("0110", true, raw, clean, mafGs, String.format(Locale.US, "%.2f", mafGs), "g/s")
        }
        val logLine = "PID 0110 | RAW=${raw.trim()} | VALUE=N/A | UNIT=g/s"
        Log.d("ELM_PARSER", logLine)
        appendLog(logLine)
        return ParsedPid("0110", false, raw, clean, Double.NaN, "N/A", "g/s")
    }

    private fun parsePid0104(raw: String): ParsedPid {
        val clean = cleanHexResponse(raw)
        val idx = clean.indexOf("4104")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val aHex = clean.substring(idx + 4, idx + 6)
            val a = aHex.toIntOrNull(16) ?: 0
            val loadPct = a * 100.0 / 255.0
            val logLine = "PID 0104 | RAW=${raw.trim()} | A=0x$aHex RAW_DEC=$a | VALUE=${String.format(Locale.US, "%.1f", loadPct)} | UNIT=%"
            Log.d("ELM_PARSER", logLine)
            appendLog(logLine)
            return ParsedPid("0104", true, raw, clean, loadPct, String.format(Locale.US, "%.1f", loadPct), "%")
        }
        val logLine = "PID 0104 | RAW=${raw.trim()} | VALUE=N/A | UNIT=%"
        Log.d("ELM_PARSER", logLine)
        appendLog(logLine)
        return ParsedPid("0104", false, raw, clean, Double.NaN, "N/A", "%")
    }
}
