package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PidDecoderTest {

    @Test
    fun testRpmDecoding() {
        val sampleZero = PidDecoder.decodeRpm("410C0000", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sampleZero.status)
        assertEquals(0.0, sampleZero.value!!, 0.01)

        val sample4000 = PidDecoder.decodeRpm("41 0C 3E 80", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample4000.status)
        assertEquals(4000.0, sample4000.value!!, 0.01)
    }

    @Test
    fun testMapDecoding() {
        val sample990 = PidDecoder.decodeMap("410B63", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample990.status)
        assertEquals(990.0, sample990.value!!, 0.01)

        val sample2430 = PidDecoder.decodeMap("41 0B F3", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample2430.status)
        assertEquals(2430.0, sample2430.value!!, 0.01)
    }

    @Test
    fun testMafDecoding() {
        val sample03 = PidDecoder.decodeMaf("410010001E", 0L, 100L, 50L, false)
        // clean string contains 4110... let's test exact 4110001E
        val sample03Exact = PidDecoder.decodeMaf("4110001E", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample03Exact.status)
        assertEquals(0.30, sample03Exact.value!!, 0.01)

        val sample88 = PidDecoder.decodeMaf("41 10 22 AB", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample88.status)
        assertEquals(88.75, sample88.value!!, 0.01)
    }

    @Test
    fun testLoadDecoding() {
        val sampleZero = PidDecoder.decodeLoad("410400", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sampleZero.status)
        assertEquals(0.0, sampleZero.value!!, 0.01)
    }

    @Test
    fun testSpeedDecoding() {
        val sample87 = PidDecoder.decodeSpeed("410D57", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, sample87.status)
        assertEquals(87.0, sample87.value!!, 0.01)
    }

    @Test
    fun testNoData() {
        val noData = PidDecoder.decodeRpm("NO DATA", 0L, 100L, 50L, false)
        assertEquals(PidStatus.NO_DATA, noData.status)
    }

    @Test
    fun testTimeoutEvenWithPayload() {
        val timedOut = PidDecoder.decodeRpm("410C3E80", 0L, 100L, 50L, true)
        assertEquals(PidStatus.TIMEOUT, timedOut.status)
    }

    @Test
    fun testVoltageAtrv() {
        val volt = PidDecoder.decodeVoltage("14.2V", 0L, 100L, 50L, false)
        assertEquals(PidStatus.VALID, volt.status)
        assertEquals(14.2, volt.value!!, 0.01)
    }
}
