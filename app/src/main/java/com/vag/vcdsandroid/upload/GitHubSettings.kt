package com.vag.vcdsandroid.upload

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Where the GitHub destination and its access token live.
 *
 * The token is a credential, so it goes into [EncryptedSharedPreferences],
 * backed by a key in the Android keystore. It is never written to a log, never
 * shown back in full, and never compiled into the apk — the user types it once.
 *
 * If the encrypted store cannot be opened on a given device, the settings fall
 * back to plain preferences **for the non-secret fields only** and the token is
 * kept in memory for that session. Silently downgrading a credential's storage
 * would be worse than making the user re-enter it.
 */
class GitHubSettings(context: Context) {

    data class Values(
        val token: String,
        val owner: String,
        val repo: String,
        val branch: String,
        val directory: String
    ) {
        /** Name of the first field that is missing, or null when usable. */
        fun missingField(): String? = when {
            token.isBlank() -> "token"
            owner.isBlank() -> "owner"
            repo.isBlank() -> "repository"
            else -> null
        }
    }

    private val appContext = context.applicationContext
    private var sessionOnlyToken: String? = null

    private val prefs: SharedPreferences = try {
        val key = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            ENCRYPTED_FILE,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "Encrypted preferences unavailable, token will not be persisted", e)
        encryptedStoreAvailable = false
        appContext.getSharedPreferences(PLAIN_FILE, Context.MODE_PRIVATE)
    }

    fun load(): Values = Values(
        token = if (encryptedStoreAvailable) prefs.getString(KEY_TOKEN, "").orEmpty()
        else sessionOnlyToken.orEmpty(),
        owner = prefs.getString(KEY_OWNER, DEFAULT_OWNER).orEmpty(),
        repo = prefs.getString(KEY_REPO, DEFAULT_REPO).orEmpty(),
        branch = prefs.getString(KEY_BRANCH, DEFAULT_BRANCH).orEmpty(),
        directory = prefs.getString(KEY_DIR, DEFAULT_DIR).orEmpty()
    )

    fun save(values: Values) {
        prefs.edit().apply {
            putString(KEY_OWNER, values.owner.trim())
            putString(KEY_REPO, values.repo.trim())
            putString(KEY_BRANCH, values.branch.trim())
            putString(KEY_DIR, values.directory.trim())
            if (encryptedStoreAvailable) {
                putString(KEY_TOKEN, values.token.trim())
            }
        }.apply()
        if (!encryptedStoreAvailable) {
            sessionOnlyToken = values.token.trim()
        }
    }

    /** True when the token survives an app restart on this device. */
    fun tokenIsPersisted(): Boolean = encryptedStoreAvailable

    companion object {
        private const val TAG = "GitHubSettings"
        private const val ENCRYPTED_FILE = "github_upload_secure"
        private const val PLAIN_FILE = "github_upload_plain"

        private const val KEY_TOKEN = "token"
        private const val KEY_OWNER = "owner"
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        private const val KEY_DIR = "directory"

        // Defaults point at this project's own log folder so the common case
        // needs only the token.
        const val DEFAULT_OWNER = "guns96x"
        const val DEFAULT_REPO = "golf5-ecu-system"
        const val DEFAULT_BRANCH = "main"
        const val DEFAULT_DIR = "logs/from-phone"

        @Volatile
        private var encryptedStoreAvailable: Boolean = true

        /** Shows only enough of a token to recognise it, never the secret part. */
        fun maskToken(token: String): String = when {
            token.isBlank() -> "(not set)"
            token.length <= 8 -> "*".repeat(token.length)
            else -> token.take(4) + "…" + token.takeLast(4)
        }
    }
}
