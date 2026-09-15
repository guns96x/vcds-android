package com.vag.vcdsandroid.protocol

import com.vag.vcdsandroid.model.MeasuringGroup
import org.junit.Assert.*
import org.junit.Test

class VwTp20ParserTest {

    @Test
    fun testRealEdc16MultiFrameWithKeepAliveA3() {
        // Raw ELM response captured from live VW Golf 5 1.9 TDI BLS EDC16
        val raw = """
            A3
            BD
            2D 00 1A 61 0B 01 69 2A
            2E 12 FF 6B 12 FF 68 17
            2F 65 CB 25 00 00 25 00
            10 00 25 00 00 25 00 00
        """.trimIndent()

        // Current TX sequence was 0x0C (request 1C)
        val result = Tp20FrameParser.parse(raw, expectedGroup = 0x0B, currentTxSeq = 0x0C)
        assertTrue("Expected Success, got $result", result is Tp20Result.Success)

        val success = result as Tp20Result.Success
        assertEquals("Payload length must be 26 bytes", 26, success.kwpPayload.size)
        assertTrue("Must detect interleaved Keep-Alive A3", success.sawKeepAlive)
        assertTrue("Final frame 10 requires ACK", success.needsAck)
        assertEquals("Final frame seq 0 -> ACK B1", 0xB1, success.ackCode)
        assertEquals("Next TX seq must be 0x0D", 0x0D, success.nextTxSeq)

        // Verify MeasuringGroup decoding
        val group = MeasuringGroup.decode(success.kwpPayload)
        assertNotNull("MeasuringGroup decode must succeed", group)
        assertEquals(11, group!!.groupNumber)
        assertEquals(4, group.values.size)

        // Field 1: Engine Speed (0.2 * 105 * 42 = 882 RPM)
        assertEquals("RPM", group.values[0].unit)
        assertEquals(882.0, group.values[0].rawValue, 0.5)

        // Field 2: Specified Boost (0.04 * 255 * 107 = 1091.4 mbar)
        assertEquals("mbar", group.values[1].unit)
        assertEquals(1091.4, group.values[1].rawValue, 0.5)

        // Field 3: Actual Boost (0.04 * 255 * 104 = 1060.8 mbar)
        assertEquals("mbar", group.values[2].unit)
        assertEquals(1060.8, group.values[2].rawValue, 0.5)

        // Field 4: N75 Duty Cycle ((203 / 256.0) * 101 = 80.1%)
        assertEquals("%", group.values[3].unit)
        assertEquals(80.1, group.values[3].rawValue, 0.5)
    }

    @Test
    fun testIncompleteMultiFrameMissingFinalFrame() {
        // Only 5 frames received (missing final frame 10)
        val raw = """
            A3
            BD
            2D 00 1A 61 0B 01 69 2A
            2E 12 FF 6B 12 FF 68 17
            2F 65 CB 25 00 00 25 00
        """.trimIndent()

        val result = Tp20FrameParser.parse(raw, expectedGroup = 0x0B, currentTxSeq = 0x0C)
        assertTrue("Expected Incomplete, got $result", result is Tp20Result.Incomplete)
        val incomplete = result as Tp20Result.Incomplete
        assertEquals(26, incomplete.expectedLen)
        assertEquals(19, incomplete.actualLen)
    }

    @Test
    fun testPeerDisconnectA8() {
        val raw = "A8"
        val result = Tp20FrameParser.parse(raw, expectedGroup = 0x0B, currentTxSeq = 0x0C)
        assertTrue("Expected PeerDisconnect, got $result", result is Tp20Result.PeerDisconnect)
    }

    @Test
    fun testA8InsidePayloadIsNotDisconnect() {
        // Frame where 0xA8 appears inside payload data (not standalone A8 frame)
        // 5 bytes in Frame 1 + 7 bytes in Frame 2 + 2 bytes in Frame 3 = 14 bytes
        val raw = """
            2D 00 0E 61 0B 01 A8 2A
            2E 12 FF 6B 12 FF 68 17
            1F 17 65
        """.trimIndent()

        val result = Tp20FrameParser.parse(raw, expectedGroup = 0x0B, currentTxSeq = 0x0C)
        assertTrue("Payload containing A8 byte should succeed, got $result", result is Tp20Result.Success)
        val success = result as Tp20Result.Success
        assertEquals(14, success.kwpPayload.size)
        assertEquals(0xB0, success.ackCode) // seq F + 1 = 0 -> B0
    }

    @Test
    fun testSequenceWrapAround() {
        // Frame seq F followed by intermediate seq 0 and final seq 1
        val raw = """
            2F 00 0E 61 0B 01 02 03
            20 04 05 06 07 08 09 0A
            11 0B 0C
        """.trimIndent()

        val result = Tp20FrameParser.parse(raw, expectedGroup = 0x0B, currentTxSeq = 0x0F)
        assertTrue("Expected Success, got $result", result is Tp20Result.Success)
        val success = result as Tp20Result.Success
        assertEquals(0, success.nextTxSeq) // 0x0F + 1 & 0x0F = 0
        assertEquals(0xB2, success.ackCode) // seq 1 + 1 = 2 -> B2
    }
}
