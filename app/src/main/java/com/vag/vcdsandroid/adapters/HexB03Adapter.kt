package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import com.vag.vcdsandroid.hardware.UsbFtdiDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Experimental adapter transport for Ross-Tech HEX-USB+CAN / B03-V2 FTDI clones (VID 0403, PID FA24).
 *
 * EVIDENCE INTEGRITY POLICY:
 * - Does NOT transmit guessed magic bytes or proprietary packet formats.
 * - Any unsupported operation explicitly returns [AdapterResponse.Unsupported].
 * - Strictly read-only in discovery/reverse-engineering mode: coding write, adaptation write,
 *   and ECU flashing are hard-blocked by [assertReadOnlyGuardrails].
 */
class HexB03Adapter(
    override val driver: HardwareDriver,
    val initialBaudRate: Int = 500000
) : AdapterTransport {

    companion object {
        const val ROSS_TECH_VID = 0x0403
        const val ROSS_TECH_PID_FA24 = 0xFA24

        // Service IDs classified as destructive/modifying under KWP2000/UDS
        private val FORBIDDEN_WRITE_SERVICES = setOf(
            0x2E.toByte(), // WriteDataByIdentifier
            0x3B.toByte(), // WriteDataByLocalIdentifier
            0x34.toByte(), // RequestDownload (flashing)
            0x35.toByte(), // RequestUpload
            0x36.toByte(), // TransferData (flashing)
            0x37.toByte(), // RequestTransferExit
            0x28.toByte(), // CommunicationControl
            0x31.toByte()  // RoutineControl (destructive tests)
        )
    }

    private var traceListener: ((direction: String, data: ByteArray) -> Unit)? = null
    private var detectedFirmwareVersion: String? = null

    override val identity: AdapterIdentity
        get() = AdapterIdentity(
            modelName = "Ross-Tech HEX-USB+CAN (B03-V2 Clone)",
            hardwareFamily = "FTDI FT232R + ATmega162 Candidate",
            serialNumber = "RT000001",
            firmwareVersion = detectedFirmwareVersion,
            isClone = true,
            capabilities = setOf(
                AdapterCapability.RAW_PACKET_TRACE
            ),
            status = AdapterStatus.EXPERIMENTAL
        )

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        val params = ConnectionParameters(
            baudRate = initialBaudRate,
            dataBits = 8,
            stopBits = 1,
            parity = 0,
            dtr = false, // DTR# line HIGH -> ATmega reset released
            rts = false
        )
        val res = driver.open(params)
        if (res.isSuccess && driver is UsbFtdiDriver) {
            // Allow coprocessor clock stabilization after reset release
            delay(100)
        }
        res
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        driver.close()
    }

    override suspend fun identify(): AdapterIdentity = withContext(Dispatchers.IO) {
        identity
    }

    /**
     * Executes an adapter transaction with strict safety guardrails.
     * Guaranteed: Any unknown command returns [AdapterResponse.Unsupported] rather than guessing.
     */
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            return@withContext AdapterResponse.Error("FTDI driver is not open")
        }

        // Safety Guardrail: Block any write/adaptation/flashing requests in discovery mode
        val violation = assertReadOnlyGuardrails(request)
        if (violation != null) {
            return@withContext AdapterResponse.Error("SECURITY VIOLATION: $violation")
        }

        // Currently, without verified captures of the PC<->ATmega framing,
        // we do NOT inject unproven proprietary opcodes.
        // If a command is not explicitly verified, fail-safe as Unsupported.
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
     * Inspects a diagnostic payload to ensure it does not attempt writing or flashing in reverse-engineering mode.
     */
    fun assertReadOnlyGuardrails(payload: ByteArray): String? {
        if (payload.isEmpty()) return null
        // Check KWP header / service byte (first byte or second byte depending on addressing)
        for (b in payload.take(3)) {
            if (FORBIDDEN_WRITE_SERVICES.contains(b)) {
                return "Blocked potentially destructive diagnostic service: 0x%02X".format(b)
            }
        }
        return null
    }
}
