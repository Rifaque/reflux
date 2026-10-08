package dev.reflux.library

import dev.reflux.library.db.RefluxDatabase
import java.nio.file.Files

/**
 * A file-backed database in a temporary directory. Tests use the same driver configuration as production,
 * because the in-memory driver hides connection-threading rules.
 */
fun testDatabase(): RefluxDatabase {
    val directory = Files.createTempDirectory("reflux-db")
    directory.toFile().deleteOnExit()
    return LibraryDatabase.open(directory.resolve("library.db"))
}
