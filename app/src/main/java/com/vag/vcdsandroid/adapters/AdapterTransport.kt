package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.HardwareDriver

/**
 * Functional capabilities advertised by a diagnostic adapter.
 */
enum class AdapterCapability {
    K_LINE_RAW,             // Transparent K-Line bitbanging/direct UART
    KWP1281,                // VAG KWP1281 5-baud address sequence support
    KWP2000_KLINE,          // ISO 14230-4 KWP2000 over K-Line
    CAN_TP20,               // Volkswagen Transport Protocol 2.0 (CAN)
    CAN_ISO_TP,             // Standard ISO 15765-2 CAN (UDS/OBD2)
    UDS_CAN,                // ISO 14229 UDS diagnostic services
    HARDWARE_BAUD_SWITCH,   // Adapter handles baud rate switching in firmware
    RAW_PACKET_TRACE        // Supports non-intrusive RAW TX/RX tracing
}

/**
 * Diagnostic adapter operational readiness and maturity status.
 */
enum class AdapterStatus {
    READY,          // Fully validated on real hardware
    EXPERIMENTAL,   // Under active research / reverse-engineering (guardrails enforced)
    UNSUPPORTED,    // Hardware recognized but protocol unsupported
    UNVERIFIED      // Unknown hardware profile
}

/**
 * Metadata identifying the active diagnostic adapter.
 */
data class AdapterIdentity(
    val modelName: String,
    val hardwareFamily: String,
    val serialNumber: String? = null,
    val firmwareVersion: String? = null,
    val isClone: Boolean = false,
    val capabilities: Set<AdapterCapability> = emptySet(),
    val status: AdapterStatus = AdapterStatus.UNVERIFIED
)

/**
 * Result of an atomic request/response transaction through the adapter transport.
 */
sealed class AdapterResponse {
    data class Success(val data: ByteArray, val roundTripMs: Long) : AdapterResponse() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return data.contentEquals(other.data) && roundTripMs == other.roundTripMs
        }
        override fun hashCode(): Int {
            var result = data.contentHashCode()
            result = 31 * result + roundTripMs.hashCode()
            return result
        }
    }
    data class Error(val message: String, val code: Int = -1) : AdapterResponse()
    object Timeout : AdapterResponse()
    object Unsupported : AdapterResponse()
}

/**
 * High-level transport contract connecting diagnostic engines (KWP2000, TP2.0, ELM327)
 * to adapter-specific command framing over an underlying [HardwareDriver].
 */
interface AdapterTransport {
    val identity: AdapterIdentity
    val driver: HardwareDriver

    suspend fun open(): Result<Unit>
    suspend fun close()
    suspend fun identify(): AdapterIdentity

    /**
     * Executes an adapter transaction with timeout.
     * Guaranteed fail-safe: unknown or unsupported operations return [AdapterResponse.Unsupported]
     * without transmitting guessed bytes to the car.
     */
    suspend fun transact(request: ByteArray, timeoutMs: Long = 1000): AdapterResponse

    /**
     * Attaches an optional callback for non-intrusive RAW trace logging.
     */
    fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?)
}
