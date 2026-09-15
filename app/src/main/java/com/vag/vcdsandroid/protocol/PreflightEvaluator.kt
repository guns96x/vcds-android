package com.vag.vcdsandroid.protocol

enum class PreflightVerdict {
    GREEN,  // All 6 core + aux channels valid (RPM, MAP, BARO, MAF, Speed, Load)
    AMBER,  // Core (RPM, MAP, BARO) valid, but some aux (MAF, Speed, Load) missing
    RED     // Core (RPM, MAP, or BARO) missing or invalid
}

data class PreflightReport(
    val verdict: PreflightVerdict,
    val rpmOk: Boolean,
    val mapOk: Boolean,
    val baroOk: Boolean,
    val baroValueMbar: Double?,
    val baroSource: String,
    val mafOk: Boolean,
    val speedOk: Boolean,
    val loadOk: Boolean,
    val coolantOk: Boolean,
    val iatOk: Boolean,
    val voltOk: Boolean,
    val voltSource: String,
    val missingAuxChannels: List<String>,
    val failureReason: String = ""
)

object PreflightEvaluator {
    fun evaluate(
        rpmOk: Boolean,
        mapOk: Boolean,
        baroReading: BaroReading,
        mafOk: Boolean,
        speedOk: Boolean,
        loadOk: Boolean,
        coolantOk: Boolean,
        iatOk: Boolean,
        voltOk: Boolean,
        voltSource: String = "0142"
    ): PreflightReport {
        val baroOk = baroReading.valueMbar != null
        val coreOk = rpmOk && mapOk && baroOk

        val missingAux = mutableListOf<String>()
        if (!mafOk) missingAux.add("MAF")
        if (!speedOk) missingAux.add("SPEED")
        if (!loadOk) missingAux.add("LOAD")

        val verdict: PreflightVerdict
        val failureReason: String

        if (!coreOk) {
            verdict = PreflightVerdict.RED
            failureReason = when {
                !rpmOk && !mapOk -> "RPM & MAP TIMEOUT/FAIL"
                !rpmOk -> "RPM TIMEOUT/FAIL"
                !mapOk -> "MAP TIMEOUT/FAIL"
                !baroOk -> "NO BARO BASELINE (need engine-off MAP or 0133)"
                else -> "CORE TELEMETRY INCOMPLETE"
            }
        } else if (missingAux.isNotEmpty()) {
            verdict = PreflightVerdict.AMBER
            failureReason = "AUX MISSING: " + missingAux.joinToString(", ")
        } else {
            verdict = PreflightVerdict.GREEN
            failureReason = ""
        }

        return PreflightReport(
            verdict = verdict,
            rpmOk = rpmOk,
            mapOk = mapOk,
            baroOk = baroOk,
            baroValueMbar = baroReading.valueMbar,
            baroSource = baroReading.source,
            mafOk = mafOk,
            speedOk = speedOk,
            loadOk = loadOk,
            coolantOk = coolantOk,
            iatOk = iatOk,
            voltOk = voltOk,
            voltSource = voltSource,
            missingAuxChannels = missingAux,
            failureReason = failureReason
        )
    }
}
