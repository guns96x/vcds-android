package com.vag.vcdsandroid.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BarometerMedianFilterTest {

    @Test
    fun testNoSampleReturnsUnavailable() {
        val filter = BarometerMedianFilter()
        val reading = filter.getMedianReading(nowNs = 1_000_000_000L)
        assertFalse(reading.available)
        assertFalse(reading.fresh)
        assertNull(reading.valueMbar)
    }

    @Test
    fun testRejectsOutOfPlausibleBounds() {
        val filter = BarometerMedianFilter()
        val t0 = 1_000_000_000L

        // Below 800.0 mbar
        filter.addSample(799.9, t0)
        var reading = filter.getMedianReading(t0)
        assertFalse(reading.available)
        assertNull(reading.valueMbar)

        // Above 1100.0 mbar
        filter.addSample(1100.1, t0)
        reading = filter.getMedianReading(t0)
        assertFalse(reading.available)
        assertNull(reading.valueMbar)

        // Valid boundary 800.0 and 1100.0
        filter.addSample(800.0, t0)
        reading = filter.getMedianReading(t0)
        assertTrue(reading.available)
        assertTrue(reading.fresh)
        assertEquals(800.0, reading.valueMbar!!, 0.01)
    }

    @Test
    fun testMedianCalculationOddAndEven() {
        val filter = BarometerMedianFilter(windowSize = 5)
        var t = 1_000_000_000L

        // Odd count (3 samples): 995.0, 1005.0, 1000.0 -> sorted: 995, 1000, 1005 -> median: 1000.0
        filter.addSample(995.0, t); t += 100_000_000L
        filter.addSample(1005.0, t); t += 100_000_000L
        filter.addSample(1000.0, t); t += 100_000_000L

        var reading = filter.getMedianReading(t)
        assertTrue(reading.fresh)
        assertEquals(1000.0, reading.valueMbar!!, 0.01)

        // Even count (4 samples): add 1010.0 -> sorted: 995, 1000, 1005, 1010 -> median: (1000+1005)/2 = 1002.5
        filter.addSample(1010.0, t); t += 100_000_000L
        reading = filter.getMedianReading(t)
        assertTrue(reading.fresh)
        assertEquals(1002.5, reading.valueMbar!!, 0.01)
    }

    @Test
    fun testStaleAfter5Seconds() {
        val filter = BarometerMedianFilter()
        val t0 = 10_000_000_000L // 10s

        filter.addSample(998.0, t0)

        // Reading at 4.9s age -> fresh
        val freshReading = filter.getMedianReading(t0 + 4_900_000_000L)
        assertTrue(freshReading.fresh)
        assertEquals(998.0, freshReading.valueMbar!!, 0.01)

        // Reading at 5.1s age -> stale (fresh == false, value == null)
        val staleReading = filter.getMedianReading(t0 + 5_100_000_000L)
        assertFalse(staleReading.fresh)
        assertTrue(staleReading.available) // sensor exists, but reading is stale
        assertNull(staleReading.valueMbar)
    }

    @Test
    fun testGapResetClearsStaleWindow() {
        val filter = BarometerMedianFilter(windowSize = 5)
        var t = 1_000_000_000L

        filter.addSample(900.0, t)
        filter.addSample(900.0, t + 100_000_000L)

        // Now a 6 second pause (e.g. app in background)
        t += 6_000_000_000L
        filter.addSample(1013.0, t)

        // The old 900.0 samples should have been cleared due to the >5s gap
        val reading = filter.getMedianReading(t)
        assertTrue(reading.fresh)
        assertEquals(1013.0, reading.valueMbar!!, 0.01)
    }
}
