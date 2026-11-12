package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.ProfileId
import dev.reflux.core.model.Season
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.WatchState
import dev.reflux.core.source.SourceLocality

/** A configured source as persisted by Reflux. */
data class SourceRecord(
    val id: SourceId,
    val type: String,
    val displayName: String,
    val locality: SourceLocality,
    /** Adapter-defined configuration, e.g. a folder path. */
    val config: String,
    val availability: Availability,
    val lastScanAtEpochMs: Long?,
    val lastAvailableAtEpochMs: Long?,
    /** Whether it takes part in the unified library (docs/NAVIGATION.md). Independent of [availability]. */
    val included: Boolean = true,
    /** It answered, and no longer accepts Reflux's sign-in: signing in again is the way back. */
    val signInRequired: Boolean = false,
    /** Whose account this is (docs/WATCH_STATE.md, "Profiles"); null for a source every profile shares. */
    val profileId: ProfileId? = null,
    /** The server address it was reached at, shared by every profile ([ServerEndpoint]); null for a folder or share. */
    val endpointId: String? = null,
)

/**
 * Which library a read is about (docs/NAVIGATION.md, section 4): the unified library, made of what is included,
 * or one source (or one of its libraries) as it is, whatever its inclusion.
 */
sealed interface LibraryScope {
    data object Unified : LibraryScope

    data class OfSource(val sourceId: SourceId, val section: String? = null) : LibraryScope
}

internal val LibraryScope.sourceValue: String? get() = (this as? LibraryScope.OfSource)?.sourceId?.value

internal val LibraryScope.sectionValue: String? get() = (this as? LibraryScope.OfSource)?.section

/** A work as the experience layer shows it: identity plus artwork, progress, and availability. */
data class LibraryEntry(
    val item: MediaItem,
    val artwork: Map<ArtworkKind, ArtworkLocator>,
    val watchState: WatchState?,
    /** Whether any version can be played right now. Cached entries stay browsable either way. */
    val availability: Availability,
    val favorite: Boolean,
    /** Provider metadata, when the work has been matched. Titles in [item] already reflect it. */
    val metadata: ItemMetadata? = null,
)

/**
 * Something the user can continue: a work in progress, or the next episode of a show they are watching.
 *
 * @param show the show, when [entry] is an episode.
 * @param lastEngagedAtEpochMs when the user last played the work, or for a show any of its episodes.
 */
data class Continuation(val entry: LibraryEntry, val show: LibraryEntry?, val lastEngagedAtEpochMs: Long)

/** A version with the state of its source. */
data class VersionInfo(
    val version: MediaVersion,
    val availability: Availability,
    val locality: SourceLocality,
    val confidence: Double,
)

data class SeasonDetail(val season: Season, val entry: LibraryEntry, val episodes: List<LibraryEntry>)

data class ShowDetail(
    val show: LibraryEntry,
    val seasons: List<SeasonDetail>,
    /** The episode to play next, if the show has been started and not finished. */
    val nextUp: Episode?,
)

/** Outcome of scanning one source. */
data class ScanReport(
    val sourceId: SourceId,
    val status: Status,
    val added: Int = 0,
    val updated: Int = 0,
    val removed: Int = 0,
    val unchanged: Int = 0,
    /** Files recognized but not added as works (extras, samples, disc structures). */
    val skipped: Map<ParsedKind, Int> = emptyMap(),
    val lowConfidence: Int = 0,
) {
    enum class Status {
        COMPLETED,

        /** The source could not be reached. The cached library was kept as is. */
        UNAVAILABLE,

        /**
         * The source was reachable but empty although Reflux knows media on it — typically an unmounted drive
         * or share whose mount point still exists. The cached library was kept and the source marked unavailable.
         */
        EMPTY_KEPT,

        /** The source was removed while it was being scanned. Nothing was written: its works are gone. */
        REMOVED,
    }
}
