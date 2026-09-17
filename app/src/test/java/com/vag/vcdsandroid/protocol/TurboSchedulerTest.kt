package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TurboSchedulerTest {

    @Test
    fun testRecordingAuxCadence488Over32Pairs() {
        val scheduler = TurboScheduler()

        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..32) {
            auxMap[i] = scheduler.nextRecordingAuxPids()
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

        val allAuxPids = auxMap.values.flatten()
        assertFalse(allAuxPids.contains("0133"))
    }

    @Test
    fun testRecordingPollsTemperaturesWithoutStackingOnCoreAux() {
        val scheduler = TurboScheduler()
        val auxMap = (1..128).associateWith { scheduler.nextRecordingAuxPids() }
        for ((i, pids) in auxMap) {
            val slot = i % 8 == 2
            assertEquals("Coolant on pair %64==2 (pair $i)", i % 64 == 2, pids.contains("0105"))
            assertEquals("Voltage on pair %64==34 (pair $i)", i % 64 == 34, pids.contains("0142"))
            assertEquals("IAT on the remaining temperature slots (pair $i)", slot && i % 64 != 2 && i % 64 != 34, pids.contains("010F"))
            if (slot) assertEquals("Temperature slot never stacks onto another aux (pair $i: $pids)", 1, pids.size)
        }
        assertEquals(12, auxMap.values.flatten().count { it == "010F" })
    }

    @Test
    fun testLiveAuxCadence366Over36Pairs() {
        val scheduler = TurboScheduler()

        val auxMap = mutableMapOf<Int, List<String>>()
        for (i in 1..36) {
            auxMap[i] = scheduler.nextLiveAuxPids()
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
    }

    @Test
    fun testRecordingSchedulerDrivenSimulationUnderRealLatency() {
        val scheduler = TurboScheduler()
        // Evaluate nominal measured mean (216 ms) and p95 stress latency (260 ms)
        for (cmdLatencyMs in listOf(216L, 260L)) {
            scheduler.reset()
            var simulatedClockMs = 0L
            var lastMafMs: Long? = null
            var lastSpeedMs: Long? = null
            var lastLoadMs: Long? = null
            var lastIatMs: Long? = null
            var maxIatIntervalMs = 0L

            var maxMafIntervalMs = 0L
            var maxSpeedIntervalMs = 0L
            var maxLoadIntervalMs = 0L

            for (pair in 1..32) {
                // Core RPM + MAP: 2 commands
                simulatedClockMs += 2 * cmdLatencyMs

                val auxPids = scheduler.nextRecordingAuxPids()
                for (pid in auxPids) {
                    simulatedClockMs += cmdLatencyMs
                    when (pid) {
                        "0110" -> {
                            if (lastMafMs != null) maxMafIntervalMs = maxOf(maxMafIntervalMs, simulatedClockMs - lastMafMs)
                            lastMafMs = simulatedClockMs
                        }
                        "010D" -> {
                            if (lastSpeedMs != null) maxSpeedIntervalMs = maxOf(maxSpeedIntervalMs, simulatedClockMs - lastSpeedMs)
                            lastSpeedMs = simulatedClockMs
                        }
                        "0104" -> {
                            if (lastLoadMs != null) maxLoadIntervalMs = maxOf(maxLoadIntervalMs, simulatedClockMs - lastLoadMs)
                            lastLoadMs = simulatedClockMs
                        }
                        "010F" -> {
                            if (lastIatMs != null) maxIatIntervalMs = maxOf(maxIatIntervalMs, simulatedClockMs - lastIatMs)
                            lastIatMs = simulatedClockMs
                        }
                    }
                }
            }

            if (cmdLatencyMs == 216L) {
                // Under nominal measured latency, intervals must be strictly below declared thresholds
                assertTrue("Recording MAF max interval ($maxMafIntervalMs ms) must be < MAF_MAX_AGE_MS (2500 ms)",
                    maxMafIntervalMs < TelemetryFreshnessPolicy.MAF_MAX_AGE_MS)
                assertTrue("Recording Speed max interval ($maxSpeedIntervalMs ms) must be < SPEED_MAX_AGE_MS",
                    maxSpeedIntervalMs < TelemetryFreshnessPolicy.SPEED_MAX_AGE_MS)
                assertTrue("Recording Load max interval ($maxLoadIntervalMs ms) must be < LOAD_MAX_AGE_MS",
                    maxLoadIntervalMs < TelemetryFreshnessPolicy.LOAD_MAX_AGE_MS)
                assertTrue("Recording IAT max interval ($maxIatIntervalMs ms) must be < SLOW_MAX_AGE_MS",
                    maxIatIntervalMs < TelemetryFreshnessPolicy.SLOW_MAX_AGE_MS)
            } else if (cmdLatencyMs == 260L) {
                // Under sustained p95 stress latency (260 ms/command), 8 pairs equals 21 bus commands = 5460 ms
                // (one temperature slot). Confirms the budget and why nominal <=216 ms is required.
                assertEquals(2860L, maxMafIntervalMs)
                assertEquals(5460L, maxSpeedIntervalMs)
                assertEquals(5460L, maxLoadIntervalMs)
            }
        }
    }

    @Test
    fun testLiveSchedulerDrivenSimulationWithSlowPidsUnderRealLatency() {
        val scheduler = TurboScheduler()
        val cmdLatencyMs = 216L

        // Test with 1 normal slow command and 2 commands for 0142 -> ATRV fallback
        for (slowCmdCount in listOf(1, 2)) {
            scheduler.reset()
            var simulatedClockMs = 1000L
            var lastMafMs: Long? = null
            var lastSpeedMs: Long? = null
            var lastLoadMs: Long? = null
            var lastIatMs: Long? = null
            var maxIatIntervalMs = 0L

            var maxMafIntervalMs = 0L
            var maxSpeedIntervalMs = 0L
            var maxLoadIntervalMs = 0L

            for (pair in 1..36) {
                // Core RPM + MAP: 2 commands
                simulatedClockMs += 2 * cmdLatencyMs

                val auxPids = scheduler.nextLiveAuxPids()
                for (pid in auxPids) {
                    simulatedClockMs += cmdLatencyMs
                    when (pid) {
                        "0110" -> {
                            if (lastMafMs != null) maxMafIntervalMs = maxOf(maxMafIntervalMs, simulatedClockMs - lastMafMs)
                            lastMafMs = simulatedClockMs
                        }
                        "010D" -> {
                            if (lastSpeedMs != null) maxSpeedIntervalMs = maxOf(maxSpeedIntervalMs, simulatedClockMs - lastSpeedMs)
                            lastSpeedMs = simulatedClockMs
                        }
                        "0104" -> {
                            if (lastLoadMs != null) maxLoadIntervalMs = maxOf(maxLoadIntervalMs, simulatedClockMs - lastLoadMs)
                            lastLoadMs = simulatedClockMs
                        }
                        "010F" -> {
                            if (lastIatMs != null) maxIatIntervalMs = maxOf(maxIatIntervalMs, simulatedClockMs - lastIatMs)
                            lastIatMs = simulatedClockMs
                        }
                    }
                }

                // In LIVE mode, slow PIDs are scheduled only on clean cycles (auxPids.isEmpty())
                if (auxPids.isEmpty()) {
                    val slowPid = scheduler.checkLiveSlowPid(simulatedClockMs, 2500L)
                    if (slowPid != null) {
                        simulatedClockMs += slowCmdCount * cmdLatencyMs
                    }
                }
            }

            assertTrue("Live MAF max interval ($maxMafIntervalMs ms) with $slowCmdCount slow cmds must be < MAF_MAX_AGE_MS (2500 ms)",
                maxMafIntervalMs < TelemetryFreshnessPolicy.MAF_MAX_AGE_MS)
            assertTrue("Live Speed max interval ($maxSpeedIntervalMs ms) with $slowCmdCount slow cmds must be < SPEED_MAX_AGE_MS",
                maxSpeedIntervalMs < TelemetryFreshnessPolicy.SPEED_MAX_AGE_MS)
            assertTrue("Live Load max interval ($maxLoadIntervalMs ms) with $slowCmdCount slow cmds must be < LOAD_MAX_AGE_MS (4500 ms)",
                maxLoadIntervalMs < TelemetryFreshnessPolicy.LOAD_MAX_AGE_MS)
        }
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

    @Test
    fun testRealCarLogCadenceAndFreshnessReplay() {
        val candidates = listOf(
            File("test_logs/20260916/Turbo_Pair_20260916_102349.csv"),
            File("../test_logs/20260916/Turbo_Pair_20260916_102349.csv")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertNotNull("Real car log file must exist for replay verification", file)

        val pairs = com.vag.vcdsandroid.analysis.LogQualityAnalyzer.parseCsv(file!!)
        assertEquals(58, pairs.size)
        assertTrue("All 58 pairs must be valid in real car log", pairs.all { it.pairValid })

        for (p in pairs) {
            assertTrue("Pair dt ${p.dtMapRpmMs} ms must be in [195..260] ms", p.dtMapRpmMs in 195L..260L)
            val mafAge = p.mafAgeMs
            if (mafAge != null) {
                assertTrue("MAF age $mafAge ms must be <= MAF_MAX_AGE_MS (2500 ms)",
                    mafAge <= TelemetryFreshnessPolicy.MAF_MAX_AGE_MS)
            }
            val speedAge = p.speedAgeMs
            if (speedAge != null) {
                assertTrue("Speed age $speedAge ms must be <= SPEED_MAX_AGE_MS (4500 ms)",
                    speedAge <= TelemetryFreshnessPolicy.SPEED_MAX_AGE_MS)
            }
            val loadAge = p.loadAgeMs
            if (loadAge != null) {
                assertTrue("Load age $loadAge ms must be <= LOAD_MAX_AGE_MS (4500 ms)",
                    loadAge <= TelemetryFreshnessPolicy.LOAD_MAX_AGE_MS)
            }
        }
    }
}
