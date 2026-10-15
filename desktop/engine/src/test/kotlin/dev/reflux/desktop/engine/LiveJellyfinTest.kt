package dev.reflux.desktop.engine

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Where the live Jellyfin test finds its server, without ever repeating what it found. */
class LiveJellyfinTest {
    private val directory: Path = Files.createTempDirectory("reflux-live-config").also { it.toFile().deleteOnExit() }
    private val file: Path = directory.resolve("jellyfin.env")
    private val nothing: (String) -> String? = { null }

    private fun written(vararg lines: String) = file.also { it.writeText(lines.joinToString("\n")) }

    @Test
    fun theEnvironmentAlone() {
        val environment = mapOf(LiveJellyfin.URL to "http://env.test:8096", LiveJellyfin.USER to "env-user", LiveJellyfin.PASSWORD to "env-secret")
        val settings = assertNotNull(LiveJellyfin.settings(environment::get, directory.resolve("absent.env")))
        assertEquals(listOf("http://env.test:8096", "env-user", "env-secret"), listOf(settings.url, settings.user, settings.password))
    }

    @Test
    fun theFileAlone() {
        written(
            "# the developer's own test server",
            "",
            "REFLUX_JELLYFIN_TEST=http://file.test:8096",
            "REFLUX_JELLYFIN_TEST_USER = file-user",
            "REFLUX_JELLYFIN_TEST_PASSWORD=\"pass=word with spaces\"\r",
        )
        val settings = assertNotNull(LiveJellyfin.settings(nothing, file))
        assertEquals(listOf("http://file.test:8096", "file-user", "pass=word with spaces"), listOf(settings.url, settings.user, settings.password))
    }

    @Test
    fun theEnvironmentOverridesTheFileKeyByKey() {
        written("REFLUX_JELLYFIN_TEST=http://file.test:8096", "REFLUX_JELLYFIN_TEST_USER=file-user", "REFLUX_JELLYFIN_TEST_PASSWORD=file-secret")
        val environment = mapOf(LiveJellyfin.URL to "http://env.test:8096", LiveJellyfin.PASSWORD to "env-secret", LiveJellyfin.USER to "")
        val settings = assertNotNull(LiveJellyfin.settings(environment::get, file))
        // An empty variable counts as unset.
        assertEquals(listOf("http://env.test:8096", "file-user", "env-secret"), listOf(settings.url, settings.user, settings.password))
    }

    @Test
    fun noFileAndNoEnvironmentSkips() {
        assertNull(LiveJellyfin.settings(nothing, directory.resolve("absent.env")))
        // A file that names no server skips as well, as it did before there was a file.
        assertNull(LiveJellyfin.settings(nothing, written("# not set up yet")))
    }

    @Test
    fun aMalformedLineIsAConfigurationErrorThatRepeatsNothing() {
        written("REFLUX_JELLYFIN_TEST=http://file.test:8096", "hunter2-on-a-line-of-its-own")
        val error = assertFailsWith<LiveJellyfin.ConfigurationError> { LiveJellyfin.settings(nothing, file) }
        assertTrue("line 2" in error.message!! && file.toString() in error.message!!, error.message)
        assertFalse("hunter2" in error.message!! || "file.test" in error.message!!, error.message)

        written("REFLUX_JELLYFIN_TEST_PASWORD=s3cret-value")
        val typo = assertFailsWith<LiveJellyfin.ConfigurationError> { LiveJellyfin.settings(nothing, file) }
        assertTrue("unknown key" in typo.message!!)
        assertFalse("s3cret" in typo.message!! || "PASWORD" in typo.message!!, typo.message)
    }

    @Test
    fun aMissingKeyIsNamedButNoValueIs() {
        written("REFLUX_JELLYFIN_TEST=http://file.test:8096", "REFLUX_JELLYFIN_TEST_PASSWORD=s3cret-value")
        val error = assertFailsWith<LiveJellyfin.ConfigurationError> { LiveJellyfin.settings(nothing, file) }
        assertTrue(LiveJellyfin.USER in error.message!!, error.message)
        assertFalse("s3cret" in error.message!! || "file.test" in error.message!!, error.message)
    }

    @Test
    fun settingsNeverPrintThemselves() {
        val settings = LiveJellyfin.Settings("http://file.test:8096", "file-user", "s3cret-value")
        assertFalse(listOf("file.test", "file-user", "s3cret").any { it in settings.toString() })
    }
}
