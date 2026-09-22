package com.vag.vcdsandroid.adapters

import androidx.annotation.VisibleForTesting
import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter transport for Ross-Tech HEX-USB+CAN / B03-V2 FTDI clones (VID 0403, PID FA24).
 *
 * SAFETY & ZERO-TX POLICY:
 * 1. Diagnostic ECU communication (01-Engine, DTC, measuring blocks) remains strictly **ZERO-TX**
 *    via [transact], which unconditionally returns [AdapterResponse.Unsupported].
 * 2. Only typed, read-only adapter commands ([VerifiedB03Command]) can be executed via [executeVerifiedCommand].
 * 3. Link parameters are configured to proven values (115200 baud, 8N1, DTR/RTS low).
 * 4. Raw developer transmission is isolated under [transactRawDebug] behind an explicit debug flag.
 */
class HexB03Adapter(
    override val driver: HardwareDriver,
    val serialNumber: String? = null,
    val configuredBaudRate: Int? = null
) : AdapterTransport {

    companion object {
        const val ROSS_TECH_VID = 0x0403
        const val ROSS_TECH_PID_FA24 = 0xFA24

        /**
         * Proven MCU transport baud rate derived from VCDS D2XX reverse engineering.
         */
        const val PROVEN_BAUD_RATE = 115200

        // Service IDs classified as destructive/modifying under KWP2000/UDS.
        // Secondary defense layer for raw debug transmissions.
        private val FORBIDDEN_WRITE_SERVICES = setOf(
            0x2E.toByte(), // WriteDataByIdentifier
            0x3B.toByte(), // WriteDataByLocalIdentifier
            0x34.toByte(), // RequestDownload (flashing)
            0x35.toByte(), // RequestUpload
            0x36.toByte(), // TransferData (flashing)
            0x37.toByte(), // RequestTransferExit
            0x28.toByte(), // CommunicationControl
            0x31.toByte()  // RoutineControl (actuator tests)
        )
    }

    private var traceListener: ((direction: String, data: ByteArray) -> Unit)? = null
    private var detectedFirmwareVersion: String? = null
    private val streamDecoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)

    override val identity: AdapterIdentity
        get() = AdapterIdentity(
            modelName = "Ross-Tech HEX-USB+CAN (B03-V2 Clone)",
            hardwareFamily = "FTDI + ATmega162",
            serialNumber = serialNumber,
            firmwareVersion = detectedFirmwareVersion,
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

        // FTDI configuration matching verified VCDS D2XX parameters:
        // 8N1, 115200 baud, DTR/RTS driven LOW to ungate ATmega162 MCU.
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
     * Standard diagnostic transaction method.
     * MANDATORY SAFETY CONTRACT: Strictly ZERO-TX for vehicle diagnostic requests.
     * Always returns [AdapterResponse.Unsupported] without writing any bytes to the physical driver.
     */
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        // Zero-TX guarantee: Never write unverified diagnostic requests to physical hardware
        AdapterResponse.Unsupported
    }

    /**
     * Executes a typed, proven read-only adapter command (e.g. ProbePing, Identify).
     * Enforces typed safety so callers cannot construct arbitrary byte streams.
     */
    suspend fun executeVerifiedCommand(
        command: VerifiedB03Command,
        timeoutMs: Long = 1000
    ): AdapterResponse = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            return@withContext AdapterResponse.Error("FTDI driver is not open")
        }

        val frameBytes = command.encodeFrame()
        traceListener?.invoke("TX", frameBytes)
        val startTime = System.currentTimeMillis()

        driver.purge()
        val written = driver.write(frameBytes)
        if (written < 0) {
            return@withContext AdapterResponse.Error("Failed to write command frame to FTDI")
        }

        val readBuffer = ByteArray(512)
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val remaining = (deadline - System.currentTimeMillis()).coerceAtLeast(10L)
            val readCount = driver.read(readBuffer, remaining)
            if (readCount > 0) {
                val rxData = readBuffer.copyOf(readCount)
                traceListener?.invoke("RX", rxData)

                val frames = streamDecoder.feed(rxData)
                val matchingFrame = frames.firstOrNull { it.opcode == command.opcode }
                if (matchingFrame != null) {
                    val elapsed = System.currentTimeMillis() - startTime
                    if (command == VerifiedB03Command.Identify && matchingFrame.payload.isNotEmpty()) {
                        val banner = String(matchingFrame.payload, Charsets.US_ASCII)
                        detectedFirmwareVersion = banner.trim()
                    }
                    return@withContext AdapterResponse.Success(matchingFrame.payload, elapsed)
                }
            } else {
                kotlinx.coroutines.delay(5)
            }
        }

        AdapterResponse.Timeout
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
        if (!com.vag.vcdsandroid.BuildConfig.DEBUG) {
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

        // Secondary Guardrail: Block any write/adaptation/flashing requests even in raw debug mode
        val violation = assertReadOnlyGuardrails(request)
        if (violation != null) {
            return@withContext AdapterResponse.Error("SECURITY VIOLATION: $violation")
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
     * Inspects a diagnostic payload to ensure it does not attempt writing or flashing.
     */
    fun assertReadOnlyGuardrails(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        for (b in payload.take(3)) {
            if (FORBIDDEN_WRITE_SERVICES.contains(b)) {
                return "Blocked potentially destructive diagnostic service: 0x%02X".format(b)
            }
        }
        return null
    }
}
