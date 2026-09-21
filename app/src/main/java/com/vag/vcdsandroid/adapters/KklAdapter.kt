package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Standard dumb KKL pass-through adapter (FTDI FT232R, CH340, CP2102).
 * Passes raw bytes directly to the K-Line transceiver at 10400 baud.
 */
class KklAdapter(
    override val driver: HardwareDriver,
    val baudRate: Int = 10400
) : AdapterTransport {

    private var traceListener: ((direction: String, data: ByteArray) -> Unit)? = null

    override val identity: AdapterIdentity = AdapterIdentity(
        modelName = "Standard KKL Pass-Through",
        hardwareFamily = "K-Line ISO 9141 / KWP2000 Transceiver",
        serialNumber = null,
        firmwareVersion = null,
        isClone = false,
        capabilities = setOf(
            AdapterCapability.K_LINE_RAW,
            AdapterCapability.KWP1281,
            AdapterCapability.KWP2000_KLINE,
            AdapterCapability.RAW_PACKET_TRACE
        ),
        status = AdapterStatus.READY
    )

    override suspend fun open(): Result<Unit> = withContext(Dispatchers.IO) {
        val params = ConnectionParameters(
            baudRate = baudRate,
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

    override suspend fun identify(): AdapterIdentity = identity

    override suspend fun transact(request: ByteArray, timeoutMs: Long): AdapterResponse = withContext(Dispatchers.IO) {
        if (!driver.isConnected) {
            return@withContext AdapterResponse.Error("Hardware driver is not connected")
        }

        traceListener?.invoke("TX", request)
        val startTime = System.currentTimeMillis()

        driver.purge()
        val written = driver.write(request)
        if (written < 0) {
            return@withContext AdapterResponse.Error("Failed to write to driver")
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
}
