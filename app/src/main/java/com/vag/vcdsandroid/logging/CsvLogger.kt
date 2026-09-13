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
        stopLog()

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "VCDS_WOT_Log_$timeStamp.csv"
        val logFile = File(getLogsDirectory(), fileName)

        val writer = BufferedWriter(FileWriter(logFile, false), 8192)
        writer.write("Timestamp_ms,RelativeTime_s,RPM,Boost_Specified_mbar,Boost_Actual_mbar,N75_Duty_pct,Driver_Wish_IQ_mg,Torque_Limit_IQ_mg,Smoke_Limit_IQ_mg,MAF_Actual_mg\n")
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

        val line = String.format(
            Locale.US,
            "%d,%.3f,%.0f,%.0f,%.0f,%.1f,%.1f,%.1f,%.1f,%.1f\n",
            now,
            relSec,
            rpm,
            boostSpecified,
            boostActual,
            n75Duty,
            driverWishIq,
            torqueLimitIq,
            smokeLimitIq,
            mafActual
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
