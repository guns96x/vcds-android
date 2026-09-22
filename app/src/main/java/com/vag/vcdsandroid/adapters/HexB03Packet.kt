package com.vag.vcdsandroid.adapters

/**
 * Protocol framing and packet codec for Ross-Tech HEX-USB+CAN / B03-V2 (0403:FA24).
 *
 * Wire format:
 * [marker][length][opcode][payload...][xor_checksum]
 * - Marker: 0x53 ('S') Host -> Cable, 0x4D ('M') Cable -> Host.
 * - Length: Total frame length in bytes (3 + payload.length).
 * - Opcode: Command or response opcode.
 * - XOR Checksum: XOR sum of all preceding bytes.
 */
object HexB03Constants {
    const val MARKER_HOST: Byte = 0x53  // ASCII 'S'
    const val MARKER_CABLE: Byte = 0x4D // ASCII 'M'
    const val MIN_FRAME_LEN: Int = 4     // Marker + Len + Opcode + XOR
    const val MAX_FRAME_LEN: Int = 255
    const val MAX_BUFFER_CAPACITY: Int = 4096
}

data class HexB03Frame(
    val marker: Byte,
    val length: Int,
    val opcode: Byte,
    val payload: ByteArray,
    val xorChecksum: Byte
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HexB03Frame
        return marker == other.marker &&
                length == other.length &&
                opcode == other.opcode &&
                payload.contentEquals(other.payload) &&
                xorChecksum == other.xorChecksum
    }

    override fun hashCode(): Int {
        var result = marker.toInt()
        result = 31 * result + length
        result = 31 * result + opcode.toInt()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + xorChecksum.toInt()
        return result
    }
}

object HexB03FrameCodec {
    /**
     * Encode an outgoing frame with calculated length and XOR checksum.
     */
    fun encode(
        marker: Byte = HexB03Constants.MARKER_HOST,
        opcode: Byte,
        payload: ByteArray = ByteArray(0)
    ): ByteArray {
        val totalLen = 4 + payload.size
        require(totalLen <= HexB03Constants.MAX_FRAME_LEN) {
            "Payload too large: total frame length $totalLen exceeds maximum ${HexB03Constants.MAX_FRAME_LEN}"
        }

        val frame = ByteArray(totalLen)
        frame[0] = marker
        frame[1] = totalLen.toByte()
        frame[2] = opcode
        if (payload.isNotEmpty()) {
            System.arraycopy(payload, 0, frame, 3, payload.size)
        }

        var xorSum: Byte = 0
        for (i in 0 until totalLen - 1) {
            xorSum = (xorSum.toInt() xor frame[i].toInt()).toByte()
        }
        frame[totalLen - 1] = xorSum
        return frame
    }

    /**
     * Decode a single complete frame from a byte array. Returns null if invalid.
     */
    fun decode(data: ByteArray): HexB03Frame? {
        if (data.size < HexB03Constants.MIN_FRAME_LEN) return null
        val marker = data[0]
        if (marker != HexB03Constants.MARKER_HOST && marker != HexB03Constants.MARKER_CABLE) return null

        val len = data[1].toInt() and 0xFF
        if (len < HexB03Constants.MIN_FRAME_LEN || data.size < len) return null

        var computedXor: Byte = 0
        for (i in 0 until len - 1) {
            computedXor = (computedXor.toInt() xor data[i].toInt()).toByte()
        }

        val storedXor = data[len - 1]
        if (computedXor != storedXor) return null

        val opcode = data[2]
        val payloadLen = len - 4
        val payload = ByteArray(payloadLen)
        if (payloadLen > 0) {
            System.arraycopy(data, 3, payload, 0, payloadLen)
        }

        return HexB03Frame(
            marker = marker,
            length = len,
            opcode = opcode,
            payload = payload,
            xorChecksum = storedXor
        )
    }
}

/**
 * Stateful stream decoder that buffers incoming UART/USB bytes, resynchronizes on markers,
 * verifies checksums, and emits complete frames.
 */
class HexB03StreamDecoder(
    private val expectedMarker: Byte = HexB03Constants.MARKER_CABLE
) {
    private val buffer = ArrayList<Byte>(1024)

    @Synchronized
    fun feed(chunk: ByteArray): List<HexB03Frame> {
        val out = mutableListOf<HexB03Frame>()

        // Prevent buffer bloat
        if (buffer.size + chunk.size > HexB03Constants.MAX_BUFFER_CAPACITY) {
            buffer.clear()
        }

        for (b in chunk) {
            buffer.add(b)
        }

        while (buffer.isNotEmpty()) {
            // Drop leading garbage until expected marker is found
            while (buffer.isNotEmpty() && buffer[0] != expectedMarker) {
                buffer.removeAt(0)
            }

            if (buffer.size < 2) break // Need at least marker and length

            val totalLen = buffer[1].toInt() and 0xFF
            if (totalLen < HexB03Constants.MIN_FRAME_LEN || totalLen > HexB03Constants.MAX_FRAME_LEN) {
                // Invalid length byte; drop corrupt marker and resync
                buffer.removeAt(0)
                continue
            }

            if (buffer.size < totalLen) {
                // Frame incomplete, wait for more data
                break
            }

            // Extract candidate frame bytes
            val frameBytes = ByteArray(totalLen)
            for (i in 0 until totalLen) {
                frameBytes[i] = buffer[i]
            }

            val frame = HexB03FrameCodec.decode(frameBytes)
            if (frame != null) {
                out.add(frame)
                // Consume frame bytes from buffer
                repeat(totalLen) { buffer.removeAt(0) }
            } else {
                // Checksum failed or corrupt payload, drop marker and search next
                buffer.removeAt(0)
            }
        }

        return out
    }

    @Synchronized
    fun reset() {
        buffer.clear()
    }
}

/**
 * Sealed hierarchy of verified read-only adapter commands.
 * Callers cannot construct arbitrary raw frames through this interface.
 */
sealed class VerifiedB03Command(
    val opcode: Byte,
    val payload: ByteArray = ByteArray(0),
    val description: String
) {
    object ProbePing : VerifiedB03Command(
        opcode = 0x02.toByte(),
        description = "PROVEN_STATIC: Probe ping (checks MCU presence)"
    )

    object Identify : VerifiedB03Command(
        opcode = 0x04.toByte(),
        description = "PROVEN_STATIC: Identify query (version banner)"
    )

    object StatusRead : VerifiedB03Command(
        opcode = 0x82.toByte(),
        description = "PROVEN_STATIC: Status read"
    )

    object ModeRead : VerifiedB03Command(
        opcode = 0x0D.toByte(),
        description = "PROVEN_STATIC: Mode read"
    )

    object KeepalivePing : VerifiedB03Command(
        opcode = 0xA0.toByte(),
        description = "PROVEN_STATIC: Keepalive ping"
    )

    fun encodeFrame(): ByteArray {
        return HexB03FrameCodec.encode(
            marker = HexB03Constants.MARKER_HOST,
            opcode = opcode,
            payload = payload
        )
    }
}
