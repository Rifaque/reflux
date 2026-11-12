package dev.reflux.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import dev.reflux.core.model.SourceId
import dev.reflux.library.db.RefluxDatabase
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Migration 7 (M8; docs/WATCH_STATE.md, "Profiles"): a library from before profiles opens with everything it had,
 * as the one profile it starts with; and the schema keeps every viewer's state keyed by profile.
 */
class ProfileMigrationTest {
    @Test
    fun aLibraryFromBeforeProfilesBecomesTheFirstProfileAndLosesNothing() {
        val directory = Files.createTempDirectory("reflux-profiles-migration")
        val file = directory.resolve("library.db")
        JdbcSqliteDriver("jdbc:sqlite:$file").use { RefluxDatabase.Schema.create(it) }
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { sql ->
                shapeBeforeProfiles { sql.execute(it) }
                sql.execute("INSERT INTO source(id, type, display_name, locality, config, availability, added_at) VALUES ('jf', 'jellyfin', 'Home', 'LOCAL_NETWORK', 'sealed:x', 'AVAILABLE', 1)")
                sql.execute("INSERT INTO source(id, type, display_name, locality, config, availability, added_at) VALUES ('nas', 'smb', 'NAS', 'LOCAL_NETWORK', 'sealed:y', 'AVAILABLE', 2)")
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, added_at) VALUES ('movie', 'MOVIE', 'Heat', 'heat', 'heat', 1)")
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, added_at) VALUES ('other', 'MOVIE', 'Alien', 'alien', 'alien', 1)")
                sql.execute(
                    "INSERT INTO version(id, item_id, source_id, path, size_bytes, modified_at, stream_origin, container, confidence, signals, first_seen_at, last_seen_at, library_source_id) " +
                        "VALUES ('v1', 'movie', 'jf', 'items/1', 1, 0, 'SOURCE', 'MATROSKA', 1.0, '', 0, 0, 'jf'), ('v2', 'other', 'nas', 'alien.mkv', 1, 0, 'SOURCE', 'MATROSKA', 1.0, '', 0, 0, 'nas')",
                )
                sql.execute("INSERT INTO watch_state(item_id, position_ms, duration_ms, completed, play_count, last_played_at, updated_at, version_id, cut) VALUES ('movie', 1200000, 6000000, 0, 1, 5, 5, 'v1', NULL)")
                sql.execute("INSERT INTO watch_sync(source_id, path, played, position_ms, last_played_at, reconciled_at) VALUES ('jf', 'items/1', 0, 1200000, 5, 6)")
                sql.execute("INSERT INTO favorite(item_id, added_at) VALUES ('movie', 1)")
                sql.execute("INSERT INTO version_preference(item_id, version_id) VALUES ('movie', 'v1')")
                sql.execute("INSERT INTO user_collection(id, name, smart_query, created_at) VALUES ('c1', 'Weekend', NULL, 1)")
                sql.execute("INSERT INTO user_collection_item(collection_id, item_id, added_at) VALUES ('c1', 'movie', 1)")
                sql.execute(
                    "INSERT INTO download(id, item_id, origin_version_id, origin_source_id, origin_path, path, state, bytes_downloaded, created_at, updated_at) " +
                        "VALUES ('d1', 'movie', 'v1', 'jf', 'items/1', 'd1.mkv', 'COMPLETED', 1, 0, 0), ('d2', 'other', 'v2', 'nas', 'alien.mkv', 'd2.mkv', 'COMPLETED', 1, 0, 0)",
                )
                sql.execute("PRAGMA user_version = 7")
            }
        }

        val library = Library(LibraryDatabase.open(file)) { 10 }
        assertEquals(listOf(Profile(ProfileId.DEFAULT, "Main", active = true, hasPin = false, lockedUntilEpochMs = null)), library.profiles())
        assertEquals(1_200_000, library.watchState(MediaId("movie"))?.positionMs)
        assertEquals(listOf("movie"), library.favorites().map { it.item.id.value })
        assertEquals(listOf("Weekend"), library.userCollections().map { it.name })
        assertEquals(listOf("movie"), library.collectionEntries(library.userCollections().single()).map { it.item.id.value })
        assertEquals(setOf("v1").map { dev.reflux.core.model.VersionId(it) }.toSet(), library.pinnedVersions(MediaId("movie")) - library.pinnedVersions(MediaId("movie")).filter { it.value != "v1" }.toSet())
        assertEquals(1_200_000, library.queries.watchSyncOf("default", "jf", "items/1").executeAsOne().position_ms, "what the server last said is kept, as the first profile's")
        // The server account was the one viewer's; the share stays everyone's.
        assertEquals(ProfileId.DEFAULT, library.source(SourceId("jf"))?.profileId)
        assertNull(library.source(SourceId("nas"))?.profileId)
        assertEquals(ProfileId.DEFAULT, library.syncProfileOf(SourceId("jf")))
        assertEquals("default", library.queries.downloadById("d1").executeAsOne().profile_id, "a copy of the account's is the profile's")
        assertNull(library.queries.downloadById("d2").executeAsOne().profile_id, "a copy of the share's is everyone's")
        assertEquals(setOf("Heat", "Alien"), library.movies().map { it.item.title }.toSet())
        // A second profile starts with nothing of it.
        library.switchProfile(library.createProfile("Sam"))
        assertEquals(listOf("Alien"), library.movies().map { it.item.title })
        assertNull(library.watchState(MediaId("movie")))
        assertTrue(library.favorites().isEmpty())
        directory.toFile().deleteRecursively()
    }

    /**
     * Every table is classified as viewer state or as the installation's. Viewer state has a `profile_id`. A new
     * table fails here until it is classified, which is the moment to decide whose it is.
     */
    @Test
    fun everyViewerStateTableIsKeyedByProfile() {
        val db = testDatabase()
        val tables = mutableListOf<String>()
        val columns = mutableMapOf<String, List<String>>()
        val driver = db.libraryQueries
        driver.settingValue("x").executeAsOneOrNull() // opens the database
        val file = Files.createTempDirectory("reflux-schema").resolve("library.db")
        JdbcSqliteDriver("jdbc:sqlite:$file").use { RefluxDatabase.Schema.create(it) }
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().executeQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'").use { rows ->
                while (rows.next()) tables += rows.getString(1)
            }
            for (table in tables) {
                connection.createStatement().executeQuery("PRAGMA table_info($table)").use { rows ->
                    val names = mutableListOf<String>()
                    while (rows.next()) names += rows.getString("name")
                    columns[table] = names
                }
            }
        }
        val unclassified = tables.filter { it !in VIEWER_STATE && it !in INSTALLATION }
        if (unclassified.isNotEmpty()) fail("tables not classified as viewer state or the installation's: $unclassified")
        for (table in VIEWER_STATE) assertTrue("profile_id" in columns.getValue(table), "$table is viewer state and has no profile_id")
    }

    private companion object {
        /** What one viewer did or chose (docs/WATCH_STATE.md, "Profiles"). */
        val VIEWER_STATE = setOf(
            "watch_state", "watch_sync", "favorite", "version_preference", "user_collection", "user_collection_item", "profile_setting",
        )

        /** Media truth, sources, copies, and device settings: the installation's, shared by every profile. */
        val INSTALLATION = setOf(
            "source", "source_section", "excluded_section", "item", "version", "track", "external_subtitle", "artwork",
            "identity_override", "metadata", "source_metadata", "catalog_description", "provider_collection", "credit",
            "metadata_attempt", "metadata_pin", "identity_alias", "download", "download_subtitle", "setting",
            "profile", "server_endpoint",
        )
    }
}
