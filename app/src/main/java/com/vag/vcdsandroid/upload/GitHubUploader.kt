package com.vag.vcdsandroid.upload

import android.util.Base64
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Uploads a finished log file to a GitHub repository through the Contents API.
 *
 * No credential is ever compiled in. The token comes from [GitHubSettings],
 * which the user fills in once from the app and which keeps it in Android's
 * encrypted preference store. The token needs only `Contents: write` on the one
 * repository it is scoped to — a fine-grained personal access token is enough
 * and is strongly preferred over a classic one.
 *
 * The parts that decide *what* gets sent are pure Kotlin, free of Android and of
 * `org.json`, so they are unit tested on the JVM without a device. Only [upload]
 * touches the network.
 */
object GitHubUploader {

    /** Outcome of an upload attempt, deliberately explicit rather than a boolean. */
    sealed class Result {
        data class Success(val path: String) : Result()
        data class Failure(val httpCode: Int, val message: String) : Result()
        data class NotConfigured(val missing: String) : Result()
    }

    private const val API_ROOT = "https://api.github.com"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000

    /**
     * Destination path inside the repository.
     *
     * Files are grouped by UTC date so a season of driving stays navigable, and
     * the original file name is preserved because it already carries the session
     * timestamp the analysis tooling keys on.
     */
    fun destinationPath(directory: String, fileName: String, atMillis: Long): String {
        val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date(atMillis))
        val cleanDir = directory.trim().trim('/')
        val prefix = if (cleanDir.isEmpty()) "" else "$cleanDir/"
        return "$prefix$day/${sanitiseFileName(fileName)}"
    }

    /**
     * Reduce a file name to characters GitHub handles predictably, rather than
     * discovering its path rules in the field with a log we cannot re-record.
     */
    fun sanitiseFileName(name: String): String {
        val cleaned = name.map { c ->
            when {
                c.isLetterOrDigit() -> c
                c == '.' || c == '-' || c == '_' -> c
                else -> '_'
            }
        }.joinToString("")
        return cleaned.ifEmpty { "log.csv" }
    }

    /** Commit message: short, and says where the data came from. */
    fun commitMessage(fileName: String, sizeBytes: Long): String =
        "log(vcds): $fileName ($sizeBytes B) from VCDS Mobile"

    /**
     * Request body for the Contents API. [existingSha] must be set to overwrite
     * an existing file; omitting it is how GitHub is told this is a new one.
     */
    fun requestBody(
        message: String,
        contentBase64: String,
        branch: String,
        existingSha: String?
    ): String {
        val fields = mutableListOf(
            "\"message\":" + jsonString(message),
            "\"content\":" + jsonString(contentBase64)
        )
        if (branch.isNotBlank()) fields += "\"branch\":" + jsonString(branch)
        if (!existingSha.isNullOrBlank()) fields += "\"sha\":" + jsonString(existingSha)
        return fields.joinToString(",", "{", "}")
    }

    /** Minimal JSON string escaping — enough for paths, messages and base64. */
    internal fun jsonString(value: String): String {
        val sb = StringBuilder()
        sb.append('"')
        for (c in value) {
            when (c) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c.code < 0x20) sb.append(String.format("\\u%04x", c.code)) else sb.append(c)
            }
        }
        sb.append('"')
        return sb.toString()
    }

    /**
     * Pulls the `message` field out of an error body without a JSON parser, so
     * failure reporting stays testable off-device. Empty when absent.
     */
    internal fun extractApiMessage(rawBody: String): String {
        val key = "\"message\""
        val at = rawBody.indexOf(key)
        if (at < 0) return ""
        val colon = rawBody.indexOf(':', at + key.length)
        if (colon < 0) return ""
        val open = rawBody.indexOf('"', colon + 1)
        if (open < 0) return ""
        val sb = StringBuilder()
        var i = open + 1
        while (i < rawBody.length) {
            val c = rawBody[i]
            if (c == '\\' && i + 1 < rawBody.length) {
                sb.append(rawBody[i + 1]); i += 2; continue
            }
            if (c == '"') break
            sb.append(c); i++
        }
        return sb.toString()
    }

    /** Turns GitHub's response codes into something actionable on a phone screen. */
    fun describeFailure(code: Int, rawBody: String): String {
        val apiMessage = extractApiMessage(rawBody)
        val hint = when (code) {
            401 -> "token rejected — check it has not expired"
            403 -> "forbidden — the token likely lacks Contents:write on this repository"
            404 -> "repository or branch not found — check owner/repo/branch, and that the token can see a private repo"
            409 -> "conflict — the branch moved, try again"
            422 -> "GitHub refused the content — often a path problem"
            else -> "HTTP $code"
        }
        return if (apiMessage.isBlank()) hint else "$hint: $apiMessage"
    }

    /** Sends one file. Blocking: call it from a background dispatcher. */
    fun upload(settings: GitHubSettings.Values, file: File): Result {
        settings.missingField()?.let { return Result.NotConfigured(it) }
        if (!file.exists()) return Result.Failure(0, "file no longer exists: ${file.name}")

        val path = destinationPath(settings.directory, file.name, System.currentTimeMillis())
        val endpoint = "$API_ROOT/repos/${settings.owner}/${settings.repo}/contents/$path"

        // A missing remote file is the normal case, so any lookup failure here
        // simply means "treat it as new" rather than aborting the upload.
        val existingSha = try {
            fetchExistingSha(endpoint, settings)
        } catch (e: Exception) {
            null
        }

        val body = requestBody(
            message = commitMessage(file.name, file.length()),
            contentBase64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP),
            branch = settings.branch,
            existingSha = existingSha
        )

        return try {
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                applyAuth(this, settings)
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.use { it.readBytes() }
                Result.Success(path)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                Result.Failure(code, describeFailure(code, err))
            }
        } catch (e: Exception) {
            Result.Failure(0, e.message ?: e.javaClass.simpleName)
        }
    }

    private fun fetchExistingSha(endpoint: String, settings: GitHubSettings.Values): String? {
        val url = if (settings.branch.isBlank()) endpoint else "$endpoint?ref=${settings.branch}"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            applyAuth(this, settings)
        }
        if (conn.responseCode != 200) return null
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return extractShaField(text).ifBlank { null }
    }

    /** Reads the top-level `sha` of a Contents API response. */
    internal fun extractShaField(rawBody: String): String {
        val key = "\"sha\""
        val at = rawBody.indexOf(key)
        if (at < 0) return ""
        val open = rawBody.indexOf('"', rawBody.indexOf(':', at + key.length) + 1)
        if (open < 0) return ""
        val close = rawBody.indexOf('"', open + 1)
        return if (close < 0) "" else rawBody.substring(open + 1, close)
    }

    private fun applyAuth(conn: HttpURLConnection, settings: GitHubSettings.Values) {
        conn.setRequestProperty("Authorization", "Bearer ${settings.token}")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        conn.setRequestProperty("User-Agent", "VCDS-Mobile")
    }
}
