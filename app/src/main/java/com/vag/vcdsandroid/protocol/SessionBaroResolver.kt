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
    @Volatile
    private var pid0133Value: Double? = null

    @Volatile
    private var engineOffMapValue: Double? = null

    @Volatile
    private var phoneBaroValue: Double? = null

    @Volatile
    private var phoneBaroFresh: Boolean = false

    @Volatile
    private var phoneBaroMonoNs: Long = 0L

    @Synchronized
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
        // 1. Valid PID 0133 takes precedence (with plausibility check)
        if (pid == "0133" && status == PidStatus.VALID && value != null && value in 600.0..1200.0) {
            pid0133Value = value
            return
        }

        // 2. Engine-off MAP baseline calibration strictly when RPM <= 50 and RPM is fresh
        if (pid == "010B" && status == PidStatus.VALID && value != null) {
            val isRpmValid = latestRpmStatus == PidStatus.VALID && latestRpmValue != null
            val isRpmFresh = latestRpmMonoNs != null && (nowNs - latestRpmMonoNs).coerceAtLeast(0L) <= maxRpmAgeNsForCalib
            val isEngineOff = isRpmValid && isRpmFresh && (latestRpmValue != null && latestRpmValue <= engineOffRpmMax)

            if (isEngineOff && value in engineOffBaroMinMbar..engineOffBaroMaxMbar) {
                engineOffMapValue = value
            }
        }
        // Note: Transient TIMEOUT / NO DATA / ERROR does NOT erase cached candidates
    }

    @Synchronized
    fun onPhoneBaro(valueMbar: Double?, monoNs: Long, fresh: Boolean) {
        if (valueMbar != null && valueMbar in 800.0..1100.0) {
            phoneBaroValue = valueMbar
            phoneBaroFresh = fresh
            phoneBaroMonoNs = monoNs
        } else {
            phoneBaroFresh = false
            if (valueMbar == null) {
                phoneBaroValue = null
            }
        }
    }

    @Synchronized
    fun resolve(nowNs: Long = 0L): BaroReading {
        // Priority 1: PID_0133
        pid0133Value?.let {
            if (it in 600.0..1200.0) return BaroReading(it, "PID_0133")
        }

        // Priority 2: ENGINE_OFF_MAP
        engineOffMapValue?.let {
            if (it in engineOffBaroMinMbar..engineOffBaroMaxMbar) return BaroReading(it, "ENGINE_OFF_MAP")
        }

        // Priority 3: PHONE_BAROMETER
        // Phone BARO MUST be strictly evaluated for freshness against nowNs (<= 5 seconds)
        // If caller omits nowNs (nowNs <= 0), phone BARO cannot be verified fresh and is rejected
        val isPhoneFresh = if (nowNs > 0L && phoneBaroMonoNs > 0L) {
            phoneBaroFresh && ((nowNs - phoneBaroMonoNs).coerceAtLeast(0L) <= 5_000_000_000L)
        } else {
            false
        }

        if (isPhoneFresh && phoneBaroValue != null && phoneBaroValue!! in 800.0..1100.0) {
            return BaroReading(phoneBaroValue, "PHONE_BAROMETER")
        }

        // Priority 4: UNAVAILABLE
        return BaroReading(null, "UNAVAILABLE")
    }

    @Synchronized
    fun reset() {
        pid0133Value = null
        engineOffMapValue = null
        phoneBaroValue = null
        phoneBaroFresh = false
        phoneBaroMonoNs = 0L
    }
}
