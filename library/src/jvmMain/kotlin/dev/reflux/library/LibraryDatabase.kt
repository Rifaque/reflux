package dev.reflux.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reflux.library.db.RefluxDatabase
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

/** Opens the library database on desktop JVMs. Creates or migrates the schema as needed. */
object LibraryDatabase {
    /** A file-backed database; parent directories are created. */
    fun open(file: Path): RefluxDatabase {
        file.toAbsolutePath().parent?.let(Files::createDirectories)
        val properties = Properties().apply {
            // WAL keeps the UI responsive while scans write.
            setProperty("journal_mode", "WAL")
            setProperty("synchronous", "NORMAL")
            setProperty("busy_timeout", "5000")
        }
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}", properties, RefluxDatabase.Schema)
        return RefluxDatabase(driver)
    }

    /** An in-memory database, for tests and previews. */
    fun inMemory(): RefluxDatabase =
        RefluxDatabase(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties(), RefluxDatabase.Schema))
}
