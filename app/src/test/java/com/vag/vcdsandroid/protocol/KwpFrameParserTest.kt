package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
