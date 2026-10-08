package dev.reflux.library

import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaVersion
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
)

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
    }
}
