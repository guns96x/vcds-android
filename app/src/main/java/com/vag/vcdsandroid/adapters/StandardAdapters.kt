package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapter transport for ELM327 / STN Bluetooth and USB adapters.
 */
class Elm327Adapter(
    override val driver: HardwareDriver,
    val baudRate: Int = 38400
) : AdapterTransport {

    private var traceListener: ((direction: String, data: ByteArray) -> Unit)? = null

    override val identity: AdapterIdentity = AdapterIdentity(
        modelName = "ELM327 / STN Compatible",
        hardwareFamily = "AT-command Microcontroller",
        capabilities = setOf(
            AdapterCapability.CAN_ISO_TP,
            AdapterCapability.KWP2000_KLINE,
            AdapterCapability.RAW_PACKET_TRACE
        ),
        status = AdapterStatus.READY
    )

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        driver.open(ConnectionParameters(baudRate = baudRate))
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        driver.close()
    }

    override suspend fun identify(): AdapterIdentity = identity

    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        if (!driver.isConnected) return@withContext AdapterResponse.Error("ELM327 driver not connected")

        traceListener?.invoke("TX", request)
        val startTime = System.currentTimeMillis()

        driver.purge()
        driver.write(request)

        val buf = ByteArray(512)
        val readCount = driver.read(buf, timeoutMs)
        val elapsed = System.currentTimeMillis() - startTime

        if (readCount <= 0) return@withContext AdapterResponse.Timeout

        val rxData = buf.copyOf(readCount)
        traceListener?.invoke("RX", rxData)
        AdapterResponse.Success(rxData, elapsed)
    }

    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {
        traceListener = listener
    }
}

/**
 * In-memory simulator adapter for UI development and unit testing.
 */
class SimulatorAdapter(
    override val driver: HardwareDriver = object : HardwareDriver {
        override val name = "SimulatorDriver"
        override var isConnected = false
        override suspend fun open(parameters: ConnectionParameters) = run { isConnected = true; Result.success(Unit) }
        override suspend fun close() { isConnected = false }
        override suspend fun setBaudRate(baudRate: Int) = true
        override suspend fun setDtr(dtr: Boolean) = true
        override suspend fun setRts(rts: Boolean) = true
        override suspend fun write(data: ByteArray) = data.size
        override suspend fun read(buffer: ByteArray, timeoutMs: Long) = 0
        override suspend fun purge() {}
    }
) : AdapterTransport {

    override val identity: AdapterIdentity = AdapterIdentity(
        modelName = "VCDS Simulator Demo",
        hardwareFamily = "Synthetic Mock Engine",
        capabilities = setOf(
            AdapterCapability.K_LINE_RAW,
            AdapterCapability.KWP2000_KLINE,
            AdapterCapability.CAN_TP20
        ),
        status = AdapterStatus.READY
    )

    override suspend fun open(): Result<Unit> = driver.open()
    override suspend fun close() = driver.close()
    override suspend fun identify(): AdapterIdentity = identity
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse =
        AdapterResponse.Success(byteArrayOf(0x50, 0x01), 10)
    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {}
}

/**
 * Placeholder for legacy HEX-COM and early serial HEX adapters.
 */
class HexLegacyAdapter(override val driver: HardwareDriver) : AdapterTransport {
    override val identity = AdapterIdentity(
        modelName = "Ross-Tech Legacy HEX",
        hardwareFamily = "Early HEX Microcontroller",
        status = AdapterStatus.UNSUPPORTED
    )
    override suspend fun open() = Result.failure<Unit>(UnsupportedOperationException("Legacy HEX not supported"))
    override suspend fun close() {}
    override suspend fun identify() = identity
    override suspend fun transact(request: ByteArray, timeoutMs: Long) = AdapterResponse.Unsupported
    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {}
}

/**
 * Placeholder for modern ARM Cortex-M (STM32F4xx) HEX-V2 clone adapters.
 */
class HexV2Adapter(override val driver: HardwareDriver) : AdapterTransport {
    override val identity = AdapterIdentity(
        modelName = "Ross-Tech HEX-V2 Clone (ARM STM32)",
        hardwareFamily = "STM32F4xx Native USB",
        status = AdapterStatus.UNSUPPORTED
    )
    override suspend fun open() = Result.failure<Unit>(UnsupportedOperationException("HEX-V2 ARM protocol unverified"))
    override suspend fun close() {}
    override suspend fun identify() = identity
    override suspend fun transact(request: ByteArray, timeoutMs: Long) = AdapterResponse.Unsupported
    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {}
}

/**
 * Adapter transport representing a recognized physical link whose downstream adapter protocol
 * is unverified or unknown. Refuses transmission until explicitly configured or profiled.
 */
class UnverifiedAdapter(
    override val driver: HardwareDriver,
    val description: String = "Unverified USB Adapter"
) : AdapterTransport {
    override val identity = AdapterIdentity(
        modelName = description,
        hardwareFamily = "Generic Serial Bridge (Downstream Protocol Unknown)",
        status = AdapterStatus.UNVERIFIED
    )
    override suspend fun open(): Result<Unit> = Result.failure(
        IllegalStateException("Cannot open unverified adapter: protocol must be confirmed by user or active probe.")
    )
    override suspend fun close() { driver.close() }
    override suspend fun identify(): AdapterIdentity = identity
    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = AdapterResponse.Unsupported
    override fun setRawTraceListener(listener: ((direction: String, data: ByteArray) -> Unit)?) {}
}

