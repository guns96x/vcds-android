package com.vag.vcdsandroid.protocol

data class BaroReading(
    val valueMbar: Double?,
    val source: String
)

class SessionBaroResolver(
    private val engineOffRpmMax: Double = 50.0,
    private val engineOffBaroMinMbar: Double = 800.0,
    private val engineOffBaroMaxMbar: Double = 1100.0,
    private val maxRpmAgeNsForCalib: Long = 1_000_000_000L // 1000 ms
) {
    var sessionBaroMbar: Double? = null
        private set

    var sessionBaroSource: String = "UNAVAILABLE"
        private set

    var sessionBaroMonoNs: Long = 0L
        private set

    fun onSample(
        pid: String,
        status: PidStatus,
        value: Double?,
        monoNs: Long,
        latestRpmValue: Double? = null,
        latestRpmStatus: PidStatus? = null,
        latestRpmMonoNs: Long? = null,
        nowNs: Long = monoNs
    ) {
        // 1. Valid PID 0133 always takes highest precedence (with plausibility check)
        if (pid == "0133" && status == PidStatus.VALID && value != null && value in 600.0..1200.0) {
            sessionBaroMbar = value
            sessionBaroSource = "PID_0133"
            sessionBaroMonoNs = monoNs
            return
        }

        // 2. Engine-off MAP baseline calibration strictly when RPM <= 50 and RPM is fresh
        if (pid == "010B" && status == PidStatus.VALID && value != null) {
            // Do not override an already valid PID_0133
            if (sessionBaroSource != "PID_0133") {
                val isRpmValid = latestRpmStatus == PidStatus.VALID && latestRpmValue != null
                val isRpmFresh = latestRpmMonoNs != null && (nowNs - latestRpmMonoNs).coerceAtLeast(0L) <= maxRpmAgeNsForCalib
                val isEngineOff = isRpmValid && isRpmFresh && (latestRpmValue != null && latestRpmValue <= engineOffRpmMax)

                if (isEngineOff && value in engineOffBaroMinMbar..engineOffBaroMaxMbar) {
                    sessionBaroMbar = value
                    sessionBaroSource = "ENGINE_OFF_MAP"
                    sessionBaroMonoNs = monoNs
                }
            }
        }
        // Note: TIMEOUT / NO DATA / ERROR does NOT erase sessionBaroMbar!
    }

    fun resolve(): BaroReading {
        val baro = sessionBaroMbar
        return if (baro != null && baro > 0.0) {
            BaroReading(baro, sessionBaroSource)
        } else {
            BaroReading(null, "UNAVAILABLE")
        }
    }

    fun reset() {
        sessionBaroMbar = null
        sessionBaroSource = "UNAVAILABLE"
        sessionBaroMonoNs = 0L
    }
}
