package com.vag.vcdsandroid.protocol

import java.util.Locale

enum class PidStatus {
    IDLE,
    VALID,
    STALE,
    INVALID_FORMAT,
    NO_DATA,
    TIMEOUT,
    ERROR
}

data class DecodedPid(
    val pid: String,
    val status: PidStatus,
    val value: Double?,
    val formatted: String,
    val unit: String,
    val rawHex: String,
    val rawString: String,
    val latencyMs: Long,
    val txNanos: Long,
    val rxNanos: Long
)

object PidDecoder {

    fun cleanHex(raw: String): String {
        val sb = java.lang.StringBuilder()
        for (ch in raw) {
            if (ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F') {
                sb.append(ch.uppercaseChar())
            }
        }
        return sb.toString()
    }

    fun decodeRpm(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("010C", PidStatus.TIMEOUT, null, "TIMEOUT", "RPM", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("010C", PidStatus.NO_DATA, null, "NO DATA", "RPM", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("410C")
        if (idx >= 0 && idx + 8 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            val b = clean.substring(idx + 6, idx + 8).toIntOrNull(16)
            if (a != null && b != null) {
                val rpm = ((a * 256) + b) / 4.0
                return DecodedPid("010C", PidStatus.VALID, rpm, String.format(Locale.US, "%.0f", rpm), "RPM", clean.substring(idx, idx + 8), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("010C", PidStatus.INVALID_FORMAT, null, "INVALID", "RPM", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeMap(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("010B", PidStatus.TIMEOUT, null, "TIMEOUT", "mbar", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("010B", PidStatus.NO_DATA, null, "NO DATA", "mbar", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("410B")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null) {
                val mbar = a * 10.0
                return DecodedPid("010B", PidStatus.VALID, mbar, String.format(Locale.US, "%.0f", mbar), "mbar", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("010B", PidStatus.INVALID_FORMAT, null, "INVALID", "mbar", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeMaf(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("0110", PidStatus.TIMEOUT, null, "TIMEOUT", "g/s", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("0110", PidStatus.NO_DATA, null, "NO DATA", "g/s", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("4110")
        if (idx >= 0 && idx + 8 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            val b = clean.substring(idx + 6, idx + 8).toIntOrNull(16)
            if (a != null && b != null) {
                val maf = ((a * 256) + b) / 100.0
                return DecodedPid("0110", PidStatus.VALID, maf, String.format(Locale.US, "%.2f", maf), "g/s", clean.substring(idx, idx + 8), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("0110", PidStatus.INVALID_FORMAT, null, "INVALID", "g/s", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeBaro(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("0133", PidStatus.TIMEOUT, null, "TIMEOUT", "mbar", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("0133", PidStatus.NO_DATA, null, "NO DATA", "mbar", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("4133")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null && a in 60..125) {
                val mbar = a * 10.0
                return DecodedPid("0133", PidStatus.VALID, mbar, String.format(Locale.US, "%.0f", mbar), "mbar", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("0133", PidStatus.INVALID_FORMAT, null, "INVALID", "mbar", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeSpeed(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("010D", PidStatus.TIMEOUT, null, "TIMEOUT", "km/h", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("010D", PidStatus.NO_DATA, null, "NO DATA", "km/h", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("410D")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null) {
                return DecodedPid("010D", PidStatus.VALID, a.toDouble(), "$a", "km/h", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("010D", PidStatus.INVALID_FORMAT, null, "INVALID", "km/h", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeLoad(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("0104", PidStatus.TIMEOUT, null, "TIMEOUT", "%", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("0104", PidStatus.NO_DATA, null, "NO DATA", "%", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("4104")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null) {
                val load = (a * 100.0) / 255.0
                return DecodedPid("0104", PidStatus.VALID, load, String.format(Locale.US, "%.1f", load), "%", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("0104", PidStatus.INVALID_FORMAT, null, "INVALID", "%", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeCoolant(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("0105", PidStatus.TIMEOUT, null, "TIMEOUT", "°C", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("0105", PidStatus.NO_DATA, null, "NO DATA", "°C", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("4105")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null) {
                val tempC = (a - 40).toDouble()
                return DecodedPid("0105", PidStatus.VALID, tempC, String.format(Locale.US, "%.0f", tempC), "°C", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("0105", PidStatus.INVALID_FORMAT, null, "INVALID", "°C", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeIat(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("010F", PidStatus.TIMEOUT, null, "TIMEOUT", "°C", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("010F", PidStatus.NO_DATA, null, "NO DATA", "°C", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("410F")
        if (idx >= 0 && idx + 6 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            if (a != null) {
                val tempC = (a - 40).toDouble()
                return DecodedPid("010F", PidStatus.VALID, tempC, String.format(Locale.US, "%.0f", tempC), "°C", clean.substring(idx, idx + 6), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("010F", PidStatus.INVALID_FORMAT, null, "INVALID", "°C", clean, raw, latencyMs, txNanos, rxNanos)
    }

    fun decodeVoltage(raw: String, txNanos: Long, rxNanos: Long, latencyMs: Long, timedOut: Boolean): DecodedPid {
        if (timedOut) {
            return DecodedPid("0142", PidStatus.TIMEOUT, null, "TIMEOUT", "V", "", raw, latencyMs, txNanos, rxNanos)
        }
        val upper = raw.uppercase()
        if (upper.contains("NO DATA") || upper.contains("NODATA")) {
            return DecodedPid("0142", PidStatus.NO_DATA, null, "NO DATA", "V", "", raw, latencyMs, txNanos, rxNanos)
        }
        val clean = cleanHex(raw)
        val idx = clean.indexOf("4142")
        if (idx >= 0 && idx + 8 <= clean.length) {
            val a = clean.substring(idx + 4, idx + 6).toIntOrNull(16)
            val b = clean.substring(idx + 6, idx + 8).toIntOrNull(16)
            if (a != null && b != null) {
                val v = ((a * 256) + b) / 1000.0
                return DecodedPid("0142", PidStatus.VALID, v, String.format(Locale.US, "%.1f", v), "V", clean.substring(idx, idx + 8), raw, latencyMs, txNanos, rxNanos)
            }
        }
        // Fallback for ATRV e.g. "14.2V"
        val atrvMatch = Regex("([0-9]+\\.[0-9]+)").find(raw)
        if (atrvMatch != null) {
            val v = atrvMatch.groupValues[1].toDoubleOrNull()
            if (v != null) {
                return DecodedPid("0142", PidStatus.VALID, v, String.format(Locale.US, "%.1f", v), "V", raw.trim(), raw, latencyMs, txNanos, rxNanos)
            }
        }
        return DecodedPid("0142", PidStatus.INVALID_FORMAT, null, "INVALID", "V", clean, raw, latencyMs, txNanos, rxNanos)
    }
}
