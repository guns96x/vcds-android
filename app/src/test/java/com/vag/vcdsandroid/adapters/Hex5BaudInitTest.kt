package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
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
        override suspend fun purge() {}
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
    fun `both the spec address byte and the plain address are tried`() {
        assertEquals(listOf(0x01, 0x81), Hex5BaudInit.addressByteVariants(0x01))
        assertEquals(0x83, Hex5BaudInit.specAddressByte(0x03))
        assertEquals(0x33, Hex5BaudInit.specAddressByte(0x33))
    }

    @Test
    fun `reply is accepted only with sync 0x55`() {
        val ok = Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte(), 0xEF.toByte(), 0x8F.toByte(), 0x55))
        assertEquals(Hex5BaudInit.Reply(10_400, 0xEF, 0x8F), ok)
        assertNull(Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte(), 0xEF.toByte(), 0x8F.toByte(), 0x00)))
        assertNull(Hex5BaudInit.parseReply(byteArrayOf(0x28, 0xA0.toByte())))
    }

    @Test
    fun `adapter stops after the first variant that returns sync`() = runBlocking {
        val driver = ScriptedDriver(cableFrame(0x84, 0x28, 0xA0, 0xEF, 0x8F, 0x55))
        val adapter = HexB03Adapter(driver, isDebugBuild = false)

        val result = adapter.init5BaudKLine(0x01)

        val success = result.success ?: error("expected a sync reply")
        assertEquals(0x01, success.addressByte)
        assertEquals(1, driver.written.size)
        assertTrue(result.describe(), result.describe().contains("SYNC 55, KB1=EF KB2=8F, baud=10400"))
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
