package com.vag.vcdsandroid.registry

import com.vag.vcdsandroid.adapters.HexB03Adapter
import com.vag.vcdsandroid.adapters.HexLegacyAdapter
import com.vag.vcdsandroid.adapters.HexV2Adapter
import com.vag.vcdsandroid.adapters.KklAdapter
import com.vag.vcdsandroid.hardware.ConnectionParameters
import com.vag.vcdsandroid.hardware.HardwareDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdapterRegistryTest {

    private class FakeHardwareDriver(
        override val name: String = "TestDriver",
        override var isConnected: Boolean = true
    ) : HardwareDriver {
        var lastOpenedParams: ConnectionParameters? = null
        var lastBaudRate: Int = 0
        var dtrState: Boolean = false
        var rtsState: Boolean = false
        var purged: Boolean = false
        var writeBuffer: ByteArray = byteArrayOf()
        var readQueue: ByteArray = byteArrayOf()

        override suspend fun open(parameters: ConnectionParameters): Result<Unit> {
            lastOpenedParams = parameters
            isConnected = true
            return Result.success(Unit)
        }

        override suspend fun close() {
            isConnected = false
        }

        override suspend fun setBaudRate(baudRate: Int): Boolean {
            lastBaudRate = baudRate
            return true
        }

        override suspend fun setDtr(dtr: Boolean): Boolean {
            dtrState = dtr
            return true
        }

        override suspend fun setRts(rts: Boolean): Boolean {
            rtsState = rts
            return true
        }

        override suspend fun write(data: ByteArray): Int {
            writeBuffer = data.copyOf()
            return data.size
        }

        override suspend fun read(buffer: ByteArray, timeoutMs: Long): Int {
            if (readQueue.isEmpty()) return 0
            val count = minOf(buffer.size, readQueue.size)
            System.arraycopy(readQueue, 0, buffer, 0, count)
            readQueue = readQueue.copyOfRange(count, readQueue.size)
            return count
        }

        override suspend fun purge() {
            purged = true
        }
    }

    @Test
    fun `selectAdapter binds Ross-Tech FA24 to HexB03Adapter`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x0403, 0xFA24)
        assertTrue("Expected HexB03Adapter but got ${adapter::class.java.simpleName}", adapter is HexB03Adapter)
        assertEquals("RT000001", adapter.identity.serialNumber)
    }

    @Test
    fun `selectAdapter binds Ross-Tech FA20 to HexLegacyAdapter`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x0403, 0xFA20)
        assertTrue("Expected HexLegacyAdapter but got ${adapter::class.java.simpleName}", adapter is HexLegacyAdapter)
    }

    @Test
    fun `selectAdapter binds Ross-Tech FA30 to HexV2Adapter`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x0403, 0xFA30)
        assertTrue("Expected HexV2Adapter but got ${adapter::class.java.simpleName}", adapter is HexV2Adapter)
    }

    @Test
    fun `selectAdapter binds standard FTDI 6001 to KklAdapter with 10400 baud`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x0403, 0x6001)
        assertTrue("Expected KklAdapter but got ${adapter::class.java.simpleName}", adapter is KklAdapter)
        assertEquals(10400, (adapter as KklAdapter).baudRate)
    }

    @Test
    fun `selectAdapter binds CH340 to KklAdapter`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x1A86, 0x7523)
        assertTrue("Expected KklAdapter but got ${adapter::class.java.simpleName}", adapter is KklAdapter)
    }

    @Test
    fun `selectAdapter safely falls back to KklAdapter on unknown FTDI device`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, 0x0403, 0x9999)
        assertTrue("Expected KklAdapter fallback but got ${adapter::class.java.simpleName}", adapter is KklAdapter)
    }

    @Test
    fun `selectAdapter safely falls back when VID or PID is null`() {
        val driver = FakeHardwareDriver()
        val adapter = AdapterRegistry.selectAdapter(driver, null, null)
        assertTrue("Expected KklAdapter fallback but got ${adapter::class.java.simpleName}", adapter is KklAdapter)
    }
}
