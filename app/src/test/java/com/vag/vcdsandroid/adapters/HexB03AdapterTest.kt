package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
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
    fun `identity exposes experimental status and hardware profile`() {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val id = adapter.identity
        assertEquals("Ross-Tech HEX-USB+CAN (B03-V2 Clone)", id.modelName)
        assertEquals("RT000001", id.serialNumber)
        assertEquals(AdapterStatus.EXPERIMENTAL, id.status)
        assertTrue(id.isClone)
        assertTrue(id.capabilities.contains(AdapterCapability.RAW_PACKET_TRACE))
    }

    @Test
    fun `guardrails permit safe read-only queries`() {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        // KWP2000 StartCommunication
        val startComm = byteArrayOf(0x81.toByte(), 0x01, 0xF1.toByte(), 0x81.toByte(), 0xF4.toByte())
        assertNull(adapter.assertReadOnlyGuardrails(startComm))

        // Read ECU ID (0x1A)
        val readEcuId = byteArrayOf(0x02, 0x1A, 0x9A.toByte(), 0x00)
        assertNull(adapter.assertReadOnlyGuardrails(readEcuId))

        // Read Data By Local ID (0x21)
        val readData = byteArrayOf(0x02, 0x21, 0x01, 0x00)
        assertNull(adapter.assertReadOnlyGuardrails(readData))

        // Read Fault Codes (0x18)
        val readDtc = byteArrayOf(0x03, 0x18, 0x00, 0x00)
        assertNull(adapter.assertReadOnlyGuardrails(readDtc))
    }

    @Test
    fun `guardrails strictly block destructive write and flash services`() {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        // WriteDataByIdentifier (0x2E)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x2E.toByte(), 0x01, 0x02)))

        // WriteDataByLocalIdentifier (0x3B)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x3B.toByte(), 0x01, 0x02)))

        // RequestDownload / Flashing (0x34)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x34.toByte(), 0x00)))

        // RequestUpload (0x35)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x35.toByte(), 0x00)))

        // TransferData / Flashing (0x36)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x36.toByte(), 0x01)))

        // RequestTransferExit (0x37)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x37.toByte())))

        // CommunicationControl (0x28)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x28.toByte(), 0x00)))

        // RoutineControl (0x31)
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x31.toByte(), 0x01)))
    }

    @Test
    fun `transact blocks security violation before writing to driver`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val flashCommand = byteArrayOf(0x34.toByte(), 0x00, 0x10)
        val response = adapter.transact(flashCommand, 1000)

        assertTrue(response is AdapterResponse.Error)
        val errMsg = (response as AdapterResponse.Error).message
        assertTrue(errMsg.contains("SECURITY VIOLATION"))
        assertTrue("Driver must not have received any written bytes", driver.writtenBytes.isEmpty())
    }

    @Test
    fun `transact fails as Unsupported on empty request`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val response = adapter.transact(byteArrayOf(), 1000)
        assertTrue(response is AdapterResponse.Unsupported)
    }

    @Test
    fun `transact fails as Error if driver is not connected`() = runBlocking {
        val driver = TestHardwareDriver().apply { isConnected = false }
        val adapter = HexB03Adapter(driver)

        val response = adapter.transact(byteArrayOf(0x01, 0x02), 1000)
        assertTrue(response is AdapterResponse.Error)
    }

    @Test
    fun `transact triggers raw trace listener and returns Success`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            mockReadData = byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x02)
        }
        val adapter = HexB03Adapter(driver)

        val traceEvents = mutableListOf<Pair<String, ByteArray>>()
        adapter.setRawTraceListener { dir, data ->
            traceEvents.add(Pair(dir, data))
        }

        val request = byteArrayOf(0x10, 0x20)
        val response = adapter.transact(request, 1000)

        assertTrue(response is AdapterResponse.Success)
        val success = response as AdapterResponse.Success
        assertArrayEquals(byteArrayOf(0x55, 0xAA.toByte(), 0x01, 0x02), success.data)

        // Verify traces
        assertEquals(2, traceEvents.size)
        assertEquals("TX", traceEvents[0].first)
        assertArrayEquals(request, traceEvents[0].second)
        assertEquals("RX", traceEvents[1].first)
        assertArrayEquals(driver.mockReadData, traceEvents[1].second)
    }

    @Test
    fun `transact returns Timeout when no response received`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            mockReadData = byteArrayOf() // 0 bytes returned
        }
        val adapter = HexB03Adapter(driver)

        val response = adapter.transact(byteArrayOf(0x10, 0x20), 100)
        assertTrue(response is AdapterResponse.Timeout)
    }
}
