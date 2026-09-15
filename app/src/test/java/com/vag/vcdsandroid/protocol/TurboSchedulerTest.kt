package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboSchedulerTest {

    @Test
    fun testPairFirstAuxCadenceOver36Pairs() {
        val scheduler = TurboScheduler()

        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..36) {
            auxMap[i] = scheduler.nextAuxPids()
        }

        // Verify across all 36 pairs:
        for (i in 1..36) {
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
    fun testLiveSlowPidsScheduledEveryIntervalIndependently() {
        val scheduler = TurboScheduler()
        var nowMs = 1000L

        // Initial check triggers first slow PID immediately
        val pid1 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0105", pid1) // Coolant

        // 1000ms later (not yet 2500ms elapsed) -> null
        nowMs += 1000L
        val pidNull = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertNull(pidNull)

        // 1600ms later (total 2600ms elapsed) -> next slow PID
        nowMs += 1600L
        val pid2 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("010F", pid2) // IAT

        nowMs += 2500L
        val pid3 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0142", pid3) // Voltage

        nowMs += 2500L
        val pid4 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0133", pid4) // Baro

        // Rotates back to 0105
        nowMs += 2500L
        val pid5 = scheduler.checkLiveSlowPid(nowMs, 2500L)
        assertEquals("0105", pid5)
    }
}
