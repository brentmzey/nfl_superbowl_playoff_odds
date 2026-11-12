package com.gridiron.superbowl

import com.gridiron.superbowl.sync.PocketHostConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PocketHostConfigTest {
    private val dir = Files.createTempDirectory("env")

    @Test
    fun envBeatsDotEnvFilesAndEarlierFilesBeatLater() {
        val local = dir.resolve(".env").also {
            Files.writeString(it, "# comment\nexport POCKETHOST_URL=\"https://nfl.pockethost.io/\"\nPOCKETHOST_USER='local@x.io'\n\nbogus line\n")
        }
        val home = dir.resolve("home.env").also { Files.writeString(it, "POCKETHOST_USER=home@x.io\nPOCKETHOST_PASSWORD=from-home\n") }
        val cfg = PocketHostConfig.resolve(mapOf("POCKETHOST_PASSWORD" to "from-env"), listOf(local, home, dir.resolve("missing"))).getOrThrow()
        assertEquals(PocketHostConfig("https://nfl.pockethost.io", "local@x.io", "from-env"), cfg)
        assertFalse(cfg.toString().contains("from-env"))
    }

    @Test
    fun acceptsLegacyAdminNames() {
        val env = mapOf("POCKETHOST_URL" to "https://a.pockethost.io", "POCKETHOST_ADMIN_EMAIL" to "a@b.c", "POCKETHOST_ADMIN_PASSWORD" to "pw")
        assertEquals("a@b.c", PocketHostConfig.resolve(env, emptyList()).getOrThrow().user)
    }

    @Test
    fun acceptsPbAdminAliases() {
        val env = mapOf("POCKETHOST_URL" to "https://a.pockethost.io", "PB_ADMIN_EMAIL" to "pb@b.c", "PB_ADMIN_PASSWORD" to "pbpw")
        val cfg = PocketHostConfig.resolve(env, emptyList()).getOrThrow()
        assertEquals("pb@b.c", cfg.user)
        assertEquals("pbpw", cfg.password)
    }

    @Test
    fun expandsBashParameterSubstitutions() {
        val file = dir.resolve("sub.env").also {
            Files.writeString(
                it,
                """
                POCKETHOST_URL=${'$'}{HOST_URL:-https://default.pockethost.io}
                POCKETHOST_USER=${'$'}{PB_ADMIN_EMAIL?PB_ADMIN_EMAIL is required}
                POCKETHOST_PASSWORD=${'$'}{PB_ADMIN_PASSWORD:-fallback-pw}
                OTHER=${'$'}{UNKNOWN:-}
                """.trimIndent(),
            )
        }
        val env = mapOf("PB_ADMIN_EMAIL" to "user@domain.com")
        val parsed = PocketHostConfig.parseDotEnv(file, env)
        assertEquals("https://default.pockethost.io", parsed["POCKETHOST_URL"])
        assertEquals("user@domain.com", parsed["POCKETHOST_USER"])
        assertEquals("fallback-pw", parsed["POCKETHOST_PASSWORD"])
        assertEquals("", parsed["OTHER"])

        // Error if required substitution is unset
        val missingEnvFile = dir.resolve("missing_required.env").also {
            Files.writeString(it, "POCKETHOST_USER=${'$'}{MISSING_USER?User is required}\n")
        }
        val res = PocketHostConfig.resolve(emptyMap(), listOf(missingEnvFile))
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull()!!.message!!.contains("User is required"))
    }

    @Test
    fun missingSettingsFailFastWithoutLeakingValues() {
        val result = PocketHostConfig.resolve(mapOf("POCKETHOST_PASSWORD" to "hunter2", "POCKETHOST_USER" to " "), emptyList())
        val message = result.exceptionOrNull()!!.message!!
        assertTrue(message.contains("POCKETHOST_URL") && message.contains("POCKETHOST_USER"))
        assertFalse(message.contains("hunter2") || message.contains("POCKETHOST_PASSWORD"))
    }

    @Test
    fun eachMissingKeyIsReported() {
        val base = mapOf("POCKETHOST_URL" to "https://a.pockethost.io", "POCKETHOST_USER" to "u", "POCKETHOST_PASSWORD" to "p")
        base.keys.forEach { key ->
            val message = PocketHostConfig.resolve(base - key, emptyList()).exceptionOrNull()!!.message!!
            assertEquals("Missing PocketHost settings: $key (copy .env.example to .env)", message)
        }
    }
}
