package com.vag.vcdsandroid.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    @Test
    fun `first two attempts keep DTR clear and the third asserts it`() {
        assertFalse(KwpSlowInit.dtrAssertedForAttempt(1))
        assertFalse(KwpSlowInit.dtrAssertedForAttempt(2))
        assertTrue(KwpSlowInit.dtrAssertedForAttempt(3))
    }

    @Test
    fun `silent line without echo is reported as interface not passing K-Line`() {
        val msg = KwpSlowInit.describeFailure("WAIT_SYNC_55", null, null, null, false, false)
        assertTrue(msg, msg.contains("No K-Line echo"))
        assertTrue(msg, msg.contains("DTR=OFF"))
    }

    @Test
    fun `echo without sync points at ignition or ECU instead of the cable`() {
        val msg = KwpSlowInit.describeFailure("WAIT_SYNC_55", null, null, null, true, true)
        assertTrue(msg, msg.contains("no 0x55 sync"))
        assertTrue(msg, msg.contains("DTR=ON"))
    }

    @Test
    fun `KW1281 keywords are named explicitly`() {
        val msg = KwpSlowInit.describeFailure("KW1281_KEYWORDS", 0x55, 0x01, 0x8A, true, false)
        assertTrue(msg, msg.contains("KW1281"))
        assertTrue(msg, msg.contains("KB1=01, KB2=8A"))
    }
}
