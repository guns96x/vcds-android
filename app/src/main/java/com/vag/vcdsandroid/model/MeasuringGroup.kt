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
        private fun decodeVagFormula(type: Int, a: Int, b: Int, group: Int, field: Int): Triple<Double, String, String> {
            return when (type) {
                1 -> Triple(0.2 * a * b, "RPM", "Engine Speed")
                2 -> Triple(a * 0.002 * b, "%", "Load/Duty")
                3 -> Triple(0.002 * a * b, "°", "Angle")
                4 -> Triple(Math.abs(128 - a) * b * 0.01, "°", "Deviation")
                5 -> Triple(a * (b - 100) * 0.1, "°C", "Temperature")
                6 -> Triple(0.001 * a * b, "V", "Voltage")
                7 -> Triple(0.01 * a * b, "km/h", "Speed")
                8 -> Triple(0.1 * a * b, "mbar", "Pressure")
                9 -> Triple((b - 127) * 0.02 * a, "°", "Angle Offset")
                14 -> Triple(0.005 * a * b, "bar", "Pressure")
                15 -> Triple(0.01 * a * b, "ms", "Time")
                18 -> Triple(0.04 * a * b, "mbar", "Pressure")
                19 -> Triple(a * b * 0.01, "l", "Volume")
                20 -> Triple(a * (b - 128) / 128.0, "%", "Correction")
                21 -> Triple(0.001 * a * b, "V", "Voltage")
                22 -> Triple(0.001 * a * b, "ms", "Period")
                23 -> Triple((b.toDouble() / 256.0) * a, "%", "Duty Cycle")
                24 -> Triple(0.001 * a * b, "A", "Current")
                25 -> Triple((b * 1.421) + (a / 182.0), "g/s", "Air Mass")
                27 -> Triple(Math.abs(b - 128) * 0.01 * a, "°", "Angle")
                28 -> Triple((b - 128) * 0.01 * a, "°", "Angle")
                31 -> Triple((b / 2560.0) * a, "°C", "Temperature")
                33 -> Triple(if (a != 0) 100.0 * b / a else 0.0, "%", "Ratio")
                36 -> Triple(((a * 256) + b) * 10.0, "km", "Distance")
                37 -> Triple(b.toDouble(), "", "Raw")
                39 -> Triple((b / 256.0) * a, "mg/str", "Mass")
                44 -> Triple(a.toDouble(), "h:m", "Time")
                49 -> Triple((b / 4.0) * 0.1 * a, "mg/str", "Quantity")
                50 -> Triple((b - 128) / (0.01 * a), "mbar", "Pressure")
                51 -> Triple(((b - 128) / 255.0) * a, "mg/str", "Correction")
                52 -> Triple(b * 0.02 * a - a, "Nm", "Torque")
                54 -> Triple((a * 256 + b).toDouble(), "count", "Counter")
                57 -> Triple((a * 256 + b).toDouble(), "°C", "Exhaust Temp")
                60 -> Triple((a * 256 + b) * 0.01, "s", "Duration")
                67 -> Triple((a * 256 + b) / 64.0, "°KW", "Synchro Angle")
                68 -> Triple((256 * a + b) / 7.36, "°KW", "Duration")
                69 -> Triple((256 * a + b) * 0.3254, "bar", "Rail Pressure")
                else -> {
                    // Fallback heuristics for EDC16
                    if (group == 11) {
                        when (field) {
                            1 -> Triple(a * 256.0 + b, "RPM", "Engine Speed")
                            2 -> Triple(a * 256.0 + b, "mbar", "Specified Boost")
                            3 -> Triple(a * 256.0 + b, "mbar", "Actual Boost")
                            4 -> Triple((b / 255.0) * 100.0, "%", "N75 Duty Cycle")
                            else -> Triple(b.toDouble(), "", "Field $field")
                        }
                    } else {
                        Triple(b.toDouble(), "", "Field $field")
                    }
                }
            }
        }

        private fun getEdc16GroupTitle(group: Int): String? {
            return when (group) {
                1 -> "Group 001 — Injected Quantity & Coolant"
                3 -> "Group 003 — Exhaust Gas Recirculation (EGR & MAF)"
                4 -> "Group 004 — Unit Injectors Timing & Synchro Angle"
                7 -> "Group 007 — Temperature Senders (Fuel, Oil, Air, Coolant)"
                8 -> "Group 008 — IQ Limitation (Drivers Wish, Torque, Smoke)"
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
                    1 -> "Engine Speed (RPM)"
                    2 -> "Driver's Wish IQ (mg/str)"
                    3 -> "Torque Limitation IQ (mg/str)"
                    4 -> "Smoke Limitation IQ (mg/str)"
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
