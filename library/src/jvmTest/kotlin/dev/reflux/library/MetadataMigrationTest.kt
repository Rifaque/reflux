package dev.reflux.library

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import dev.reflux.core.metadata.MatchEvidence
import dev.reflux.core.model.MediaId
import dev.reflux.library.db.RefluxDatabase
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Migration 6 (docs/METADATA.md): a library from before it opens with its watch state, sources, pins, metadata,
 * and settings as they were, and gains what M5 keeps.
 */
class MetadataMigrationTest {
    @Test
    fun aLibraryFromBeforeOpensWithEverythingItHad() {
        val directory = Files.createTempDirectory("reflux-migration")
        val file = directory.resolve("library.db")
        // The current schema, taken back to the shape it had before migration 6.
        JdbcSqliteDriver("jdbc:sqlite:$file").use { RefluxDatabase.Schema.create(it) }
        DriverManager.getConnection("jdbc:sqlite:$file").use { connection ->
            connection.createStatement().use { sql ->
                sql.execute("DROP TABLE source_metadata")
                sql.execute("ALTER TABLE metadata DROP COLUMN match_evidence")
                sql.execute("ALTER TABLE metadata DROP COLUMN end_date")
                sql.execute("ALTER TABLE metadata DROP COLUMN studios")
                sql.execute("INSERT INTO source(id, type, display_name, locality, config, availability, added_at) VALUES ('s1', 'server', 'Home', 'LOCAL_NETWORK', 'sealed:x', 'AVAILABLE', 1)")
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, added_at) VALUES ('show', 'SHOW', 'Severance', 'severance', 'severance', 1)")
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, show_id, season_number, episode_number, added_at) VALUES ('ep', 'EPISODE', 'E1', 'e1', 'e1', 'show', 1, 1, 1)")
                sql.execute("INSERT INTO item(id, kind, title, title_key, sort_key, added_at) VALUES ('movie', 'MOVIE', 'Heat', 'heat', 'heat', 1)")
                sql.execute(
                    "INSERT INTO metadata(item_id, provider_ref, title, genres, external_ids, match_score, fetched_at, overview) VALUES " +
                        "('show', 'tmdb:show:95396', 'Severance', '', '', 1.0, 5, 'Work.'), ('ep', 'tmdb:show:95396/1/1', 'Good News', '', '', 1.0, 5, NULL), " +
                        "('movie', 'tmdb:movie:949', 'Heat', 'Crime', '', 0.9, 5, 'A cop.')",
                )
                sql.execute("INSERT INTO metadata_pin(item_id, provider_ref) VALUES ('show', 'tmdb:show:95396')")
                sql.execute("INSERT INTO watch_state(item_id, position_ms, duration_ms, completed, play_count) VALUES ('movie', 1200000, 6000000, 0, 1)")
                sql.execute("INSERT INTO favorite(item_id, added_at) VALUES ('movie', 1)")
                sql.execute("INSERT INTO setting(key, value) VALUES ('appearance.mediaAtmosphere', 'on')")
                sql.execute("PRAGMA user_version = 6")
            }
        }

        val library = Library(LibraryDatabase.open(file)) { 10 }
        assertEquals(1_200_000, library.watchState(MediaId("movie"))!!.positionMs)
        assertTrue(library.queries.allFavorites().executeAsList().any { it.item_id == "movie" })
        assertEquals("sealed:x", library.sources().single().config)
        assertEquals("on", library.setting("appearance.mediaAtmosphere"))
        assertEquals("A cop.", library.metadata(MediaId("movie"))!!.overview)
        assertEquals(listOf("Crime"), library.metadata(MediaId("movie"))!!.genres)
        // What the user chose is known as theirs, for the show and its episodes; the rest is of unknown strength.
        assertEquals(MatchEvidence.USER, library.metadata(MediaId("show"))!!.evidence)
        assertEquals(MatchEvidence.USER, library.metadata(MediaId("ep"))!!.evidence)
        assertNull(library.metadata(MediaId("movie"))!!.evidence)
        assertTrue(library.queries.sourceMetadataOfSource("s1").executeAsList().isEmpty(), "the new table is there, empty")
        directory.toFile().deleteRecursively()
    }
}
