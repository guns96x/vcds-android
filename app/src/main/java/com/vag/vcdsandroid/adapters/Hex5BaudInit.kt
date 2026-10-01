package com.vag.vcdsandroid.adapters

import com.vag.vcdsandroid.usb.B03RxClassifier
import com.vag.vcdsandroid.usb.UsbConnectResult

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
 * FUN_14007e3b4 lines 20-33): odd parity in bit 7, set only when the 7-bit
 * address has an EVEN number of ones, so 0x01 -> 0x01 and 0x03 -> 0x83; 0x33 is
 * sent unchanged. The first draft's 0x81 for ECU 01 is RETRACTED in the
 * canonical knowledge base (VCDS_AI_ENTRYPOINT.md section 5) and is not sent.
 *
 * Open question (UNKNOWN): whether the 0x55 in the reply is read from the K-Line
 * or filled in by the cable. Static notes disagree, so 0x55 plus plausible key
 * bytes is reported as "ECU answered 5-baud init", never as a KWP session.
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

    fun encodeRequest(addressByte: Int): ByteArray =
        HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_HOST,
            opcode = OPCODE_5BAUD_INIT,
            payload = byteArrayOf(SUB_KLINE_INIT, addressByte.toByte(), FLAGS_STANDARD)
        )

    data class Reply(val baud: Int, val keyByte1: Int, val keyByte2: Int)

    /**
     * Parses the payload of a 0x84 reply frame (opcode excluded):
     * [baudHi, baudLo, KB1, KB2, 0x55]. Returns null unless it is exactly five
     * bytes and the sync byte is 0x55 (frame length byte 0x09 = 4 + 5).
     */
    fun parseReply(payload: ByteArray): Reply? {
        if (payload.size != 5) return null
        if ((payload[4].toInt() and 0xFF) != SYNC_BYTE) return null
        val baud = ((payload[0].toInt() and 0xFF) shl 8) or (payload[1].toInt() and 0xFF)
        return Reply(
            baud = baud,
            keyByte1 = payload[2].toInt() and 0xFF,
            keyByte2 = payload[3].toInt() and 0xFF
        )
    }
}

/** Key bytes of a floating or shorted line (all zero / all one) cannot come from an ECU. */
fun Hex5BaudInit.Reply.hasPlausibleKeyBytes(): Boolean =
    !(keyByte1 == 0x00 && keyByte2 == 0x00) && !(keyByte1 == 0xFF && keyByte2 == 0xFF)

/** KW1281 keywords (01 8A): the ECU does not speak KWP2000 on this path. */
fun Hex5BaudInit.Reply.isKw1281(): Boolean = keyByte1 == 0x01 && keyByte2 == 0x8A

/** One 0x84 attempt: what was sent and every checksum-valid frame the cable returned. */
data class Hex5BaudAttempt(
    val addressByte: Int,
    val request: ByteArray,
    val frames: List<HexB03Frame>,
    val rawRx: ByteArray,
    val reply: Hex5BaudInit.Reply?
)

/** Classifies one 0x84 attempt into a user-visible outcome; pure so every branch is tested. */
fun Hex5BaudAttempt.outcome(): UsbConnectResult {
    val r = reply
    if (r != null) {
        return when {
            r.isKw1281() -> UsbConnectResult.UNSUPPORTED_PROTOCOL
            !r.hasPlausibleKeyBytes() -> UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE
            else -> UsbConnectResult.ECU_INIT_ANSWERED
        }
    }
    // The cable answered on 0x84 but without sync: it ran the wake-up and the ECU did not answer.
    if (frames.any { it.opcode == Hex5BaudInit.OPCODE_5BAUD_INIT }) return UsbConnectResult.ECU_NOT_RESPONDING
    return B03RxClassifier.classifyNoReply(
        stage = B03RxClassifier.Stage.ECU_INIT,
        request = request,
        raw = rawRx,
        frames = frames,
        expectedOpcode = Hex5BaudInit.OPCODE_5BAUD_INIT
    )
}

data class Hex5BaudInitResult(
    val address: Int,
    val attempts: List<Hex5BaudAttempt>
) {
    val success: Hex5BaudAttempt? get() = attempts.firstOrNull { it.reply != null }

    /** Outcome of the deciding attempt: the one with a reply, else the last one made. */
    fun outcome(): UsbConnectResult =
        (success ?: attempts.lastOrNull())?.outcome() ?: UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE

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
            a.outcome() == UsbConnectResult.ADAPTER_ECHO_ONLY ->
                "$head -> ECHO ONLY (cable returned our request), raw=" +
                    a.rawRx.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            a.rawRx.isNotEmpty() ->
                "$head -> no frame, raw=" +
                    a.rawRx.take(16).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
            else -> "$head -> silent (timeout)"
        }
    }
}
