package com.vag.vcdsandroid.adapters

import androidx.annotation.VisibleForTesting
import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import com.vag.vcdsandroid.hardware.UsbFtdiDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class B03InterfaceProbeResult(
    val identityText: String,
    val probePayload: ByteArray,
    val identifyPayload: ByteArray,
    val statusPayload: ByteArray,
    val modePayload: ByteArray,
    val elapsedMs: Long
)

/**
 * Adapter transport for Ross-Tech HEX-USB+CAN / B03-V2 FTDI clones (VID 0403, PID FA24).
 *
 * SAFETY & ZERO-TX POLICY:
 * 1. Diagnostic ECU communication (01-Engine, DTC, measuring blocks) remains strictly **ZERO-TX**
 *    via [transact], which unconditionally returns [AdapterResponse.Unsupported].
 * 2. Candidate adapter commands ([CandidateB03Command]) evaluated via [executeCandidateCommand]
 *    strictly maintain **ZERO-TX** until promoted by dynamic trace evidence.
 * 3. Link parameters are configured to candidate values (115200 baud hypothesis, 8N1).
 * 4. Raw developer transmission is isolated under [transactRawDebug] behind an explicit debug flag.
 */
class HexB03Adapter(
    override val driver: HardwareDriver,
    val serialNumber: String? = null,
    val configuredBaudRate: Int? = null,
    private val isDebugBuild: Boolean = com.vag.vcdsandroid.BuildConfig.DEBUG
) : AdapterTransport {

    companion object {
        const val ROSS_TECH_VID = 0x0403
        const val ROSS_TECH_PID_FA24 = 0xFA24

        /**
         * Candidate MCU transport baud rate hypothesis.
         */
        const val CANDIDATE_BAUD_RATE = 115200

        /**
         * Strict allowlist of permissible read-only diagnostic service IDs (ISO 14230 / KWP2000).
         * Any service not in this allowlist is unconditionally blocked from transmission.
         */
        val ALLOWED_READ_SERVICES = setOf(
            0x1A.toByte(), // ReadEcuIdentification
            0x21.toByte(), // ReadDataByLocalIdentifier (Measuring groups)
            0x22.toByte(), // ReadDataByIdentifier
            0x18.toByte(), // ReadDiagnosticTroubleCodesByStatus
            0x13.toByte(), // ReadDiagnosticTroubleCodes
            0x17.toByte(), // ReadStatusOfDiagnosticTroubleCodes
            0x3E.toByte(), // TesterPresent
            0x81.toByte(), // StartCommunication
            0x82.toByte()  // StopCommunication
        )
    }

    private var traceListener: ((direction: String, data: ByteArray) -> Unit)? = null
    private val streamDecoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)

    override val identity: AdapterIdentity
        get() = AdapterIdentity(
            modelName = "Ross-Tech HEX-USB+CAN (B03-V2 Clone)",
            hardwareFamily = "FTDI FT232R + candidate MCU (ATmega162 unconfirmed)",
            serialNumber = serialNumber,
            firmwareVersion = null,
            isClone = true,
            capabilities = setOf(
                AdapterCapability.RAW_PACKET_TRACE
            ),
            status = AdapterStatus.EXPERIMENTAL
        )

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        val baud = configuredBaudRate ?: return@withContext Result.failure(
            IllegalStateException(
                "B03-V2 MCU UART baud rate is UNKNOWN. " +
                "Must be extracted from USBPcap capture or D2XX trace before link activation."
            )
        )

        // FTDI configuration matching candidate D2XX parameters (HYPOTHESIS):
        // 8N1, 115200 baud, DTR/RTS lines configured for candidate MCU.
        val params = ConnectionParameters(
            baudRate = baud,
            dataBits = 8,
            stopBits = 1,
            parity = 0,
            dtr = false,
            rts = false
        )
        streamDecoder.reset()
        driver.open(params)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        streamDecoder.reset()
        driver.close()
    }

    override suspend fun identify(): AdapterIdentity = withContext(Dispatchers.IO) {
        identity
    }

    /**
     * Minimal cable-only proof of communication.
     *
     * This reproduces only the capture-grounded plaintext interface exchange used by
     * Ross-Tech-style 0403:FA24 HEX cables: 0x02 probe followed by 0x04 identify.
     * It deliberately stops before session setup/auth and sends no ECU diagnostic request.
     *
     * Public live captures of the same FA24 interface family establish:
     * - FTDI setup: 8N1, 9600 -> 19200 -> 115200, 1 ms latency, DTR/RTS clear;
     * - flat S/M framing with total-length byte and XOR checksum;
     * - 0x02 probe and 0x04 identify, whose reply contains "ROSSTECH".
     *
     * Success here proves Android <-> cable bidirectional communication only.
     */
    suspend fun probeInterface(timeoutMs: Long = 1000): Result<B03InterfaceProbeResult> =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            streamDecoder.reset()

            val openResult = openFa24Transport()
            if (openResult.isFailure) {
                return@withContext Result.failure(
                    openResult.exceptionOrNull() ?: IllegalStateException("Unable to open FA24 interface")
                )
            }

            val probeFrame = sendInterfaceCommand(
                opcode = HexB03Constants.OPCODE_PROBE,
                timeoutMs = timeoutMs
            ) ?: run {
                close()
                return@withContext Result.failure(
                    IllegalStateException("FA24 probe timed out: no valid 0x02 reply")
                )
            }

            val identifyFrame = sendInterfaceCommand(
                opcode = HexB03Constants.OPCODE_IDENTIFY,
                timeoutMs = timeoutMs
            ) ?: run {
                close()
                return@withContext Result.failure(
                    IllegalStateException("FA24 identify timed out: no valid 0x04 reply")
                )
            }

            val identityText = parseInterfaceIdentity(identifyFrame.payload)
                ?: run {
                    close()
                    return@withContext Result.failure(
                        IllegalStateException(
                            "FA24 identify replied, but the ROSSTECH identity string was not present"
                        )
                    )
                }

            // The user's returned identity A89D010009 matches the independently captured
            // FA24 family sample byte-for-byte. The next two read-only control queries are
            // part of that same plaintext open sequence and do not address an ECU.
            val statusFrame = sendInterfaceCommand(
                opcode = HexB03Constants.OPCODE_STATUS,
                timeoutMs = timeoutMs
            ) ?: run {
                close()
                return@withContext Result.failure(
                    IllegalStateException("FA24 status query timed out: no valid 0x82 reply")
                )
            }

            val modeFrame = sendInterfaceCommand(
                opcode = HexB03Constants.OPCODE_READ_BOOT,
                timeoutMs = timeoutMs
            ) ?: run {
                close()
                return@withContext Result.failure(
                    IllegalStateException("FA24 mode query timed out: no valid 0x0D reply")
                )
            }

            // Success is intentionally left OPEN. This is the M1/M2 staging state:
            // Android has a live, initialized bidirectional link to the physical interface.
            Result.success(
                B03InterfaceProbeResult(
                    identityText = identityText,
                    probePayload = probeFrame.payload,
                    identifyPayload = identifyFrame.payload,
                    statusPayload = statusFrame.payload,
                    modePayload = modeFrame.payload,
                    elapsedMs = System.currentTimeMillis() - started
                )
            )
        }

    /**
     * Reads current boot/operating mode from adapter via HC::ReadBoot (opcode 0x0D).
     *
     * Returns:
     * - [HexB03Constants.BOOT_MODE_LEGACY_DUMB] (0x00): Legacy dumb K-Line pass-through mode
     * - [HexB03Constants.BOOT_MODE_SMART] (0x02): Intelligent / Smart mode
     */
    suspend fun readBootMode(timeoutMs: Long = 1000): Result<Byte> = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            val opened = openFa24Transport()
            if (opened.isFailure) {
                return@withContext Result.failure(
                    opened.exceptionOrNull() ?: IllegalStateException("Unable to open FA24 transport")
                )
            }
        }

        val modeFrame = sendInterfaceCommand(
            opcode = HexB03Constants.OPCODE_READ_BOOT,
            timeoutMs = timeoutMs
        ) ?: return@withContext Result.failure(
            IllegalStateException("FA24 mode query timed out: no valid 0x0D reply")
        )

        if (modeFrame.payload.isEmpty()) {
            return@withContext Result.failure(
                IllegalStateException("FA24 mode query returned empty payload")
            )
        }

        Result.success(modeFrame.payload[0])
    }

    /**
     * Switches the adapter from Intelligent / Smart Mode into Legacy Dumb K-Line mode.
     *
     * Reverse-engineered from VCDS 26.3 x64:
     * - HC::SetBoot (0x140083208) sends opcode 0x0E with mode parameter in dl (0x00 for dumb mode).
     * - Wire request frame: [0x53, 0x05, 0x0E, 0x00, 0x58].
     * - Wire reply frame:   [0x4D, 0x04, 0xFE, 0xB7] (opcode 0xFE ACK).
     * - HC::ReadBoot (0x1400832B4) verifies the mode transition (0x0D returning payload 0x00).
     *
     * Once switched, the MCU ceases S/M protocol framing and acts as a transparent UART level-shifter
     * to the OBD-II K-Line (transceiver SI9243A/L9637D), allowing standard 10400-baud KWP slow-init.
     */
    suspend fun setLegacyDumbMode(timeoutMs: Long = 1000): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            val opened = openFa24Transport()
            if (opened.isFailure) {
                return@withContext Result.failure(
                    opened.exceptionOrNull() ?: IllegalStateException("Unable to open FA24 transport")
                )
            }
        }

        // 1. Check current mode; if already 0x00, avoid redundant rewrite
        val currentMode = readBootMode(timeoutMs).getOrNull()
        if (currentMode == HexB03Constants.BOOT_MODE_LEGACY_DUMB) {
            return@withContext Result.success(true)
        }

        // 2. Transmit HC::SetBoot(0)
        val ackFrame = sendInterfaceCommand(
            opcode = HexB03Constants.OPCODE_SET_BOOT,
            payload = byteArrayOf(HexB03Constants.BOOT_MODE_LEGACY_DUMB),
            expectedOpcode = HexB03Constants.OPCODE_ACK,
            timeoutMs = timeoutMs
        ) ?: return@withContext Result.failure(
            IllegalStateException("HC::SetBoot(0) timed out: no 0xFE ACK reply from cable")
        )

        // 3. Verify transition with HC::ReadBoot (0x0D)
        val verifiedMode = readBootMode(timeoutMs).getOrNull()
        if (verifiedMode != HexB03Constants.BOOT_MODE_LEGACY_DUMB) {
            return@withContext Result.failure(
                IllegalStateException(
                    "HC::SetBoot(0) ACKed, but ReadBoot returned 0x%02X instead of 0x00"
                        .format(verifiedMode?.toInt() ?: -1)
                )
            )
        }

        Result.success(true)
    }

    /**
     * Switches the adapter back to Intelligent / Smart Mode (HC::SetBoot(2)).
     *
     * Wire request frame: [0x53, 0x05, 0x0E, 0x02, 0x5A].
     * Wire reply frame:   [0x4D, 0x04, 0xFE, 0xB7] (opcode 0xFE ACK).
     */
    suspend fun setIntelligentMode(timeoutMs: Long = 1000): Result<Boolean> = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            val opened = openFa24Transport()
            if (opened.isFailure) {
                return@withContext Result.failure(
                    opened.exceptionOrNull() ?: IllegalStateException("Unable to open FA24 transport")
                )
            }
        }

        val currentMode = readBootMode(timeoutMs).getOrNull()
        if (currentMode == HexB03Constants.BOOT_MODE_SMART) {
            return@withContext Result.success(true)
        }

        val ackFrame = sendInterfaceCommand(
            opcode = HexB03Constants.OPCODE_SET_BOOT,
            payload = byteArrayOf(HexB03Constants.BOOT_MODE_SMART),
            expectedOpcode = HexB03Constants.OPCODE_ACK,
            timeoutMs = timeoutMs
        ) ?: return@withContext Result.failure(
            IllegalStateException("HC::SetBoot(2) timed out: no 0xFE ACK reply from cable")
        )

        val verifiedMode = readBootMode(timeoutMs).getOrNull()
        if (verifiedMode != HexB03Constants.BOOT_MODE_SMART) {
            return@withContext Result.failure(
                IllegalStateException(
                    "HC::SetBoot(2) ACKed, but ReadBoot returned 0x%02X instead of 0x02"
                        .format(verifiedMode?.toInt() ?: -1)
                )
            )
        }

        Result.success(true)
    }

    private suspend fun openFa24Transport(): Result<Unit> {
        return when (val hw = driver) {
            is UsbFtdiDriver -> hw.openRossTechFa24()
            else -> {
                // Test/fallback driver path mirrors the capture-grounded FA24 settings.
                val opened = hw.open(
                    ConnectionParameters(
                        baudRate = 9_600,
                        dataBits = 8,
                        stopBits = 1,
                        parity = 0,
                        dtr = false,
                        rts = false
                    )
                )
                if (opened.isSuccess) {
                    hw.purge()
                    hw.setBaudRate(19_200)
                    hw.setBaudRate(115_200)
                    hw.setDtr(false)
                    hw.setRts(false)
                }
                opened
            }
        }
    }

    private suspend fun sendInterfaceCommand(
        opcode: Byte,
        payload: ByteArray = ByteArray(0),
        expectedOpcode: Byte = opcode,
        timeoutMs: Long
    ): HexB03Frame? {
        val request = HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_HOST,
            opcode = opcode,
            payload = payload
        )
        traceListener?.invoke("TX", request)

        val written = driver.write(request)
        if (written != request.size) return null

        val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
        val readBuffer = ByteArray(512)

        while (System.nanoTime() < deadlineNs) {
            val remainingMs = ((deadlineNs - System.nanoTime()) / 1_000_000L)
                .coerceAtLeast(1L)
            val count = driver.read(readBuffer, minOf(100L, remainingMs))
            if (count < 0) return null
            if (count == 0) {
                delay(2)
                continue
            }

            val chunk = readBuffer.copyOf(count)
            traceListener?.invoke("RX", chunk)
            val frames = streamDecoder.feed(chunk)
            val matched = frames.firstOrNull { it.opcode == expectedOpcode }
            if (matched != null) return matched
        }
        return null
    }

    private fun parseInterfaceIdentity(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        val asciiLen = payload.indexOfFirst { b ->
            val v = b.toInt() and 0xFF
            v < 0x20 || v > 0x7E
        }.let { if (it < 0) payload.size else it }

        if (asciiLen == 0) return null
        val tag = payload.copyOfRange(0, asciiLen).toString(Charsets.US_ASCII)
        if (!tag.contains("ROSSTECH", ignoreCase = true)) return null

        val version = payload.copyOfRange(asciiLen, payload.size)
            .dropWhile { it == 0.toByte() }
        return if (version.isEmpty()) {
            tag
        } else {
            tag + " " + version.joinToString("") { "%02X".format(it.toInt() and 0xFF) }
        }
    }

    /**
     * Standard diagnostic transaction method.
     * MANDATORY SAFETY CONTRACT: Strictly ZERO-TX for vehicle diagnostic requests.
     * Always returns [AdapterResponse.Unsupported] without writing any bytes to the physical driver.
     */
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        // Zero-TX guarantee: Never write unverified diagnostic requests to physical hardware
        AdapterResponse.Unsupported
    }

    /**
     * Evaluates a candidate read-only adapter command (e.g. ProbePing, Identify).
     * MANDATORY ZERO-TX CONTRACT: Strictly returns [AdapterResponse.Unsupported]
     * without writing any bytes to physical hardware until an evidence gate promotes
     * the command to PROVEN_DYNAMIC.
     */
    suspend fun executeCandidateCommand(
        command: CandidateB03Command,
        timeoutMs: Long = 1000
    ): AdapterResponse = withContext(Dispatchers.IO) {
        // Zero-TX guarantee: Do not transmit unverified hypothesis frames to physical hardware
        AdapterResponse.Unsupported
    }

    /**
     * Explicit developer-only raw transaction interface for reverse-engineering exploration.
     * Isolated from normal diagnostic flows. Requires [enableUnsafeDeveloperRawTx] = true.
     */
    @VisibleForTesting
    suspend fun transactRawDebug(
        request: ByteArray,
        timeoutMs: Long,
        enableUnsafeDeveloperRawTx: Boolean = false
    ): AdapterResponse = withContext(Dispatchers.IO) {
        // Secondary Guardrail: Block any write/adaptation/flashing requests even in raw debug mode
        val violation = assertReadOnlyGuardrails(request)
        if (violation != null) {
            return@withContext AdapterResponse.Error("SECURITY VIOLATION: $violation")
        }

        if (!isDebugBuild) {
            return@withContext AdapterResponse.Error(
                "Raw transmission blocked: transactRawDebug is strictly disabled in release builds."
            )
        }

        if (!enableUnsafeDeveloperRawTx) {
            return@withContext AdapterResponse.Error(
                "Raw transmission blocked: enableUnsafeDeveloperRawTx must be explicitly set for developer probe mode."
            )
        }

        if (!driver.isConnected) {
            return@withContext AdapterResponse.Error("FTDI driver is not open")
        }

        if (request.isEmpty()) {
            return@withContext AdapterResponse.Unsupported
        }


        traceListener?.invoke("TX", request)
        val startTime = System.currentTimeMillis()

        driver.purge()
        val written = driver.write(request)
        if (written < 0) {
            return@withContext AdapterResponse.Error("Failed to write to FTDI bulk endpoint")
        }

        val buf = ByteArray(256)
        val readCount = driver.read(buf, timeoutMs)
        val elapsed = System.currentTimeMillis() - startTime

        if (readCount <= 0) {
            return@withContext AdapterResponse.Timeout
        }

        val rxData = buf.copyOf(readCount)
        traceListener?.invoke("RX", rxData)
        AdapterResponse.Success(rxData, elapsed)
    }

    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {
        traceListener = listener
    }

    /**
     * Inspects a diagnostic payload to ensure it conforms to the strict read-only allowlist.
     * Blocks any un-whitelisted, non-read, or potentially modifying service ID.
     */
    fun assertReadOnlyGuardrails(payload: ByteArray): String? {
        if (payload.isEmpty()) return null

        // Extract service ID:
        // Format 1: Direct service request [sid, ...]
        // Format 2: Framed ISO 14230 header [fmt/dest, target, source, sid, ...]
        val sid = if (payload.size >= 4 && (payload[0].toInt() and 0xC0) == 0x80) {
            payload[3]
        } else {
            payload[0]
        }

        if (!ALLOWED_READ_SERVICES.contains(sid)) {
            return "Blocked non-whitelisted diagnostic service: 0x%02X (strict read-only allowlist enforced)".format(sid)
        }
        return null
    }
}
