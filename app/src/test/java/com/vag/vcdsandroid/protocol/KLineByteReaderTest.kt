package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KLineByteReaderTest {

    /** Fake USB port: hands out scripted chunks; empty chunks mean "timeout". */
    private class FakePort(vararg chunks: ByteArray) {
        val script = ArrayDeque(chunks.toList())
        val requestedSizes = mutableListOf<Int>()
        var clockNs = 0L

        fun read(buffer: ByteArray, timeoutMs: Int): Int {
            // Mirrors usb-serial-for-android 3.x FTDI: tiny buffers are rejected.
            require(buffer.size > 2) { "Read buffer too small" }
            requestedSizes += buffer.size
            clockNs += timeoutMs * 1_000_000L
            val next = script.removeFirstOrNull() ?: return 0
            next.copyInto(buffer)
            return next.size
        }
    }

    private fun reader(port: FakePort) =
        KLineByteReader(readChunk = port::read, nanoTime = { port.clockNs })

    @Test
    fun `never asks the driver for a read smaller than a USB packet`() {
        val port = FakePort(byteArrayOf(0x55))
        val r = reader(port)

        assertEquals(0x55, r.readByteUntil(1_000_000_000L))
        assertTrue(port.requestedSizes.all { it >= 64 })
    }

    @Test
    fun `sync and both key bytes in one USB packet are all delivered in order`() {
        val port = FakePort(byteArrayOf(0x55, 0xEF.toByte(), 0x8F.toByte()))
        val r = reader(port)
        val deadline = 1_000_000_000L

        assertEquals(0x55, r.readByteUntil(deadline))
        assertEquals(0xEF, r.readByteUntil(deadline))
        assertEquals(0x8F, r.readByteUntil(deadline))
        assertEquals("all three bytes came from one read", 1, port.requestedSizes.size)
    }

    @Test
    fun `bytes split across reads with timeouts in between are joined`() {
        val port = FakePort(byteArrayOf(), byteArrayOf(0x00), byteArrayOf(), byteArrayOf(0x55))
        val r = reader(port)
        val deadline = 1_000_000_000L

        assertEquals("break echo before sync", 0x00, r.readByteUntil(deadline))
        assertEquals(0x55, r.readByteUntil(deadline))
    }

    @Test
    fun `returns null at the deadline when the line stays silent`() {
        val port = FakePort()
        val r = reader(port)

        assertNull(r.readByteUntil(20_000_000L))
        assertTrue("clock advanced to the deadline", port.clockNs >= 20_000_000L)
    }

    @Test
    fun `clear drops queued bytes`() {
        val port = FakePort(byteArrayOf(0x01, 0x02))
        val r = reader(port)

        assertEquals(0x01, r.readByteUntil(1_000_000_000L))
        assertEquals(1, r.pendingCount)
        r.clear()
        assertNull(r.readByteUntil(port.clockNs + 10_000_000L))
    }
}
