package com.vag.vcdsandroid.logging

import android.content.Context
import android.os.Environment
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-performance CSV logger for recording VAG Measuring Blocks (003, 008, 011)
 * during 4th gear WOT pulls.
 */
class CsvLogger(private val context: Context) {

    private var currentWriter: BufferedWriter? = null
    private var currentFile: File? = null
    private var startTimestampMs: Long = 0
    private var sampleCount: Int = 0

    // Live pull metrics
    var peakBoostMbar: Double = 0.0
        private set
    var peakRpm: Double = 0.0
        private set
    var isLogging: Boolean = false
        private set

    fun getLogsDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "VCDS_Logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun startNewLog(): File {
        return startTurboFastLog()
    }

    fun startTurboFastLog(): File {
        stopLog()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Turbo_Fast_Log_$timeStamp.csv"
        val logFile = File(getLogsDirectory(), fileName)

        val writer = BufferedWriter(FileWriter(logFile, false), 8192)
        writer.write("utc_ms,mono_ms,rpm,map_mbar_abs,baro_mbar,baro_source,boost_mbar,boost_bar,pair_skew_ms,maf_g_s,latency_ms\n")
        writer.flush()

        currentWriter = writer
        currentFile = logFile
        startTimestampMs = System.currentTimeMillis()
        sampleCount = 0
        peakBoostMbar = 0.0
        peakRpm = 0.0
        isLogging = true

        return logFile
    }

    fun startVagOemLog(): File {
        stopLog()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VAG_OEM_Log_$timeStamp.csv"
        val logFile = File(getLogsDirectory(), fileName)

        val writer = BufferedWriter(FileWriter(logFile, false), 8192)
        writer.write("timestamp_ms,rel_sec,group_type,rpm,boost_spec_mbar,boost_act_mbar,n75_pct,driver_wish_mg,torque_limit_mg,smoke_limit_mg,maf_act_mg\n")
        writer.flush()

        currentWriter = writer
        currentFile = logFile
        startTimestampMs = System.currentTimeMillis()
        sampleCount = 0
        peakBoostMbar = 0.0
        peakRpm = 0.0
        isLogging = true

        return logFile
    }

    fun logTurboFastPair(
        utcMs: Long,
        monoMs: Long,
        rpm: Double,
        mapMbarAbs: Double,
        baroMbar: Double?,
        baroSource: String,
        pairSkewMs: Long,
        mafGs: Double?,
        latencyMs: Long
    ) {
        val writer = currentWriter ?: return
        if (!isLogging) return

        if (mapMbarAbs > peakBoostMbar) peakBoostMbar = mapMbarAbs
        if (rpm > peakRpm) peakRpm = rpm

        val baroStr = if (baroMbar != null && baroMbar > 0.0) String.format(Locale.US, "%.1f", baroMbar) else ""
        val boostMbarStr = if (baroMbar != null && baroMbar > 0.0) String.format(Locale.US, "%.1f", mapMbarAbs - baroMbar) else ""
        val boostBarStr = if (baroMbar != null && baroMbar > 0.0) String.format(Locale.US, "%.3f", (mapMbarAbs - baroMbar) / 1000.0) else ""
        val mafStr = if (mafGs != null && mafGs > 0.0) String.format(Locale.US, "%.2f", mafGs) else ""

        val line = String.format(
            Locale.US,
            "%d,%d,%.0f,%.1f,%s,%s,%s,%s,%d,%s,%d\n",
            utcMs,
            monoMs,
            rpm,
            mapMbarAbs,
            baroStr,
            baroSource,
            boostMbarStr,
            boostBarStr,
            pairSkewMs,
            mafStr,
            latencyMs
        )

        try {
            writer.write(line)
            sampleCount++
            if (sampleCount % 5 == 0) {
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logVagOemSample(
        timestampMs: Long,
        groupType: String,
        rpm: Double,
        boostSpec: Double?,
        boostAct: Double?,
        n75: Double?,
        driverWish: Double?,
        torqueLim: Double?,
        smokeLim: Double?,
        mafAct: Double?
    ) {
        val writer = currentWriter ?: return
        if (!isLogging) return

        if (boostAct != null && boostAct > peakBoostMbar) peakBoostMbar = boostAct
        if (rpm > peakRpm) peakRpm = rpm

        val relSec = (timestampMs - startTimestampMs) / 1000.0
        val bSpecStr = if (boostSpec != null) String.format(Locale.US, "%.0f", boostSpec) else ""
        val bActStr = if (boostAct != null) String.format(Locale.US, "%.0f", boostAct) else ""
        val n75Str = if (n75 != null) String.format(Locale.US, "%.1f", n75) else ""
        val dwStr = if (driverWish != null) String.format(Locale.US, "%.1f", driverWish) else ""
        val tlStr = if (torqueLim != null) String.format(Locale.US, "%.1f", torqueLim) else ""
        val slStr = if (smokeLim != null) String.format(Locale.US, "%.1f", smokeLim) else ""
        val mafStr = if (mafAct != null) String.format(Locale.US, "%.2f", mafAct) else ""

        val line = String.format(
            Locale.US,
            "%d,%.3f,%s,%.0f,%s,%s,%s,%s,%s,%s,%s\n",
            timestampMs, relSec, groupType, rpm, bSpecStr, bActStr, n75Str, dwStr, tlStr, slStr, mafStr
        )

        try {
            writer.write(line)
            sampleCount++
            if (sampleCount % 5 == 0) {
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun logSample(
        rpm: Double,
        boostSpecified: Double,
        boostActual: Double,
        n75Duty: Double,
        driverWishIq: Double,
        torqueLimitIq: Double,
        smokeLimitIq: Double,
        mafActual: Double
    ) {
        val writer = currentWriter ?: return
        if (!isLogging) return

        val now = System.currentTimeMillis()
        val relSec = (now - startTimestampMs) / 1000.0

        if (boostActual > peakBoostMbar) peakBoostMbar = boostActual
        if (rpm > peakRpm) peakRpm = rpm

        val reqStr = if (boostSpecified.isNaN() || boostSpecified < 0.0) "N/A" else String.format(Locale.US, "%.0f", boostSpecified)
        val actStr = if (boostActual.isNaN() || boostActual < 0.0) "N/A" else String.format(Locale.US, "%.0f", boostActual)
        val n75Str = if (n75Duty.isNaN() || n75Duty < 0.0) "N/A" else String.format(Locale.US, "%.1f", n75Duty)
        val dwStr = if (driverWishIq.isNaN() || driverWishIq < 0.0) "N/A" else String.format(Locale.US, "%.1f", driverWishIq)
        val tlStr = if (torqueLimitIq.isNaN() || torqueLimitIq < 0.0) "N/A" else String.format(Locale.US, "%.1f", torqueLimitIq)
        val slStr = if (smokeLimitIq.isNaN() || smokeLimitIq < 0.0) "N/A" else String.format(Locale.US, "%.1f", smokeLimitIq)
        val mafStr = if (mafActual.isNaN() || mafActual < 0.0) "N/A" else String.format(Locale.US, "%.2f", mafActual)

        val line = String.format(
            Locale.US,
            "%d,%.3f,%.0f,%s,%s,%s,%s,%s,%s,%s\n",
            now,
            relSec,
            rpm,
            reqStr,
            actStr,
            n75Str,
            dwStr,
            tlStr,
            slStr,
            mafStr
        )

        try {
            writer.write(line)
            sampleCount++
            // Flush periodically or on every write for diagnostic integrity
            if (sampleCount % 5 == 0) {
                writer.flush()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopLog(): File? {
        if (!isLogging) return null
        isLogging = false
        try {
            currentWriter?.flush()
            currentWriter?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        val file = currentFile
        currentWriter = null
        currentFile = null
        return file
    }

    fun getSampleCount(): Int = sampleCount

    fun getDurationSeconds(): Double {
        if (startTimestampMs == 0L) return 0.0
        return (System.currentTimeMillis() - startTimestampMs) / 1000.0
    }

    fun listExistingLogs(): List<File> {
        val dir = getLogsDirectory()
        return dir.listFiles { _, name -> name.endsWith(".csv") }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
