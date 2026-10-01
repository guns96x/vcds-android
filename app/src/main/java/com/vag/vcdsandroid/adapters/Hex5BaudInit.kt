package com.vag.vcdsandroid.adapters

/**
 * Smart-mode K-Line wake-up executed by the FA24 interface MCU (opcode 0x84).
 *
 * Source: static Ghidra analysis of VCDS 26.3 x64 on branch reverse/vcds-ghidra
 * (306ba40, reverse/IMPLEMENTATION_SPEC.md 2.4, HC::Init5Baud @ 0x14007E3B4).
 * Status: PROVEN_STATIC only — not yet observed on the wire for this cable.
 *
 * Request:  53 07 84 03 <addr> 00 <xor>   (sub 0x03 = K-Line init, flags 0x00)
 * Response: 4D 09 84 <baudHi> <baudLo> <KB1> <KB2> 55 <xor>
 *
 * Address byte: reverse/IMPLEMENTATION_SPEC_V2.md (audit/vcds-ghidra-proof,
 * FUN_14007e3b4 lines 20-33) corrects the first draft: odd parity in bit 7,
 * set only when the 7-bit address has an EVEN number of ones, so 0x01 -> 0x01
 * and 0x03 -> 0x83; 0x33 is sent unchanged. The first draft's 0x81 is kept
 * only as a fallback variant; whichever produces a 0x55 reply is recorded.
 *
 * Android-free so the byte layout is unit tested.
 */
object Hex5BaudInit {
    const val OPCODE_5BAUD_INIT: Byte = 0x84.toByte()
    const val SUB_KLINE_INIT: Byte = 0x03
    const val FLAGS_STANDARD: Byte = 0x00
    const val SYNC_BYTE = 0x55

    /** VCDS waits 3300 ms (0xCE4); the physical 5-baud address alone takes ~2 s. */
    const val TIMEOUT_MS = 3_300L

    /** Spec V2: odd parity in bit 7 (set when the 7-bit address has an even bit count); 0x33 exempt. */
    fun specAddressByte(address: Int): Int {
        require(address in 0..0x7F) { "K-Line address must be 7-bit" }
        if (address == 0x33) return address
        return if (Integer.bitCount(address) % 2 == 0) address or 0x80 else address
    }

    /** First-draft encoding (0x01 -> 0x81), retracted by the audit; fallback only. */
    fun firstDraftAddressByte(address: Int): Int =
        if (Integer.bitCount(address) % 2 == 1) address or 0x80 else address

    /** Spec V2 byte first, then the first-draft byte if it differs. */
    fun addressByteVariants(address: Int): List<Int> =
        listOf(specAddressByte(address), firstDraftAddressByte(address)).distinct()

    fun encodeRequest(addressByte: Int): ByteArray =
        HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_HOST,
            opcode = OPCODE_5BAUD_INIT,
            payload = byteArrayOf(SUB_KLINE_INIT, addressByte.toByte(), FLAGS_STANDARD)
        )

    data class Reply(val baud: Int, val keyByte1: Int, val keyByte2: Int)

    /**
     * Parses the payload of a 0x84 reply frame (opcode excluded):
     * [baudHi, baudLo, KB1, KB2, 0x55]. Returns null unless the sync byte is 0x55.
     */
    fun parseReply(payload: ByteArray): Reply? {
        if (payload.size < 5) return null
        if ((payload[4].toInt() and 0xFF) != SYNC_BYTE) return null
        val baud = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        return Reply(
            baud = baud,
            keyByte1 = payload[2].toInt() and 0xFF,
            keyByte2 = payload[3].toInt() and 0xFF
        )
    }
}

/** One 0x84 attempt: what was sent and every checksum-valid frame the cable returned. */
data class Hex5BaudAttempt(
    val addressByte: Int,
    val request: ByteArray,
    val frames: List<HexB03Frame>,
    val rawRx: ByteArray,
    val reply: Hex5BaudInit.Reply?
)

data class Hex5BaudInitResult(
    val address: Int,
    val attempts: List<Hex5BaudAttempt>
) {
    val success: Hex5BaudAttempt? get() = attempts.firstOrNull { it.reply != null }

    fun describe(): String = attempts.joinToString("\n") { a ->
        val head = "0x84 addr=%02X".format(a.addressByte)
        when {
            a.reply != null ->
                "$head -> SYNC 55, KB1=%02X KB2=%02X, baud=%d".format(
                    a.reply.keyByte1, a.reply.keyByte2, a.reply.baud
                )
            a.frames.isNotEmpty() ->
                "$head -> frames: " + a.frames.joinToString(" | ") { f ->
                    "op=%02X [%s]".format(
                        f.opcode.toInt() and 0xFF,
                        f.payload.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
                    )
                }
            a.rawRx.isNotEmpty() ->
                "$head -> no frame, raw=" +
                    a.rawRx.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            else -> "$head -> silent (timeout)"
        }
    }
}
