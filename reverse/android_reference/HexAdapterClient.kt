package com.vcds.android.transport

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Ross-Tech HEX Adapter Client for Android
 * Implements high-level diagnostic operations proven from VCDS 26.3
 */
class HexAdapterClient(
    private val inputStream: InputStream,
    private val outputStream: OutputStream,
    private val baudRateSetter: (baud: Int) -> Unit
) {

    data class AdapterInfo(val version: String, val model: String, val isCanCapable: Boolean)
    data class EcuSession(val baudRate: Int, val keyByte1: Byte, val keyByte2: Byte, val syncByte: Byte)
    data class Group011Data(
        val engineRpm: Double,
        val specifiedBoostMbar: Double,
        val actualBoostMbar: Double,
        val n75DutyCyclePct: Double
    )

    /**
     * Opcode 0x02: Queries adapter firmware version and hardware capability.
     * Evidence: Ghidra VA 0x14007E988
     */
    @Throws(IOException::class)
    fun identify(): AdapterInfo {
        val requestPayload = byteArrayOf(0x02) // Opcode 0x02
        val frame = HexFraming.buildFrame(requestPayload)
        outputStream.write(frame)
        outputStream.flush()

        val resp = HexFraming.readFrame(inputStream, timeoutMs = 750)
        if (resp.isEmpty() || resp[0] != 0x02.toByte()) {
            throw IOException("Unexpected response opcode: ${resp.getOrNull(0)}")
        }

        val major = resp.getOrElse(1) { 0 }.toInt() and 0xFF
        val minor = resp.getOrElse(2) { 0 }.toInt() and 0xFF
        val modelChar = resp.getOrElse(3) { 0 }.toInt().toChar()
        val versionStr = "$major.$minor"

        val modelName = when (modelChar) {
            'C' -> "Ross-Tech HEX-USB (CAN)"
            'D' -> "Ross-Tech HEX-USB+CAN (Dual-K + CAN)"
            'F' -> "Ross-Tech Fast-HEX"
            else -> "Ross-Tech HEX ($modelChar)"
        }

        return AdapterInfo(version = versionStr, model = modelName, isCanCapable = modelChar == 'C' || modelChar == 'D' || modelChar == 'F')
    }

    /**
     * Opcode 0x03: Switches host-to-adapter UART baud rate to 115200.
     * Evidence: Ghidra VA 0x14007EC2C
     */
    @Throws(IOException::class)
    fun setSpeed115200() {
        val payload = byteArrayOf(
            0x03,             // Opcode
            0x00, 0xC2.toByte(), 0x01, 0x00 // 115200 Little-Endian (0x0001C200)
        )
        val frame = HexFraming.buildFrame(payload)
        outputStream.write(frame)
        outputStream.flush()

        // Phase 1: Wait for adapter ack 0xFE
        val resp1 = HexFraming.readFrame(inputStream, timeoutMs = 750)
        if (resp1.isEmpty() || resp1[0] != 0xFE.toByte()) {
            throw IOException("Adapter rejected baud switch request: ${resp1.getOrNull(0)}")
        }

        // Phase 2: Switch FTDI UART baud rate on host side
        baudRateSetter(115200)

        // Phase 3: Wait for adapter ready signal 0xFD at 115200
        val resp2 = HexFraming.readFrame(inputStream, timeoutMs = 750)
        if (resp2.isEmpty() || resp2[0] != 0xFD.toByte()) {
            throw IOException("Adapter ready handshake failed: ${resp2.getOrNull(0)}")
        }

        // Phase 4: Send confirmation 0xFE
        val confirmFrame = HexFraming.buildFrame(byteArrayOf(0xFE.toByte()))
        outputStream.write(confirmFrame)
        outputStream.flush()
    }

    /**
     * Opcode 0x84: Establishes K-Line / KWP session via autonomous adapter 5-baud wake-up.
     * Evidence: Ghidra VA 0x14007E3B4 & 0x14007E21D
     *
     * @param ecuAddress target controller (e.g. 0x01 for Engine)
     */
    @Throws(IOException::class)
    fun openEcu(ecuAddress: Byte = 0x01): EcuSession {
        // Calculate 7-bit odd parity for target address
        var parityCount = 0
        var temp = ecuAddress.toInt() and 0x7F
        while (temp > 0) {
            if ((temp and 1) != 0) parityCount++
            temp = temp ushr 1
        }
        val addressWithParity = if (parityCount % 2 == 0) {
            (ecuAddress.toInt() or 0x80).toByte()
        } else {
            (ecuAddress.toInt() and 0x7F).toByte()
        }

        val payload = byteArrayOf(
            0x84.toByte(),        // Opcode 0x84 (HEX_CMD_5BAUD_INIT)
            0x03,                 // Subcommand (K-Line init)
            addressWithParity,    // Address with 7-bit odd parity (0x81 for 0x01)
            0x00                  // Timing flags
        )

        val frame = HexFraming.buildFrame(payload)
        outputStream.write(frame)
        outputStream.flush()

        // 5-Baud wake-up takes ~2.2s on wire; timeout is 3300 ms (0xCE4 in VCDS)
        val resp = HexFraming.readFrame(inputStream, timeoutMs = 3300)
        if (resp.isEmpty() || resp[0] != 0x84.toByte()) {
            throw IOException("ECU 5-baud session establishment failed, resp: ${resp.getOrNull(0)}")
        }

        // Response format: [ 0x84, BaudHi, BaudLo, KB1, KB2, Sync=0x55 ]
        val baudHi = resp.getOrElse(1) { 0 }.toInt() and 0xFF
        val baudLo = resp.getOrElse(2) { 0 }.toInt() and 0xFF
        val detectedBaud = (baudHi shl 8) or baudLo
        val kb1 = resp.getOrElse(3) { 0 }
        val kb2 = resp.getOrElse(4) { 0 }
        val sync = resp.getOrElse(5) { 0 }

        if (sync != 0x55.toByte()) {
            throw IOException("ECU sync byte invalid: expected 0x55, got 0x${sync.toString(16)}")
        }

        return EcuSession(baudRate = detectedBaud, keyByte1 = kb1, keyByte2 = kb2, syncByte = sync)
    }

    /**
     * Reads Measuring Blocks Group 011 (Charge Pressure Control / Boost).
     * Evidence: Ghidra VA 0x14005708C, 0x14011FEB8, 0x14005C09A
     */
    @Throws(IOException::class)
    fun readGroup011(): Group011Data {
        // Send KWP2000 ReadDataByLocalIdentifier (0x21), LocalId = 0x0B (Group 11)
        val payload = byteArrayOf(0x21, 0x0B)
        val frame = HexFraming.buildFrame(payload)
        outputStream.write(frame)
        outputStream.flush()

        val resp = HexFraming.readFrame(inputStream, timeoutMs = 750)
        // Parse fields according to VAG formulas:
        // Field 1: Engine Speed (Formula 0x01: RPM = 0.2 * A * B)
        // Field 2: Specified MAP (Formula 0x07: mbar = 0.04 * A * B)
        // Field 3: Actual MAP (Formula 0x07: mbar = 0.04 * A * B)
        // Field 4: N75 Duty Cycle (Formula 0x19: % = 0.005 * A * B)
        return parseGroup011Payload(resp)
    }

    /**
     * Keepalive frame sent every 1000 ms to maintain ECU session active.
     * Evidence: Ghidra VA 0x14011A0D4
     */
    @Throws(IOException::class)
    fun keepAlive() {
        // KWP2000 TesterPresent service 0x3E
        val payload = byteArrayOf(0x3E)
        val frame = HexFraming.buildFrame(payload)
        outputStream.write(frame)
        outputStream.flush()
        try {
            HexFraming.readFrame(inputStream, timeoutMs = 500)
        } catch (_: IOException) {
            // Non-critical if keepalive ack dropped
        }
    }

    private fun parseGroup011Payload(resp: ByteArray): Group011Data {
        if (resp.size >= 14 && resp[0] == 0x61.toByte() && resp[1] == 0x0B.toByte()) {
            // KWP2000 format
            val rpm = 0.2 * (resp[2].toInt() and 0xFF) * (resp[3].toInt() and 0xFF)
            val specMbar = 0.04 * (resp[5].toInt() and 0xFF) * (resp[6].toInt() and 0xFF)
            val actMbar = 0.04 * (resp[8].toInt() and 0xFF) * (resp[9].toInt() and 0xFF)
            val dutyPct = 0.005 * (resp[11].toInt() and 0xFF) * (resp[12].toInt() and 0xFF)
            return Group011Data(rpm, specMbar, actMbar, dutyPct)
        } else if (resp.size >= 13 && resp[0] == 0xE7.toByte()) {
            // KWP1281 format: [ 0xE7, F1(3), F2(3), F3(3), F4(3) ]
            val rpm = 0.2 * (resp[2].toInt() and 0xFF) * (resp[3].toInt() and 0xFF)
            val specMbar = 0.04 * (resp[5].toInt() and 0xFF) * (resp[6].toInt() and 0xFF)
            val actMbar = 0.04 * (resp[8].toInt() and 0xFF) * (resp[9].toInt() and 0xFF)
            val dutyPct = 0.005 * (resp[11].toInt() and 0xFF) * (resp[12].toInt() and 0xFF)
            return Group011Data(rpm, specMbar, actMbar, dutyPct)
        }
        // Fallback default
        return Group011Data(0.0, 0.0, 0.0, 0.0)
    }
}
