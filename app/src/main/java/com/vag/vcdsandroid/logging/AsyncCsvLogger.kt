package com.vag.vcdsandroid.logging

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

sealed class AsyncLogRecord {
    data class RawEvent(
        val seq: Long,
        val utcMs: Long,
        val monoNs: Long,
        val pid: String,
        val requestCommand: String = pid,
        val value: Double?,
        val unit: String,
        val raw: String,
        val latencyMs: Long,
        val status: String
    ) : AsyncLogRecord()

    data class TurboPair(
        val seq: Long,
        val utcMs: Long,
        val monoNs: Long,
        val rpm: Double,
        val mapMbarAbs: Double,
        val baroMbar: Double?,
        val baroSource: String,
        val boostMbar: Double?,
        val boostBar: Double?,
        val dtMapRpmMs: Long,
        val pairValid: Boolean,
        val invalidReason: String,
        val mafGs: Double?,
        val mafAgeMs: Long? = null,
        val speedKmh: Double? = null,
        val speedAgeMs: Long? = null,
        val loadPct: Double? = null,
        val loadAgeMs: Long? = null,
        val coolantC: Double? = null,
        val coolantAgeMs: Long? = null,
        val iatC: Double? = null,
        val iatAgeMs: Long? = null,
        val voltageV: Double? = null,
        val voltageAgeMs: Long? = null,
        val latencyMs: Long
    ) : AsyncLogRecord()
}

class AsyncCsvLogger(private val context: Context) {

    companion object {
        private const val TAG = "ASYNC_CSV"
        private const val CHANNEL_CAPACITY = 2048
    }

    private val isLoggingActive = AtomicBoolean(false)
    private val writerHealthy = AtomicBoolean(false)
    @Volatile var lastWriterError: String? = null
        private set

    val isWriterHealthy: Boolean
        get() = writerHealthy.get() && writerJob?.isActive == true

    private val loggerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var channel: Channel<AsyncLogRecord>? = null
    private var writerJob: Job? = null

    private var rawFile: File? = null
    private var pairFile: File? = null
    private var rawWriter: BufferedWriter? = null
    private var pairWriter: BufferedWriter? = null

    private val rawSeq = AtomicLong(0L)
    private val pairSeq = AtomicLong(0L)
    private val rawRowsActuallyWritten = AtomicLong(0L)
    private val pairRowsActuallyWritten = AtomicLong(0L)
    private val droppedCount = AtomicLong(0L)
    private val pendingQueue = java.util.concurrent.atomic.AtomicInteger(0)

    var peakMapMbarAbs: Double = 0.0
        private set
    var peakBoostMbar: Double = 0.0
        private set
    var peakRpm: Double = 0.0
        private set

    fun clearWriterError() {
        lastWriterError = null
    }

    val isLogging: Boolean
        get() = isLoggingActive.get()

    val droppedRecords: Long
        get() = droppedCount.get()

    val queueSize: Int
        get() = pendingQueue.get().coerceAtLeast(0)

    val rowsWritten: Long
        get() = pairRowsActuallyWritten.get()

    val rawRowsWritten: Long
        get() = rawRowsActuallyWritten.get()

    val fileSizeBytes: Long
        get() = (rawFile?.length() ?: 0L) + (pairFile?.length() ?: 0L)

    val isDegraded: Boolean
        get() = droppedCount.get() > 0

    val currentRawFile: File?
        get() = rawFile

    val currentPairFile: File?
        get() = pairFile

    fun getLogsDirectory(): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "VCDS_Logs")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    @Synchronized
    fun startLogging(): Pair<File, File> {
        if (isLoggingActive.get()) {
            runBlocking(Dispatchers.IO) { stopLogging() }
        }

        val logsDir = getLogsDirectory()
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

        val rFile = File(logsDir, "Event_RAW_$timestamp.csv")
        val pFile = File(logsDir, "Turbo_Pair_$timestamp.csv")

        val rWriter = BufferedWriter(FileWriter(rFile, false), 16384)
        rWriter.write("event_seq,timestamp_utc_ms,mono_ns,pid,request,value,unit,raw,latency_ms,status\n")
        rWriter.flush()

        val pWriter = BufferedWriter(FileWriter(pFile, false), 16384)
        pWriter.write("pair_seq,timestamp_utc_ms,mono_ns,rpm,map_mbar_abs,baro_mbar,baro_source,boost_mbar,boost_bar,dt_map_rpm_ms,pair_valid,invalid_reason,maf_g_s,maf_age_ms,speed_kmh,speed_age_ms,load_pct,load_age_ms,coolant_c,coolant_age_ms,iat_c,iat_age_ms,voltage_v,voltage_age_ms,latency_ms\n")
        pWriter.flush()

        rawFile = rFile
        pairFile = pFile
        rawWriter = rWriter
        pairWriter = pWriter

        rawSeq.set(0L)
        pairSeq.set(0L)
        rawRowsActuallyWritten.set(0L)
        pairRowsActuallyWritten.set(0L)
        droppedCount.set(0L)
        pendingQueue.set(0)
        peakMapMbarAbs = 0.0
        peakBoostMbar = 0.0
        peakRpm = 0.0

        val chan = Channel<AsyncLogRecord>(
            capacity = CHANNEL_CAPACITY,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
            onUndeliveredElement = { record ->
                pendingQueue.decrementAndGet()
                droppedCount.incrementAndGet()
                Log.w(TAG, "Record evicted from channel queue: $record")
            }
        )
        channel = chan
        lastWriterError = null
        writerHealthy.set(true)
        isLoggingActive.set(true)

        val localRawWriter = rWriter
        val localPairWriter = pWriter

        writerJob = loggerScope.launch {
            var itemsWritten = 0
            try {
                for (record in chan) {
                    pendingQueue.decrementAndGet()
                    when (record) {
                        is AsyncLogRecord.RawEvent -> {
                            val valStr = if (record.value != null) String.format(Locale.US, "%.2f", record.value) else ""
                            val sanitizedRaw = "\"" + record.raw.replace("\r", " ").replace("\n", " ").replace("\"", "\"\"").trim() + "\""
                            val line = "${record.seq},${record.utcMs},${record.monoNs},${record.pid},${record.requestCommand},$valStr,${record.unit},$sanitizedRaw,${record.latencyMs},${record.status}\n"
                            localRawWriter.write(line)
                            rawRowsActuallyWritten.incrementAndGet()
                        }
                        is AsyncLogRecord.TurboPair -> {
                            val bMbarStr = if (record.boostMbar != null) String.format(Locale.US, "%.1f", record.boostMbar) else ""
                            val bBarStr = if (record.boostBar != null) String.format(Locale.US, "%.3f", record.boostBar) else ""
                            val baroStr = if (record.baroMbar != null) String.format(Locale.US, "%.1f", record.baroMbar) else ""
                            val mafStr = if (record.mafGs != null) String.format(Locale.US, "%.2f", record.mafGs) else ""
                            val mafAgeStr = record.mafAgeMs?.toString() ?: ""
                            val speedStr = if (record.speedKmh != null) String.format(Locale.US, "%.0f", record.speedKmh) else ""
                            val speedAgeStr = record.speedAgeMs?.toString() ?: ""
                            val loadStr = if (record.loadPct != null) String.format(Locale.US, "%.1f", record.loadPct) else ""
                            val loadAgeStr = record.loadAgeMs?.toString() ?: ""
                            val coolantStr = if (record.coolantC != null) String.format(Locale.US, "%.0f", record.coolantC) else ""
                            val coolantAgeStr = record.coolantAgeMs?.toString() ?: ""
                            val iatStr = if (record.iatC != null) String.format(Locale.US, "%.0f", record.iatC) else ""
                            val iatAgeStr = record.iatAgeMs?.toString() ?: ""
                            val voltStr = if (record.voltageV != null) String.format(Locale.US, "%.1f", record.voltageV) else ""
                            val voltAgeStr = record.voltageAgeMs?.toString() ?: ""
                            val line = String.format(
                                Locale.US,
                                "%d,%d,%d,%.0f,%.1f,%s,%s,%s,%s,%d,%b,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%d\n",
                                record.seq,
                                record.utcMs,
                                record.monoNs,
                                record.rpm,
                                record.mapMbarAbs,
                                baroStr,
                                record.baroSource,
                                bMbarStr,
                                bBarStr,
                                record.dtMapRpmMs,
                                record.pairValid,
                                record.invalidReason,
                                mafStr,
                                mafAgeStr,
                                speedStr,
                                speedAgeStr,
                                loadStr,
                                loadAgeStr,
                                coolantStr,
                                coolantAgeStr,
                                iatStr,
                                iatAgeStr,
                                voltStr,
                                voltAgeStr,
                                record.latencyMs
                            )
                            localPairWriter.write(line)
                            pairRowsActuallyWritten.incrementAndGet()
                        }
                    }
                    itemsWritten++
                    if (itemsWritten % 20 == 0) {
                        localRawWriter.flush()
                        localPairWriter.flush()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in async CSV writer: ${e.message}", e)
                lastWriterError = e.message ?: "Writer exception"
                writerHealthy.set(false)
                isLoggingActive.set(false)
            } finally {
                writerHealthy.set(false)
                try {
                    localRawWriter.flush()
                    localRawWriter.close()
                } catch (_: Exception) {}
                try {
                    localPairWriter.flush()
                    localPairWriter.close()
                } catch (_: Exception) {}
                Log.i(TAG, "Async CSV writer terminated. Total items processed: $itemsWritten")
            }
        }

        Log.i(TAG, "Async logging started: RAW='${rFile.name}', PAIR='${pFile.name}'")
        return Pair(rFile, pFile)
    }

    fun logRawEvent(
        pid: String,
        value: Double?,
        unit: String,
        raw: String,
        latencyMs: Long,
        status: String,
        txNanos: Long = 0L,
        rxNanos: Long = 0L,
        requestCommand: String = pid,
        utcTimestampMs: Long = 0L
    ) {
        if (!isLoggingActive.get()) return
        val chan = channel ?: return

        val seq = rawSeq.incrementAndGet()
        val utcMs = if (utcTimestampMs > 0L) utcTimestampMs else System.currentTimeMillis()
        val monoNs = if (rxNanos > 0L) rxNanos else SystemClock.elapsedRealtimeNanos()

        val record = AsyncLogRecord.RawEvent(
            seq = seq,
            utcMs = utcMs,
            monoNs = monoNs,
            pid = pid,
            requestCommand = requestCommand,
            value = value,
            unit = unit,
            raw = raw,
            latencyMs = latencyMs,
            status = status
        )

        val result = chan.trySend(record)
        if (result.isSuccess) {
            pendingQueue.incrementAndGet()
        } else {
            droppedCount.incrementAndGet()
            Log.w(TAG, "Channel trySend failed for RawEvent seq=$seq. Dropped count: ${droppedCount.get()}")
        }
    }

    fun logTurboPair(
        rpm: Double,
        mapMbarAbs: Double,
        baroMbar: Double?,
        baroSource: String,
        dtMapRpmMs: Long,
        pairValid: Boolean,
        invalidReason: String = "",
        mafGs: Double? = null,
        mafAgeMs: Long? = null,
        speedKmh: Double? = null,
        speedAgeMs: Long? = null,
        loadPct: Double? = null,
        loadAgeMs: Long? = null,
        coolantC: Double? = null,
        coolantAgeMs: Long? = null,
        iatC: Double? = null,
        iatAgeMs: Long? = null,
        voltageV: Double? = null,
        voltageAgeMs: Long? = null,
        latencyMs: Long = 0L,
        monoNs: Long = 0L
    ) {
        if (!isLoggingActive.get()) return
        val chan = channel ?: return

        val boostMbar = if (baroMbar != null && baroMbar > 0.0) mapMbarAbs - baroMbar else null
        val boostBar = if (boostMbar != null) boostMbar / 1000.0 else null

        if (mapMbarAbs > peakMapMbarAbs) peakMapMbarAbs = mapMbarAbs
        if (boostMbar != null && boostMbar > peakBoostMbar) peakBoostMbar = boostMbar
        if (rpm > peakRpm) peakRpm = rpm

        val seq = pairSeq.incrementAndGet()
        val utcMs = System.currentTimeMillis()
        val mNs = if (monoNs > 0L) monoNs else SystemClock.elapsedRealtimeNanos()

        val record = AsyncLogRecord.TurboPair(
            seq = seq,
            utcMs = utcMs,
            monoNs = mNs,
            rpm = rpm,
            mapMbarAbs = mapMbarAbs,
            baroMbar = baroMbar,
            baroSource = baroSource,
            boostMbar = boostMbar,
            boostBar = boostBar,
            dtMapRpmMs = dtMapRpmMs,
            pairValid = pairValid,
            invalidReason = invalidReason,
            mafGs = mafGs,
            mafAgeMs = mafAgeMs,
            speedKmh = speedKmh,
            speedAgeMs = speedAgeMs,
            loadPct = loadPct,
            loadAgeMs = loadAgeMs,
            coolantC = coolantC,
            coolantAgeMs = coolantAgeMs,
            iatC = iatC,
            iatAgeMs = iatAgeMs,
            voltageV = voltageV,
            voltageAgeMs = voltageAgeMs,
            latencyMs = latencyMs
        )

        val result = chan.trySend(record)
        if (result.isSuccess) {
            pendingQueue.incrementAndGet()
        } else {
            droppedCount.incrementAndGet()
            Log.w(TAG, "Channel trySend failed for TurboPair seq=$seq. Dropped count: ${droppedCount.get()}")
        }
    }

    suspend fun stopLogging(): Pair<File?, File?> = withContext(Dispatchers.IO) {
        writerHealthy.set(false)
        if (!isLoggingActive.compareAndSet(true, false)) return@withContext Pair(rawFile, pairFile)

        try {
            channel?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing channel: ${e.message}")
        }

        try {
            withTimeoutOrNull(3000L) {
                writerJob?.join()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception waiting for writer job: ${e.message}")
        }

        val r = rawFile
        val p = pairFile
        channel = null
        writerJob = null
        rawWriter = null
        pairWriter = null

        Pair(r, p)
    }
}
