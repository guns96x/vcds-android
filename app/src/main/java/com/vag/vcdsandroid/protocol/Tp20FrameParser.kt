package com.vag.vcdsandroid.protocol

import java.util.Locale

sealed class Tp20Result {
    data class Success(
        val kwpPayload: ByteArray,
        val nextTxSeq: Int,
        val lastRxSeq: Int,
        val sawKeepAlive: Boolean,
        val sawAck: Boolean,
        val needsAck: Boolean,
        val ackCode: Int
    ) : Tp20Result() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Success
            return kwpPayload.contentEquals(other.kwpPayload) &&
                    nextTxSeq == other.nextTxSeq &&
                    lastRxSeq == other.lastRxSeq &&
                    sawKeepAlive == other.sawKeepAlive &&
                    sawAck == other.sawAck &&
                    needsAck == other.needsAck &&
                    ackCode == other.ackCode
        }

        override fun hashCode(): Int {
            var result = kwpPayload.contentHashCode()
            result = 31 * result + nextTxSeq
            result = 31 * result + lastRxSeq
            result = 31 * result + sawKeepAlive.hashCode()
            result = 31 * result + sawAck.hashCode()
            result = 31 * result + needsAck.hashCode()
            result = 31 * result + ackCode
            return result
        }
    }

    data class PeerDisconnect(val reason: String) : Tp20Result()
    data class PeerBusy(val reason: String) : Tp20Result()
    data class Incomplete(val expectedLen: Int, val actualLen: Int) : Tp20Result()
    data class ProtocolError(val message: String) : Tp20Result()
    object TimeoutOrNoData : Tp20Result()
}

object Tp20FrameParser {
    /**
     * Parses a measuring-group KWP response carried over TP 2.0 and validates
     * the expected positive service (0x61) and group number.
     */
    fun parse(rawResp: String, expectedGroup: Int, currentTxSeq: Int): Tp20Result =
        parseInternal(rawResp, currentTxSeq, expectedGroup, minPayloadLength = 14)

    /**
     * Parses an arbitrary KWP payload carried over TP 2.0. Framing, segmentation,
     * payload length, ACK sequence and control frames are still validated, but
     * the KWP service byte is left to the caller (needed for DTC 0x18/0x14).
     */
    fun parseKwp(rawResp: String, currentTxSeq: Int, minPayloadLength: Int = 1): Tp20Result =
        parseInternal(rawResp, currentTxSeq, expectedGroup = null, minPayloadLength = minPayloadLength)

    private fun parseInternal(
        rawResp: String,
        currentTxSeq: Int,
        expectedGroup: Int?,
        minPayloadLength: Int
    ): Tp20Result {
        if (rawResp.contains("NO DATA", ignoreCase = true) ||
            rawResp.contains("UNABLE TO CONNECT", ignoreCase = true) ||
            rawResp.contains("BUS BUSY", ignoreCase = true)
        ) {
            return Tp20Result.TimeoutOrNoData
        }

        val lines = rawResp.split("\r", "\n")
            .map { it.replace(" ", "").trim().uppercase(Locale.US) }
            .filter { line ->
                line.isNotEmpty() &&
                !line.startsWith("OK") &&
                !line.startsWith("SEARCHING") &&
                !line.startsWith(">") &&
                !line.contains("NODATA") &&
                !line.contains("ERROR") &&
                !line.contains("?")
            }

        if (lines.isEmpty()) return Tp20Result.TimeoutOrNoData

        var sawKeepAlive = false
        var sawAck = false
        var peerDisconnect = false
        var peerBusy = false
        var expectedPayloadLen = -1
        val payloadBytes = ArrayList<Byte>()
        var lastRxSeq = -1
        var needsAck = false

        for (line in lines) {
            // Check standalone control frames (length exactly 2 hex characters)
            if (line == "A8") {
                peerDisconnect = true
                continue
            }
            if (line == "A3") {
                sawKeepAlive = true
                continue
            }
            if (line.length == 2 && line.startsWith("B")) {
                sawAck = true
                continue
            }
            if (line.length == 2 && line.startsWith("9")) {
                peerBusy = true
                continue
            }

            if (line.length < 2) continue

            val opcodeByte = line.substring(0, 2).toIntOrNull(16) ?: continue
            val highNibble = (opcodeByte shr 4) and 0x0F
            val seq = opcodeByte and 0x0F

            when (highNibble) {
                0x0 -> {
                    // Single / unsegmented frame: 0x [len] [payload...]
                    if (expectedPayloadLen < 0) {
                        if (line.length >= 6) {
                            val lenHi = line.substring(2, 4).toIntOrNull(16) ?: 0
                            val lenLo = line.substring(4, 6).toIntOrNull(16) ?: 0
                            expectedPayloadLen = (lenHi shl 8) or lenLo
                            val dataHex = line.substring(6)
                            for (k in 0 until dataHex.length step 2) {
                                if (k + 2 <= dataHex.length) {
                                    payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                                }
                            }
                        } else if (line.length >= 4) {
                            val len = line.substring(2, 4).toIntOrNull(16) ?: 0
                            expectedPayloadLen = len
                            val dataHex = line.substring(4)
                            for (k in 0 until dataHex.length step 2) {
                                if (k + 2 <= dataHex.length) {
                                    payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                                }
                            }
                        }
                    } else {
                        val dataHex = line.substring(2)
                        for (k in 0 until dataHex.length step 2) {
                            if (k + 2 <= dataHex.length) {
                                payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                            }
                        }
                    }
                    lastRxSeq = seq
                }
                0x2 -> {
                    // Intermediate segmented frame
                    if (expectedPayloadLen < 0) {
                        // First frame of segmented message: 2x <lenHi> <lenLo> <payload...>
                        if (line.length >= 6) {
                            val lenHi = line.substring(2, 4).toIntOrNull(16) ?: 0
                            val lenLo = line.substring(4, 6).toIntOrNull(16) ?: 0
                            expectedPayloadLen = (lenHi shl 8) or lenLo

                            val dataHex = line.substring(6)
                            for (k in 0 until dataHex.length step 2) {
                                if (k + 2 <= dataHex.length) {
                                    payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                                }
                            }
                        }
                    } else {
                        // Subsequent intermediate frame: 2x <payload...>
                        val dataHex = line.substring(2)
                        for (k in 0 until dataHex.length step 2) {
                            if (k + 2 <= dataHex.length) {
                                payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                            }
                        }
                    }
                    lastRxSeq = seq
                }
                0x1 -> {
                    // Final segmented frame or single frame
                    if (expectedPayloadLen < 0) {
                        // First and only frame: 1x <lenHi> <lenLo> <payload...>
                        if (line.length >= 6) {
                            val lenHi = line.substring(2, 4).toIntOrNull(16) ?: 0
                            val lenLo = line.substring(4, 6).toIntOrNull(16) ?: 0
                            expectedPayloadLen = (lenHi shl 8) or lenLo
                            val dataHex = line.substring(6)
                            for (k in 0 until dataHex.length step 2) {
                                if (k + 2 <= dataHex.length) {
                                    payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                                }
                            }
                        } else {
                            val dataHex = line.substring(2)
                            for (k in 0 until dataHex.length step 2) {
                                if (k + 2 <= dataHex.length) {
                                    payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                                }
                            }
                        }
                    } else {
                        // Final frame of already established segmented message: 1x <payload...>
                        val dataHex = line.substring(2)
                        for (k in 0 until dataHex.length step 2) {
                            if (k + 2 <= dataHex.length) {
                                payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                            }
                        }
                    }
                    lastRxSeq = seq
                    needsAck = true
                }
                0x3 -> {
                    // Segmented frame requesting immediate ACK: 3x <payload...>
                    val dataHex = line.substring(2)
                    for (k in 0 until dataHex.length step 2) {
                        if (k + 2 <= dataHex.length) {
                            payloadBytes.add(dataHex.substring(k, k + 2).toInt(16).toByte())
                        }
                    }
                    lastRxSeq = seq
                    needsAck = true
                }
                0xA -> {
                    if (line == "A8") {
                        peerDisconnect = true
                    } else if (line == "A3") {
                        sawKeepAlive = true
                    }
                }
                0xB -> {
                    sawAck = true
                }
                0x9 -> {
                    peerBusy = true
                }
            }
        }

        if (peerDisconnect) {
            return Tp20Result.PeerDisconnect("ECU sent A8")
        }
        if (peerBusy && payloadBytes.isEmpty()) {
            return Tp20Result.PeerBusy("ECU sent 9x")
        }

        // Validate payload length if expectedPayloadLen was set
        if (expectedPayloadLen > 0 && payloadBytes.size < expectedPayloadLen) {
            return Tp20Result.Incomplete(expectedPayloadLen, payloadBytes.size)
        }

        val requiredMin = if (expectedGroup != null) {
            maxOf(14, minPayloadLength)
        } else {
            minPayloadLength.coerceAtLeast(1)
        }
        if (payloadBytes.size < requiredMin) {
            return Tp20Result.Incomplete(requiredMin, payloadBytes.size)
        }

        val finalBytes = if (expectedPayloadLen > 0 && payloadBytes.size >= expectedPayloadLen) {
            payloadBytes.take(expectedPayloadLen).toByteArray()
        } else {
            payloadBytes.toByteArray()
        }

        if (expectedGroup != null) {
            // Measuring-group positive response: 0x61 <group>.
            val respService = finalBytes[0].toInt() and 0xFF
            val respGroup = finalBytes[1].toInt() and 0xFF
            if (respService != 0x61 || respGroup != expectedGroup) {
                return Tp20Result.ProtocolError(
                    "Invalid KWP header: expected 61 %02X, got %02X %02X"
                        .format(Locale.US, expectedGroup, respService, respGroup)
                )
            }
        }

        val ackSeq = if (lastRxSeq >= 0) (lastRxSeq + 1) and 0x0F else 0
        val ackCode = 0xB0 or ackSeq

        return Tp20Result.Success(
            kwpPayload = finalBytes,
            nextTxSeq = (currentTxSeq + 1) and 0x0F,
            lastRxSeq = lastRxSeq,
            sawKeepAlive = sawKeepAlive,
            sawAck = sawAck,
            needsAck = needsAck,
            ackCode = ackCode
        )
    }
}
