package com.vag.vcdsandroid.hardware

/**
 * Standard parameters for serial/hardware connection initialization.
 */
data class ConnectionParameters(
    val baudRate: Int = 10400,
    val dataBits: Int = 8,
    val stopBits: Int = 1,
    val parity: Int = 0,
    val dtr: Boolean = false,
    val rts: Boolean = false
)

/**
 * Abstract physical link interface.
 * Decouples physical transport (USB FTDI, CH340, CP2102, Bluetooth RFCOMM)
 * from adapter command protocols (ELM327, KKL, B03-V2, STN, etc.).
 */
interface HardwareDriver {
    val name: String
    val isConnected: Boolean

    suspend fun open(parameters: ConnectionParameters = ConnectionParameters()): Result<Unit>
    suspend fun close()

    suspend fun setBaudRate(baudRate: Int): Boolean
    suspend fun setDtr(dtr: Boolean): Boolean
    suspend fun setRts(rts: Boolean): Boolean

    suspend fun write(data: ByteArray): Int
    suspend fun read(buffer: ByteArray, timeoutMs: Long): Int
    suspend fun purge()
}
