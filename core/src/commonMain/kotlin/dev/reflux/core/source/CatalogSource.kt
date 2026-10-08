package dev.reflux.core.source

import dev.reflux.core.identify.ParsedMedia
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

/** The user's state for a work as the source records it (e.g. a media server's played flag). */
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
)

/** A source that lists already-identified works (media servers such as Jellyfin). */
interface CatalogSource : MediaSource {
    /** Lists the whole catalog. Throws [SourceUnavailableException] when the source cannot be reached. */
    fun catalog(): Flow<CatalogEntry>
}

/** A source that keeps its own watch state and accepts updates from Reflux. */
interface WatchStateSyncSource : MediaSource {
    suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean)
    suspend fun reportStopped(path: String, positionMs: Long)
    suspend fun setPlayed(path: String, played: Boolean)
}
