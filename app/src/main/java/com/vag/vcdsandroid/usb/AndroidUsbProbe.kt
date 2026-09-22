package com.vag.vcdsandroid.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import com.hoho.android.usbserial.driver.UsbSerialPort
import org.json.JSONArray
import org.json.JSONObject

/**
 * Detailed endpoint report captured from Android UsbEndpoint.
 */
data class UsbEndpointReport(
    val address: Int,
    val endpointNumber: Int,
    val direction: String, // "IN" or "OUT"
    val type: String,      // "BULK", "INTERRUPT", "CONTROL", etc.
    val maxPacketSize: Int,
    val attributes: Int
)

/**
 * Detailed interface report captured from Android UsbInterface.
 */
data class UsbInterfaceReport(
    val id: Int,
    val alternateSetting: Int,
    val interfaceClass: Int,
    val interfaceSubclass: Int,
    val interfaceProtocol: Int,
    val endpointCount: Int,
    val endpoints: List<UsbEndpointReport>
)

/**
 * Diagnostic evidence classification for adapter detection.
 */
enum class HardwareProfile {
    ROSS_TECH_HEX_FA24_FTDI,   // VID 0403, PID FA24 (Candidate B03-V2 / HEX-USB+CAN clone)
    ROSS_TECH_HEX_FA20_FTDI,   // VID 0403, PID FA20 (HEX-USB)
    FTDI_6001_BRIDGE,          // VID 0403, PID 6001 (FTDI USB-UART bridge; downstream protocol unverified)
    CH34X_BRIDGE,              // VID 1A86 (CH340/CH341 USB-UART bridge; downstream protocol unverified)
    CP210X_BRIDGE,             // VID 10C4 (CP2102 USB-UART bridge; downstream protocol unverified)
    PL2303_BRIDGE,             // VID 067B (PL2303 USB-UART bridge; downstream protocol unverified)
    UNKNOWN_USB_DEVICE;

    companion object {
        @Deprecated("Use FTDI_6001_BRIDGE", ReplaceWith("FTDI_6001_BRIDGE"))
        val FTDI_FT232R_BRIDGE = FTDI_6001_BRIDGE
    }
}

/**
 * Comprehensive USB hardware inspection report for a connected Android USB device.
 */
data class UsbDeviceReport(
    val vendorIdHex: String,
    val productIdHex: String,
    val deviceName: String,
    val manufacturerName: String?,
    val productName: String?,
    val serialNumber: String?,
    val deviceClass: Int,
    val deviceSubclass: Int,
    val deviceProtocol: Int,
    val interfaceCount: Int,
    val interfaces: List<UsbInterfaceReport>,
    val hasPermission: Boolean,
    val hardwareProfile: HardwareProfile,
    val controllerHypothesis: String,
    val openResult: String
) {
    fun toJson(): JSONObject {
        val root = JSONObject()
        root.put("vendorIdHex", vendorIdHex)
        root.put("productIdHex", productIdHex)
        root.put("deviceName", deviceName)
        root.put("manufacturerName", manufacturerName ?: "null")
        root.put("productName", productName ?: "null")
        root.put("serialNumber", serialNumber ?: "null")
        root.put("deviceClass", deviceClass)
        root.put("deviceSubclass", deviceSubclass)
        root.put("deviceProtocol", deviceProtocol)
        root.put("interfaceCount", interfaceCount)
        root.put("hasPermission", hasPermission)
        root.put("hardwareProfile", hardwareProfile.name)
        root.put("controllerHypothesis", controllerHypothesis)
        root.put("openResult", openResult)

        val ifacesArray = JSONArray()
        for (iface in interfaces) {
            val iObj = JSONObject()
            iObj.put("id", iface.id)
            iObj.put("alternateSetting", iface.alternateSetting)
            iObj.put("interfaceClass", iface.interfaceClass)
            iObj.put("interfaceSubclass", iface.interfaceSubclass)
            iObj.put("interfaceProtocol", iface.interfaceProtocol)
            val epsArray = JSONArray()
            for (ep in iface.endpoints) {
                val eObj = JSONObject()
                eObj.put("address", ep.address)
                eObj.put("endpointNumber", ep.endpointNumber)
                eObj.put("direction", ep.direction)
                eObj.put("type", ep.type)
                eObj.put("maxPacketSize", ep.maxPacketSize)
                epsArray.put(eObj)
            }
            iObj.put("endpoints", epsArray)
            ifacesArray.put(iObj)
        }
        root.put("interfaces", ifacesArray)
        return root
    }
}

/**
 * Reusable Android USB hardware discovery helper.
 * Strictly non-destructive: only reads USB descriptors and tests safe userspace open/close.
 */
object AndroidUsbProbe {

    fun classifyProfile(vid: Int, pid: Int): Pair<HardwareProfile, String> {
        return when {
            vid == 0x0403 && pid == 0xFA24 -> Pair(
                HardwareProfile.ROSS_TECH_HEX_FA24_FTDI,
                "PROVEN: FTDI USB bridge with Ross-Tech PID. HYPOTHESIS: ATmega162 + MCP2515 coprocessor (unconfirmed until protocol/board probe)."
            )
            vid == 0x0403 && pid == 0xFA20 -> Pair(
                HardwareProfile.ROSS_TECH_HEX_FA20_FTDI,
                "PROVEN: FTDI USB bridge with Ross-Tech HEX-USB PID. HYPOTHESIS: Legacy HEX coprocessor."
            )
            vid == 0x0403 && pid == 0x6001 -> Pair(
                HardwareProfile.FTDI_6001_BRIDGE,
                "PROVEN: FTDI USB-UART bridge (0403:6001). ADAPTER PROTOCOL: UNVERIFIED (KKL pass-through requires user confirmation)."
            )
            vid == 0x1A86 -> Pair(
                HardwareProfile.CH34X_BRIDGE,
                "PROVEN: WCH CH340/CH341 USB-UART bridge. ADAPTER PROTOCOL: UNVERIFIED."
            )
            vid == 0x10C4 -> Pair(
                HardwareProfile.CP210X_BRIDGE,
                "PROVEN: Silicon Labs CP210x USB-UART bridge. ADAPTER PROTOCOL: UNVERIFIED."
            )
            vid == 0x067B -> Pair(
                HardwareProfile.PL2303_BRIDGE,
                "PROVEN: Prolific PL2303 USB-UART bridge. ADAPTER PROTOCOL: UNVERIFIED."
            )
            else -> Pair(
                HardwareProfile.UNKNOWN_USB_DEVICE,
                "UNKNOWN: Unrecognized USB vendor/product combination."
            )
        }
    }

    fun inspectDevice(context: Context, device: UsbDevice): UsbDeviceReport {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        val hasPerm = usbManager.hasPermission(device)

        val ifaces = mutableListOf<UsbInterfaceReport>()
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            val eps = mutableListOf<UsbEndpointReport>()
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                val dirStr = if (ep.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT"
                val typeStr = when (ep.type) {
                    UsbConstants.USB_ENDPOINT_XFER_BULK -> "BULK"
                    UsbConstants.USB_ENDPOINT_XFER_INT -> "INTERRUPT"
                    UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "CONTROL"
                    UsbConstants.USB_ENDPOINT_XFER_ISOC -> "ISOCHRONOUS"
                    else -> "UNKNOWN(${ep.type})"
                }
                eps.add(
                    UsbEndpointReport(
                        address = ep.address,
                        endpointNumber = ep.endpointNumber,
                        direction = dirStr,
                        type = typeStr,
                        maxPacketSize = ep.maxPacketSize,
                        attributes = ep.attributes
                    )
                )
            }
            ifaces.add(
                UsbInterfaceReport(
                    id = iface.id,
                    alternateSetting = iface.alternateSetting,
                    interfaceClass = iface.interfaceClass,
                    interfaceSubclass = iface.interfaceSubclass,
                    interfaceProtocol = iface.interfaceProtocol,
                    endpointCount = iface.endpointCount,
                    endpoints = eps
                )
            )
        }

        var openStatus = if (!hasPerm) "NO_PERMISSION" else "NOT_ATTEMPTED"
        if (hasPerm) {
            var conn: UsbDeviceConnection? = null
            try {
                conn = usbManager.openDevice(device)
                openStatus = if (conn != null) "SUCCESS" else "OPEN_RETURNED_NULL"
            } catch (e: Exception) {
                openStatus = "OPEN_EXCEPTION: ${e.message}"
            } finally {
                try { conn?.close() } catch (_: Exception) {}
            }
        }

        val (profile, hypothesis) = classifyProfile(device.vendorId, device.productId)

        val mfg = try { device.manufacturerName } catch (_: Exception) { null }
        val prod = try { device.productName } catch (_: Exception) { null }
        val ser = try { if (hasPerm) device.serialNumber else "PERMISSION_REQUIRED" } catch (_: Exception) { null }

        return UsbDeviceReport(
            vendorIdHex = "%04X".format(device.vendorId),
            productIdHex = "%04X".format(device.productId),
            deviceName = device.deviceName,
            manufacturerName = mfg,
            productName = prod,
            serialNumber = ser,
            deviceClass = device.deviceClass,
            deviceSubclass = device.deviceSubclass,
            deviceProtocol = device.deviceProtocol,
            interfaceCount = device.interfaceCount,
            interfaces = ifaces,
            hasPermission = hasPerm,
            hardwareProfile = profile,
            controllerHypothesis = hypothesis,
            openResult = openStatus
        )
    }

    fun inspectAll(context: Context): List<UsbDeviceReport> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        return usbManager.deviceList.values.map { inspectDevice(context, it) }
    }
}
