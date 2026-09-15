package com.vag.vcdsandroid.protocol

import com.vag.vcdsandroid.bluetooth.ElmResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenericObdHandshakeTest {

    private fun mockResponse(cmd: String, raw: String, timedOut: Boolean = false): ElmResponse {
        return ElmResponse(
            command = cmd,
            raw = raw,
            elapsedMs = 100L,
            timedOut = timedOut,
            promptReceived = !timedOut
        )
    }

    @Test
    fun testAutoStageSuccessWhenBothRpmAndMapValid() = runBlocking {
        val logLines = mutableListOf<String>()
        val sentCommands = mutableListOf<String>()

        val handshake = GenericObdHandshake(
            sendCmd = { cmd, _ ->
                sentCommands.add(cmd)
                when (cmd) {
                    "010C" -> mockResponse(cmd, "41 0C 0B B8\r>") // 750 RPM
                    "010B" -> mockResponse(cmd, "41 0B 63\r>")    // 990 mbar
                    "ATDP" -> mockResponse(cmd, "ISO 15765-4 (CAN 11/500)\r>")
                    "ATDPN" -> mockResponse(cmd, "6\r>")
                    else -> mockResponse(cmd, "OK\r>")
                }
            },
            log = { logLines.add(it) }
        )

        val result = handshake.execute()
        assertTrue(result is HandshakeResult.Success)
        val success = result as HandshakeResult.Success
        assertEquals("AUTO", success.stage)

        // Ensure Stage 2 (ATSP6) was NEVER reached
        assertFalse(sentCommands.contains("ATSP6"))
    }

    @Test
    fun testAutoStageFailsWhenOnlyRpmValidThenStage2Succeeds() = runBlocking {
        val sentCommands = mutableListOf<String>()
        var stage2Reached = false

        val handshake = GenericObdHandshake(
            sendCmd = { cmd, _ ->
                sentCommands.add(cmd)
                when (cmd) {
                    "ATSP6" -> {
                        stage2Reached = true
                        mockResponse(cmd, "OK\r>")
                    }
                    "010C" -> {
                        mockResponse(cmd, "41 0C 0B B8\r>")
                    }
                    "010B" -> {
                        if (!stage2Reached) {
                            mockResponse(cmd, "NO DATA\r>") // Stage 1 fails on MAP!
                        } else {
                            mockResponse(cmd, "41 0B 63\r>") // Stage 2 succeeds on MAP!
                        }
                    }
                    "ATDP" -> mockResponse(cmd, "ISO 15765-4 (CAN 11/500)\r>")
                    "ATDPN" -> mockResponse(cmd, "6\r>")
                    else -> mockResponse(cmd, "OK\r>")
                }
            },
            log = {}
        )

        val result = handshake.execute()
        assertTrue(result is HandshakeResult.Success)
        val success = result as HandshakeResult.Success
        assertEquals("SP6_DEFAULT_HEADER", success.stage)
        assertTrue(stage2Reached)
    }

    @Test
    fun testAutoStageFailsWhenOnlyMapValid() = runBlocking {
        val handshake = GenericObdHandshake(
            sendCmd = { cmd, _ ->
                when (cmd) {
                    "010C" -> mockResponse(cmd, "NO DATA\r>")
                    "010B" -> mockResponse(cmd, "41 0B 63\r>")
                    else -> mockResponse(cmd, "OK\r>")
                }
            },
            log = {}
        )

        val result = handshake.execute()
        assertTrue(result is HandshakeResult.Failure)
    }

    @Test
    fun testOnly0100NeverSucceedsForTurboFast() = runBlocking {
        val handshake = GenericObdHandshake(
            sendCmd = { cmd, _ ->
                when (cmd) {
                    "010C" -> mockResponse(cmd, "NO DATA\r>")
                    "010B" -> mockResponse(cmd, "NO DATA\r>")
                    "0100" -> mockResponse(cmd, "41 00 BE 3F B8 11\r>")
                    else -> mockResponse(cmd, "OK\r>")
                }
            },
            log = {}
        )

        val result = handshake.execute()
        assertTrue(result is HandshakeResult.Failure)
    }

    @Test
    fun testTimeoutWithPartialPayloadIsRejected() = runBlocking {
        val handshake = GenericObdHandshake(
            sendCmd = { cmd, _ ->
                when (cmd) {
                    "010C" -> mockResponse(cmd, "41 0C 0B B8", timedOut = true)
                    "010B" -> mockResponse(cmd, "41 0B 63", timedOut = false)
                    else -> mockResponse(cmd, "OK\r>")
                }
            },
            log = {}
        )

        val result = handshake.execute()
        assertTrue(result is HandshakeResult.Failure)
    }

    private fun assertFalse(condition: Boolean) {
        assertTrue(!condition)
    }
}
