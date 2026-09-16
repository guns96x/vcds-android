package com.vag.vcdsandroid.analysis

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class LogQualityAnalyzerTest {

    @Test
    fun testRealCarRoadPullFrom20260916() {
        val candidates = listOf(
            File("test_logs/20260916/Turbo_Pair_20260916_102349.csv"),
            File("../test_logs/20260916/Turbo_Pair_20260916_102349.csv")
        )
        val realLogFile = candidates.firstOrNull { it.exists() }
        assertNotNull("Real car log file must exist for fixture test", realLogFile)

        val report = LogQualityAnalyzer.analyze(realLogFile!!)

        // 1. Data volume & pair validity
        assertEquals("Total pairs must be 58", 58, report.totalPairs)
        assertEquals("Valid pairs must be 58", 58, report.validPairs)
        assertEquals(100.0, report.validPairPct, 0.01)

        // 2. Dynamics
        assertEquals(899.0, report.minRpm ?: 0.0, 1.0)
        assertEquals(3344.0, report.maxRpm ?: 0.0, 1.0)
        assertEquals(2410.0, report.peakMapMbar ?: 0.0, 1.0)
        assertEquals(1.405, report.peakBoostBar ?: 0.0, 0.005)

        // 3. BARO
        assertTrue("Barometer should be stable across pull", report.baroStable)
        assertEquals("PHONE_BAROMETER", report.baroSource)
        assertEquals(1004.4, report.minBaroMbar ?: 0.0, 0.5)
        assertEquals(1004.8, report.maxBaroMbar ?: 0.0, 0.5)

        // 4. Gear Ratio Verification: real car was run in 3rd gear (~24.8 km/h / 1000 RPM)
        assertEquals("Real car was run in 3rd gear", GearVerdict.DETECTED_3RD_GEAR, report.gearVerdict)
        assertNotNull("Observed ratio must be present", report.observedRatioKmhPer1000Rpm)
        assertTrue(
            "Observed ratio should be around 23.0..26.5 km/h per 1000 RPM, got ${report.observedRatioKmhPer1000Rpm}",
            report.observedRatioKmhPer1000Rpm!! in 23.0..26.5
        )

        // 5. RPM coverage & spool load
        assertFalse("Log did not reach 3900..4000 RPM (stopped at 3344)", report.hasRpmCoverage1300To4000)
        assertFalse("1500..1900 RPM did not have fresh WOT load (driver floored at ~1998 RPM)", report.hasFreshSpoolLoad)

        // 6. Overall verdict
        assertEquals(PullVerdict.INCOMPLETE_PULL_WARNING, report.overallVerdict)
        assertTrue("Checklist must warn about 3rd gear", report.actionableChecklist.any { it.contains("3-й передачі") })
        assertTrue("Checklist must warn about max RPM < 4000", report.actionableChecklist.any { it.contains("3344 RPM") || it.contains("4000 RPM") })
        assertTrue("Checklist must warn about late WOT in spool zone", report.actionableChecklist.any { it.contains("1500–1900") })

        val readable = report.formatHumanReadable()
        assertTrue(readable.contains("3-ю передачу"))
        assertTrue(readable.contains("1.405 бар"))
    }

    @Test
    fun testSyntheticPerfect4thGearPull() {
        val rows = mutableListOf<TurboPairRecord>()
        val startUtc = 1700000000000L
        val startNs = 1000000000000L

        // Generate synthetic pull in 4th gear: ~35.4 km/h per 1000 RPM
        // RPM rises from 1200 to 4100 RPM
        val rpmSteps = (1200..4100 step 100).toList()
        for ((idx, rpmVal) in rpmSteps.withIndex()) {
            val speedVal = (rpmVal * 0.0354) // ~35.4 km/h per 1000 RPM
            val boostVal = if (rpmVal >= 1900) 1.35 else 0.85
            val mapVal = 1000.0 + boostVal * 1000.0
            val loadVal = 99.0

            rows.add(
                TurboPairRecord(
                    pairSeq = (idx + 1).toLong(),
                    timestampUtcMs = startUtc + idx * 220L,
                    monoNs = startNs + idx * 220000000L,
                    rpm = rpmVal.toDouble(),
                    mapMbarAbs = mapVal,
                    baroMbar = 1000.0,
                    baroSource = "PHONE_BAROMETER",
                    boostMbar = boostVal * 1000.0,
                    boostBar = boostVal,
                    dtMapRpmMs = 215L,
                    pairValid = true,
                    invalidReason = "",
                    mafGs = 85.0,
                    mafAgeMs = 300L,
                    speedKmh = speedVal,
                    speedAgeMs = 400L,
                    loadPct = loadVal,
                    loadAgeMs = 350L,
                    coolantC = 85.0,
                    coolantAgeMs = 1200L,
                    iatC = 25.0,
                    iatAgeMs = 1200L,
                    voltageV = 14.1,
                    voltageAgeMs = 1200L,
                    latencyMs = 430L
                )
            )
        }

        val report = LogQualityAnalyzer.analyzePairs(rows)
        assertEquals(PullVerdict.PASS_ACCEPTANCE_PULL, report.overallVerdict)
        assertEquals(GearVerdict.VERIFIED_4TH_GEAR, report.gearVerdict)
        assertTrue(report.hasRpmCoverage1300To4000)
        assertTrue(report.hasFreshSpoolLoad)
        assertTrue(report.actionableChecklist.isEmpty())
        assertTrue(report.formatHumanReadable().contains("ACCEPTANCE PASS"))
    }

    @Test
    fun testRealCarRoadPull4thGearFrom20260916_111930() {
        val candidates = listOf(
            File("test_logs/20260916/Turbo_Pair_20260916_111930.csv"),
            File("../test_logs/20260916/Turbo_Pair_20260916_111930.csv")
        )
        val realLogFile = candidates.firstOrNull { it.exists() }
        assertNotNull("Real car 4th gear log file must exist for fixture test", realLogFile)

        val report = LogQualityAnalyzer.analyze(realLogFile!!)

        // 1. Data volume & pair validity
        assertEquals("Total pairs must be 48", 48, report.totalPairs)
        assertEquals("Valid pairs must be 48", 48, report.validPairs)
        assertEquals(100.0, report.validPairPct, 0.01)

        // 2. Dynamics
        assertEquals(1198.0, report.minRpm ?: 0.0, 1.0)
        assertEquals(4084.0, report.maxRpm ?: 0.0, 1.0)
        assertEquals(2330.0, report.peakMapMbar ?: 0.0, 1.0)
        assertEquals(1.325, report.peakBoostBar ?: 0.0, 0.005)

        // 3. BARO
        assertTrue("Barometer should be stable across pull", report.baroStable)
        assertEquals("PHONE_BAROMETER", report.baroSource)

        // 4. Gear Ratio Verification: 4th gear (~35.2 km/h / 1000 RPM)
        assertEquals("Real car was verified in 4th gear", GearVerdict.VERIFIED_4TH_GEAR, report.gearVerdict)
        assertNotNull("Observed ratio must be present", report.observedRatioKmhPer1000Rpm)
        assertTrue(
            "Observed ratio should be around 33.0..37.5 km/h per 1000 RPM, got ${report.observedRatioKmhPer1000Rpm}",
            report.observedRatioKmhPer1000Rpm!! in 33.0..37.5
        )

        // 5. RPM coverage & spool load
        assertTrue("Log reached 4084 RPM from 1198 RPM", report.hasRpmCoverage1300To4000)
        assertTrue("Spool zone has fresh WOT load", report.hasFreshSpoolLoad)

        // 6. Overall verdict
        assertEquals(PullVerdict.PASS_ACCEPTANCE_PULL, report.overallVerdict)
        assertTrue("Actionable checklist should be empty on pass", report.actionableChecklist.isEmpty())

        val readable = report.formatHumanReadable()
        assertTrue(readable.contains("ACCEPTANCE PASS"))
        assertTrue(readable.contains("4-а передача підтверджена"))
        assertTrue(readable.contains("1.325 бар"))
    }

    @Test
    fun testEmptyLogHandling() {
        val report = LogQualityAnalyzer.analyzePairs(emptyList())
        assertEquals(PullVerdict.POOR_DATA_QUALITY_ERROR, report.overallVerdict)
        assertEquals(0, report.totalPairs)
        assertEquals(0, report.validPairs)
    }
}
