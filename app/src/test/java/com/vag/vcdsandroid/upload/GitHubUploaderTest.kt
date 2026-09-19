package com.vag.vcdsandroid.upload

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the decisions the uploader makes before it touches the network:
 * where a file lands, what the request says, and how a refusal is explained.
 */
class GitHubUploaderTest {

    /** 2026-09-19T09:15:00Z */
    private val instant = 1789729200000L

    @Test
    fun `destination groups files by UTC date under the configured directory`() {
        val path = GitHubUploader.destinationPath(
            "logs/from-phone", "Turbo_Pair_20260919_091500.csv", instant
        )
        assertTrue("expected the configured directory as prefix", path.startsWith("logs/from-phone/"))
        assertTrue("expected a yyyy-MM-dd folder", path.contains(Regex("/\\d{4}-\\d{2}-\\d{2}/")))
        assertTrue("original file name must survive", path.endsWith("Turbo_Pair_20260919_091500.csv"))
    }

    @Test
    fun `destination tolerates stray slashes and an empty directory`() {
        assertEquals(
            GitHubUploader.destinationPath("logs", "a.csv", instant),
            GitHubUploader.destinationPath("/logs/", "a.csv", instant)
        )
        val bare = GitHubUploader.destinationPath("", "a.csv", instant)
        assertFalse("must not start with a slash", bare.startsWith("/"))
    }

    @Test
    fun `file names are reduced to characters GitHub handles predictably`() {
        assertEquals("VCDS_Log 2026.csv".let { GitHubUploader.sanitiseFileName(it) }, "VCDS_Log_2026.csv")
        assertEquals("a/b\\c.csv", "a_b_c.csv", GitHubUploader.sanitiseFileName("a/b\\c.csv"))
        assertEquals("log.csv", GitHubUploader.sanitiseFileName(""))
    }

    @Test
    fun `new file request omits sha, overwrite includes it`() {
        val fresh = GitHubUploader.requestBody("msg", "Y29udGVudA==", "main", null)
        assertFalse("a new file must not claim a sha", fresh.contains("\"sha\""))
        assertTrue(fresh.contains("\"branch\":\"main\""))
        assertTrue(fresh.contains("\"content\":\"Y29udGVudA==\""))

        val overwrite = GitHubUploader.requestBody("msg", "Y29udGVudA==", "main", "abc123")
        assertTrue(overwrite.contains("\"sha\":\"abc123\""))
    }

    @Test
    fun `blank branch is left out so the repository default applies`() {
        val body = GitHubUploader.requestBody("m", "Yg==", "", null)
        assertFalse(body.contains("branch"))
    }

    @Test
    fun `commit message names the file and its size`() {
        val msg = GitHubUploader.commitMessage("Turbo_Pair_1.csv", 2048)
        assertTrue(msg.contains("Turbo_Pair_1.csv"))
        assertTrue(msg.contains("2048"))
    }

    @Test
    fun `failures are explained in terms the driver can act on`() {
        assertTrue(
            GitHubUploader.describeFailure(403, """{"message":"Resource not accessible"}""")
                .contains("Contents:write")
        )
        assertTrue(
            GitHubUploader.describeFailure(404, "{}").contains("not found")
        )
        assertTrue(
            "the API message should be surfaced, not swallowed",
            GitHubUploader.describeFailure(401, """{"message":"Bad credentials"}""")
                .contains("Bad credentials")
        )
    }

    @Test
    fun `settings report the first missing field`() {
        fun values(token: String = "t", owner: String = "o", repo: String = "r") =
            GitHubSettings.Values(token, owner, repo, "main", "logs")

        assertEquals("token", values(token = "").missingField())
        assertEquals("owner", values(owner = "").missingField())
        assertEquals("repository", values(repo = "").missingField())
        assertNull(values().missingField())
    }

    @Test
    fun `a token is never shown in full`() {
        val secret = "ghp_abcdefghijklmnopqrstuvwxyz"
        val masked = GitHubSettings.maskToken(secret)
        assertFalse("the masked form must not contain the secret", masked.contains("mnopqrstuv"))
        assertTrue(masked.startsWith("ghp_"))
        assertEquals("(not set)", GitHubSettings.maskToken(""))
    }
}
