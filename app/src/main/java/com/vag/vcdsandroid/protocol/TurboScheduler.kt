package com.vag.vcdsandroid.protocol

class TurboScheduler {
    private var pairCount = 0L
    private var liveStep = 0
    private var lastSlowPidMs = 0L
    private var slowPidIdx = 0

    val slowPids = listOf("0105", "010F", "0142", "0133")

    fun reset() {
        pairCount = 0L
        liveStep = 0
        lastSlowPidMs = 0L
        slowPidIdx = 0
    }

    /**
     * Called in RECORDING mode:
     * High priority to core RPM+MAP.
     * No slow sensors (0105, 010F, 0142, 0133) polled during WOT recording.
     * MAF every 6th pair.
     * Speed every 12th pair.
     * Load every 12th pair offset by 6.
     */
    fun nextRecordingAuxPids(): List<String> {
        pairCount++
        val pids = mutableListOf<String>()
        if (pairCount % 6L == 0L) pids.add("0110")  // MAF
        if (pairCount % 12L == 0L) pids.add("010D") // Speed
        if (pairCount % 12L == 6L) pids.add("0104") // Load
        return pids
    }

    /**
     * Called in LIVE (non-recording) mode:
     * Full 9-step scheduler.
     */
    fun nextLiveStep(): Int {
        val s = liveStep
        liveStep = (liveStep + 1) % 9
        return s
    }

    /**
     * Check if a slow PID should be queried in LIVE mode.
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
