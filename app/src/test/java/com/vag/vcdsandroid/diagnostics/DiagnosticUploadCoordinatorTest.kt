package com.vag.vcdsandroid.diagnostics

import com.vag.vcdsandroid.upload.GitHubUploader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiagnosticUploadCoordinatorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `selectFilesForUpload includes diag log even when csv is missing`() {
        val diagFile = tempFolder.newFile("connection_diagnostics.log").apply {
            writeText("session start")
        }
        val selected = DiagnosticUploadCoordinator.selectFilesForUpload(diagFile, null)
        assertEquals(1, selected.size)
        assertEquals("connection_diagnostics.log", selected[0].name)
    }

    @Test
    fun `selectFilesForUpload includes both diag log and csv when both present`() {
        val diagFile = tempFolder.newFile("connection_diagnostics.log").apply {
            writeText("session start")
        }
        val csvFile = tempFolder.newFile("Turbo_Log_20260920.csv").apply {
            writeText("time,rpm,boost")
        }
        val selected = DiagnosticUploadCoordinator.selectFilesForUpload(diagFile, csvFile)
        assertEquals(2, selected.size)
        assertTrue(selected.any { it.name == "connection_diagnostics.log" })
        assertTrue(selected.any { it.name == "Turbo_Log_20260920.csv" })
    }

    @Test
    fun `selectFilesForUpload includes only csv when diag log is null or empty`() {
        val emptyDiag = tempFolder.newFile("connection_diagnostics.log") // 0 bytes
        val csvFile = tempFolder.newFile("Turbo_Log.csv").apply {
            writeText("data")
        }
        val selected = DiagnosticUploadCoordinator.selectFilesForUpload(emptyDiag, csvFile)
        assertEquals(1, selected.size)
        assertEquals("Turbo_Log.csv", selected[0].name)

        val selectedNull = DiagnosticUploadCoordinator.selectFilesForUpload(null, csvFile)
        assertEquals(1, selectedNull.size)
        assertEquals("Turbo_Log.csv", selectedNull[0].name)
    }

    @Test
    fun `selectFilesForUpload returns empty list when both are missing or empty`() {
        val emptyDiag = tempFolder.newFile("connection_diagnostics.log")
        val emptyCsv = tempFolder.newFile("empty.csv")
        assertTrue(DiagnosticUploadCoordinator.selectFilesForUpload(emptyDiag, emptyCsv).isEmpty())
        assertTrue(DiagnosticUploadCoordinator.selectFilesForUpload(null, null).isEmpty())
    }

    @Test
    fun `shouldAutoUpload accepts valid snapshot with configured settings after interval`() {
        val diagFile = tempFolder.newFile("connection_diagnostics.log").apply {
            writeText("connect failed: timeout")
        }
        val now = 100_000L
        val last = 50_000L
        assertTrue(
            DiagnosticUploadCoordinator.shouldAutoUpload(
                nowMs = now,
                lastUploadMs = last,
                isUploading = false,
                file = diagFile,
                hasMissingSettings = false,
                minIntervalMs = 20_000L
            )
        )
    }

    @Test
    fun `shouldAutoUpload skips unconfigured settings safely`() {
        val diagFile = tempFolder.newFile("connection_diagnostics.log").apply {
            writeText("connect failed: timeout")
        }
        assertFalse(
            "unconfigured settings must safely skip auto-upload",
            DiagnosticUploadCoordinator.shouldAutoUpload(
                nowMs = 100_000L,
                lastUploadMs = 50_000L,
                isUploading = false,
                file = diagFile,
                hasMissingSettings = true,
                minIntervalMs = 20_000L
            )
        )
    }

    @Test
    fun `shouldAutoUpload prevents upload storm during debounce window or concurrent upload`() {
        val diagFile = tempFolder.newFile("connection_diagnostics.log").apply {
            writeText("data")
        }
        // Case 1: In flight
        assertFalse(
            "must not upload if another upload is already in flight",
            DiagnosticUploadCoordinator.shouldAutoUpload(
                nowMs = 100_000L,
                lastUploadMs = 50_000L,
                isUploading = true,
                file = diagFile,
                hasMissingSettings = false
            )
        )

        // Case 2: Debounced
        assertFalse(
            "must not upload before minIntervalMs has elapsed",
            DiagnosticUploadCoordinator.shouldAutoUpload(
                nowMs = 55_000L,
                lastUploadMs = 50_000L,
                isUploading = false,
                file = diagFile,
                hasMissingSettings = false,
                minIntervalMs = 20_000L
            )
        )
    }

    @Test
    fun `formatUploadSummary reports single file outcome clearly`() {
        val f1 = File("connection_diagnostics.log")
        val success = DiagnosticUploadCoordinator.UploadResultItem(
            f1, GitHubUploader.Result.Success("logs/2026-09-20/connection_diagnostics.log")
        )
        val summarySuccess = DiagnosticUploadCoordinator.formatUploadSummary(listOf(success))
        assertTrue(summarySuccess.contains("connection_diagnostics.log"))
        assertTrue(summarySuccess.contains("logs/2026-09-20/connection_diagnostics.log"))

        val failure = DiagnosticUploadCoordinator.UploadResultItem(
            f1, GitHubUploader.Result.Failure(404, "Repository not found")
        )
        val summaryFailure = DiagnosticUploadCoordinator.formatUploadSummary(listOf(failure))
        assertTrue(summaryFailure.contains("connection_diagnostics.log"))
        assertTrue(summaryFailure.contains("Upload failed"))
        assertTrue(summaryFailure.contains("Repository not found"))
    }

    @Test
    fun `formatUploadSummary reports multi-file mixed outcomes identifying each file`() {
        val f1 = File("connection_diagnostics.log")
        val f2 = File("session.csv")
        val items = listOf(
            DiagnosticUploadCoordinator.UploadResultItem(
                f1, GitHubUploader.Result.Success("logs/2026-09-20/connection_diagnostics.log")
            ),
            DiagnosticUploadCoordinator.UploadResultItem(
                f2, GitHubUploader.Result.Failure(403, "Forbidden")
            )
        )
        val summary = DiagnosticUploadCoordinator.formatUploadSummary(items)
        assertTrue("must clearly show success for connection_diagnostics.log", summary.contains("Uploaded: connection_diagnostics.log"))
        assertTrue("must clearly show failure for session.csv", summary.contains("Failed: session.csv"))
    }
}
