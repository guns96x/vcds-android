package com.vag.vcdsandroid.protocol

object TelemetryFreshnessPolicy {
    const val RPM_MAX_AGE_MS = 1000L
    const val MAP_MAX_AGE_MS = 1000L
    const val MAF_MAX_AGE_MS = 2500L
    const val SPEED_MAX_AGE_MS = 4500L
    const val LOAD_MAX_AGE_MS = 4500L
    const val SLOW_MAX_AGE_MS = 12000L
    const val PHONE_BARO_MAX_AGE_MS = 5000L

    fun getMaxAgeMs(pid: String): Long {
        return when (pid) {
            "010C" -> RPM_MAX_AGE_MS
            "010B" -> MAP_MAX_AGE_MS
            "0110" -> MAF_MAX_AGE_MS
            "010D" -> SPEED_MAX_AGE_MS
            "0104" -> LOAD_MAX_AGE_MS
            "0105", "010F", "0142", "0133" -> SLOW_MAX_AGE_MS
            else -> 10000L
        }
    }
}
