package com.vcds.android.transport

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Ross-Tech HEX-USB+CAN Packet Framing and Transport Layer
 * Reverse-engineered directly from VCDS 26.3 (VCDS.EXE)
 *
 * Evidence:
 * - Frame builder: Ghidra VA 0x14007E734 (FUN_14007e734)
 * - Response parser: Ghidra VA 0x14007E824 (FUN_14007e824)
 */
object HexFraming {
    const val SYNC_REQUEST: Byte = 0x53.toByte()   // ASCII 'S'
    const val SYNC_RESPONSE: Byte = 0x4D.toByte()  // ASCII 'M'
    const val MAX_PAYLOAD_SIZE = 80

    /**
     * Builds wire frame from command payload.
     * Wire layout: [ 0x53 ] [ Length ] [ Opcode ] [ Data ... ] [ XOR Checksum ]
     * Length = payload.size + 3
     */
    fun buildFrame(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty()) { "Payload cannot be empty" }
        require(payload.size <= MAX_PAYLOAD_SIZE) { "Payload size exceeds maximum of $MAX_PAYLOAD_SIZE bytes" }

        val frameLength = (payload.size + 3).toByte()
        var checksum = (SYNC_REQUEST.toInt() xor frameLength.toInt()).toByte()

        val output = ByteArray(frameLength.toInt())
        output[0] = SYNC_REQUEST
        output[1] = frameLength

        for (i in payload.indices) {
            val b = payload[i]
            output[2 + i] = b
            checksum = (checksum.toInt() xor b.toInt()).toByte()
        }

        output[frameLength.toInt() - 1] = checksum
        return output
    }

    /**
     * Reads and validates a complete response frame from the adapter.
     * Validates:
     * 1. Header sync byte == 0x4D ('M')
     * 2. Length byte (3 .. 48)
     * 3. Cumulative XOR checksum == 0
     * Returns extracted payload (stripping 'M', length, and checksum).
     */
    @Throws(IOException::class)
    fun readFrame(inputStream: InputStream, timeoutMs: Long = 750): ByteArray {
        val startTime = System.currentTimeMillis()

        // 1. Wait for sync byte 'M'
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (inputStream.available() > 0) {
                val b = inputStream.read()
                if (b == SYNC_RESPONSE.toInt() and 0xFF) {
                    return readFramePayload(inputStream, startTime, timeoutMs)
                }
            } else {
                Thread.sleep(1)
            }
        }
        throw IOException("Timeout waiting for response sync byte 'M' (0x4D)")
    }

    private fun readFramePayload(inputStream: InputStream, startTime: Long, timeoutMs: Long): ByteArray {
        // 2. Read length byte
        while (inputStream.available() == 0) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw IOException("Timeout waiting for frame length byte")
            }
            Thread.sleep(1)
        }
        val len = inputStream.read()
        if (len < 3 || len > 48) {
            throw IOException("Invalid frame length: $len (expected 3..48)")
        }

        // 3. Read remaining (len - 2) bytes
        val frame = ByteArray(len)
        frame[0] = SYNC_RESPONSE
        frame[1] = len.toByte()

        var bytesRead = 2
        while (bytesRead < len) {
            if (System.currentTimeMillis() - startTime >= timeoutMs) {
                throw IOException("Timeout reading frame body ($bytesRead/$len bytes)")
            }
            val avail = inputStream.available()
            if (avail > 0) {
                val readNow = inputStream.read(frame, bytesRead, len - bytesRead)
                if (readNow > 0) {
                    bytesRead += readNow
                }
            } else {
                Thread.sleep(1)
            }
        }

        // 4. Validate cumulative XOR checksum over all bytes
        var xorSum = 0
        for (b in frame) {
            xorSum = xorSum xor (b.toInt() and 0xFF)
        }
        if (xorSum != 0) {
            throw IOException("XOR Checksum validation failed: cumulative XOR = 0x${xorSum.toString(16)}")
        }

        // 5. Extract payload (bytes 2 until len - 1)
        val payloadLen = len - 3
        val payload = ByteArray(payloadLen)
        System.arraycopy(frame, 2, payload, 0, payloadLen)
        return payload
    }
}
