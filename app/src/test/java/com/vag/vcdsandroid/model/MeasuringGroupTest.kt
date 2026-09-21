package com.vag.vcdsandroid.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class MeasuringGroupTest {

    private fun response(group: Int, vararg cells: Triple<Int, Int, Int>): ByteArray {
        require(cells.size == 4)
        val out = ByteArray(14)
        out[0] = 0x61
        out[1] = group.toByte()
        cells.forEachIndexed { index, (type, a, b) ->
            val off = 2 + index * 3
            out[off] = type.toByte()
            out[off + 1] = a.toByte()
            out[off + 2] = b.toByte()
        }
        return out
    }

    @Test
    fun `group 011 uses protocol scalers for rpm pressure and duty`() {
        val data = response(
            11,
            Triple(0x01, 20, 200),   // 800 rpm
            Triple(0x12, 250, 200),  // 2000 mbar
            Triple(0x12, 250, 210),  // 2100 mbar
            Triple(0x17, 200, 128)   // 100 %
        )
        val group = assertNotNull(MeasuringGroup.decode(data)) as MeasuringGroup
        assertEquals(800.0, group.values[0].rawValue, 0.001)
        assertEquals("RPM", group.values[0].unit)
        assertEquals(2000.0, group.values[1].rawValue, 0.001)
        assertEquals("mbar", group.values[1].unit)
        assertEquals(2100.0, group.values[2].rawValue, 0.001)
        assertEquals(100.0, group.values[3].rawValue, 0.001)
        assertEquals("%", group.values[3].unit)
    }

    @Test
    fun `torque scaler 0x5E is decoded in Nm`() {
        val data = response(
            8,
            Triple(0x01, 50, 200),
            Triple(0x5E, 200, 75),
            Triple(0x5E, 180, 75),
            Triple(0x5E, 160, 75)
        )
        val group = assertNotNull(MeasuringGroup.decode(data)) as MeasuringGroup
        assertEquals(100.0, group.values[1].rawValue, 0.001)
        assertEquals("Nm", group.values[1].unit)
        assertEquals(90.0, group.values[2].rawValue, 0.001)
        assertEquals(80.0, group.values[3].rawValue, 0.001)
    }

    @Test
    fun `temperature scaler 0x1A is b minus a`() {
        val data = response(
            7,
            Triple(0x1A, 100, 140),
            Triple(0x1A, 90, 130),
            Triple(0x1A, 80, 120),
            Triple(0x1A, 70, 110)
        )
        val group = assertNotNull(MeasuringGroup.decode(data)) as MeasuringGroup
        group.values.forEach {
            assertEquals(40.0, it.rawValue, 0.001)
            assertEquals("°C", it.unit)
        }
    }

    @Test
    fun `unknown scaler is exposed as raw instead of guessed engineering value`() {
        val data = response(
            99,
            Triple(0xFE, 0x12, 0x34),
            Triple(0xFE, 0, 1),
            Triple(0xFE, 0, 2),
            Triple(0xFE, 0, 3)
        )
        val group = assertNotNull(MeasuringGroup.decode(data)) as MeasuringGroup
        assertEquals(0x1234.toDouble(), group.values[0].rawValue, 0.001)
        assertEquals("raw", group.values[0].unit)
    }
}
