package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionBaroResolverTest {

    @Test
    fun testValid0133SetsPid0133AndSurvivesTransientFailure() {
        val resolver = SessionBaroResolver()

        // 1. Initial state UNAVAILABLE
        val init = resolver.resolve()
        assertNull(init.valueMbar)
        assertEquals("UNAVAILABLE", init.source)

        // 2. Valid 0133 received
        resolver.onSample("0133", PidStatus.VALID, 990.0, monoNs = 1_000_000L)
        val valid = resolver.resolve()
        assertEquals(990.0, valid.valueMbar!!, 0.01)
        assertEquals("PID_0133", valid.source)

        // 3. Transient timeout on 0133 does NOT erase session BARO
        resolver.onSample("0133", PidStatus.TIMEOUT, null, monoNs = 2_000_000L)
        val afterTimeout = resolver.resolve()
        assertEquals(990.0, afterTimeout.valueMbar!!, 0.01)
        assertEquals("PID_0133", afterTimeout.source)

        // 4. Session reset clears cache
        resolver.reset()
        val afterReset = resolver.resolve()
        assertNull(afterReset.valueMbar)
        assertEquals("UNAVAILABLE", afterReset.source)
    }

    @Test
    fun testEngineOffMapCalibration() {
        val resolver = SessionBaroResolver()
        val now = 10_000_000_000L // 10s

        // Fresh RPM = 0 at now
        val rpmVal = 0.0
        val rpmStatus = PidStatus.VALID
        val rpmNs = now - 200_000_000L // 200ms ago

        // MAP = 990.0
        resolver.onSample(
            pid = "010B",
            status = PidStatus.VALID,
            value = 990.0,
            monoNs = now,
            latestRpmValue = rpmVal,
            latestRpmStatus = rpmStatus,
            latestRpmMonoNs = rpmNs,
            nowNs = now
        )

        val calib = resolver.resolve()
        assertEquals(990.0, calib.valueMbar!!, 0.01)
        assertEquals("ENGINE_OFF_MAP", calib.source)
    }

    @Test
    fun testStaleRpmDoesNotCalibrateMap() {
        val resolver = SessionBaroResolver()
        val now = 10_000_000_000L // 10s

        // Stale RPM = 0 from 5 seconds ago (> 1000ms threshold)
        val rpmVal = 0.0
        val rpmStatus = PidStatus.VALID
        val rpmNs = now - 5_000_000_000L

        resolver.onSample(
            pid = "010B",
            status = PidStatus.VALID,
            value = 990.0,
            monoNs = now,
            latestRpmValue = rpmVal,
            latestRpmStatus = rpmStatus,
            latestRpmMonoNs = rpmNs,
            nowNs = now
        )

        val res = resolver.resolve()
        assertNull(res.valueMbar)
        assertEquals("UNAVAILABLE", res.source)
    }

    @Test
    fun testRunningEngineDoesNotCalibrateMap() {
        val resolver = SessionBaroResolver()
        val now = 10_000_000_000L

        // RPM = 850 (Engine running idle)
        val rpmVal = 850.0
        val rpmStatus = PidStatus.VALID
        val rpmNs = now - 100_000_000L

        resolver.onSample(
            pid = "010B",
            status = PidStatus.VALID,
            value = 990.0,
            monoNs = now,
            latestRpmValue = rpmVal,
            latestRpmStatus = rpmStatus,
            latestRpmMonoNs = rpmNs,
            nowNs = now
        )

        val res = resolver.resolve()
        assertNull(res.valueMbar)
        assertEquals("UNAVAILABLE", res.source)
    }
}
