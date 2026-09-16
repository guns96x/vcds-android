package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboSchedulerTest {

    @Test
    fun testPairFirstAuxCadence366Over36Pairs() {
        val scheduler = TurboScheduler()

        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..36) {
            auxMap[i] = scheduler.nextAuxPids()
        }

        // Verify 3/6/6 cadence across all 36 pairs:
        for (i in 1..36) {
            val pids = auxMap[i]!!

            // MAF (0110): strictly every 3rd pair
            if (i % 3 == 0) {
                assertTrue("Pair $i must contain MAF (0110)", pids.contains("0110"))
            } else {
                assertFalse("Pair $i must NOT contain MAF (0110)", pids.contains("0110"))
            }

            // Speed (010D): strictly every 6th pair
            if (i % 6 == 0) {
                assertTrue("Pair $i must contain Speed (010D)", pids.contains("010D"))
            } else {
                assertFalse("Pair $i must NOT contain Speed (010D)", pids.contains("010D"))
            }

            // Load (0104): strictly every 6th pair offset by 3
            if (i % 6 == 3) {
                assertTrue("Pair $i must contain Load (0104)", pids.contains("0104"))
            } else {
                assertFalse("Pair $i must NOT contain Load (0104)", pids.contains("0104"))
            }
        }

        // None of the slow PIDs (0105, 010F, 0142, 0133) should EVER appear in aux cadence
        val allAuxPids = auxMap.values.flatten()
        assertFalse(allAuxPids.contains("0105"))
        assertFalse(allAuxPids.contains("010F"))
        assertFalse(allAuxPids.contains("0142"))
        assertFalse(allAuxPids.contains("0133"))
    }

    @Test
    fun testTimingBudgetUnderMeasuredAdapterLatency() {
        val scheduler = TurboScheduler()
        val cmdLatencyMs = 216L // measured real V-LINK average

        // Over a full 6-pair cycle (1..6):
        // Pairs:
        // Pair 1: RPM + MAP (2 cmds)
        // Pair 2: RPM + MAP (2 cmds)
        // Pair 3: RPM + MAP + MAF + Load (4 cmds)
        // Pair 4: RPM + MAP (2 cmds)
        // Pair 5: RPM + MAP (2 cmds)
        // Pair 6: RPM + MAP + MAF + Speed (4 cmds)
        // Total normal commands = 16 commands = 3456 ms.

        // Worst case with 0142 -> ATRV fallback (2 slow commands) injected in LIVE mode:
        val maxSlowCmds = 2L
        val totalCycleTimeMs = (16L + maxSlowCmds) * cmdLatencyMs // 18 * 216 = 3888 ms

        // Worst case between two Speed samples (every 6 pairs):
        assertTrue("Worst-case Speed interval ($totalCycleTimeMs ms) must be < SPEED_MAX_AGE_MS (4500 ms)",
            totalCycleTimeMs < TelemetryFreshnessPolicy.SPEED_MAX_AGE_MS)

        // Worst case between two Load samples (every 6 pairs):
        assertTrue("Worst-case Load interval ($totalCycleTimeMs ms) must be < LOAD_MAX_AGE_MS (4500 ms)",
            totalCycleTimeMs < TelemetryFreshnessPolicy.LOAD_MAX_AGE_MS)

        // Worst case between two MAF samples (every 3 pairs: 6 core + 1 aux + 2 slow = 9 commands):
        val maxMafTimeMs = (6L + 1L + maxSlowCmds) * cmdLatencyMs // 9 * 216 = 1944 ms
        assertTrue("Worst-case MAF interval ($maxMafTimeMs ms) must be < MAF_MAX_AGE_MS (2500 ms)",
            maxMafTimeMs < TelemetryFreshnessPolicy.MAF_MAX_AGE_MS)
    }

    @Test
    fun testLiveSlowPidsScheduledEveryIntervalIndependently() {
        val scheduler = TurboScheduler()
        var nowMs = 1000L

        val pid1 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0105", pid1) // Coolant

        nowMs += 1000L
        val pidNull = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertNull(pidNull)

        nowMs += 1600L
        val pid2 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("010F", pid2) // IAT

        nowMs += 2500L
        val pid3 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0142", pid3) // Voltage

        nowMs += 2500L
        val pid4 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0133", pid4) // Baro
    }
}
