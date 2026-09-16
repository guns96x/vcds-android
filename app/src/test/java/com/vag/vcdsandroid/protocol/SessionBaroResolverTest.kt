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

    @Test
    fun testPhoneBarometerPriorityHierarchy() {
        val resolver = SessionBaroResolver()
        val t0 = 10_000_000_000L

        // 1. PHONE_BAROMETER alone resolves
        resolver.onPhoneBaro(995.0, monoNs = t0, fresh = true)
        var res = resolver.resolve(nowNs = t0)
        assertEquals(995.0, res.valueMbar!!, 0.01)
        assertEquals("PHONE_BAROMETER", res.source)

        // 2. ENGINE_OFF_MAP replaces PHONE_BAROMETER
        resolver.onSample(
            pid = "010B",
            status = PidStatus.VALID,
            value = 992.0,
            monoNs = t0 + 1_000_000_000L,
            latestRpmValue = 0.0,
            latestRpmStatus = PidStatus.VALID,
            latestRpmMonoNs = t0 + 1_000_000_000L,
            nowNs = t0 + 1_000_000_000L
        )
        res = resolver.resolve(nowNs = t0 + 1_000_000_000L)
        assertEquals(992.0, res.valueMbar!!, 0.01)
        assertEquals("ENGINE_OFF_MAP", res.source)

        // 3. PID_0133 replaces both ENGINE_OFF_MAP and PHONE_BAROMETER
        resolver.onSample(
            pid = "0133",
            status = PidStatus.VALID,
            value = 1001.0,
            monoNs = t0 + 2_000_000_000L
        )
        res = resolver.resolve(nowNs = t0 + 2_000_000_000L)
        assertEquals(1001.0, res.valueMbar!!, 0.01)
        assertEquals("PID_0133", res.source)

        // 4. Stale phone reading does not erase PID_0133 or ENGINE_OFF_MAP
        resolver.onPhoneBaro(null, monoNs = t0 + 3_000_000_000L, fresh = false)
        res = resolver.resolve(nowNs = t0 + 3_000_000_000L)
        assertEquals(1001.0, res.valueMbar!!, 0.01)
        assertEquals("PID_0133", res.source)
    }

    @Test
    fun testStalePhoneBarometerBecomesUnavailableWhenOnlySource() {
        val resolver = SessionBaroResolver()
        val t0 = 10_000_000_000L

        // Valid phone baro at t0
        resolver.onPhoneBaro(995.0, monoNs = t0, fresh = true)
        var res = resolver.resolve(nowNs = t0)
        assertEquals(995.0, res.valueMbar!!, 0.01)
        assertEquals("PHONE_BAROMETER", res.source)

        // After 6 seconds (> 5s freshness limit)
        res = resolver.resolve(nowNs = t0 + 6_000_000_000L)
        assertNull(res.valueMbar)
        assertEquals("UNAVAILABLE", res.source)
    }

    @Test
    fun testPhoneBaroWithoutTimestampReturnsUnavailable() {
        val resolver = SessionBaroResolver()
        val t0 = 10_000_000_000L

        // Valid phone baro added
        resolver.onPhoneBaro(995.0, monoNs = t0, fresh = true)

        // Resolving with valid timestamp resolves PHONE_BAROMETER
        val withTime = resolver.resolve(nowNs = t0)
        assertEquals(995.0, withTime.valueMbar!!, 0.01)
        assertEquals("PHONE_BAROMETER", withTime.source)

        // Resolving without timestamp (nowNs = 0L) must reject phone baro as unverified freshness
        val withoutTime = resolver.resolve()
        assertNull(withoutTime.valueMbar)
        assertEquals("UNAVAILABLE", withoutTime.source)
    }
    @Test
    fun testOldSampleIsNotArtificiallyExtendedAcrossReconnect() {
        val resolver = SessionBaroResolver()
        val t0 = 10_000_000_000L // 10s

        // Sample taken at t0
        resolver.onPhoneBaro(1004.0, monoNs = t0, fresh = true)

        // At t0 + 4.9s, it is still valid
        val r1 = resolver.resolve(nowNs = t0 + 4_900_000_000L)
        assertEquals(1004.0, r1.valueMbar!!, 0.01)

        // Re-read at t0 + 4.9s carrying the original monoNs = t0
        resolver.onPhoneBaro(1004.0, monoNs = t0, fresh = true)

        // At t0 + 5.1s, it MUST expire to UNAVAILABLE (not extended by 5 more seconds)
        val r2 = resolver.resolve(nowNs = t0 + 5_100_000_000L)
        assertNull(r2.valueMbar)
        assertEquals("UNAVAILABLE", r2.source)
    }
}