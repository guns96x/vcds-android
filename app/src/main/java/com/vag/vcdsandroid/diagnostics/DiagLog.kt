package com.vag.vcdsandroid.diagnostics

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistent, file-backed mirror of the connection/USB diagnostic log.
 *
 * logcat disappears on reboot and needs a live ADB tether to read; this
 * writes the same events to a file under the app's log directory so a
 * failed field connection can be diagnosed from the GitHub upload alone.
 * Keeps recent sessions across process restarts, including a crash before the
 * user has a chance to upload the file. Old content is trimmed at 1 MiB.
 */
object DiagLog {
    const val FILE_NAME = "connection_diagnostics.log"
    private const val MAX_FILE_BYTES = 1_048_576L
    private const val RETAIN_BYTES = 524_288
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    @Volatile private var logFile: File? = null

    @Synchronized
    fun init(context: Context) {
        if (logFile != null) {
            i("DIAG_LOG", "Activity recreated; continuing the same diagnostic session")
            return
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "VCDS_Logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, FILE_NAME)
        try {
            if (file.length() > MAX_FILE_BYTES) {
                val tail = ByteArray(RETAIN_BYTES)
                RandomAccessFile(file, "r").use { source ->
                    source.seek(source.length() - RETAIN_BYTES)
                    source.readFully(tail)
                }
                file.writeText("[older diagnostics trimmed]\n")
                file.appendBytes(tail)
            }
        } catch (_: Exception) {
        }
        logFile = file
        i("DIAG_LOG", "=== session start ${timeFmt.format(Date())} ===")
    }

    fun currentFile(): File? = logFile

    fun i(tag: String, msg: String) { write("I", tag, msg); Log.i(tag, msg) }
    fun w(tag: String, msg: String) { write("W", tag, msg); Log.w(tag, msg) }
    fun e(tag: String, msg: String) { write("E", tag, msg); Log.e(tag, msg) }

    @Synchronized
    private fun write(level: String, tag: String, msg: String) {
        val file = logFile ?: return
        try {
            file.appendText("${timeFmt.format(Date())} $level/$tag: $msg\n")
        } catch (_: Exception) {
        }
    }
}
