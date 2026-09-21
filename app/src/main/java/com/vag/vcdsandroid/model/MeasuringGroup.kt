package com.vag.vcdsandroid.model

import java.util.Locale

/**
 * Represents a single measuring value within a VAG Measuring Block.
 */
data class MeasuringValue(
    val fieldIndex: Int,
    val title: String,
    val rawValue: Double,
    val formattedValue: String,
    val unit: String,
    val isWarning: Boolean = false
)

/**
 * Represents a VAG Measuring Block (Group 001 - 255) containing 4 values.
 */
data class MeasuringGroup(
    val groupNumber: Int,
    val title: String,
    val values: List<MeasuringValue>,
    val timestampMs: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Decodes a raw KWP2000 0x61 response into a structured MeasuringGroup.
         * Raw response format: [0x61, groupNum, val1_type, a1, b1, val2_type, a2, b2, val3_type, a3, b3, val4_type, a4, b4]
         */
        fun decode(data: ByteArray): MeasuringGroup? {
            if (data.size < 14) return null
            if (data[0] != 0x61.toByte()) return null

            val groupNum = data[1].toInt() and 0xFF
            val valuesList = ArrayList<MeasuringValue>()

            for (i in 0 until 4) {
                val offset = 2 + (i * 3)
                if (offset + 2 >= data.size) break

                val formulaType = data[offset].toInt() and 0xFF
                val byteA = data[offset + 1].toInt() and 0xFF
                val byteB = data[offset + 2].toInt() and 0xFF

                val (decodedVal, unit, defTitle) = decodeVagFormula(formulaType, byteA, byteB, groupNum, i + 1)
                
                val title = getEdc16FieldTitle(groupNum, i + 1) ?: defTitle
                val formatted = formatValue(decodedVal, unit)

                valuesList.add(
                    MeasuringValue(
                        fieldIndex = i + 1,
                        title = title,
                        rawValue = decodedVal,
                        formattedValue = formatted,
                        unit = unit
                    )
                )
            }

            val groupTitle = getEdc16GroupTitle(groupNum) ?: String.format(Locale.US, "Group %03d", groupNum)
            return MeasuringGroup(groupNum, groupTitle, valuesList)
        }

        private fun formatValue(value: Double, unit: String): String {
            return when {
                unit == "RPM" -> String.format(Locale.US, "%.0f", value)
                unit == "mbar" || unit == "hPa" -> String.format(Locale.US, "%.0f", value)
                unit == "%" -> String.format(Locale.US, "%.1f", value)
                unit == "mg/str" || unit == "mg/h" -> String.format(Locale.US, "%.1f", value)
                unit == "°C" -> String.format(Locale.US, "%.1f", value)
                unit == "°BTDC" || unit == "°KW" -> String.format(Locale.US, "%.2f", value)
                unit == "V" -> String.format(Locale.US, "%.2f", value)
                else -> String.format(Locale.US, "%.1f", value)
            }
        }

        /**
         * Standard VAG measuring formula decoder (Formulas 1..70)
         */
        /**
         * Decode the VAG measuring-block scaler byte from a KWP 0x61 response.
         *
         * The scaler is a protocol byte (for example 0x12 pressure, 0x1A
         * temperature, 0x31 air mass, 0x5E torque), not a group-specific guess.
         * Unknown scalers deliberately stay raw so the app never invents a
         * physically plausible-looking value from an unsupported formula.
         */
        private fun decodeVagFormula(type: Int, a: Int, b: Int, group: Int, field: Int): Triple<Double, String, String> {
            val raw16 = ((a shl 8) or b).toDouble()
            return when (type) {
                0x01 -> Triple(a * b / 5.0, "RPM", "Engine Speed")
                0x04 -> Triple((b - 127) * 0.01 * a, "°ATDC", "Timing")
                0x07 -> Triple(0.01 * a * b, "km/h", "Speed")
                0x08, 0x10, 0x25 -> Triple(raw16, "raw", "Raw/Binary")
                0x12 -> Triple(a * b / 25.0, "mbar", "Pressure")
                0x14 -> Triple(a * b / 128.0 - 1.0, "%", "Percentage")
                0x15 -> Triple(0.001 * a * b, "V", "Voltage")
                0x16 -> Triple(0.001 * a * b, "ms", "Time")
                0x17 -> Triple(a * b / 256.0, "%", "Duty Cycle")
                0x19 -> Triple(if (a != 0) 100.0 * b / a else 0.0, "g/s", "Air Mass")
                0x1A -> Triple((b - a).toDouble(), "°C", "Temperature")
                0x21 -> Triple(if (a == 0) 100.0 * b else 100.0 * b / a, "%", "Ratio")
                0x22 -> Triple((b - 128) * 0.01 * a, "kW", "Power")
                0x23 -> Triple(a * b / 100.0, "l/h", "Consumption")
                0x24 -> Triple(((a * 256) + b) * 10.0, "km", "Distance")
                0x27 -> Triple(a * b / 256.0, "mg/str", "Fuel Quantity")
                0x31 -> Triple(a * b / 40.0, "mg/str", "Air Mass")
                0x33 -> Triple(((b - 128) / 255.0) * a, "mg/str", "Correction")
                0x36 -> Triple(raw16, "count", "Counter")
                0x37 -> Triple(a * b / 200.0, "s", "Time")
                0x5E -> Triple(a * (b / 50.0 - 1.0), "Nm", "Torque")

                // EDC-specific scalers retained from the existing implementation.
                // They do not overlap the standard KWP scaler IDs above.
                0x03 -> Triple(0.002 * a * b, "°", "Angle")
                0x05 -> Triple(a * (b - 100) * 0.1, "°C", "Temperature")
                0x06 -> Triple(0.001 * a * b, "V", "Voltage")
                0x09 -> Triple((b - 127) * 0.02 * a, "°", "Angle Offset")
                0x0E -> Triple(0.005 * a * b, "bar", "Pressure")
                0x0F -> Triple(0.01 * a * b, "ms", "Time")
                0x1B -> Triple(kotlin.math.abs(b - 128) * 0.01 * a, "°", "Angle")
                0x1F -> Triple((b / 2560.0) * a, "°C", "Temperature")
                0x2C -> Triple(a.toDouble(), "h:m", "Time")
                0x32 -> Triple(if (a != 0) (b - 128) / (0.01 * a) else 0.0, "mbar", "Pressure")
                0x39 -> Triple(raw16, "°C", "Exhaust Temp Raw")
                0x3C -> Triple(raw16 * 0.01, "s", "Duration")
                0x43 -> Triple(raw16 / 64.0, "°KW", "Synchro Angle")
                0x44 -> Triple(raw16 / 7.36, "°KW", "Duration")
                0x45 -> Triple(raw16 * 0.3254, "bar", "Rail Pressure")

                else -> Triple(raw16, "raw", "Unsupported scaler 0x%02X".format(type))
            }
        }

        private fun getEdc16GroupTitle(group: Int): String? {
            return when (group) {
                1 -> "Group 001 — Injected Quantity & Coolant"
                3 -> "Group 003 — Exhaust Gas Recirculation (EGR & MAF)"
                4 -> "Group 004 — Unit Injectors Timing & Synchro Angle"
                7 -> "Group 007 — Temperature Senders (Fuel, Oil, Air, Coolant)"
                8 -> "Group 008 — Torque / IQ Limitations"
                10 -> "Group 010 — Air System (MAF, Baro, Boost, Throttle)"
                11 -> "Group 011 — Charge Pressure Control (Turbo & N75)"
                13 -> "Group 013 — Idle Stabilization (Cyl 1-4 Smoothness)"
                15 -> "Group 015 — Fuel Consumption & Pedal"
                18 -> "Group 018 — PD Injector Solenoid Status"
                23 -> "Group 023 — PD Injector BIP Values (Begin of Period)"
                67 -> "Group 067 — Diesel Particulate Filter (DPF) Temps"
                68 -> "Group 068 — DPF Soot Load & Ash Volume"
                75 -> "Group 075 — Exhaust Gas Temp & Pressure (G450)"
                else -> null
            }
        }

        private fun getEdc16FieldTitle(group: Int, field: Int): String? {
            return when (group) {
                1 -> when (field) {
                    1 -> "Engine Speed (RPM)"
                    2 -> "Injected Quantity (mg/str)"
                    3 -> "Duration Specified (°KW)"
                    4 -> "Coolant Temperature (°C)"
                    else -> null
                }
                3 -> when (field) {
                    1 -> "Engine Speed (RPM)"
                    2 -> "MAF Specified (mg/str)"
                    3 -> "MAF Actual (mg/str)"
                    4 -> "EGR Duty Cycle (%)"
                    else -> null
                }
                4 -> when (field) {
                    1 -> "Engine Speed (RPM)"
                    2 -> "Start of Injection (°BTDC)"
                    3 -> "Duration (°KW)"
                    4 -> "Synchro Angle (°KW)"
                    else -> null
                }
                8 -> when (field) {
                    1 -> "Engine Speed"
                    2 -> "Driver Intention"
                    3 -> "Torque Limitation"
                    4 -> "Smoke Limitation"
                    else -> null
                }
                10 -> when (field) {
                    1 -> "MAF Actual (mg/str)"
                    2 -> "Barometric Pressure (mbar)"
                    3 -> "Actual Boost Pressure (mbar)"
                    4 -> "Throttle Pedal Position (%)"
                    else -> null
                }
                11 -> when (field) {
                    1 -> "Engine Speed (RPM)"
                    2 -> "Specified Boost (mbar)"
                    3 -> "Actual Boost (mbar)"
                    4 -> "N75 Duty Cycle (%)"
                    else -> null
                }
                13 -> when (field) {
                    1 -> "Cylinder 1 Deviation (mg/str)"
                    2 -> "Cylinder 2 Deviation (mg/str)"
                    3 -> "Cylinder 3 Deviation (mg/str)"
                    4 -> "Cylinder 4 Deviation (mg/str)"
                    else -> null
                }
                68 -> when (field) {
                    1 -> "DPF Soot Load (%)"
                    2 -> "Ash Mass (g)"
                    3 -> "Soot Mass Calculated (g)"
                    4 -> "Soot Mass Measured (g)"
                    else -> null
                }
                else -> null
            }
        }
    }
}
