package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreflightEvaluatorTest {

    private val validBaro = BaroReading(1005.0, "PID_0133")
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
    fun testCoreValidButAuxMissingReturnsAmber() {
        val report = PreflightEvaluator.evaluate(
            rpmOk = true,
            mapOk = true,
            baroReading = validBaro,
            mafOk = false, // missing
            speedOk = true,
            loadOk = false, // missing
            coolantOk = true,
            iatOk = true,
            voltOk = true
        )
        assertEquals(PreflightVerdict.AMBER, report.verdict)
        assertEquals(listOf("MAF", "LOAD"), report.missingAuxChannels)
        assertTrue(report.failureReason.contains("AUX MISSING: MAF, LOAD"))
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
            coolantOk = false, // optional
            iatOk = false,     // optional
            voltOk = false     // optional
        )
        assertEquals(PreflightVerdict.GREEN, report.verdict)
    }
}
