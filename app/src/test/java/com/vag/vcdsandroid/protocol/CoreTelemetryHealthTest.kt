package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTelemetryHealthTest {

    @Test
    fun testInitialStateHealthy() {
        val health = CoreTelemetryHealth(3)
        assertEquals(0, health.consecutiveFailures)
        assertFalse(health.isTripped)
    }

    @Test
    fun testValidPairResetsCounter() {
        val health = CoreTelemetryHealth(3)
        assertTrue(health.onPair(PidStatus.VALID, PidStatus.VALID))
        assertEquals(0, health.consecutiveFailures)
    }

    @Test
    fun testOneTimeoutDoesNotTrip() {
        val health = CoreTelemetryHealth(3)
        val healthy = health.onPair(PidStatus.TIMEOUT, PidStatus.VALID)
        assertTrue(healthy)
        assertEquals(1, health.consecutiveFailures)
        assertFalse(health.isTripped)
    }

    @Test
    fun testTwoConsecutiveFailuresDoNotTrip() {
        val health = CoreTelemetryHealth(3)
        health.onPair(PidStatus.TIMEOUT, PidStatus.VALID)
        val healthy = health.onPair(PidStatus.VALID, PidStatus.ERROR)
        assertTrue(healthy)
        assertEquals(2, health.consecutiveFailures)
        assertFalse(health.isTripped)
    }

    @Test
    fun testThreeConsecutiveHardFailuresTrips() {
        val health = CoreTelemetryHealth(3)
        health.onPair(PidStatus.TIMEOUT, PidStatus.VALID)
        health.onPair(PidStatus.VALID, PidStatus.ERROR)
        val healthy = health.onPair(PidStatus.TIMEOUT, PidStatus.TIMEOUT)
        assertFalse(healthy)
        assertEquals(3, health.consecutiveFailures)
        assertTrue(health.isTripped)
    }

    @Test
    fun testRecoveryBeforeTripResetsCounter() {
        val health = CoreTelemetryHealth(3)
        health.onPair(PidStatus.TIMEOUT, PidStatus.VALID)
        health.onPair(PidStatus.VALID, PidStatus.ERROR)
        assertEquals(2, health.consecutiveFailures)

        // Valid pair recovers
        val healthy = health.onPair(PidStatus.VALID, PidStatus.VALID)
        assertTrue(healthy)
        assertEquals(0, health.consecutiveFailures)
        assertFalse(health.isTripped)
    }

    @Test
    fun testResetClearsFailures() {
        val health = CoreTelemetryHealth(3)
        health.onPair(PidStatus.TIMEOUT, PidStatus.TIMEOUT)
        health.onPair(PidStatus.TIMEOUT, PidStatus.TIMEOUT)
        health.onPair(PidStatus.TIMEOUT, PidStatus.TIMEOUT)
        assertTrue(health.isTripped)

        health.reset()
        assertEquals(0, health.consecutiveFailures)
        assertFalse(health.isTripped)
    }

    @Test
    fun testThreeConsecutiveNoDataTrips() {
        val health = CoreTelemetryHealth(3)
        health.onPair(PidStatus.NO_DATA, PidStatus.VALID)
        health.onPair(PidStatus.VALID, PidStatus.NO_DATA)
        val healthy = health.onPair(PidStatus.NO_DATA, PidStatus.NO_DATA)
        assertFalse(healthy)
        assertEquals(3, health.consecutiveFailures)
        assertTrue(health.isTripped)
    }
}
