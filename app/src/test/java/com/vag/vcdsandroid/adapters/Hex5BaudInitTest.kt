package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import com.vag.vcdsandroid.usb.UsbConnectResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Hex5BaudInitTest {

    private class ScriptedDriver(vararg replies: ByteArray) : HardwareDriver {
        override val name = "Scripted"
        override var isConnected = true
        val written = mutableListOf<ByteArray>()
        var purgeCount = 0
        private val queue = ArrayDeque(replies.toList())

        override suspend fun open(parameters: ConnectionParameters) = Result.success(Unit)
        override suspend fun close() { isConnected = false }
        override suspend fun setBaudRate(baudRate: Int) = true
        override suspend fun setDtr(dtr: Boolean) = true
        override suspend fun setRts(rts: Boolean) = true
        override suspend fun write(data: ByteArray): Int { written += data; return data.size }
        override suspend fun read(buffer: ByteArray, timeoutMs: Long): Int {
            val next = queue.removeFirstOrNull() ?: return 0
            next.copyInto(buffer)
            return next.size
        }
        override suspend fun purge() { purgeCount++ }
    }

    private fun cableFrame(opcode: Int, vararg payload: Int): ByteArray =
        HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_CABLE,
            opcode = opcode.toByte(),
            payload = ByteArray(payload.size) { payload[it].toByte() }
        )

    @Test
    fun `request for engine matches spec V2 bytes 53 07 84 03 01 00 D2`() {
        val frame = Hex5BaudInit.encodeRequest(Hex5BaudInit.specAddressByte(0x01))
        assertArrayEquals(
            byteArrayOf(0x53, 0x07, 0x84.toByte(), 0x03, 0x01, 0x00, 0xD2.toByte()),
            frame
        )
    }

    @Test
    fun `payload order is sub-command, address byte, flags`() {
        val frame = Hex5BaudInit.encodeRequest(Hex5BaudInit.specAddressByte(0x01))
        // Old wrong layout was [addr, 00, 03]; the wire layout is [03, addr, 00].
        assertArrayEquals(byteArrayOf(0x03, 0x01, 0x00), frame.copyOfRange(3, 6))
    }

    @Test
    fun `address byte follows odd parity and the retracted 0x81 is never produced for ECU 01`() {
        assertEquals(0x01, Hex5BaudInit.specAddressByte(0x01))
        assertEquals(0x83, Hex5BaudInit.specAddressByte(0x03))
        assertEquals(0x33, Hex5BaudInit.specAddressByte(0x33))
    }

    @Test
    fun `reply is accepted only with sync 0x55 and exactly five payload bytes`() {
        val ok = Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte(), 0xEF.toByte(), 0x8F.toByte(), 0x55))
        assertEquals(Hex5BaudInit.Reply(10_400, 0xEF, 0x8F), ok)
        assertNull(Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte(), 0xEF.toByte(), 0x8F.toByte(), 0x00)))
        assertNull(Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte())))
        // A longer payload is a different layout; it must not be read as a sync reply.
        assertNull(
            Hex5BaudInit.parseReply(
                byteArrayOf(0x28, 0xA0.toByte(), 0x00, 0x00, 0xEF.toByte(), 0x55)
            )
        )
    }

    @Test
    fun `answered request is sent once, flushed first, and reported as ECU init answered`() = runBlocking {
        val driver = ScriptedDriver(cableFrame(0x84, 0x28, 0xA0, 0xEF, 0x8F, 0x55))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01)

        val success = result.success ?: error("expected a sync reply")
        assertEquals(0x01, success.addressByte)
        assertEquals(1, driver.written.size)
        assertArrayEquals(
            byteArrayOf(0x53, 0x07, 0x84.toByte(), 0x03, 0x01, 0x00, 0xD2.toByte()),
            driver.written[0]
        )
        assertTrue("0x84 path must flush stale bytes first", driver.purgeCount >= 1)
        assertEquals(UsbConnectResult.ECU_INIT_ANSWERED, result.outcome())
        assertTrue(result.describe(), result.describe().contains("SYNC 55, KB1=EF KB2=8F, baud=10400"))
    }

    @Test
    fun `silent cable is asked twice with the same canonical request and ends as init timeout`() = runBlocking {
        val driver = ScriptedDriver()
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 2, replyTimeoutMs = 40, retryGapMs = 1)

        assertEquals(2, driver.written.size)
        assertArrayEquals(driver.written[0], driver.written[1])
        assertEquals(UsbConnectResult.INIT_TIMEOUT, result.outcome())
        assertTrue(result.describe(), result.describe().contains("silent (timeout)"))
    }

    @Test
    fun `cable that only echoes the request is reported as echo only and not retried`() = runBlocking {
        val request = Hex5BaudInit.encodeRequest(Hex5BaudInit.specAddressByte(0x01))
        val driver = ScriptedDriver(request)
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 2, replyTimeoutMs = 40, retryGapMs = 1)

        assertEquals(1, driver.written.size)
        assertEquals(UsbConnectResult.ADAPTER_ECHO_ONLY, result.outcome())
        assertTrue(result.describe(), result.describe().contains("ECHO ONLY"))
    }

    @Test
    fun `0x84 frame without sync means the cable ran the wake-up and the ECU did not answer`() = runBlocking {
        val driver = ScriptedDriver(cableFrame(0x84, 0x07))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 1, replyTimeoutMs = 40)

        assertEquals(UsbConnectResult.ECU_NOT_RESPONDING, result.outcome())
    }

    @Test
    fun `KW1281 keywords are reported as unsupported protocol`() = runBlocking {
        val driver = ScriptedDriver(cableFrame(0x84, 0x28, 0xA0, 0x01, 0x8A, 0x55))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 1, replyTimeoutMs = 40)

        assertEquals(UsbConnectResult.UNSUPPORTED_PROTOCOL, result.outcome())
    }

    @Test
    fun `key bytes of a dead line are not accepted as an ECU answer`() = runBlocking {
        val driver = ScriptedDriver(cableFrame(0x84, 0x28, 0xA0, 0x00, 0x00, 0x55))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 1, replyTimeoutMs = 40)

        assertEquals(UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE, result.outcome())
    }

    @Test
    fun `reply with a wrong checksum is reported as invalid checksum`() = runBlocking {
        val good = cableFrame(0x84, 0x28, 0xA0, 0xEF, 0x8F, 0x55)
        val bad = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val driver = ScriptedDriver(bad)
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 1, replyTimeoutMs = 40)

        assertNull(result.success)
        assertEquals(UsbConnectResult.INVALID_CHECKSUM, result.outcome())
    }

    @Test
    fun `reply split over two USB reads is still decoded`() = runBlocking {
        val frame = cableFrame(0x84, 0x28, 0xA0, 0xEF, 0x8F, 0x55)
        val driver = ScriptedDriver(frame.copyOfRange(0, 4), frame.copyOfRange(4, frame.size))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01, maxAttempts = 1, replyTimeoutMs = 200)

        assertEquals(UsbConnectResult.ECU_INIT_ANSWERED, result.outcome())
    }

    @Test
    fun `boot-mode writes are refused unless explicitly enabled`() = runBlocking {
        val driver = ScriptedDriver()
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        assertTrue(adapter.setLegacyDumbMode().isFailure)
        assertTrue(adapter.setIntelligentMode().isFailure)
        assertTrue(adapter.forceIntelligentModeRescue().isFailure)
        assertTrue("nothing may be written to the cable", driver.written.isEmpty())
    }

    @Test
    fun `describe reports silent and non-sync replies distinctly`() {
        val silent = Hex5BaudAttempt(0x81, byteArrayOf(), emptyList(), byteArrayOf(), null)
        val other = Hex5BaudAttempt(
            0x01, byteArrayOf(),
            listOf(HexB03Frame(0x4D, 5, 0x84.toByte(), byteArrayOf(0x07), 0x00)),
            byteArrayOf(), null
        )
        val text = Hex5BaudInitResult(0x01, listOf(silent, other)).describe()
        assertTrue(text, text.contains("0x84 addr=81 -> silent (timeout)"))
        assertTrue(text, text.contains("0x84 addr=01 -> frames: op=84 [07]"))
    }
}
