package com.vag.vcdsandroid.protocol

class CoreTelemetryHealth(
    private val maxConsecutiveFailures: Int = 3
) {
    var consecutiveFailures: Int = 0
        private set

    val isTripped: Boolean
        get() = consecutiveFailures >= maxConsecutiveFailures

    /**
     * Evaluates a pair of core RPM and MAP statuses.
     * Returns true if link remains healthy, false if tripped (>= maxConsecutiveFailures).
     */
    fun onPair(rpmStatus: PidStatus, mapStatus: PidStatus): Boolean {
        val healthy = rpmStatus == PidStatus.VALID && mapStatus == PidStatus.VALID
        if (healthy) {
            consecutiveFailures = 0
            return true
        }

        val hardFailure = rpmStatus in HARD_FAILURES || mapStatus in HARD_FAILURES
        if (hardFailure) {
            consecutiveFailures++
        }
        return consecutiveFailures < maxConsecutiveFailures
    }

    fun reset() {
        consecutiveFailures = 0
    }

    companion object {
        val HARD_FAILURES = setOf(
            PidStatus.TIMEOUT,
            PidStatus.ERROR,
            PidStatus.INVALID_FORMAT,
            PidStatus.NO_DATA
        )
    }
}
