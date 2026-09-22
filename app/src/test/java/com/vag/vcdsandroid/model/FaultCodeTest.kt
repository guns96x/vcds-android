package com.vag.vcdsandroid.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaultCodeTest {

    @Test
    fun `parses VAG 16683 as P0299 underboost`() {
        // 16683 = 0x412B -> high=0x41, low=0x2B
        // Status 0x20 -> not intermittent, MIL not active
        val code = FaultCode.parseFromBytes(0x41, 0x2B, 0x20, emptyMap())
        assertEquals("16683", code.vagCode)
        assertEquals("P0299", code.saeCode)
        assertTrue(code.descriptionEn.contains("Underboost"))
        assertFalse(code.isMilActive)
        assertFalse(code.isIntermittent)
    }

    @Test
    fun `parses VAG 16384 as P0000 base`() {
        // 16384 = 0x4000 -> high=0x40, low=0x00
        val code = FaultCode.parseFromBytes(0x40, 0x00, 0x80, emptyMap())
        assertEquals("16384", code.vagCode)
        assertEquals("P0000", code.saeCode)
        assertTrue(code.isMilActive)
    }

    @Test
    fun `parses VAG 17964 as P1580`() {
        // 17964 - 16384 = 1580 -> P1580
        val high = (17964 shr 8) and 0xFF
        val low = 17964 and 0xFF
        val code = FaultCode.parseFromBytes(high, low, 0x60, emptyMap())
        assertEquals("17964", code.vagCode)
        assertEquals("P1580", code.saeCode)
        assertTrue(code.isIntermittent)
    }
}
