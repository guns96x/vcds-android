package com.vag.vcdsandroid.bluetooth

import java.io.InputStream

object ElmPromptRecovery {
    /**
     * Attempts to drain the input stream until an ELM327 '>' prompt is encountered.
     * Returns true if prompt was seen within timeoutMs, false otherwise.
     */
    fun recoverToPrompt(
        input: InputStream,
        timeoutMs: Long = 1200L,
        timeProvider: () -> Long = { System.currentTimeMillis() },
        sleepFn: (Long) -> Unit = { Thread.sleep(it) }
    ): Boolean {
        val start = timeProvider()
        try {
            while (timeProvider() - start < timeoutMs) {
                while (input.available() > 0) {
                    val c = input.read().toChar()
                    if (c == '>') {
                        return true
                    }
                }
                sleepFn(2)
            }
        } catch (_: Exception) {
            return false
        }
        return false
    }
}
