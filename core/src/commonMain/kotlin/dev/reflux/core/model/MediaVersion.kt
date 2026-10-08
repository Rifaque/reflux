package dev.reflux.core.model

import dev.reflux.core.playback.StreamInfo

/**
 * Where a version physically lives: a source plus a source-relative path.
 *
 * Paths use `/` separators and never start with `/`. They are opaque to everything except the owning source.
 */
data class MediaLocation(val sourceId: SourceId, val path: String) {
    init {
        require(!path.startsWith("/")) { "location path must be source-relative: $path" }
    }

    val fileName: String get() = path.substringAfterLast('/')
}

/** How trustworthy a version's technical description is. */
enum class StreamInfoOrigin {
    /** Inferred from release tokens in the file name (e.g. `2160p`, `x265`, `HDR`). */
    FILENAME_HINTS,

    /** Read from the media container by a probe. */
    PROBE,

    /** Reported by the source (e.g. a media server). */
    SOURCE,
}

/** An external subtitle file that belongs to a version (e.g. `Movie.en.forced.srt`). */
data class ExternalSubtitle(
    val location: MediaLocation,
    val format: dev.reflux.core.playback.SubtitleFormat,
    val language: String?,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
)

/**
 * One playable version of a work: a specific file (or stream) at a specific location.
 *
 * A work may have many versions (4K and 1080p, theatrical and extended, local and remote copies).
 */
data class MediaVersion(
    val id: VersionId,
    val itemId: MediaId,
    val location: MediaLocation,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
    val stream: StreamInfo,
    val streamOrigin: StreamInfoOrigin,
    val edition: String? = null,
    /** Part number for works split across multiple files (`cd1`, `part2`). */
    val part: Int? = null,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
)
