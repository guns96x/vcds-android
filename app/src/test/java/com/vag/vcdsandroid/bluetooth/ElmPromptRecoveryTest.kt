package com.vag.vcdsandroid.bluetooth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class ElmPromptRecoveryTest {

    @Test
    fun testPromptArrivesImmediately() {
        val stream = ByteArrayInputStream(">".toByteArray())
        val ok = ElmPromptRecovery.recoverToPrompt(stream, timeoutMs = 100L)
        assertTrue(ok)
    }

    @Test
    fun testPromptArrivesAfterGarbage() {
        val stream = ByteArrayInputStream("GARBAGE DATA NO DATA\r\n>".toByteArray())
        val ok = ElmPromptRecovery.recoverToPrompt(stream, timeoutMs = 100L)
        assertTrue(ok)
    }

    @Test
    fun testNoPromptReturnsFalse() {
        val stream = ByteArrayInputStream("GARBAGE DATA NO PROMPT".toByteArray())
        val ok = ElmPromptRecovery.recoverToPrompt(
            stream,
            timeoutMs = 50L,
            timeProvider = { System.currentTimeMillis() },
            sleepFn = {}
        )
        assertFalse(ok)
    }

    @Test
    fun testEmptyStreamReturnsFalse() {
        val stream = ByteArrayInputStream(ByteArray(0))
        val ok = ElmPromptRecovery.recoverToPrompt(
            stream,
            timeoutMs = 50L,
            timeProvider = { System.currentTimeMillis() },
            sleepFn = {}
        )
        assertFalse(ok)
    }
}
