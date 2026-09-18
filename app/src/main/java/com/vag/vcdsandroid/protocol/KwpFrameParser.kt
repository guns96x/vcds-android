package com.vag.vcdsandroid.protocol

/**
 * Pure KWP2000 (ISO 14230-2) frame extraction for K-Line traffic.
 *
 * Kept free of Android dependencies so the echo-rejection rules can be unit
 * tested directly — they are safety relevant: a frame wrongly accepted here is
 * decoded downstream as live measuring-group data from the ECU.
 *
 * ## Why echo rejection matters on K-Line
 *
 * K-Line is a single-wire bidirectional bus. Every byte the tester transmits is
 * echoed straight back into its own receive buffer by the interface. That echo
 * is a fully well-formed KWP2000 frame with a correct checksum *by
 * construction*, because we built it. Any acceptance test based on framing plus
 * checksum alone therefore accepts our own request as if it were a reply.
 *
 * The only robust discriminator is the SOURCE address byte: a frame whose
 * source is the tester ([TESTER_ADDRESS]) is our echo and can never be a reply.
 *
 * ## Frame layout (address-information format)
 *
 * ```
 * [fmt] [target] [source] [payload ...] [checksum]
 *   fmt bits 7..6 = 0b10 : header carries address information
 *   fmt bits 5..0 = payload length (0 means "length in a separate byte")
 *   checksum      = sum of all preceding bytes, mod 256
 * ```
 */
object KwpFrameParser {

    /** Physical KWP2000 address of the diagnostic tester (us). */
    const val TESTER_ADDRESS = 0xF1

    /** Header length: fmt + target + source. The checksum adds one more byte. */
    private const val HEADER_LEN = 3

    /**
     * Extracts the first payload that can be attributed to the ECU.
     *
     * Two passes, both of which reject our own echo:
     *  1. strict — the frame must be addressed to the tester and come from
     *     someone else. This is the normal case.
     *  2. tolerant about the TARGET address only, for gateways that re-address
     *     responses. The source check still applies.
     *
     * @return the payload bytes, or null when the buffer holds no ECU frame.
     */
    fun extractPayload(buffer: ByteArray, count: Int): ByteArray? {
        if (count < HEADER_LEN + 1) return null
        return scan(buffer, count, requireAddressedToTester = true)
            ?: scan(buffer, count, requireAddressedToTester = false)
    }

    private fun scan(buffer: ByteArray, count: Int, requireAddressedToTester: Boolean): ByteArray? {
        for (i in 0..count - (HEADER_LEN + 1)) {
            val fmt = buffer[i].toInt() and 0xFF
            if ((fmt and 0xC0) != 0x80) continue

            val source = buffer[i + 2].toInt() and 0xFF
            // Our own transmission echoed back. Never a reply, in either pass.
            if (source == TESTER_ADDRESS) continue

            if (requireAddressedToTester) {
                val target = buffer[i + 1].toInt() and 0xFF
                if (target != TESTER_ADDRESS) continue
            }

            val length = fmt and 0x3F
            // length == 0 selects the extended form, where the real length lives
            // in a separate byte after the header. Not produced by EDC16U34 for
            // measuring groups; treated as unsupported rather than misparsed.
            if (length == 0) continue

            val totalMsgLen = length + HEADER_LEN + 1
            if (i + totalMsgLen > count) continue

            var calculated = 0
            for (k in i until i + totalMsgLen - 1) {
                calculated += (buffer[k].toInt() and 0xFF)
            }
            if ((calculated and 0xFF) != (buffer[i + totalMsgLen - 1].toInt() and 0xFF)) continue

            val payload = ByteArray(length)
            System.arraycopy(buffer, i + HEADER_LEN, payload, 0, length)
            return payload
        }
        return null
    }

    /**
     * Builds a request frame: `[0x80|len] [target] [tester] [payload] [checksum]`.
     */
    fun buildMessage(target: Byte, payload: ByteArray): ByteArray {
        require(payload.isNotEmpty() && payload.size <= 0x3F) {
            "payload length must be 1..63, was ${payload.size}"
        }
        val msg = ByteArray(payload.size + HEADER_LEN + 1)
        msg[0] = (0x80 or payload.size).toByte()
        msg[1] = target
        msg[2] = TESTER_ADDRESS.toByte()
        System.arraycopy(payload, 0, msg, HEADER_LEN, payload.size)
        var cs = 0
        for (k in 0 until msg.size - 1) cs += (msg[k].toInt() and 0xFF)
        msg[msg.size - 1] = (cs and 0xFF).toByte()
        return msg
    }
}
