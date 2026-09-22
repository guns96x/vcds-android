package com.vag.vcdsandroid.model

import java.util.Locale

/**
 * Diagnostic Trouble Code (DTC) parsed from KWP2000 Service 0x18.
 */
data class FaultCode(
    val vagCode: String,
    val saeCode: String,
    val descriptionEn: String,
    val descriptionUk: String,
    val statusByte: Int,
    val isMilActive: Boolean,
    val isIntermittent: Boolean
) {
    companion object {
        fun parseFromBytes(highByte: Int, lowByte: Int, status: Int, lookup: Map<String, Pair<String, String>>): FaultCode {
            val raw16 = ((highByte and 0xFF) shl 8) or (lowByte and 0xFF)
            
            // Format VAG 5-digit code e.g. "17964" or "00575"
            val vagCode = String.format(Locale.US, "%05d", raw16)
            
            // Derive SAE P-code e.g. "P0299"
            val saeCode = deriveSaeCode(highByte, lowByte)
            
            // Status flags according to ISO 14230-3
            val isMilActive = (status and 0x80) != 0
            val isIntermittent = (status and 0x40) != 0 || (status and 0x20) == 0

            // Check dictionary lookup
            val (descEn, descUk) = lookup[saeCode] ?: lookup[vagCode] ?: getDefaultDescription(saeCode, vagCode)

            return FaultCode(
                vagCode = vagCode,
                saeCode = saeCode,
                descriptionEn = descEn,
                descriptionUk = descUk,
                statusByte = status,
                isMilActive = isMilActive,
                isIntermittent = isIntermittent
            )
        }

        private fun deriveSaeCode(high: Int, low: Int): String {
            val raw16 = ((high and 0xFF) shl 8) or (low and 0xFF)
            if (raw16 in 16384..19999) {
                return String.format(Locale.US, "P%04d", raw16 - 16384)
            }
            val prefix = when ((high shr 6) and 0x03) {
                0 -> "P"
                1 -> "C"
                2 -> "B"
                else -> "U"
            }
            val digit1 = (high shr 4) and 0x03
            val digit2 = high and 0x0F
            val digit3 = (low shr 4) and 0x0F
            val digit4 = low and 0x0F
            return String.format(Locale.US, "%s%X%X%X%X", prefix, digit1, digit2, digit3, digit4)
        }

        private fun getDefaultDescription(sae: String, vag: String): Pair<String, String> {
            return when (sae) {
                "P0299" -> Pair(
                    "Boost Pressure Regulation: Control Range Not Reached (Underboost)",
                    "Регулювання тиску наддуву: нижче нижньої межі (недодув турбіни)"
                )
                "P0234" -> Pair(
                    "Boost Pressure Regulation: Limit Exceeded (Overboost Condition)",
                    "Регулювання тиску наддуву: перевищено верхню межу (передув)"
                )
                "P0101" -> Pair(
                    "Mass Air Flow Sensor (G70): Implausible Signal",
                    "Витратомір повітря G70: недостовірний сигнал"
                )
                "P0401" -> Pair(
                    "Exhaust Gas Recirculation (EGR) System: Insufficient Flow Detected",
                    "Система рециркуляції ОГ (EGR): недостатня пропускна здатність"
                )
                "P0471" -> Pair(
                    "Exhaust Pressure Sensor 1 (G450): Implausible Signal",
                    "Датчик різниці тиску сажового фільтра G450: недостовірний сигнал"
                )
                "P2002" -> Pair(
                    "Particulate Trap Bank 1: Efficiency Below Threshold",
                    "Сажовий фільтр DPF: ефективність нижче допустимого порогу"
                )
                else -> Pair(
                    "Fault Code $vag ($sae)",
                    "Код помилки $vag ($sae)"
                )
            }
        }
    }
}
