package com.vag.vcdsandroid.diagnostics

import com.vag.vcdsandroid.upload.GitHubUploader
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pure Kotlin coordinator for diagnostic and log upload decisions.
 * Decoupled from Android UI classes so that debounce logic, file selection,
 * and result formatting can be verified in off-device unit tests.
 */
object DiagnosticUploadCoordinator {
    const val DEFAULT_MIN_UPLOAD_INTERVAL_MS = 20_000L

    /** Timestamp of the last auto-upload attempt, surviving Activity recreations in the same process. */
    @Volatile
    var lastAutoUploadAtMs = 0L

    /** Mutual exclusion flag ensuring only one auto-upload job runs at a time. */
    val isAutoUploadInProgress = AtomicBoolean(false)

    /**
     * Selects files to upload during a manual GitHub upload.
     * Evaluates [diagLog] and [latestCsv], returning all valid, non-empty files.
     */
    fun selectFilesForUpload(diagLog: File?, latestCsv: File?): List<File> {
        val list = mutableListOf<File>()
        val validDiag = diagLog?.takeIf { it.exists() && it.length() > 0 }
        val validCsv = latestCsv?.takeIf { it.exists() && it.length() > 0 }

        if (validDiag != null) {
            list.add(validDiag)
        }
        if (validCsv != null && (validDiag == null || validCsv.canonicalPath != validDiag.canonicalPath)) {
            list.add(validCsv)
        }
        return list
    }

    /**
     * Determines whether an automatic failure upload should proceed.
     * Prevents upload storms and avoids crashing on unconfigured settings.
     */
    fun shouldAutoUpload(
        nowMs: Long,
        lastUploadMs: Long,
        isUploading: Boolean,
        file: File?,
        hasMissingSettings: Boolean,
        minIntervalMs: Long = DEFAULT_MIN_UPLOAD_INTERVAL_MS
    ): Boolean {
        if (isUploading) return false
        if (hasMissingSettings) return false
        if (file == null || !file.exists() || file.length() == 0L) return false
        if (nowMs - lastUploadMs < minIntervalMs) return false
        return true
    }

    data class UploadResultItem(val file: File, val result: GitHubUploader.Result)

    /**
     * Formats a clear status string explaining which files succeeded and which failed.
     */
    fun formatUploadSummary(items: List<UploadResultItem>): String {
        if (items.isEmpty()) return "No files uploaded."

        if (items.size == 1) {
            val item = items[0]
            return when (val res = item.result) {
                is GitHubUploader.Result.Success -> "${item.file.name}: Uploaded to ${res.path}"
                is GitHubUploader.Result.Failure -> "${item.file.name}: Upload failed (${res.message})"
                is GitHubUploader.Result.NotConfigured -> "${item.file.name}: Missing ${res.missing}"
            }
        }

        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (item in items) {
            when (val res = item.result) {
                is GitHubUploader.Result.Success -> succeeded.add(item.file.name)
                is GitHubUploader.Result.Failure -> failed.add("${item.file.name} (${res.message})")
                is GitHubUploader.Result.NotConfigured -> failed.add("${item.file.name} (missing ${res.missing})")
            }
        }

        return when {
            failed.isEmpty() -> "Uploaded ${succeeded.size} files: ${succeeded.joinToString(", ")}"
            succeeded.isEmpty() -> "Upload failed for ${failed.size} files: ${failed.joinToString("; ")}"
            else -> "Uploaded: ${succeeded.joinToString(", ")}; Failed: ${failed.joinToString("; ")}"
        }
    }
}
