package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightEvaluatorTest {

    private val validBaro = BaroReading(1005.0, "PID_0133")
    private val phoneBaro = BaroReading(998.0, "PHONE_BAROMETER")
    private val missingBaro = BaroReading(null, "UNAVAILABLE")

    @Test
    fun testAllChannelsValidReturnsGreen() {
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = validBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true,
            voltSource = "ATRV"
        )
        assertEquals(PreflightVerdict.GREEN, report.verdict)
        assertTrue(report.missingAuxChannels.isEmpty())
        assertEquals("", report.failureReason)
    }

    @Test
    fun testPhoneBarometerFreshSatisfiesBaroRequirement() {
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = phoneBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true,
            voltSource = "0142"
        )
        assertEquals(PreflightVerdict.GREEN, report.verdict)
        assertEquals(998.0, report.baroValueMbar!!, 0.01)
        assertEquals("PHONE_BAROMETER", report.baroSource)
    }

    @Test
    fun testCoreValidButAuxMissingReturnsAmber() {
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = validBaro,
            mafOk = false, // missing or stale
            speedOk = true,
            loadOk = false, // missing or stale
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.AMBER, report.verdict)
        assertEquals(listOf("MAF", "LOAD"), report.missingAuxChannels)
        assertTrue(report.failureReason.contains("AUX MISSING: MAF, LOAD"))
    }

    @Test
    fun testStaleMafDoesNotYieldGreen() {
        // When MAF is evaluated as stale (mafOk = false), preflight MUST NOT be GREEN
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = validBaro,
            mafOk = false,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.AMBER, report.verdict)
        assertTrue(report.missingAuxChannels.contains("MAF"))
    }

    @Test
    fun testCoreMissingReturnsRed() {
        // Missing RPM
        val report1 = PreflightEvaluator.evaluate(
            rpmOk = false,
            mapOk = true,
            baroReading = validBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.RED, report1.verdict)
        assertEquals("RPM TIMEOUT/FAIL", report1.failureReason)

        // Missing MAP
        val report2 = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = false,
            baroReading = validBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.RED, report2.verdict)
        assertEquals("MAP TIMEOUT/FAIL", report2.failureReason)

        // Missing BARO
        val report3 = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = missingBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.RED, report3.verdict)
        assertTrue(report3.failureReason.contains("NO BARO BASELINE"))
    }

    @Test
    fun testOptionalSensorsMissingStillGreenIfCoreAndAuxValid() {
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = validBaro,
            mafOk = true,
            speedOk = true,
            loadOk = true,
            coolantOk = false, // optional missing
            iatOk = false,     // optional missing
            voltOk = false     // optional missing
        )
        assertEquals(PreflightVerdict.GREEN, report.verdict)
        assertFalse(report.coolantOk)
        assertFalse(report.iatOk)
        assertFalse(report.voltOk)
    }
    @Test
    fun testTelemetryFreshnessPolicyThresholds() {
        assertEquals(1000L, TelemetryFreshnessPolicy.getMaxAgeMs("010C"))
        assertEquals(1000L, TelemetryFreshnessPolicy.getMaxAgeMs("010B"))
        assertEquals(2500L, TelemetryFreshnessPolicy.getMaxAgeMs("0110"))
        assertEquals(5000L, TelemetryFreshnessPolicy.getMaxAgeMs("010D"))
        assertEquals(5000L, TelemetryFreshnessPolicy.getMaxAgeMs("0104"))
        assertEquals(12000L, TelemetryFreshnessPolicy.getMaxAgeMs("0105"))
        assertEquals(12000L, TelemetryFreshnessPolicy.getMaxAgeMs("010F"))
        assertEquals(12000L, TelemetryFreshnessPolicy.getMaxAgeMs("0142"))
        assertEquals(12000L, TelemetryFreshnessPolicy.getMaxAgeMs("0133"))
    }
}