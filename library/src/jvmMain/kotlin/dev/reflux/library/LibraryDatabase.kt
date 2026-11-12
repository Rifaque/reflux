package dev.reflux.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reflux.library.db.RefluxDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.Properties

/**
 * The library at [file] was written by a newer Reflux than this one ([found] is its schema, [supported] the newest
 * this build understands). It is not opened: nothing in it is read as something else, migrated, or changed.
 */
class NewerLibraryException(val file: Path, val found: Long, val supported: Long) : IllegalStateException(
    "This library was created by a newer version of Reflux (library format $found; this version understands up to $supported). " +
        "This version cannot open it safely, and has left it untouched. Install the newer Reflux to use it.",
)

/** Opens the library database on desktop JVMs. Creates or migrates the schema as needed. */
object LibraryDatabase {
    /**
     * A file-backed database; parent directories are created. An older library is migrated; one from a newer
     * Reflux is refused ([NewerLibraryException]) before anything is written to it, as Android refuses a downgrade.
     */
    fun open(file: Path): RefluxDatabase {
        file.toAbsolutePath().parent?.let(Files::createDirectories)
        refuseNewer(file)
        val properties = Properties().apply {
            // WAL keeps the UI responsive while scans write.
            setProperty("journal_mode", "WAL")
            setProperty("synchronous", "NORMAL")
            setProperty("busy_timeout", "5000")
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", properties, RefluxDatabase.Schema)
        return RefluxDatabase(driver)
    }

    /** The schema version this build writes and understands. */
    val schemaVersion: Long get() = RefluxDatabase.Schema.version

    /** Reads the file's schema version without changing the file, and throws when it is newer than this build's. */
    private fun refuseNewer(file: Path) {
        if (!Files.isRegularFile(file) || Files.size(file) == 0L) return
        val found = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}", Properties().apply { setProperty("open_mode", "1") }).use { connection ->
            connection.createStatement().use { statement -> statement.executeQuery("PRAGMA user_version").use { rows -> if (rows.next()) rows.getLong(1) else 0L } }
        }
        if (found > schemaVersion) throw NewerLibraryException(file, found, schemaVersion)
    }

    /**
     * An in-memory database for UI previews. It uses a single shared connection, so it does not enforce the
     * connection-threading rules of [open]; tests should use a file-backed database instead.
     */
    fun inMemory(): RefluxDatabase =
        RefluxDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), RefluxDatabase.Schema))
}
