package com.vag.vcdsandroid.registry

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.Ch34xSerialDriver
import com.hoho.android.usbserial.driver.Cp21xxSerialDriver
import com.hoho.android.usbserial.driver.FtdiSerialDriver
import com.hoho.android.usbserial.driver.ProlificSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.vag.vcdsandroid.adapters.AdapterTransport
import com.vag.vcdsandroid.adapters.HexB03Adapter
import com.vag.vcdsandroid.adapters.HexLegacyAdapter
import com.vag.vcdsandroid.adapters.HexV2Adapter
import com.vag.vcdsandroid.adapters.KklAdapter
import com.vag.vcdsandroid.adapters.UnverifiedAdapter
import com.vag.vcdsandroid.hardware.HardwareDriver
import com.vag.vcdsandroid.hardware.UsbCdcDriver
import com.vag.vcdsandroid.hardware.UsbCh34xDriver
import com.vag.vcdsandroid.hardware.UsbFtdiDriver
import com.vag.vcdsandroid.usb.UsbKwpTransport

/**
 * Two-stage modular registry:
 * Stage 1: Detect and instantiate the physical [HardwareDriver].
 * Stage 2: Bind the appropriate [AdapterTransport] profile based on descriptors and hardware IDs.
 */
object AdapterRegistry {

    /**
     * Stage 1: Discovers and creates the low-level physical [HardwareDriver] for a USB device.
     */
    fun createHardwareDriver(
        context: Context,
        device: UsbDevice,
        portIndex: Int = 0
    ): Result<HardwareDriver> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val prober = UsbKwpTransport.createProber()

        val serialDriver: UsbSerialDriver = prober.probeDevice(device) ?: run {
            when (device.vendorId) {
                0x0403 -> FtdiSerialDriver(device)
                0x1A86 -> Ch34xSerialDriver(device)
                0x10C4 -> Cp21xxSerialDriver(device)
                0x067B -> ProlificSerialDriver(device)
                else -> return Result.failure(IllegalArgumentException("Unsupported USB Vendor ID: %04X".format(device.vendorId)))
            }
        }

        if (serialDriver.ports.isEmpty() || portIndex >= serialDriver.ports.size) {
            return Result.failure(IllegalStateException("No serial ports found on USB device"))
        }

        val port: UsbSerialPort = serialDriver.ports[portIndex]

        val hardwareDriver: HardwareDriver = when (device.vendorId) {
            0x0403 -> UsbFtdiDriver(usbManager, device, port)
            0x1A86 -> UsbCh34xDriver(usbManager, device, port)
            else -> UsbCdcDriver(usbManager, device, port)
        }

        return Result.success(hardwareDriver)
    }

    /**
     * Stage 2: Selects the appropriate [AdapterTransport] protocol profile for the driver and device.
     * Crucial: FTDI link does NOT automatically mean KKL pass-through.
     * Ross-Tech PIDs bind to [HexB03Adapter], while standard FT232R binds to [KklAdapter].
     */
    fun selectAdapter(
        driver: HardwareDriver,
        device: UsbDevice?,
        userSelectedKkl: Boolean = false
    ): AdapterTransport {
        if (device == null) {
            return UnverifiedAdapter(driver, "No USB Device Provided")
        }
        return selectAdapter(
            driver = driver,
            vid = device.vendorId,
            pid = device.productId,
            serialNumber = device.serialNumber,
            userSelectedKkl = userSelectedKkl
        )
    }

    fun selectAdapter(
        driver: HardwareDriver,
        vid: Int?,
        pid: Int?,
        serialNumber: String? = null,
        userSelectedKkl: Boolean = false
    ): AdapterTransport {
        if (vid == null || pid == null) {
            return UnverifiedAdapter(driver, "Unknown USB Device (Null IDs)")
        }

        return when {
            // Ross-Tech HEX-USB+CAN / B03-V2 FTDI clone
            vid == 0x0403 && pid == 0xFA24 -> HexB03Adapter(driver, serialNumber = serialNumber)

            // Ross-Tech legacy HEX-USB
            vid == 0x0403 && pid == 0xFA20 -> HexLegacyAdapter(driver)

            // Ross-Tech HEX-V2 clone (ARM STM32) placeholder
            vid == 0x0403 && pid == 0xFA30 -> HexV2Adapter(driver)

            // If user explicitly confirmed/selected KKL pass-through mode
            userSelectedKkl -> KklAdapter(driver, baudRate = 10400)

            // Generic FTDI / CH340 / CP2102 / PL2303 are USB-UART bridges:
            // Do NOT assume they are KKL cables! They could be anything (ELM327, Arduino, GPS).
            // Return UnverifiedAdapter requiring explicit profile or user confirmation.
            vid == 0x0403 && pid == 0x6001 -> UnverifiedAdapter(
                driver,
                "FTDI FT232R Bridge (0403:6001) - Protocol Unverified"
            )
            vid == 0x1A86 && pid == 0x7523 -> UnverifiedAdapter(
                driver,
                "CH340 Bridge (1A86:7523) - Protocol Unverified"
            )
            vid == 0x0403 -> UnverifiedAdapter(
                driver,
                "Generic FTDI Bridge (%04X:%04X) - Protocol Unverified".format(vid, pid)
            )
            vid == 0x1A86 -> UnverifiedAdapter(
                driver,
                "Generic CH34x Bridge (%04X:%04X) - Protocol Unverified".format(vid, pid)
            )
            vid == 0x10C4 -> UnverifiedAdapter(
                driver,
                "CP210x Bridge (%04X:%04X) - Protocol Unverified".format(vid, pid)
            )
            vid == 0x067B -> UnverifiedAdapter(
                driver,
                "PL2303 Bridge (%04X:%04X) - Protocol Unverified".format(vid, pid)
            )
            else -> UnverifiedAdapter(
                driver,
                "Unknown USB Device (%04X:%04X)".format(vid, pid)
            )
        }
    }

    /**
     * Convenience factory: resolves both stages directly from a connected [UsbDevice].
     */
    fun createAdapterForDevice(context: Context, device: UsbDevice): Result<AdapterTransport> {
        val driverResult = createHardwareDriver(context, device)
        if (driverResult.isFailure) {
            return Result.failure(driverResult.exceptionOrNull()!!)
        }
        val driver = driverResult.getOrThrow()
        val adapter = selectAdapter(driver, device)
        return Result.success(adapter)
    }
}
