package com.vag.vcdsandroid.protocol

/**
 * Pure ISO 9141 / ISO 14230 five-baud slow-init helpers.
 *
 * Kept Android-free so the timing/bit rules can be unit tested on the JVM.
 */
object KwpSlowInit {
    const val PRIMARY_SESSION_BAUD = 10_400

    /** ISO 14230-4 OBD functional address, the one the ELM proved on this car. */
    const val OBD_FUNCTIONAL_ADDRESS = 0x33
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

    /** DTR level per connection attempt: clear, clear, then asserted. */
    fun dtrAssertedForAttempt(attempt: Int): Boolean = attempt >= 3

    /** Human-readable cause of a failed five-baud init, shown to the user. */
    fun describeFailure(
        stage: String?,
        sync: Int?,
        key1: Int?,
        key2: Int?,
        klineEchoSeen: Boolean,
        dtrAsserted: Boolean
    ): String {
        fun hex(v: Int?) = v?.let { "%02X".format(it) } ?: "--"
        val bytes = "(sync=${hex(sync)}, KB1=${hex(key1)}, KB2=${hex(key2)}, " +
            "DTR=${if (dtrAsserted) "ON" else "OFF"})"
        val hint = when {
            stage == "KW1281_KEYWORDS" ->
                "ECU answered with KW1281 keywords 01 8A; this app only speaks KWP2000 on K-Line."
            stage == "WAIT_SYNC_55" && !klineEchoSeen ->
                "No K-Line echo at all: the interface is not passing K-Line " +
                    "(still in intelligent mode, or no power on OBD pin 16)."
            stage == "WAIT_SYNC_55" ->
                "K-Line echo seen but no 0x55 sync: ignition off, or ECU 01 is not on K-Line."
            stage == "W4_MISSED" ->
                "ECU keywords received but the phone answered outside the 25-50 ms W4 window."
            stage == "WAIT_ADDRESS_COMPLEMENT" ->
                "ECU did not confirm the key-byte complement."
            else -> null
        }
        return "01-Engine slow init failed at ${stage ?: "UNKNOWN"} $bytes." +
            (hint?.let { " $it" } ?: "")
    }
    data class AttemptSummary(val dtrAsserted: Boolean, val stage: String?, val klineEchoSeen: Boolean)

    /**
     * Final M2 error text: every attempt's DTR level and outcome, the last
     * detailed failure, and the verdict of the OBD 0x33 control init if it ran.
     */
    fun summarizeAttempts(
        attempts: List<AttemptSummary>,
        lastFailure: String,
        obdControlSuccess: Boolean?,
        obdControlStage: String?
    ): String {
        val perAttempt = attempts.mapIndexed { i, a ->
            "#${i + 1} DTR=${if (a.dtrAsserted) "ON" else "OFF"} " +
                "${a.stage ?: "OK"} echo=${if (a.klineEchoSeen) "yes" else "no"}"
        }.joinToString("; ")
        val verdict = when (obdControlSuccess) {
            true ->
                "OBD control init on address 33 SUCCEEDED: cable and K-Line work. " +
                    "ECU does not accept VAG address 01 on K-Line; 01-Engine needs CAN/TP2.0."
            false ->
                "OBD control init on address 33 also failed (${obdControlStage ?: "UNKNOWN"}): " +
                    "the phone-cable K-Line path itself is not working yet."
            null -> null
        }
        return "Attempts: $perAttempt.\n$lastFailure" + (verdict?.let { "\n$it" } ?: "")
    }
}
