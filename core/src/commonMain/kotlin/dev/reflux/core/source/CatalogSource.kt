package dev.reflux.core.source

import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.metadata.Description
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import kotlinx.coroutines.flow.Flow

/** A playable file or stream listed by a catalog source. [path] is a source-relative, adapter-defined locator. */
data class CatalogVersion(
    val path: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
    val stream: StreamInfo,
    val edition: String? = null,
    val subtitles: List<CatalogSubtitle> = emptyList(),
)

data class CatalogSubtitle(
    val path: String,
    val format: SubtitleFormat,
    val language: String?,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
)

/**
 * The user's state for a work as the source records it (e.g. a media server's played flag), translated by
 * the adapter. [positionMs] is a time in the source's own copy of the work. This is one witness of what the
 * user did, not Reflux's watch state: `WatchReconciler` decides what it changes.
 */
data class SourceUserState(
    val played: Boolean,
    val positionMs: Long,
    val playCount: Int,
    val lastPlayedAtEpochMs: Long?,
    val favorite: Boolean,
)

/**
 * A movie or episode as a catalog source lists it, already identified by the source.
 *
 * [identity] uses the same shape as a file-name parse (for episodes, title and year describe the show),
 * so catalog works go through the same identity rules as local files and merge with them.
 */
data class CatalogEntry(
    val identity: ParsedMedia,
    val versions: List<CatalogVersion>,
    /** Artwork paths (resolved through the source) for the movie or episode. */
    val artwork: Map<ArtworkKind, String> = emptyMap(),
    /** For episodes: artwork paths for the show. */
    val showArtwork: Map<ArtworkKind, String> = emptyMap(),
    val userState: SourceUserState? = null,
    /** The library of the source the work is listed in ([SourceSection.id]), for sources made of libraries. */
    val section: String? = null,
    /** What the source says the movie or episode is about. Credits' pictures are paths in the source. */
    val description: Description? = null,
    /** For episodes: what the source says about the show. */
    val showDescription: Description? = null,
    /** For episodes: the season as the source describes it, with its artwork. */
    val season: CatalogSeason? = null,
)

/** A season as a catalog source describes it: its own name, what it is about, and its artwork (paths in the source). */
data class CatalogSeason(
    val title: String? = null,
    val overview: String? = null,
    val airDate: dev.reflux.core.model.CalendarDate? = null,
    val artwork: Map<ArtworkKind, String> = emptyMap(),
)

/** A source that lists already-identified works (media servers such as Jellyfin). */
interface CatalogSource : MediaSource {
    /** Lists the whole catalog. Throws [SourceUnavailableException] when the source cannot be reached. */
    fun catalog(): Flow<CatalogEntry>
}

/**
 * A source that keeps its own watch state and accepts updates from Reflux. Every call may fail (the source
 * is away); Reflux's own state never depends on one succeeding.
 */
interface WatchStateSyncSource : MediaSource {
    /**
     * The fraction of a work's running time past which the source marks it played by itself when a position
     * is reported to it, or null when it does not. Positions Reflux reports stay below it
     * (`WatchRules.reportedPosition`); a playback that finishes is reported as having reached the end.
     */
    val marksPlayedPast: Double? get() = null

    suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean)

    /** Records where playback of [path] stopped. */
    suspend fun reportStopped(path: String, positionMs: Long)

    /**
     * Brings the source's position in [path] up to date (what a scan finds the source has not heard of yet).
     * It says nothing about a playback: a source on which this device has a playback of [path] open keeps
     * it open, so that the playback's next report does not count as another one there.
     */
    suspend fun updatePosition(path: String, positionMs: Long) = reportStopped(path, positionMs)

    /** Marks [path] played or not played. Marking it not played also forgets the source's position in it. */
    suspend fun setPlayed(path: String, played: Boolean)
}
