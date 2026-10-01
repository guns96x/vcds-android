package com.vag.vcdsandroid.usb

import com.vag.vcdsandroid.adapters.HexB03Constants
import com.vag.vcdsandroid.adapters.HexB03Frame
import com.vag.vcdsandroid.adapters.HexB03FrameCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbConnectionStateTest {

    private val probeRequest = HexB03FrameCodec.encode(opcode = HexB03Constants.OPCODE_PROBE)

    private fun cableFrame(opcode: Byte, vararg payload: Int): HexB03Frame {
        val bytes = HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_CABLE,
            opcode = opcode,
            payload = ByteArray(payload.size) { payload[it].toByte() }
        )
        return HexB03FrameCodec.decode(bytes)!!
    }

    // ---- tracker

    @Test
    fun `tracker only moves forward and logs every transition with elapsed time`() {
        val log = ArrayList<String>()
        var now = 0L
        val tracker = UsbConnectionTracker(sink = { log += it }, nanoTime = { now })

        now = 5_000_000L
        tracker.advance(UsbConnectionState.USB_DETECTED, "0403:FA24")
        now = 9_000_000L
        tracker.advance(UsbConnectionState.INTERFACE_IDENTIFIED)
        tracker.advance(UsbConnectionState.USB_PERMISSION) // backwards: ignored

        assertEquals(UsbConnectionState.INTERFACE_IDENTIFIED, tracker.state)
        assertTrue(log.toString(), log.any { it.contains("IDLE -> USB_DETECTED t=5ms 0403:FA24") })
        assertTrue(log.toString(), log.any { it.contains("USB_DETECTED -> INTERFACE_IDENTIFIED t=9ms") })
        assertTrue(log.toString(), log.any { it.contains("ignored USB_PERMISSION") })
    }

    @Test
    fun `finish keeps the last reached state and reports failure with evidence`() {
        val log = ArrayList<String>()
        val tracker = UsbConnectionTracker(sink = { log += it })
        tracker.advance(UsbConnectionState.USB_DETECTED)
        tracker.advance(UsbConnectionState.INTERFACE_OPENED)

        tracker.finish(UsbConnectResult.INTERFACE_NOT_POWERED)

        assertEquals(UsbConnectionState.INTERFACE_OPENED, tracker.state)
        assertEquals(UsbConnectResult.INTERFACE_NOT_POWERED, tracker.result)
        assertTrue(log.last(), log.last().contains("RESULT INTERFACE_NOT_POWERED (FAIL, evidence=INFERRED)"))
    }

    @Test
    fun `progress text marks reached steps and names the cause`() {
        val tracker = UsbConnectionTracker()
        tracker.advance(UsbConnectionState.USB_DETECTED)
        tracker.advance(UsbConnectionState.USB_PERMISSION)
        tracker.finish(UsbConnectResult.PERMISSION_DENIED)

        val text = tracker.progressText()
        assertTrue(text, text.contains("[x] USB detected"))
        assertTrue(text, text.contains("[x] USB permission"))
        assertTrue(text, text.contains("[ ] Interface opened"))
        assertTrue(text, text.contains("USB permission denied"))
    }

    @Test
    fun `reset starts a clean attempt`() {
        val tracker = UsbConnectionTracker()
        tracker.advance(UsbConnectionState.SESSION_ACTIVE)
        tracker.finish(UsbConnectResult.CONNECTION_ESTABLISHED)
        tracker.reset()
        assertEquals(UsbConnectionState.IDLE, tracker.state)
        assertEquals(null, tracker.result)
    }

    @Test
    fun `only the two success results are not failures and neither claims more than it has`() {
        val ok = UsbConnectResult.values().filter { !it.isFailure }
        assertEquals(
            setOf(UsbConnectResult.ECU_INIT_ANSWERED, UsbConnectResult.CONNECTION_ESTABLISHED),
            ok.toSet()
        )
        // The smart path has no KWP framing proof, so its success text must say so.
        assertTrue(UsbConnectResult.ECU_INIT_ANSWERED.hint.contains("UNKNOWN"))
    }

    // ---- classifier

    @Test
    fun `no byte at all while probing is inferred to be an unpowered interface`() {
        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.INTERFACE_PROBE, probeRequest, ByteArray(0), emptyList(), HexB03Constants.OPCODE_PROBE
        )
        assertEquals(UsbConnectResult.INTERFACE_NOT_POWERED, r)
        assertEquals(Evidence.INFERRED, r.evidence)
    }

    @Test
    fun `no byte at all during ECU init is an init timeout`() {
        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.ECU_INIT, probeRequest, ByteArray(0), emptyList(), 0x84.toByte()
        )
        assertEquals(UsbConnectResult.INIT_TIMEOUT, r)
    }

    @Test
    fun `our own request coming back is echo only, whole or cut short`() {
        for (raw in listOf(probeRequest, probeRequest.copyOf(2), probeRequest + byteArrayOf(0x00))) {
            val r = B03RxClassifier.classifyNoReply(
                B03RxClassifier.Stage.INTERFACE_PROBE, probeRequest, raw, emptyList(), HexB03Constants.OPCODE_PROBE
            )
            assertEquals(UsbConnectResult.ADAPTER_ECHO_ONLY, r)
        }
    }

    @Test
    fun `a valid frame with another opcode is an unexpected adapter response`() {
        val frame = cableFrame(0x04, 0x01)
        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.INTERFACE_PROBE, probeRequest, byteArrayOf(0x4D), listOf(frame), HexB03Constants.OPCODE_PROBE
        )
        assertEquals(UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE, r)
    }

    @Test
    fun `a complete cable frame with a wrong xor is an invalid checksum`() {
        val good = HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_CABLE, opcode = 0x02, payload = byteArrayOf(0x01, 0x60, 0x44)
        )
        val bad = good.copyOf().also { it[it.size - 1] = (it[it.size - 1] + 1).toByte() }
        assertTrue(B03RxClassifier.hasBadChecksumFrame(bad))
        assertFalse(B03RxClassifier.hasBadChecksumFrame(good))

        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.INTERFACE_PROBE, probeRequest, bad, emptyList(), HexB03Constants.OPCODE_PROBE
        )
        assertEquals(UsbConnectResult.INVALID_CHECKSUM, r)
    }

    @Test
    fun `a truncated cable frame is not blamed on the checksum`() {
        val good = HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_CABLE, opcode = 0x02, payload = byteArrayOf(0x01, 0x60, 0x44)
        )
        val cut = good.copyOf(good.size - 2)
        assertFalse(B03RxClassifier.hasBadChecksumFrame(cut))
        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.INTERFACE_PROBE, probeRequest, cut, emptyList(), HexB03Constants.OPCODE_PROBE
        )
        assertEquals(UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE, r)
    }

    @Test
    fun `random bytes are an unexpected adapter response`() {
        val r = B03RxClassifier.classifyNoReply(
            B03RxClassifier.Stage.INTERFACE_PROBE,
            probeRequest,
            byteArrayOf(0xFF.toByte(), 0x00, 0x13),
            emptyList(),
            HexB03Constants.OPCODE_PROBE
        )
        assertEquals(UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE, r)
    }

    @Test
    fun `every failure the screen must distinguish has its own result`() {
        val names = UsbConnectResult.values().map { it.name }.toSet()
        for (required in listOf(
            "USB_DEVICE_NOT_FOUND", "PERMISSION_DENIED", "INTERFACE_NOT_POWERED", "SERIAL_OPEN_FAILED",
            "ADAPTER_IDENTIFY_FAILED", "ADAPTER_ECHO_ONLY", "NO_KLINE_ADAPTER_RESPONSE", "INIT_TIMEOUT",
            "INVALID_CHECKSUM", "UNEXPECTED_ADAPTER_RESPONSE", "ECU_NOT_RESPONDING", "UNSUPPORTED_PROTOCOL",
            "CONNECTION_ESTABLISHED"
        )) assertTrue("missing $required", required in names)
    }
}
