package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboSchedulerTest {

    @Test
    fun testPairFirstAuxCadence488Over32Pairs() {
        val scheduler = TurboScheduler()

        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..32) {
            auxMap[i] = scheduler.nextAuxPids()
        }

        // Verify 4/8/8 cadence across all 32 pairs:
        for (i in 1..32) {
            val pids = auxMap[i]!!

            // MAF (0110): strictly every 4th pair
            if (i % 4 == 0) {
                assertTrue("Pair $i must contain MAF (0110)", pids.contains("0110"))
            } else {
                assertFalse("Pair $i must NOT contain MAF (0110)", pids.contains("0110"))
            }

            // Speed (010D): strictly every 8th pair
            if (i % 8 == 0) {
                assertTrue("Pair $i must contain Speed (010D)", pids.contains("010D"))
            } else {
                assertFalse("Pair $i must NOT contain Speed (010D)", pids.contains("010D"))
            }

            // Load (0104): strictly every 8th pair offset by 4
            if (i % 8 == 4) {
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

        // Over a full 8-pair recording cycle (1..8):
        // 6 pairs: RPM + MAP (2 cmds each = 12 cmds)
        // 2 pairs: RPM + MAP + MAF + (Speed or Load) (4 cmds each = 8 cmds)
        // Total normal recording commands = 20 commands = 4320 ms.
        val recordingCycleCommands = 20L
        val recordingCycleTimeMs = recordingCycleCommands * cmdLatencyMs // 4320 ms

        // Worst case between two Speed samples (every 8 pairs):
        assertTrue("Worst-case Speed interval ($recordingCycleTimeMs ms) must be < SPEED_MAX_AGE_MS (4500 ms)",
            recordingCycleTimeMs < TelemetryFreshnessPolicy.SPEED_MAX_AGE_MS)

        // Worst case between two Load samples (every 8 pairs):
        assertTrue("Worst-case Load interval ($recordingCycleTimeMs ms) must be < LOAD_MAX_AGE_MS (4500 ms)",
            recordingCycleTimeMs < TelemetryFreshnessPolicy.LOAD_MAX_AGE_MS)

        // Worst case between two MAF samples (every 4 pairs: 3 pairs * 2 cmds + 1 pair * 4 cmds = 10 cmds):
        val maxMafTimeMs = 10L * cmdLatencyMs // 10 * 216 = 2160 ms
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
