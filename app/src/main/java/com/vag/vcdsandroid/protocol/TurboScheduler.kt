package com.vag.vcdsandroid.protocol

class TurboScheduler {
    private var pairCount = 0L
    private var lastSlowPidMs = 0L
    private var slowPidIdx = 0

    val slowPids = listOf("0105", "010F", "0142", "0133")

    fun reset() {
        pairCount = 0L
        lastSlowPidMs = 0L
        slowPidIdx = 0
    }

    /**
     * Deterministic aux cadence used for pair-first polling:
     * - MAF (0110): every 4th RPM/MAP pair
     * - Speed (010D): every 8th RPM/MAP pair
     * - Load (0104): every 8th RPM/MAP pair, offset from Speed by 4 pairs
     */
    fun nextAuxPids(): List<String> {
        pairCount++
        val pids = mutableListOf<String>()
        if (pairCount % 4L == 0L) pids.add("0110")  // MAF
        if (pairCount % 8L == 0L) pids.add("010D") // Speed
        if (pairCount % 8L == 4L) pids.add("0104") // Load
        return pids
    }

    fun nextRecordingAuxPids(): List<String> = nextAuxPids()

    /**
     * Check if a slow PID should be queried in LIVE mode (~every 2500 ms).
     * Never queried in RECORDING mode.
     */
    fun checkLiveSlowPid(nowMs: Long, intervalMs: Long = 2500L): String? {
        if (lastSlowPidMs == 0L || nowMs - lastSlowPidMs >= intervalMs) {
            lastSlowPidMs = nowMs
            val pid = slowPids[slowPidIdx % slowPids.size]
            slowPidIdx++
            return pid
        }
        return null
    }
}
