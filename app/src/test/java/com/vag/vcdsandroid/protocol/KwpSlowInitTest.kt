package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class KwpSlowInitTest {

    @Test
    fun `address 01 generates 7O1 five baud pattern`() {
        // start=0, data LSB-first=1 0 0 0 0 0 0, odd parity=0, stop=1
        assertArrayEquals(
            booleanArrayOf(false, true, false, false, false, false, false, false, false, true),
            KwpSlowInit.addressBits7O1(0x01)
        )
    }

    @Test
    fun `address 33 gets odd parity bit`() {
        // 0x33 lower 7 bits contain four ones, so odd parity bit must be one.
        val bits = KwpSlowInit.addressBits7O1(0x33)
        assertEquals(true, bits[8])
        assertEquals(true, bits[9])
    }

    @Test
    fun `complements are byte exact`() {
        assertEquals(0xFE, KwpSlowInit.expectedAddressComplement(0x01))
        assertEquals(0x70, KwpSlowInit.byteComplement(0x8F))
    }
}
