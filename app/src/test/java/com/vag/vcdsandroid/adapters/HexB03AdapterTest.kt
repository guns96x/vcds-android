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

        var mockReadQueue = mutableListOf<ByteArray>()

        override suspend fun read(buffer: ByteArray, timeoutMs: Long): Int {
            if (mockReadQueue.isNotEmpty()) {
                val next = mockReadQueue.removeAt(0)
                val len = minOf(buffer.size, next.size)
                System.arraycopy(next, 0, buffer, 0, len)
                return len
            }
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
    }

    @Test
    fun `transactRawDebug blocks transmission in release builds even with developer flag`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val response = adapter.transactRawDebug(
            request = byteArrayOf(0x1A, 0x9B.toByte()),
            timeoutMs = 1000,
            enableUnsafeDeveloperRawTx = true
        )

        assertTrue(response is AdapterResponse.Error)
        val msg = (response as AdapterResponse.Error).message
        assertTrue(msg.contains("strictly disabled in release builds"))
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
        val adapter = HexB03Adapter(driver, isDebugBuild = true)

        val traces = mutableListOf<Pair<String, ByteArray>>()
        adapter.setRawTraceListener { dir, data -> traces.add(Pair(dir, data)) }

        val request = byteArrayOf(0x1A, 0x9B.toByte()) // ReadEcuIdentification (whitelisted)
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

    @Test
    fun `interface probe completes plaintext open and keeps link alive`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x02,
                    payload = byteArrayOf(0x01, 0x60, 0x44)
                )
            )
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x04,
                    payload = byteArrayOf(
                        0x52, 0x4F, 0x53, 0x53, 0x54, 0x45, 0x43, 0x48,
                        0x00, 0x00, 0x00,
                        0xA8.toByte(), 0x9D.toByte(), 0x01, 0x00, 0x09
                    )
                )
            )
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x82.toByte(),
                    payload = byteArrayOf(0x00, 0x00)
                )
            )
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x0D,
                    payload = byteArrayOf(0x02)
                )
            )
        }
        val adapter = HexB03Adapter(driver, serialNumber = "RT000001")

        val result = adapter.probeInterface()

        assertTrue(result.isSuccess)
        val probe = result.getOrThrow()
        assertTrue(probe.identityText.startsWith("ROSSTECH"))
        assertArrayEquals(byteArrayOf(0x01, 0x60, 0x44), probe.probePayload)
        assertEquals(115200, driver.lastBaudRate)
        assertTrue(driver.isConnected)

        assertArrayEquals(byteArrayOf(0x00, 0x00), probe.statusPayload)
        assertArrayEquals(byteArrayOf(0x02), probe.modePayload)

        val expectedTx = byteArrayOf(
            0x53, 0x04, 0x02, 0x55,
            0x53, 0x04, 0x04, 0x53,
            0x53, 0x04, 0x82.toByte(), 0xD5.toByte(),
            0x53, 0x04, 0x0D, 0x5A
        )
        assertArrayEquals(expectedTx, driver.writtenBytes.toByteArray())
    }

    @Test
    fun `interface probe fails if identify does not contain ROSSTECH`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x02,
                    payload = byteArrayOf(0x01, 0x60, 0x44)
                )
            )
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = 0x04,
                    payload = "UNKNOWN".toByteArray()
                )
            )
        }
        val adapter = HexB03Adapter(driver)

        val result = adapter.probeInterface()

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ROSSTECH") == true)
        assertFalse(driver.isConnected)
    }

    @Test
    fun `executeCandidateCommand strictly enforces ZERO-TX on candidate commands`() = runBlocking {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        val response = adapter.executeCandidateCommand(CandidateB03Command.ProbePing, timeoutMs = 500)
        assertEquals(AdapterResponse.Unsupported, response)
        assertTrue("Zero-TX contract violated: driver received bytes!", driver.writtenBytes.isEmpty())
    }

    @Test
    fun `assertReadOnlyGuardrails allows whitelisted read services and blocks non-read services`() {
        val driver = TestHardwareDriver()
        val adapter = HexB03Adapter(driver)

        // Allowed read requests: direct SID
        assertNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x1A, 0x9B.toByte()))) // ReadEcuId
        assertNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x21, 0x01)))          // ReadDataByLocalId
        assertNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x18, 0x00, 0x00)))    // ReadDTCs

        // Allowed read requests: framed ISO 14230 [fmt, target, source, sid, ...]
        assertNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x82.toByte(), 0x01, 0xF1.toByte(), 0x1A, 0x9B.toByte(), 0x69)))

        // Blocked write/flash/security/reset services
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x34.toByte(), 0x00))) // Flash Download
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x11, 0x01)))          // ECU Reset
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x14, 0x00, 0x00)))    // Clear DTCs
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x27, 0x01)))          // Security Access
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x30, 0x01)))          // IO Control
        assertNotNull(adapter.assertReadOnlyGuardrails(byteArrayOf(0x2E.toByte(), 0x01))) // WriteDataById
    }

    @Test
    fun `setLegacyDumbMode transitions adapter from smart mode to dumb mode with verified 0xFE ACK`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            // 1. Initial 0x0D ReadBoot query returns 0x02 (Smart mode)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_SMART)
                )
            )
            // 2. 0x0E SetBoot(0) response is 0xFE (ACK)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_ACK
                )
            )
            // 3. Verification 0x0D ReadBoot query returns 0x00 (Legacy Dumb mode)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_LEGACY_DUMB)
                )
            )
        }
        val adapter = HexB03Adapter(driver)

        val result = adapter.setLegacyDumbMode()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())

        // Verify wire frame sequence:
        // 1. Query 0x0D: [0x53, 0x04, 0x0D, 0x5A]
        // 2. SetBoot(0): [0x53, 0x05, 0x0E, 0x00, 0x58]
        // 3. Verify 0x0D: [0x53, 0x04, 0x0D, 0x5A]
        val expectedTx = byteArrayOf(
            0x53, 0x04, 0x0D, 0x5A,
            0x53, 0x05, 0x0E, 0x00, 0x58,
            0x53, 0x04, 0x0D, 0x5A
        )
        assertArrayEquals(expectedTx, driver.writtenBytes.toByteArray())
    }

    @Test
    fun `setLegacyDumbMode is no-op if adapter is already in dumb mode`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            // Initial 0x0D ReadBoot query returns 0x00 (Already Dumb)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_LEGACY_DUMB)
                )
            )
        }
        val adapter = HexB03Adapter(driver)

        val result = adapter.setLegacyDumbMode()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())

        // Must ONLY have sent the 0x0D query, without sending 0x0E write
        val expectedTx = byteArrayOf(0x53, 0x04, 0x0D, 0x5A)
        assertArrayEquals(expectedTx, driver.writtenBytes.toByteArray())
    }

    @Test
    fun `setLegacyDumbMode fails if SetBoot times out without 0xFE ACK`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            // Initial 0x0D query returns 0x02
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_SMART)
                )
            )
            // No reply to 0x0E
        }
        val adapter = HexB03Adapter(driver)

        val result = adapter.setLegacyDumbMode(timeoutMs = 100)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("timed out") == true)
    }

    @Test
    fun `setIntelligentMode transitions adapter from dumb mode to smart mode`() = runBlocking {
        val driver = TestHardwareDriver().apply {
            // 1. Initial 0x0D query returns 0x00 (Dumb mode)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_LEGACY_DUMB)
                )
            )
            // 2. 0x0E SetBoot(2) returns 0xFE ACK
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_ACK
                )
            )
            // 3. Verification 0x0D returns 0x02 (Smart mode)
            mockReadQueue.add(
                HexB03FrameCodec.encode(
                    marker = HexB03Constants.MARKER_CABLE,
                    opcode = HexB03Constants.OPCODE_READ_BOOT,
                    payload = byteArrayOf(HexB03Constants.BOOT_MODE_SMART)
                )
            )
        }
        val adapter = HexB03Adapter(driver)

        val result = adapter.setIntelligentMode()
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())

        // Expected TX:
        // 0x0D ReadBoot: [0x53, 0x04, 0x0D, 0x5A]
        // 0x0E SetBoot(2): [0x53, 0x05, 0x0E, 0x02, 0x5A]
        // 0x0D ReadBoot: [0x53, 0x04, 0x0D, 0x5A]
        val expectedTx = byteArrayOf(
            0x53, 0x04, 0x0D, 0x5A,
            0x53, 0x05, 0x0E, 0x02, 0x5A,
            0x53, 0x04, 0x0D, 0x5A
        )
        assertArrayEquals(expectedTx, driver.writtenBytes.toByteArray())
    }
}
