package com.vag.vcdsandroid.hardware

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort

/**
 * Dedicated hardware driver for QinHeng CH340 / CH341 USB-UART adapters.
 */
class UsbCh34xDriver(
    usbManager: UsbManager,
    usbDevice: UsbDevice,
    port: UsbSerialPort
) : UsbSerialDriverBase(
    usbManager = usbManager,
    usbDevice = usbDevice,
    port = port,
    name = "CH34x Driver (%04X:%04X)".format(usbDevice.vendorId, usbDevice.productId)
)

/**
 * Generic USB CDC / CP210x / PL2303 serial hardware driver.
 */
class UsbCdcDriver(
    usbManager: UsbManager,
    usbDevice: UsbDevice,
    port: UsbSerialPort
) : UsbSerialDriverBase(
    usbManager = usbManager,
    usbDevice = usbDevice,
    port = port,
    name = "USB Serial Driver (%04X:%04X)".format(usbDevice.vendorId, usbDevice.productId)
)
