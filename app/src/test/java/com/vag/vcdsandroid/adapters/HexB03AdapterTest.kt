package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexB03AdapterTest {

    private class TestHardwareDriver : HardwareDriver {
        override val name: String = "TestUsbFtdi"
        override var isConnected: Boolean = true

        var lastBaudRate: Int = 0
        var dtr: Boolean = false
        var rts: Boolean = false
        var purged: Boolean = false
        var writtenBytes = mutableListOf<Byte>()
        var mockReadData = byteArrayOf()

        override suspend fun open(parameters: ConnectionParameters): Result<Unit> {
            lastBaudRate = parameters.baudRate
            dtr = parameters.dtr
            rts = parameters.rts
            isConnected = true
            return Result.success(Unit)
        }

        override suspend fun close() {
            isConnected = false
        }

        override suspend fun setBaudRate(baudRate: Int): Boolean {
            lastBaudRate = baudRate
            return true
        }

        override suspend fun setDtr(dtr: Boolean): Boolean {
            this.dtr = dtr
            return true
        }

        override suspend fun setRts(rts: Boolean): Boolean {
            this.rts = rts
            return true
        }

        override suspend fun write(data: ByteArray): Int {
            writtenBytes.addAll(data.toList())
            return data.size
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Long): Int {
            if (mockReadData.isEmpty()) return 0
            val len = minOf(buffer.size, mockReadData.size)
            System.arraycopy(mockReadData, 0, buffer, 0, len)
            return len
        }

        override suspend fun purge() {
            purged = true
        }
    }

    @Test
    fun `identity exposes experimental status and dynamic serial without hardcoding`() {
        val driver = TestHardwareDriver()
        val adapterWithSerial = HexB03Adapter(driver, serialNumber = "DYNAMIC_SERIAL_001")
        assertEquals("DYNAMIC_SERIAL_001", adapterWithSerial.identity.serialNumber)

        val adapterWithoutSerial = HexB03Adapter(driver)
        assertNull(adapterWithoutSerial.identity.serialNumber)
        assertEquals(AdapterStatus.EXPERIMENTAL, adapterWithoutSerial.identity.status)
        assertTrue(adapterWithoutSerial.identity.capabilities.contains(AdapterCapability.RAW_PACKET_TRACE))
    }

    @Test
    fun `open fails when baud rate is not derived from evidence`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver, configuredBaudRate = null)

        val res = adapter.open()
        assertTrue("Expected failure when baud is UNKNOWN", res.isFailure)
        assertTrue(res.exceptionOrNull()?.message?.contains("UNKNOWN") == true)
    }

    @Test
    fun `open succeeds when evidence-derived baud rate is configured`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver, configuredBaudRate = 115200)

        val res = adapter.open()
        assertTrue(res.isSuccess)
        assertEquals(115200, driver.lastBaudRate)
    }

    @Test
    fun `transact strictly enforces ZERO-TX on normal diagnostic requests`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        // Attempting to send normal diagnostic payload
        val kwpPayload = byteArrayOf(0x81.toByte(), 0x01, 0xF1.toByte(), 0x81.toByte(), 0xF4.toByte())
        val response = adapter.transact(kwpPayload, 1000)

        // Must return Unsupported
        assertEquals(AdapterResponse.Unsupported, response)
        // MUST NEVER write any bytes to the physical link
        assertTrue("Zero-TX contract violated: driver received bytes!", driver.writtenBytes.isEmpty())
    }

    @Test
    fun `transactRawDebug blocks transmission when developer flag is false`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val response = adapter.transactRawDebug(
            request = byteArrayOf(0x10, 0x20),
            timeoutMs = 1000,
            enableUnsafeDeveloperRawTx = false
        )

        assertTrue(response is AdapterResponse.Error)
        assertTrue(driver.writtenBytes.isEmpty())
    }

    @Test
    fun `transactRawDebug blocks destructive flash commands even in raw developer mode`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val flashCmd = byteArrayOf(0x34.toByte(), 0x00)
        val response = adapter.transactRawDebug(
            request = flashCmd,
            timeoutMs = 1000,
            enableUnsafeDeveloperRawTx = true
        )

        assertTrue(response is AdapterResponse.Error)
        val msg = (response as AdapterResponse.Error).message
        assertTrue(msg.contains("SECURITY VIOLATION"))
        assertTrue(driver.writtenBytes.isEmpty())
    }

    @Test
    fun `transactRawDebug transmits and logs traces when developer flag is explicitly true`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            mockReadData = byteArrayOf(0xDE.toByte(), 0xAD.toByte())
        }
        val adapter = HexB03Adapter(driver)

        val traces = mutableListOf<Pair<String, ByteArray>>()
        adapter.setRawTraceListener { dir, data -> traces.add(Pair(dir, data)) }

        val request = byteArrayOf(0x01, 0x02)
        val response = adapter.transactRawDebug(
            request = request,
            timeoutMs = 1000,
            enableUnsafeDeveloperRawTx = true
        )

        assertTrue(response is AdapterResponse.Success)
        val success = response as AdapterResponse.Success
        assertArrayEquals(driver.mockReadData, success.data)
        assertEquals(2, traces.size)
        assertEquals("TX", traces[0].first)
        assertArrayEquals(request, traces[0].second)
        assertEquals("RX", traces[1].first)
        assertArrayEquals(driver.mockReadData, traces[1].second)
    }
}
