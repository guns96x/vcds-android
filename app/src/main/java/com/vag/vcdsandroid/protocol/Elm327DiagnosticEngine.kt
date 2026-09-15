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

    var onLogListener: ((String) -> Unit)? = null

    private val _logHistory = mutableListOf<String>()
    val logHistory: List<String>
        get() = _logHistory.toList()

    private fun appendLog(msg: String) {
        _logHistory.add(msg)
        if (_logHistory.size > 200) {
            _logHistory.removeAt(0)
        }
        Log.i(TAG, msg)
        onLogListener?.invoke(msg)
    }

    suspend fun connect(targetDevice: BluetoothDevice? = null, forceGeneric: Boolean = forceGenericObd): Boolean = withContext(Dispatchers.IO) {
        this@Elm327DiagnosticEngine.forceGenericObd = forceGeneric
        commMutex.withLock {
            state = DiagState.CONNECTING
            elmState = ElmDiagnosticState.CONNECTING_RFCOMM
            lastError = null
            _logHistory.clear()

            val dev = targetDevice ?: transport.findPairedElmDevice()
            if (dev == null) {
                val err = "Не знайдено спареного адаптера (V-LINK / ELM327) у списку Bluetooth! Спаруйте його в налаштуваннях Android."
                lastError = err
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                appendLog("ERR: $err")
                return@withContext false
            }

            appendLog("1. Початок RFCOMM зв'язку з ${dev.name} [${dev.address}]...")
            val rfcommOk = transport.connect(dev) { logMsg ->
                appendLog(logMsg)
            }

            if (!rfcommOk) {
                val err = "RFCOMM connect() failed для ${dev.name} [${dev.address}]. Перевірте адаптер!"
                lastError = err
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                appendLog("ERR: $err")
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
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                appendLog("ERR: $err")
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
                        state = DiagState.CONNECTED
                        elmState = ElmDiagnosticState.OBD_READY
                        appendLog("==> VW TP 2.0 ПІДКЛЮЧЕНО! Опитуємо справжній VAG Group 011 (Target Boost, Actual, N75 %)")
                        return@withContext true
                    } else {
                        appendLog("WARN: TP 2.0 канал не відповів на 0x200, перемикаємось на Generic OBD-II Mode 01...")
                    }
                } else {
                    appendLog("WARN: Адаптер не підтримує ATV1/Raw CAN, перемикаємось на Generic OBD-II Mode 01...")
                }
            }

            isTp20Active = false

            // Step 4: Generic OBD Mode 01 Fallback (0100 & 010C fallback)
            elmState = ElmDiagnosticState.OBD_CONNECTING
            appendLog("5. Перевірка напруги та конфігурація OBD-II зв'язку...")

            // Read battery voltage & log it
            val vBat = transport.sendCommand("ATRV", 1000L)
            appendLog("Напруга бортової мережі: ${vBat.raw.trim()}")

            transport.sendCommand("ATCAF1", 1000L)
            transport.sendCommand("ATCFC1", 1000L)
            transport.sendCommand("ATV0", 1000L)
            transport.sendCommand("ATAT1", 1000L) // Adaptive timing ON
            transport.sendCommand("ATST64", 1000L) // 400ms timeout for reliable initial gateway wakeup

            var obdSuccess = false

            // Strategy 1: CAN 11-bit 500k with Functional broadcast (7DF)
            appendLog("Пробуємо CAN 11-bit 500k Functional (ATSH7DF)...")
            transport.sendCommand("ATSP6", 1500L)
            transport.sendCommand("ATSH7DF", 1000L)
            transport.sendCommand("ATCRA", 1000L)
            transport.sendCommand("ATAR", 1000L)

            val csResp1 = transport.sendCommand("ATCS", 1000L)
            appendLog("CAN Status (7DF): ${csResp1.raw.trim()}")

            var test0100 = transport.sendCommand("0100", 7000L)
            appendLog("RX (0100 @ 7DF) << ${formatRx(test0100)}")
            var clean0100 = cleanHexResponse(test0100.raw)

            var test010C = transport.sendCommand("010C", 3000L)
            appendLog("RX (010C @ 7DF) << ${formatRx(test010C)}")
            var clean010C = cleanHexResponse(test010C.raw)

            if (clean0100.contains("4100") || clean010C.contains("410C")) {
                obdSuccess = true
                elmState = ElmDiagnosticState.OBD_READY
                state = DiagState.CONNECTED
                appendLog("==> OBD_READY: ЕБУ двигуна онлайн через CAN 11/500 Functional (7DF)!")
            }

            // Strategy 2: CAN 11-bit 500k with Physical Engine ECU addressing (7E0 -> 7E8)
            // Essential for Golf 5 / PQ35 when Gateway filters broadcast 7DF
            if (!obdSuccess) {
                appendLog("WARN: 7DF не відповів, пробуємо Physical Engine addressing (ATSH7E0 -> ATCRA7E8)...")
                transport.sendCommand("ATSH7E0", 1000L)
                transport.sendCommand("ATCRA7E8", 1000L)

                val csResp2 = transport.sendCommand("ATCS", 1000L)
                appendLog("CAN Status (7E0): ${csResp2.raw.trim()}")

                test0100 = transport.sendCommand("0100", 7000L)
                appendLog("RX (0100 @ 7E0) << ${formatRx(test0100)}")
                clean0100 = cleanHexResponse(test0100.raw)

                test010C = transport.sendCommand("010C", 3000L)
                appendLog("RX (010C @ 7E0) << ${formatRx(test010C)}")
                clean010C = cleanHexResponse(test010C.raw)

                if (clean0100.contains("4100") || clean010C.contains("410C")) {
                    obdSuccess = true
                    elmState = ElmDiagnosticState.OBD_READY
                    state = DiagState.CONNECTED
                    appendLog("==> OBD_READY: ЕБУ двигуна онлайн через Physical CAN (7E0/7E8)!")
                }
            }

            // Strategy 3: Clean Auto-Protocol (ATSP0) with cleared filters
            if (!obdSuccess) {
                appendLog("WARN: CAN 500k не відповів, пробуємо чистий ATSP0 Auto-Protocol...")
                transport.sendCommand("ATCRA", 1000L)
                transport.sendCommand("ATAR", 1000L)
                transport.sendCommand("ATSP0", 2000L)

                test0100 = transport.sendCommand("0100", 8000L)
                appendLog("RX (0100 @ Auto) << ${formatRx(test0100)}")
                clean0100 = cleanHexResponse(test0100.raw)

                test010C = transport.sendCommand("010C", 4000L)
                appendLog("RX (010C @ Auto) << ${formatRx(test010C)}")
                clean010C = cleanHexResponse(test010C.raw)

                if (clean0100.contains("4100") || clean010C.contains("410C")) {
                    obdSuccess = true
                    val dpResp = transport.sendCommand("ATDP", 1000L)
                    val dpnResp = transport.sendCommand("ATDPN", 1000L)
                    elmState = ElmDiagnosticState.OBD_READY
                    state = DiagState.CONNECTED
                    appendLog("==> OBD_READY: ЕБУ знайдено через авто-протокол: ${dpResp.raw.trim()} (${dpnResp.raw.trim()})!")
                }
            }

            if (!obdSuccess) {
                val err = "ЕБУ двигуна не відповідає на OBD-II Mode 01 (0100/010C).\nПеревірте, щоб запалювання було увімкнене (або заведіть двигун) та адаптер був щільно вставлений у роз'єм OBD!"
                lastError = err
                state = DiagState.ERROR
                elmState = ElmDiagnosticState.ERROR
                appendLog("ERR: $err")
                transport.disconnect()
                return@withContext false
            }

            return@withContext true
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
                val tpGroup = when (groupNum) {
                    11 -> tp20Transport.readGroup011()
                    8 -> tp20Transport.readGroup008()
                    3 -> tp20Transport.readGroup003()
                    else -> null
                }
                return@withContext tpGroup
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
