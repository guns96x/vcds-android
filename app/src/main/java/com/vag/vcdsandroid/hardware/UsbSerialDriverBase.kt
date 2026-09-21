package com.vag.vcdsandroid.hardware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Base implementation of [HardwareDriver] over Android USB Host via UsbSerialPort.
 */
open class UsbSerialDriverBase(
    val usbManager: UsbManager,
    val usbDevice: UsbDevice,
    val port: UsbSerialPort,
    override val name: String = "UsbSerialDriver"
) : HardwareDriver {

    protected var connection: UsbDeviceConnection? = null
    protected var isOpen: Boolean = false

    override val isConnected: Boolean
        get() = isOpen && connection != null

    override suspend fun open(parameters: ConnectionParameters): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!usbManager.hasPermission(usbDevice)) {
                return@withContext Result.failure(SecurityException("Missing USB permission for ${usbDevice.deviceName}"))
            }

            val conn = usbManager.openDevice(usbDevice)
                ?: return@withContext Result.failure(IOException("usbManager.openDevice returned null"))
            connection = conn

            port.open(conn)
            port.setParameters(
                parameters.baudRate,
                parameters.dataBits,
                parameters.stopBits,
                parameters.parity
            )
            try { port.dtr = parameters.dtr } catch (_: Exception) {}
            try { port.rts = parameters.rts } catch (_: Exception) {}

            isOpen = true
            Result.success(Unit)
        } catch (e: Exception) {
            close()
            Result.failure(e)
        }
    }

    override suspend fun close() = withContext(Dispatchers.IO) {
        isOpen = false
        try { port.close() } catch (_: Exception) {}
        try { connection?.close() } catch (_: Exception) {}
        connection = null
    }

    override suspend fun setBaudRate(baudRate: Int): Boolean = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext false
        try {
            port.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setDtr(dtr: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext false
        try {
            port.dtr = dtr
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun setRts(rts: Boolean): Boolean = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext false
        try {
            port.rts = rts
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun write(data: ByteArray): Int = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext -1
        try {
            port.write(data, 1000)
            data.size
        } catch (e: Exception) {
            -1
        }
    }

    override suspend fun read(buffer: ByteArray, timeoutMs: Long): Int = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext -1
        try {
            port.read(buffer, timeoutMs.toInt())
        } catch (e: Exception) {
            -1
        }
    }

    override suspend fun purge() = withContext(Dispatchers.IO) {
        if (!isOpen) return@withContext
        try {
            port.purgeHwBuffers(true, true)
        } catch (_: Exception) {}
    }
}
