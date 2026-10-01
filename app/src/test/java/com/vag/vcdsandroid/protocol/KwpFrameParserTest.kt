package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for KWP2000 K-Line frame extraction.
 *
 * The central risk these lock down: on K-Line the tester sees its own
 * transmission echoed back. That echo is a valid frame with a valid checksum,
 * so any parser that accepts on framing + checksum alone will return the
 * request we just sent and the caller will decode it as live ECU data.
 */
class KwpFrameParserTest {

    private val ecuAddress: Byte = 0x01

    /** Request actually sent for measuring group 11: service 0x21, group 0x0B. */
    private fun group11Request() =
        KwpFrameParser.buildMessage(ecuAddress, byteArrayOf(0x21, 0x0B))

    /** A plausible ECU reply: positive response 0x61 + group + four value pairs. */
    private fun group11Reply(): ByteArray {
        val payload = byteArrayOf(
            0x61, 0x0B,
            0x05, 0x64.toByte(),   // pair 1
            0x0A, 0x32,            // pair 2
            0x0A, 0x28,            // pair 3
            0x03, 0x2D             // pair 4
        )
        val msg = ByteArray(payload.size + 4)
        msg[0] = (0x80 or payload.size).toByte()
        msg[1] = KwpFrameParser.TESTER_ADDRESS.toByte()   // to tester
        msg[2] = ecuAddress                               // from ECU
        System.arraycopy(payload, 0, msg, 3, payload.size)
        var cs = 0
        for (k in 0 until msg.size - 1) cs += (msg[k].toInt() and 0xFF)
        msg[msg.size - 1] = (cs and 0xFF).toByte()
        return msg
    }

    @Test
    fun `echo of our own request is never returned as a reply`() {
        val echo = group11Request()
        // Nothing but our own echo in the buffer: there is no ECU answer here.
        assertNull(
            "The parser returned our own transmission as if it were an ECU reply",
            KwpFrameParser.extractPayload(echo, echo.size)
        )
    }

    @Test
    fun `reply is extracted when preceded by the echo, as on a real K-Line`() {
        val echo = group11Request()
        val reply = group11Reply()
        val buffer = echo + reply

        val payload = KwpFrameParser.extractPayload(buffer, buffer.size)
            ?: error("expected the ECU reply to be extracted")

        assertEquals("positive response to service 0x21", 0x61, payload[0].toInt() and 0xFF)
        assertEquals("group number echoed back", 0x0B, payload[1].toInt() and 0xFF)
        assertEquals("group 011 carries four value pairs", 10, payload.size)
    }

    @Test
    fun `echo is rejected even when the reply is addressed to someone else`() {
        // Exercises the tolerant second pass: a gateway re-addressed the reply,
        // so the target is not the tester. The echo must still be rejected.
        val echo = group11Request()
        val reply = group11Reply().copyOf()
        reply[1] = 0x33                                   // re-addressed target
        var cs = 0
        for (k in 0 until reply.size - 1) cs += (reply[k].toInt() and 0xFF)
        reply[reply.size - 1] = (cs and 0xFF).toByte()

        val buffer = echo + reply
        val payload = KwpFrameParser.extractPayload(buffer, buffer.size)
            ?: error("expected the re-addressed reply to be extracted")
        assertEquals(0x61, payload[0].toInt() and 0xFF)
    }

    @Test
    fun `specific ECU filter rejects a valid frame from another controller`() {
        val foreignReply = group11Reply().copyOf()
        foreignReply[2] = 0x02 // transmission ECU, not requested Engine 01
        var cs = 0
        for (k in 0 until foreignReply.size - 1) {
            cs += (foreignReply[k].toInt() and 0xFF)
        }
        foreignReply[foreignReply.size - 1] = (cs and 0xFF).toByte()

        assertNull(
            KwpFrameParser.extractPayload(
                foreignReply,
                foreignReply.size,
                expectedSource = 0x01
            )
        )
    }

    @Test
    fun `frame with a corrupt checksum is rejected`() {
        val reply = group11Reply().copyOf()
        reply[reply.size - 1] = (reply[reply.size - 1] + 1).toByte()
        assertNull(KwpFrameParser.extractPayload(reply, reply.size))
    }

    @Test
    fun `truncated frame is rejected rather than read past the end`() {
        val reply = group11Reply()
        val truncated = reply.copyOf(reply.size - 3)
        assertNull(KwpFrameParser.extractPayload(truncated, truncated.size))
    }

    @Test
    fun `buildMessage produces a frame the parser accepts from the ECU side`() {
        val msg = KwpFrameParser.buildMessage(ecuAddress, byteArrayOf(0x21, 0x0B))
        assertEquals("format byte carries length 2", 0x82, msg[0].toInt() and 0xFF)
        assertEquals("target is the ECU", 0x01, msg[1].toInt() and 0xFF)
        assertEquals("source is the tester", 0xF1, msg[2].toInt() and 0xFF)
        var cs = 0
        for (k in 0 until msg.size - 1) cs += (msg[k].toInt() and 0xFF)
        assertEquals("checksum is sum mod 256", cs and 0xFF, msg[msg.size - 1].toInt() and 0xFF)
    }

    @Test
    fun `reply is found when leading line noise precedes it`() {
        val noise = byteArrayOf(0x00, 0xFF.toByte(), 0x55, 0xAA.toByte())
        val buffer = noise + group11Request() + group11Reply()
        val payload = KwpFrameParser.extractPayload(buffer, buffer.size)
            ?: error("expected the reply to be found after noise and echo")
        assertArrayEquals(
            byteArrayOf(0x61, 0x0B, 0x05, 0x64.toByte(), 0x0A, 0x32, 0x0A, 0x28, 0x03, 0x2D),
            payload
        )
    }
    /** ECU reply using the extended header: fmt=0x80, separate length byte. */
    private fun extendedReply(payload: ByteArray, source: Byte = ecuAddress): ByteArray {
        val msg = ByteArray(payload.size + 5)
        msg[0] = 0x80.toByte()
        msg[1] = KwpFrameParser.TESTER_ADDRESS.toByte()
        msg[2] = source
        msg[3] = payload.size.toByte()
        System.arraycopy(payload, 0, msg, 4, payload.size)
        var cs = 0
        for (k in 0 until msg.size - 1) cs += (msg[k].toInt() and 0xFF)
        msg[msg.size - 1] = (cs and 0xFF).toByte()
        return msg
    }

    /** 1A 9B identity replies are longer than 63 bytes on VAG controllers. */
    private fun longIdentityPayload(): ByteArray =
        byteArrayOf(0x5A, 0x9B.toByte()) + ByteArray(78) { (0x30 + it % 10).toByte() }

    @Test
    fun `extended length identity reply after echo is extracted`() {
        val echo = KwpFrameParser.buildMessage(ecuAddress, byteArrayOf(0x1A, 0x9B.toByte()))
        val reply = extendedReply(longIdentityPayload())
        val buffer = echo + reply

        val payload = KwpFrameParser.extractPayload(buffer, buffer.size, expectedSource = 0x01)
            ?: error("expected the long identity reply to be extracted")

        assertArrayEquals(longIdentityPayload(), payload)
    }

    @Test
    fun `truncated extended length reply is rejected`() {
        val reply = extendedReply(longIdentityPayload())
        val truncated = reply.copyOf(reply.size - 1)
        assertNull(KwpFrameParser.extractPayload(truncated, truncated.size, expectedSource = 0x01))
    }

    @Test
    fun `extended length frame from the tester is still treated as echo`() {
        val echo = extendedReply(longIdentityPayload(), source = KwpFrameParser.TESTER_ADDRESS.toByte())
        assertNull(KwpFrameParser.extractPayload(echo, echo.size))
    }

    // ---- fragmentation, multiple frames, garbage, bounds

    @Test
    fun `reply delivered in fragments is found only once complete`() {
        val buffer = group11Request() + group11Reply()
        val firstReplyByte = group11Request().size
        // Every strict prefix that cuts the reply short must yield nothing: no partial frame is ever decoded.
        for (n in 0 until buffer.size) {
            assertNull(
                "prefix of $n bytes (reply starts at $firstReplyByte) must not decode",
                KwpFrameParser.extractPayload(buffer, n, expectedSource = 0x01)
            )
        }
        val whole = KwpFrameParser.extractPayload(buffer, buffer.size, expectedSource = 0x01)
        assertArrayEquals(
            byteArrayOf(0x61, 0x0B, 0x05, 0x64.toByte(), 0x0A, 0x32, 0x0A, 0x28, 0x03, 0x2D),
            whole
        )
    }

    @Test
    fun `several frames in one USB packet are all returned in order`() {
        val pending = ByteArray(7).also {
            it[0] = 0x83.toByte(); it[1] = 0xF1.toByte(); it[2] = 0x01
            it[3] = 0x7F; it[4] = 0x21; it[5] = 0x78
            it[6] = ((it[0] + it[1] + it[2] + it[3] + it[4] + it[5]) and 0xFF).toByte()
        }
        val buffer = group11Request() + pending + group11Reply()

        val all = KwpFrameParser.extractAll(buffer, buffer.size, expectedSource = 0x01)

        assertEquals(2, all.size)
        assertArrayEquals(byteArrayOf(0x7F, 0x21, 0x78), all[0])
        assertEquals(0x61, all[1][0].toInt() and 0xFF)
        // extractPayload keeps returning the first one.
        assertArrayEquals(all[0], KwpFrameParser.extractPayload(buffer, buffer.size, expectedSource = 0x01))
        // limit stops early.
        assertEquals(1, KwpFrameParser.extractAll(buffer, buffer.size, expectedSource = 0x01, limit = 1).size)
    }

    @Test
    fun `a corrupt frame does not hide the good frame behind it`() {
        val bad = group11Reply().copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        val buffer = bad + group11Reply()
        val all = KwpFrameParser.extractAll(buffer, buffer.size, expectedSource = 0x01)
        assertEquals(1, all.size)
        assertEquals(0x61, all[0][0].toInt() and 0xFF)
    }

    @Test
    fun `garbage between frames is skipped`() {
        val garbage = byteArrayOf(0x00, 0x80.toByte(), 0xFF.toByte(), 0x13)
        val buffer = group11Reply() + garbage + group11Reply()
        assertEquals(2, KwpFrameParser.extractAll(buffer, buffer.size, expectedSource = 0x01).size)
    }

    @Test
    fun `bytes inside a payload are not re-read as a new header`() {
        // The payload itself holds a complete, checksum-valid frame from the ECU address.
        val inner = byteArrayOf(0x81.toByte(), 0xF1.toByte(), 0x01, 0x3E).let {
            it + (it.sumOf { b -> b.toInt() and 0xFF } and 0xFF).toByte()
        }
        val reply = extendedReply(byteArrayOf(0x5A) + inner + ByteArray(3))
        assertEquals(
            "sanity: the inner frame decodes on its own",
            1, KwpFrameParser.extractAll(inner, inner.size, expectedSource = 0x01).size
        )
        val all = KwpFrameParser.extractAll(reply, reply.size, expectedSource = 0x01)
        assertEquals("only the outer frame counts", 1, all.size)
    }

    @Test
    fun `scan is bounded on a large buffer of garbage and honours the byte count`() {
        val noise = ByteArray(65_536) { (it * 31 + 7).toByte() }
        // Must terminate quickly with no result (no valid, checksum-matching, ECU-sourced frame).
        val started = System.nanoTime()
        val result = KwpFrameParser.extractAll(noise, noise.size, expectedSource = 0x01)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000L
        assertTrue("scan took $elapsedMs ms", elapsedMs < 2_000)
        assertTrue(result.isEmpty())

        // Bytes beyond `count` are never read: a valid reply placed after count is invisible.
        val reply = group11Reply()
        val padded = ByteArray(reply.size + 4)
        System.arraycopy(reply, 0, padded, 4, reply.size)
        assertTrue(KwpFrameParser.extractAll(padded, 6, expectedSource = 0x01).isEmpty())
        // A count larger than the array is clamped instead of crashing.
        assertEquals(1, KwpFrameParser.extractAll(reply, reply.size + 100, expectedSource = 0x01).size)
    }

    @Test
    fun `tester echo of several requests never produces a payload`() {
        val buffer = group11Request() + group11Request() +
            KwpFrameParser.buildMessage(ecuAddress, byteArrayOf(0x1A, 0x9B.toByte()))
        assertTrue(KwpFrameParser.extractAll(buffer, buffer.size, expectedSource = null).isEmpty())
    }
}
