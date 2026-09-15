package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TurboSchedulerTest {

    @Test
    fun testRecordingModeAuxScheduler() {
        val scheduler = TurboScheduler()

        // Loop over 24 pairs
        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..24) {
            auxMap[i] = scheduler.nextRecordingAuxPids()
        }

        // Pairs 1..3 must have no aux PIDs
        for (i in 1..3) {
            assertTrue(auxMap[i]!!.isEmpty())
        }

        // Pair 4: MAF (0110) + Load (0104)
        val p4 = auxMap[4]!!
        assertTrue(p4.contains("0110"))
        assertTrue(p4.contains("0104"))
        assertFalse(p4.contains("010D"))

        // Pair 8: MAF (0110) + Speed (010D)
        val p8 = auxMap[8]!!
        assertTrue(p8.contains("0110"))
        assertTrue(p8.contains("010D"))
        assertFalse(p8.contains("0104"))

        // Pair 12: MAF (0110) + Load (0104)
        val p12 = auxMap[12]!!
        assertTrue(p12.contains("0110"))
        assertTrue(p12.contains("0104"))
        assertFalse(p12.contains("010D"))

        // Pair 16: MAF (0110) + Speed (010D)
        val p16 = auxMap[16]!!
        assertTrue(p16.contains("0110"))
        assertTrue(p16.contains("010D"))
        assertFalse(p16.contains("0104"))

        // None of the slow PIDs (0105, 010F, 0142, 0133) should EVER appear
        val allAuxPids = auxMap.values.flatten()
        assertFalse(allAuxPids.contains("0105"))
        assertFalse(allAuxPids.contains("010F"))
        assertFalse(allAuxPids.contains("0142"))
        assertFalse(allAuxPids.contains("0133"))
    }

    @Test
    fun testLiveSlowPidsScheduledEveryInterval() {
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
    }
}
