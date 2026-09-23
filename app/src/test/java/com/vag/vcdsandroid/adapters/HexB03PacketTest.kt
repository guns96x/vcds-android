package com.vag.vcdsandroid.adapters

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HexB03PacketTest {

    @Test
    fun testEncodeDecodeHostPingFrame() {
        val encoded = HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_HOST,
            opcode = 0x02.toByte(),
            payload = ByteArray(0)
        )

        // 53 04 02 55 -> 0x53 ^ 0x04 ^ 0x02 = 0x55
        val expected = byteArrayOf(0x53, 0x04, 0x02, 0x55)
        assertArrayEquals(expected, encoded)

        val decoded = HexB03FrameCodec.decode(encoded)
        assertNotNull(decoded)
        assertEquals(HexB03Constants.MARKER_HOST, decoded!!.marker)
        assertEquals(4, decoded.length)
        assertEquals(0x02.toByte(), decoded.opcode)
        assertEquals(0, decoded.payload.size)
        assertEquals(0x55.toByte(), decoded.xorChecksum)
    }

    @Test
    fun testDecodeMcuPingResponse() {
        // 4D 07 02 01 60 44 6D -> 0x4D ^ 0x07 ^ 0x02 ^ 0x01 ^ 0x60 ^ 0x44 = 0x6D
        val raw = byteArrayOf(0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D)
        val decoded = HexB03FrameCodec.decode(raw)

        assertNotNull(decoded)
        assertEquals(HexB03Constants.MARKER_CABLE, decoded!!.marker)
        assertEquals(7, decoded.length)
        assertEquals(0x02.toByte(), decoded.opcode)
        assertArrayEquals(byteArrayOf(0x01, 0x60, 0x44), decoded.payload)
        assertEquals(0x6D.toByte(), decoded.xorChecksum)
    }

    @Test
    fun testDecodeCorruptedChecksumReturnsNull() {
        val corrupted = byteArrayOf(0x53, 0x04, 0x02, 0x00) // 0x00 instead of 0x55
        assertNull(HexB03FrameCodec.decode(corrupted))
    }

    @Test
    fun testDecodeTooShortReturnsNull() {
        assertNull(HexB03FrameCodec.decode(byteArrayOf(0x53, 0x04, 0x02)))
    }

    @Test
    fun testStreamDecoderSingleFrame() {
        val decoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)
        val raw = byteArrayOf(0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D)

        val frames = decoder.feed(raw)
        assertEquals(1, frames.size)
        assertEquals(0x02.toByte(), frames[0].opcode)
        assertArrayEquals(byteArrayOf(0x01, 0x60, 0x44), frames[0].payload)
    }

    @Test
    fun testStreamDecoderFragmentedAcrossChunks() {
        val decoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)
        val chunk1 = byteArrayOf(0x4D, 0x07, 0x02)
        val chunk2 = byteArrayOf(0x01, 0x60, 0x44, 0x6D)

        val frames1 = decoder.feed(chunk1)
        assertEquals(0, frames1.size) // Incomplete, waiting for remainder

        val frames2 = decoder.feed(chunk2)
        assertEquals(1, frames2.size)
        assertEquals(0x02.toByte(), frames2[0].opcode)
        assertArrayEquals(byteArrayOf(0x01, 0x60, 0x44), frames2[0].payload)
    }

    @Test
    fun testStreamDecoderMultipleConcatenatedFrames() {
        val decoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)
        val frame1 = byteArrayOf(0x4D, 0x04, 0xFE.toByte(), 0xB7.toByte()) // 4D ^ 04 ^ FE = B7
        val frame2 = byteArrayOf(0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D)

        val combined = frame1 + frame2
        val frames = decoder.feed(combined)

        assertEquals(2, frames.size)
        assertEquals(0xFE.toByte(), frames[0].opcode)
        assertEquals(0x02.toByte(), frames[1].opcode)
    }

    @Test
    fun testStreamDecoderLeadingGarbageResync() {
        val decoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)
        val garbage = byteArrayOf(0x00, 0xFF.toByte(), 0xAA.toByte(), 0x55)
        val frame = byteArrayOf(0x4D, 0x07, 0x02, 0x01, 0x60, 0x44, 0x6D)

        val stream = garbage + frame
        val frames = decoder.feed(stream)

        assertEquals(1, frames.size)
        assertEquals(0x02.toByte(), frames[0].opcode)
    }

    @Test
    fun testStreamDecoderBufferOverflowProtection() {
        val decoder = HexB03StreamDecoder(expectedMarker = HexB03Constants.MARKER_CABLE)
        val largeGarbage = ByteArray(5000) { 0x11 }

        // Must not throw OutOfMemoryError and must cleanly reset buffer
        val frames = decoder.feed(largeGarbage)
        assertEquals(0, frames.size)
    }

    @Test
    fun testCandidateB03CommandsEncoding() {
        val pingFrame = CandidateB03Command.ProbePing.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, pingFrame[0])
        assertEquals(4.toByte(), pingFrame[1])
        assertEquals(0x02.toByte(), pingFrame[2])

        val identifyFrame = CandidateB03Command.Identify.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, identifyFrame[0])
        assertEquals(4.toByte(), identifyFrame[1])
        assertEquals(0x04.toByte(), identifyFrame[2])

        val statusFrame = CandidateB03Command.StatusRead.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, statusFrame[0])
        assertEquals(0x82.toByte(), statusFrame[2])

        val modeFrame = CandidateB03Command.ModeRead.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, modeFrame[0])
        assertEquals(0x0D.toByte(), modeFrame[2])

        val setBootDumbFrame = CandidateB03Command.SetBootDumb.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, setBootDumbFrame[0])
        assertEquals(5.toByte(), setBootDumbFrame[1])
        assertEquals(0x0E.toByte(), setBootDumbFrame[2])
        assertEquals(0x00.toByte(), setBootDumbFrame[3])
        // 0x53 ^ 0x05 ^ 0x0E ^ 0x00 = 0x58
        assertEquals(0x58.toByte(), setBootDumbFrame[4])

        val setBootSmartFrame = CandidateB03Command.SetBootSmart.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, setBootSmartFrame[0])
        assertEquals(5.toByte(), setBootSmartFrame[1])
        assertEquals(0x0E.toByte(), setBootSmartFrame[2])
        assertEquals(0x02.toByte(), setBootSmartFrame[3])
        // 0x53 ^ 0x05 ^ 0x0E ^ 0x02 = 0x5A
        assertEquals(0x5A.toByte(), setBootSmartFrame[4])

        val echoFrame = CandidateB03Command.Echo10400.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, echoFrame[0])
        assertEquals(4.toByte(), echoFrame[1])
        assertEquals(0x9A.toByte(), echoFrame[2])

        val keepaliveFrame = CandidateB03Command.KeepalivePing.encodeFrame()
        assertEquals(HexB03Constants.MARKER_HOST, keepaliveFrame[0])
        assertEquals(0xA0.toByte(), keepaliveFrame[2])
    }
}
