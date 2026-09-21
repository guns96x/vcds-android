package com.vag.vcdsandroid.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUsbProbeTest {

    @Test
    fun `classifyProfile identifies Ross-Tech FA24 as Hex clone candidate without premature certainty`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x0403, 0xFA24)
        assertEquals(HardwareProfile.ROSS_TECH_HEX_FA24_FTDI, profile)
        assertTrue(hypothesis.contains("PROVEN: FTDI USB bridge with Ross-Tech PID"))
        assertTrue(hypothesis.contains("HYPOTHESIS"))
        assertTrue(hypothesis.contains("unconfirmed"))
    }

    @Test
    fun `classifyProfile identifies Ross-Tech legacy FA20`() {
        val (profile, _) = AndroidUsbProbe.classifyProfile(0x0403, 0xFA20)
        assertEquals(HardwareProfile.ROSS_TECH_HEX_FA20_FTDI, profile)
    }

    @Test
    fun `classifyProfile identifies generic FTDI 6001 as bridge with unverified protocol`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x0403, 0x6001)
        assertEquals(HardwareProfile.FTDI_FT232R_BRIDGE, profile)
        assertTrue(hypothesis.contains("FTDI FT232R USB-UART bridge"))
        assertTrue(hypothesis.contains("UNVERIFIED"))
    }

    @Test
    fun `classifyProfile identifies CH340 as bridge with unverified protocol`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x1A86, 0x7523)
        assertEquals(HardwareProfile.CH34X_BRIDGE, profile)
        assertTrue(hypothesis.contains("CH340/CH341 USB-UART bridge"))
        assertTrue(hypothesis.contains("UNVERIFIED"))
    }

    @Test
    fun `classifyProfile identifies CP210x as bridge with unverified protocol`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x10C4, 0xEA60)
        assertEquals(HardwareProfile.CP210X_BRIDGE, profile)
        assertTrue(hypothesis.contains("CP210x USB-UART bridge"))
        assertTrue(hypothesis.contains("UNVERIFIED"))
    }

    @Test
    fun `classifyProfile identifies PL2303 as bridge with unverified protocol`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x067B, 0x2303)
        assertEquals(HardwareProfile.PL2303_BRIDGE, profile)
        assertTrue(hypothesis.contains("PL2303 USB-UART bridge"))
        assertTrue(hypothesis.contains("UNVERIFIED"))
    }

    @Test
    fun `classifyProfile handles unknown devices safely`() {
        val (profile, hypothesis) = AndroidUsbProbe.classifyProfile(0x1234, 0x5678)
        assertEquals(HardwareProfile.UNKNOWN_USB_DEVICE, profile)
        assertTrue(hypothesis.contains("UNKNOWN"))
    }
}
