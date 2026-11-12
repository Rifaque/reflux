package dev.reflux.library

import dev.reflux.core.model.MediaId
import java.nio.file.Files
import java.security.MessageDigest
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A library from a newer Reflux is refused, untouched; the current one opens; an older one is migrated. */
class LibraryDowngradeTest {
    private val directory = Files.createTempDirectory("reflux-downgrade")
    private val file = directory.resolve("library.db")

    private fun setVersion(version: Long) = DriverManager.getConnection("jdbc:sqlite:$file").use { it.createStatement().use { sql -> sql.execute("PRAGMA user_version = $version") } }

    private fun sha() = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))

    @Test
    fun theCurrentLibraryOpensAndOpensAgain() {
        Library(LibraryDatabase.open(file)) { 1 }.setSetting("kept", "yes")
        assertEquals("yes", Library(LibraryDatabase.open(file)) { 2 }.setting("kept"))
    }

    @Test
    fun anOlderLibraryIsMigrated() {
        LibraryDatabase.open(file)
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { sql ->
                shapeBeforeProfiles { sql.execute(it) }
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, added_at) VALUES ('m', 'MOVIE', 'Heat', 'heat', 'heat', 1)")
                sql.execute("INSERT INTO watch_state(item_id, position_ms, duration_ms, completed, play_count) VALUES ('m', 5, 10, 0, 1)")
                sql.execute("PRAGMA user_version = ${LibraryDatabase.schemaVersion - 1}")
            }
        }
        assertEquals(5, Library(LibraryDatabase.open(file)) { 1 }.watchState(MediaId("m"))?.positionMs)
    }

    @Test
    fun aNewerLibraryIsRefusedAndLeftExactlyAsItWas() {
        Library(LibraryDatabase.open(file)) { 1 }.setSetting("kept", "yes")
        setVersion(LibraryDatabase.schemaVersion + 1)
        // Settle the file (the write above may leave a journal to fold in) before taking its fingerprint.
        DriverManager.getConnection("jdbc:sqlite:$file").use { it.createStatement().use { sql -> sql.execute("PRAGMA wal_checkpoint(TRUNCATE)") } }
        val before = sha()
        val refused = assertFailsWith<NewerLibraryException> { LibraryDatabase.open(file) }
        assertEquals(LibraryDatabase.schemaVersion + 1, refused.found)
        assertEquals(LibraryDatabase.schemaVersion, refused.supported)
        assertTrue("newer version of Reflux" in refused.message!! && "untouched" in refused.message!!, refused.message)
        assertContentEquals(before, sha(), "not a byte of it changed")
        // And it still opens once this build is the newer one again.
        setVersion(LibraryDatabase.schemaVersion)
        assertEquals("yes", Library(LibraryDatabase.open(file)) { 2 }.setting("kept"))
    }
}
