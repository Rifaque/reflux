package dev.reflux.desktop.engine

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where the live Jellyfin test finds its server and test account: the environment first, then a file of the
 * developer's own outside the repository, `~/.reflux-live/jellyfin.env` (`%USERPROFILE%` on Windows), of
 * `KEY=value` lines. Test support only; Reflux itself never reads this file.
 *
 * Nothing read here is ever put into a message: errors name the file, the line, and the key, not a value.
 */
internal object LiveJellyfin {
    const val URL = "REFLUX_JELLYFIN_TEST"
    const val USER = "REFLUX_JELLYFIN_TEST_USER"
    const val PASSWORD = "REFLUX_JELLYFIN_TEST_PASSWORD"
    private val keys = listOf(URL, USER, PASSWORD)

    /** A server and account to test against. Its text form leaves all three out. */
    class Settings(val url: String, val user: String, val password: String) {
        override fun toString() = "LiveJellyfin.Settings(<redacted>)"
    }

    /** The configuration is there but cannot be used as written. */
    class ConfigurationError(message: String) : IllegalStateException(message)

    fun defaultFile(): Path = Paths.get(System.getProperty("user.home"), ".reflux-live", "jellyfin.env")

    /**
     * The settings, or null when no server is named anywhere (the live test is then skipped). Each key is
     * taken from the environment when it is set there, and from [file] otherwise.
     */
    fun settings(environment: (String) -> String? = System::getenv, file: Path = defaultFile()): Settings? {
        val written = if (Files.isRegularFile(file)) parse(Files.readAllLines(file), file.toString()) else emptyMap()
        val values = keys.associateWith { key -> environment(key)?.takeIf { it.isNotEmpty() } ?: written[key] }
        if (values[URL] == null) return null
        // A password may be empty; a server and a user may not.
        val missing = keys.filter { key -> values[key].let { it == null || (it.isEmpty() && key != PASSWORD) } }
        if (missing.isNotEmpty()) {
            throw ConfigurationError("${missing.joinToString()} not set: name the server, the test user, and the password, in the environment or in $file")
        }
        return Settings(values.getValue(URL)!!, values.getValue(USER)!!, values.getValue(PASSWORD)!!)
    }

    /** `KEY=value` lines; blank lines and `#` comments are allowed, and a value may be quoted. */
    internal fun parse(lines: List<String>, name: String): Map<String, String> {
        val values = mutableMapOf<String, String>()
        lines.forEachIndexed { index, raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val where = "$name, line ${index + 1}"
            val key = line.substringBefore('=', "").trim()
            if (key.isEmpty()) throw ConfigurationError("$where: expected KEY=value")
            if (key !in keys) throw ConfigurationError("$where: unknown key (expected one of ${keys.joinToString()})")
            if (key in values) throw ConfigurationError("$where: $key is set twice")
            values[key] = unquote(line.substringAfter('=').trim())
        }
        return values
    }

    private fun unquote(value: String): String =
        if (value.length >= 2 && value.first() == value.last() && value.first() in "\"'") value.substring(1, value.length - 1) else value
}
