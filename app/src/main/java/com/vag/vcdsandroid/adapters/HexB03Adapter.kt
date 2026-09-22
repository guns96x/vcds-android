package com.vag.vcdsandroid.adapters

import androidx.annotation.VisibleForTesting
import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Experimental adapter transport for Ross-Tech HEX-USB+CAN / B03-V2 FTDI clones (VID 0403, PID FA24).
 *
 * STRICT ZERO-TX EVIDENCE POLICY:
 * 1. PC <-> MCU framing format and command opcodes are UNKNOWN until verified via real USBPcap captures.
 * 2. MCU operating baud rate is UNKNOWN until derived from FTDI_SIO_SET_BAUDRATE control transfers in capture.
 * 3. Normal [transact] is strictly ZERO-TX: it immediately returns [AdapterResponse.Unsupported]
 *    without writing any bytes to the physical hardware.
 * 4. Raw developer transmission is isolated under [transactRawDebug] behind an explicit safety flag,
 *    and cannot be invoked by standard diagnostic flows.
 */
class HexB03Adapter(
    override val driver: HardwareDriver,
    val serialNumber: String? = null,
    val configuredBaudRate: Int? = null
) : AdapterTransport {

    companion object {
        const val ROSS_TECH_VID = 0x0403
        const val ROSS_TECH_PID_FA24 = 0xFA24

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

    override val identity: AdapterIdentity
        get() = AdapterIdentity(
            modelName = "Ross-Tech HEX-USB+CAN (B03-V2 Clone)",
            hardwareFamily = "FTDI FT232R + ATmega162 Candidate",
            serialNumber = serialNumber,
            firmwareVersion = detectedFirmwareVersion,
            isClone = true,
            capabilities = setOf(
                AdapterCapability.RAW_PACKET_TRACE
            ),
            status = AdapterStatus.EXPERIMENTAL
        )

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        if (configuredBaudRate == null) {
            // Baud rate is UNKNOWN from physical capture evidence.
            // Transport cannot be operated blindly without evidence-derived baud.
            return@withContext Result.failure(
                IllegalStateException(
                    "B03-V2 MCU UART baud rate is UNKNOWN. " +
                    "Must be extracted from USBPcap capture (FTDI_SIO_SET_BAUDRATE) before link activation."
                )
            )
        }

        val params = ConnectionParameters(
            baudRate = configuredBaudRate,
            dataBits = 8,
            stopBits = 1,
            parity = 0,
            dtr = false,
            rts = false
        )
        driver.open(params)
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        driver.close()
    }

    override suspend fun identify(): AdapterIdentity = withContext(Dispatchers.IO) {
        identity
    }

    /**
     * Standard diagnostic transaction method.
     * MANDATORY SAFETY CONTRACT: Strictly ZERO-TX until live capture evidence proves
     * host <-> MCU framing and command opcodes.
     * Always returns [AdapterResponse.Unsupported] without writing any bytes to the physical driver.
     */
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        // Zero-TX guarantee: Never write unverified bytes to uncharacterized hardware
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
     * Note: Once adapter framing is proven from USB capture, safety validation will be applied
     * to decoded typed frame structures rather than raw byte scans.
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
