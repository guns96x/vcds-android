package com.vag.vcdsandroid.protocol

/**
 * Pure ISO 9141 / ISO 14230 five-baud slow-init helpers.
 *
 * Kept Android-free so the timing/bit rules can be unit tested on the JVM.
 */
object KwpSlowInit {
    const val PRIMARY_SESSION_BAUD = 10_400
    const val SECONDARY_SESSION_BAUD = 9_600

    const val BIT_TIME_MS = 200L
    const val W0_IDLE_MIN_MS = 2L
    const val PRE_INIT_QUIET_MS = 300L
    const val W1_SYNC_MAX_MS = 450L
    const val W2_KEY1_MAX_MS = 20L
    const val W3_KEY2_MAX_MS = 20L
    const val W4_COMPLEMENT_MIN_MS = 25L
    const val W4_COMPLEMENT_MAX_MS = 50L
    const val ADDRESS_COMPLEMENT_TIMEOUT_MS = 100L

    // In-car retries immediately after a failed init are unreliable; the existing
    // literature/bench work uses a multi-second quiet gap.
    const val RETRY_QUIET_MS = 2_600L

    /**
     * Generates the line levels for a seven-data-bit, odd-parity five-baud address.
     *
     * false = K-line LOW / BREAK asserted
     * true  = K-line HIGH / BREAK released
     *
     * Order: start, D0..D6 (LSB first), odd parity, stop.
     */
    fun addressBits7O1(address: Int): BooleanArray {
        require(address in 0..0x7F) { "Five-baud 7O1 address must fit in 7 bits" }

        val bits = BooleanArray(10)
        bits[0] = false // start

        var ones = 0
        for (i in 0 until 7) {
            val high = ((address shr i) and 1) != 0
            bits[1 + i] = high
            if (high) ones++
        }

        // Odd parity: data ones + parity one must be odd.
        bits[8] = (ones % 2 == 0)
        bits[9] = true // stop / idle high
        return bits
    }

    fun expectedAddressComplement(address: Int): Int {
        require(address in 0..0xFF)
        return address xor 0xFF
    }

    fun byteComplement(value: Int): Int {
        require(value in 0..0xFF)
        return value xor 0xFF
    }
}
