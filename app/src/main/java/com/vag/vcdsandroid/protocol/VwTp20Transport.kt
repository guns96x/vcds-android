package com.vag.vcdsandroid.protocol

import android.util.Log
import com.vag.vcdsandroid.bluetooth.BluetoothElmTransport
import com.vag.vcdsandroid.bluetooth.ElmResponse
import com.vag.vcdsandroid.model.MeasuringGroup
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * VW Transport Protocol 2.0 (VW TP 2.0) implementation over CAN (ISO 15765 11-bit 500k)
 * using an ELM327 Bluetooth adapter in raw CAN mode (ATCAF0, ATV1).
 *
 * Designed specifically for PQ35 platform (VW Golf 5 1.9 TDI BLS, Bosch EDC16U34).
 */
class VwTp20Transport(
    private val transport: BluetoothElmTransport,
    private val log: (String) -> Unit = {}
) {
    companion object {
        private const val TAG = "ELM_TP20"
        private const val SETUP_CAN_ID = "200"
        private const val SETUP_RX_ID = "201"
        private const val DEFAULT_TX_ID = "740"
        private const val DEFAULT_RX_ID = "300"
    }

    var isChannelOpen: Boolean = false
        private set

    var txCanId: String = DEFAULT_TX_ID
        private set

    var rxCanId: String = DEFAULT_RX_ID
        private set

    private var txSequence: Int = 0
    private var rxSequence: Int = 0

    // Timing parameters negotiated with ECU (in ms)
    private var t1Ms: Long = 100L
    private var t3Ms: Long = 100L
    private var blockSize: Int = 15

    private fun appendLog(msg: String) {
        Log.i(TAG, msg)
        log(msg)
    }

    /**
     * Probes whether the ELM327 adapter supports the required raw CAN commands:
     * - ATCAF0 (Turn off ISO-TP auto-formatting)
     * - ATV1   (Variable CAN DLC - critical for TP 2.0 variable length frames)
     * - ATCFC0 (Turn off ISO Flow Control)
     * - ATBI   (Bypass initialization)
     */
    suspend fun probeCapabilities(): Boolean {
        appendLog("==> Перевірка CAN-можливостей ELM327 для VW TP 2.0...")

        val probeCmds = listOf(
            "ATSP6" to "Встановлення протоколу ISO 15765-4 CAN 11-bit 500k (Golf 5)",
            "ATBI" to "Обхід ISO-15765 ініціалізації (дозволяє прямі CAN-кадри)",
            "ATCAF0" to "Вимкнення ISO-TP форматування",
            "ATV1" to "Підтримка змінної довжини кадру (Variable DLC)",
            "ATCFC0" to "Вимкнення ISO Flow Control",
            "ATAL" to "Дозвіл довгих кадрів (Allow Long)",
            "ATAT0" to "Фіксований таймінг адаптера",
            "ATST32" to "Встановлення таймауту 200мс (0x32 * 4мс) для надійного узгодження TP 2.0",
            "ATR1" to "Увімкнення відповідей"
        )

        for ((cmd, desc) in probeCmds) {
            val resp = transport.sendCommand(cmd, 1500L)
            appendLog("TX >> $cmd [$desc] -> RX: ${resp.raw.trim()}")
            if (resp.raw.contains("?") || resp.timedOut) {
                if (cmd == "ATV1") {
                    appendLog("ERR: Адаптер не підтримує ATV1 (Variable DLC). VW TP 2.0 вимагає кадри змінної довжини!")
                    return false
                }
                if (cmd == "ATCAF0") {
                    appendLog("ERR: Адаптер не підтримує ATCAF0 (Raw CAN).")
                    return false
                }
            }
        }

        appendLog("==> ELM327 підтримує всі необхідні команди для VW TP 2.0!")
        return true
    }

    /**
     * Initiates VW TP 2.0 Channel Setup with Engine ECU (Logical Address 0x01).
     * Broadcasts to CAN ID 0x200, expects response from CAN ID 0x201.
     */
    suspend fun setupChannel(ecuLogicalAddress: Byte = 0x01): Boolean {
        isChannelOpen = false
        txSequence = 0
        rxSequence = 0
        // Ensure any previous unclosed channel is cleanly terminated on txCanId
        if (txCanId.isNotEmpty()) {
            transport.sendCommand("ATSH$txCanId", 500L)
            transport.sendCommand("A8", 200L)
            delay(50L)
        }

        // Set 200ms timeout for setup handshake so ECU has sufficient time to reply to D0 and A0
        transport.sendCommand("ATST32", 500L)
        // Configure ELM receive filter (0x200..0x3FF) so both 0x201 (setup) and 0x300 (ECU channel)
        // are accepted by ELM hardware without mid-handshake filter reconfiguration latency
        transport.sendCommand("ATCM600", 500L)
        transport.sendCommand("ATCF200", 500L)
        transport.sendCommand("ATSH$SETUP_CAN_ID", 1000L)

        // Setup channel request (DLC 7):
        // [Destination=0x01 (Engine), Opcode=0xC0 (Setup Request), RX_Prefix=0x00, RX_ID=0x10, TX_Prefix=0x00, TX_ID=0x03, AppType=0x01 (KWP2000)]
        val setupPayload = String.format(Locale.US, "%02X C0 00 10 00 03 01", ecuLogicalAddress)
        appendLog("TX >> $setupPayload (Channel Setup to 0x01 Engine)...")

        val resp = transport.sendCommand(setupPayload, 3000L)
        val clean = cleanHex(resp.raw)
        appendLog("RX << ${resp.raw.trim()} [clean=$clean]")

        // Reject if empty, NO DATA, ERROR, or negative responses (D6, D7, D8)
        val isNegativeResponse = clean.contains("D6") || clean.contains("D7") || clean.contains("D8")
        val d0Idx = clean.indexOf("D0")

        if (clean.isEmpty() ||
            resp.raw.contains("NO DATA", ignoreCase = true) ||
            resp.raw.contains("ERROR", ignoreCase = true) ||
            resp.raw.contains("CAN ERROR", ignoreCase = true) ||
            resp.raw.contains("?") ||
            isNegativeResponse ||
            d0Idx < 0
        ) {
            appendLog("ERR: TP 2.0 канал не відкрито (немає позитивної відповіді D0). Отримано: ${resp.raw.trim()}")
            return false
        }

        // Expected response format from 0x201 (DLC 7):
        // 00 D0 <rxLo> <rxHi> <txLo> <txHi> 01
        // Example: 00 D0 00 03 40 07 01 -> rxID=0x0300, txID=0x0740
        if (d0Idx + 10 <= clean.length) {
            val rxLo = clean.substring(d0Idx + 2, d0Idx + 4)
            val rxHi = clean.substring(d0Idx + 4, d0Idx + 6)
            val txLo = clean.substring(d0Idx + 6, d0Idx + 8)
            val txHi = clean.substring(d0Idx + 8, d0Idx + 10)

            val parsedRxId = ((rxHi.toIntOrNull(16) ?: 3) shl 8) or (rxLo.toIntOrNull(16) ?: 0x00)
            val parsedTxId = ((txHi.toIntOrNull(16) ?: 7) shl 8) or (txLo.toIntOrNull(16) ?: 0x40)

            txCanId = String.format(Locale.US, "%03X", parsedTxId)
            rxCanId = String.format(Locale.US, "%03X", parsedRxId)
        } else {
            appendLog("ERR: Відповідь D0 занадто коротка або пошкоджена: $clean")
            return false
        }

        // CRITICAL TIMING: TP 2.0 channel setup timer T_E is 100ms!
        // Switch ATSH (18ms) and immediately send A0 without ANY logging overhead in between
        transport.sendCommand("ATSH$txCanId", 1000L)
        val paramResp = transport.sendCommand("A0 0F 8A FF 32 FF", 2000L)

        appendLog("==> TP 2.0 канал: TX = 0x$txCanId, RX = 0x$rxCanId")
        appendLog("2. Узгодження таймінгів TP 2.0 (A0)...")
        val paramClean = cleanHex(paramResp.raw)
        appendLog("RX << ${paramResp.raw.trim()} [clean=$paramClean]")

        if (paramClean.contains("A8")) {
            appendLog("ERR: ECU відхилив узгодження таймінгів (надіслав A8 Disconnect)!")
            transport.sendCommand("A8", 200L)
            return false
        }

        if (paramClean.contains("A1")) {
            appendLog("==> Параметри TP 2.0 успішно узгоджено (A1 ACK)!")
        } else {
            appendLog("WARN: Відповідь на A0 неочікувана: ${paramResp.raw.trim()}")
            return false
        }

        // Set exact RX filter for the ECU channel now that handshake is complete
        transport.sendCommand("ATCRA$rxCanId", 500L)

        // Step 3: Start KWP2000 Diagnostic Session (10 89)
        val sessionOk = startKwpSession()
        if (!sessionOk) {
            appendLog("ERR: Не вдалося активувати KWP2000 діагностичну сесію (10 89)!")
            return false
        }

        // Switch to fast 40ms timeout for high-speed live streaming without frame truncation
        transport.sendCommand("ATST0A", 500L)
        isChannelOpen = true
        return true
    }

    /**
     * Sends KWP2000 StartDiagnosticSession (0x10 0x89) over TP 2.0.
     */
    private suspend fun startKwpSession(): Boolean {
        appendLog("3. Запуск KWP2000 діагностичної сесії (10 89)...")
        // Sequence 0, Opcode 0x10, Length 0x0002, Service 0x10 0x89
        val cmd = "10 00 02 10 89"
        val resp = transport.sendCommand(cmd, 2500L)
        appendLog("RX << ${resp.raw.trim()}")

        val clean = cleanHex(resp.raw)
        // Per Sol 5.6 audit: strictly require 5089 for positive KWP session start
        if (clean.contains("5089")) {
            appendLog("==> KWP2000 діагностична сесія активна (50 89)!")
            // Acknowledge session response with B1
            transport.sendCommand("B1", 500L)
            txSequence = 1
            return true
        }
        return false
    }

    /**
     * Polls VAG Measuring Block Group 011 (Turbo Boost & N75 Duty Cycle)
     * via KWP2000 Service 0x21, Local Identifier 0x0B.
     */
    suspend fun readGroup011(): MeasuringGroup? {
        return readMeasuringGroup(0x0B)
    }

    /**
     * Polls VAG Measuring Block Group 003 (MAF & EGR Duty Cycle)
     * via KWP2000 Service 0x21, Local Identifier 0x03.
     */
    suspend fun readGroup003(): MeasuringGroup? {
        return readMeasuringGroup(0x03)
    }

    /**
     * Polls VAG Measuring Block Group 008 (Injection Quantity Limits)
     * via KWP2000 Service 0x21, Local Identifier 0x08.
     */
    suspend fun readGroup008(): MeasuringGroup? {
        return readMeasuringGroup(0x08)
    }

    /**
     * Reads a specific VAG Measuring Group by its group number.
     * Command: KWP Service 0x21, Identifier <groupNum>.
     */
    private var consecutiveFails: Int = 0

    /**
     * Sends an arbitrary KWP2000 payload over the already negotiated VW TP 2.0
     * channel and returns the reassembled KWP payload.
     */
    suspend fun requestKwp(payload: ByteArray, timeoutMs: Long = 2000L): ByteArray? {
        require(payload.isNotEmpty() && payload.size <= 0xFF) {
            "KWP payload length must be 1..255"
        }

        if (!isChannelOpen) {
            val ok = setupChannel(0x01)
            if (!ok) {
                delay(1000L)
                return null
            }
        }

        val currentTxSeq = txSequence and 0x0F
        val currentTxOpcode = 0x10 or currentTxSeq
        val payloadHex = payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
        val queryCmd = String.format(
            Locale.US,
            "%02X 00 %02X %s",
            currentTxOpcode,
            payload.size,
            payloadHex
        )

        Log.i(TAG, "TX KWP >> $queryCmd")
        val resp = transport.sendCommand(queryCmd, timeoutMs)
        Log.i(TAG, "RX KWP << ${resp.raw.trim()} (${resp.elapsedMs}ms)")

        when (val result = Tp20FrameParser.parseKwp(resp.raw, currentTxSeq, minPayloadLength = 1)) {
            is Tp20Result.Success -> {
                if (result.needsAck && result.lastRxSeq >= 0) {
                    val ackCmd = String.format(Locale.US, "%02X", result.ackCode)
                    transport.sendCommand(ackCmd, 250L)
                }
                if (result.sawKeepAlive) {
                    transport.sendCommand("A1 0F 8A FF 4A FF", 250L)
                }
                txSequence = result.nextTxSeq
                consecutiveFails = 0
                return result.kwpPayload
            }

            is Tp20Result.PeerDisconnect -> {
                try { transport.sendCommand("A8", 200L) } catch (_: Exception) {}
                isChannelOpen = false
                txSequence = 0
                return null
            }

            is Tp20Result.PeerBusy -> return null

            is Tp20Result.Incomplete,
            is Tp20Result.ProtocolError,
            is Tp20Result.TimeoutOrNoData -> {
                consecutiveFails++
                if (consecutiveFails >= 2) {
                    isChannelOpen = false
                    setupChannel(0x01)
                    consecutiveFails = 0
                }
                return null
            }
        }
    }

    suspend fun readMeasuringGroup(groupNum: Int): MeasuringGroup? {
        if (!isChannelOpen) {
            val ok = setupChannel(0x01)
            if (!ok) {
                delay(1000L)
                return null
            }
        }

        val currentTxSeq = txSequence and 0x0F
        val currentTxOpcode = 0x10 or currentTxSeq
        // Query without trailing count so ELM collects all CAN frames (including Keep-Alive A3 if interleaved)
        // With ATST0A (40ms), query completes in ~50ms without leaving unread frames in ECU buffers
        val queryCmd = String.format(Locale.US, "%02X 00 02 21 %02X", currentTxOpcode, groupNum)

        Log.i(TAG, "TX Group $groupNum >> $queryCmd")
        val resp = transport.sendCommand(queryCmd, 1200L)
        Log.i(TAG, "RX Group $groupNum << ${resp.raw.trim()} (${resp.elapsedMs}ms)")

        when (val result = Tp20FrameParser.parse(resp.raw, groupNum, currentTxSeq)) {
            is Tp20Result.Success -> {
                // 1. Send ACK IMMEDIATELY before decode or logging!
                if (result.needsAck && result.lastRxSeq >= 0) {
                    val ackCmd = String.format(Locale.US, "%02X", result.ackCode)
                    Log.i(TAG, "TX ACK >> $ackCmd")
                    transport.sendCommand(ackCmd, 250L)
                }

                // 2. If peer sent Keep-Alive A3, respond immediately
                if (result.sawKeepAlive) {
                    Log.i(TAG, "Peer sent Keep-Alive A3! Replying with timing ACK...")
                    transport.sendCommand("A1 0F 8A FF 4A FF", 250L)
                }

                // 3. Advance txSequence only on successful transaction
                txSequence = result.nextTxSeq
                consecutiveFails = 0

                // 4. Decode payload into MeasuringGroup
                return MeasuringGroup.decode(result.kwpPayload)
            }

            is Tp20Result.PeerDisconnect -> {
                Log.w(TAG, "ECU sent A8 (Channel Disconnect)! Resetting channel state...")
                try { transport.sendCommand("A8", 200L) } catch (_: Exception) {}
                isChannelOpen = false
                txSequence = 0
                return null
            }

            is Tp20Result.PeerBusy -> {
                Log.w(TAG, "ECU sent 9x (Busy/Wait). Backing off briefly...")
                return null
            }

            is Tp20Result.Incomplete -> {
                Log.w(TAG, "Incomplete TP2.0 transaction: expected ${result.expectedLen} bytes, got ${result.actualLen}")
                consecutiveFails++
                if (consecutiveFails >= 2) {
                    Log.w(TAG, "$consecutiveFails consecutive failures, re-establishing channel...")
                    isChannelOpen = false
                    setupChannel(0x01)
                    consecutiveFails = 0
                }
                return null
            }

            is Tp20Result.ProtocolError -> {
                Log.w(TAG, "Protocol error: ${result.message}")
                consecutiveFails++
                if (consecutiveFails >= 2) {
                    isChannelOpen = false
                    setupChannel(0x01)
                    consecutiveFails = 0
                }
                return null
            }

            is Tp20Result.TimeoutOrNoData -> {
                consecutiveFails++
                if (consecutiveFails >= 2) {
                    Log.w(TAG, "$consecutiveFails consecutive timeouts, re-establishing channel...")
                    isChannelOpen = false
                    setupChannel(0x01)
                    consecutiveFails = 0
                }
                return null
            }
        }
    }

    /**
     * Sends a periodic TP 2.0 Keep-Alive frame (0xA3) to keep the channel open.
     */
    suspend fun sendKeepAlive() {
        if (!isChannelOpen) return
        transport.sendCommand("A3", 1000L)
    }

    /**
     * Closes the TP 2.0 Channel cleanly.
     */
    suspend fun closeChannel() {
        if (!isChannelOpen) return
        try {
            transport.sendCommand("A8", 1000L)
        } catch (_: Exception) {}
        isChannelOpen = false
    }

    private fun cleanHex(raw: String): String {
        val upper = raw.uppercase(Locale.ROOT)
        if (upper.contains("NO DATA") || upper.contains("ERROR") || upper.contains("UNABLE TO CONNECT")) {
            return ""
        }
        return upper.replace(Regex("[^0-9A-F]"), "")
    }
}
