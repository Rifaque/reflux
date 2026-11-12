package dev.reflux.library

/**
 * The current schema taken back to the shape it had before migration 7 (profiles, M8), for the migration tests of
 * migration 7 and of the ones before it: the tables that profiles key by viewer are as they were, keyed by work, and
 * what migration 7 adds is gone. [execute] runs one statement.
 */
fun shapeBeforeProfiles(execute: (String) -> Unit) {
    execute("DROP TABLE profile")
    execute("DROP TABLE profile_setting")
    execute("DROP TABLE server_endpoint")
    execute("ALTER TABLE source DROP COLUMN endpoint_id")
    execute("ALTER TABLE source DROP COLUMN profile_id")
    execute("ALTER TABLE source DROP COLUMN sync_profile_id")
    execute("ALTER TABLE version DROP COLUMN visible")
    execute("ALTER TABLE download DROP COLUMN profile_id")
    execute("ALTER TABLE user_collection DROP COLUMN profile_id")
    execute("ALTER TABLE user_collection_item DROP COLUMN profile_id")
    execute("DROP TABLE watch_state")
    execute(
        "CREATE TABLE watch_state (item_id TEXT NOT NULL PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER, " +
            "completed INTEGER NOT NULL, play_count INTEGER NOT NULL, last_played_at INTEGER, updated_at INTEGER, version_id TEXT, cut TEXT)",
    )
    execute("DROP TABLE watch_sync")
    execute(
        "CREATE TABLE watch_sync (source_id TEXT NOT NULL, path TEXT NOT NULL, played INTEGER NOT NULL, position_ms INTEGER NOT NULL, " +
            "last_played_at INTEGER, reconciled_at INTEGER NOT NULL, PRIMARY KEY (source_id, path))",
    )
    execute("DROP TABLE favorite")
    execute("CREATE TABLE favorite (item_id TEXT NOT NULL PRIMARY KEY, added_at INTEGER NOT NULL)")
    execute("DROP TABLE version_preference")
    execute("CREATE TABLE version_preference (item_id TEXT NOT NULL PRIMARY KEY, version_id TEXT NOT NULL)")
}
