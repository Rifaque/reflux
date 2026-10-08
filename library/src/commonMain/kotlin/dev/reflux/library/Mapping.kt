package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.model.CalendarDate
import dev.reflux.core.model.Episode
import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.model.MediaKind
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.SourceLocality
import dev.reflux.library.db.External_subtitle
import dev.reflux.library.db.Item
import dev.reflux.library.db.Source
import dev.reflux.library.db.Track
import dev.reflux.library.db.Watch_state

internal const val TRACK_AUDIO = "AUDIO"
internal const val TRACK_SUBTITLE = "SUBTITLE"

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } }

internal fun Item.toModel(): MediaItem {
    val id = MediaId(id)
    return when (enumOrNull<MediaKind>(kind)) {
        MediaKind.MOVIE -> Movie(id, title, year?.toInt())
        MediaKind.SHOW -> Show(id, title, year?.toInt())
        MediaKind.SEASON -> Season(id, MediaId(show_id!!), season_number!!.toInt())
        MediaKind.EPISODE -> Episode(
            id = id,
            showId = MediaId(show_id!!),
            seasonId = MediaId(season_id!!),
            seasonNumber = season_number!!.toInt(),
            episodeNumber = episode_number?.toInt(),
            episodeNumberEnd = episode_number_end?.toInt(),
            absoluteNumber = absolute_number?.toInt(),
            airDate = air_date?.let(CalendarDate::parse),
            // Episode rows store the episode's own title only when one was found.
            episodeTitle = title.takeIf { it.isNotEmpty() },
        )
        null -> error("unknown item kind $kind for $id")
    }
}

internal fun Source.toModel() = SourceRecord(
    id = SourceId(id),
    type = type,
    displayName = display_name,
    locality = enumOrNull<SourceLocality>(locality) ?: SourceLocality.REMOTE,
    config = config,
    availability = enumOrNull<Availability>(availability) ?: Availability.UNKNOWN,
    lastScanAtEpochMs = last_scan_at,
    lastAvailableAtEpochMs = last_available_at,
)

internal fun Watch_state.toModel() = WatchState(
    itemId = MediaId(item_id),
    positionMs = position_ms,
    durationMs = duration_ms,
    completed = completed != 0L,
    playCount = play_count.toInt(),
    lastPlayedAtEpochMs = last_played_at,
)

internal fun External_subtitle.toModel() = ExternalSubtitle(
    location = MediaLocation(SourceId(source_id), path),
    format = enumOrNull<SubtitleFormat>(format) ?: SubtitleFormat.UNKNOWN,
    language = language,
    forced = forced != 0L,
    hearingImpaired = hearing_impaired != 0L,
)

/**
 * Maps the columns of `versionsOfItem` / `versionById` without running queries (a row mapper must never query:
 * the JDBC driver forbids nested statements). Tracks and subtitles are attached afterwards with [complete].
 */
@Suppress("LongParameterList")
internal fun versionRow(
    id: String, itemId: String, sourceId: String, path: String, size: Long, modified: Long, origin: String,
    container: String, videoCodec: String?, width: Long?, height: Long?, bitDepth: Long?, dynamicRange: String?,
    dvProfile: Long?, frameRate: Double?, duration: Long?, bitrate: Long?, edition: String?, part: Long?,
    confidence: Double, @Suppress("UNUSED_PARAMETER") signals: String, @Suppress("UNUSED_PARAMETER") probeAttemptedAt: Long?,
    @Suppress("UNUSED_PARAMETER") sourceIdentity: String?, @Suppress("UNUSED_PARAMETER") firstSeen: Long,
    @Suppress("UNUSED_PARAMETER") lastSeen: Long, availability: String, locality: String,
): VersionInfo {
    val video = if (videoCodec == null && height == null && dynamicRange == null) {
        null
    } else {
        VideoStream(
            codec = enumOrNull<VideoCodec>(videoCodec) ?: VideoCodec.UNKNOWN,
            width = width?.toInt(),
            height = height?.toInt(),
            bitDepth = bitDepth?.toInt(),
            dynamicRange = enumOrNull<DynamicRange>(dynamicRange) ?: DynamicRange.SDR,
            dolbyVisionProfile = dvProfile?.toInt(),
            frameRate = frameRate,
        )
    }
    return VersionInfo(
        version = MediaVersion(
            id = VersionId(id),
            itemId = MediaId(itemId),
            location = MediaLocation(SourceId(sourceId), path),
            sizeBytes = size,
            modifiedAtEpochMs = modified,
            stream = StreamInfo(
                container = enumOrNull<Container>(container) ?: Container.UNKNOWN,
                video = video,
                durationMs = duration,
                bitrateBps = bitrate,
            ),
            streamOrigin = enumOrNull<StreamInfoOrigin>(origin) ?: StreamInfoOrigin.FILENAME_HINTS,
            edition = edition,
            part = part?.toInt(),
        ),
        availability = enumOrNull<Availability>(availability) ?: Availability.UNKNOWN,
        locality = enumOrNull<SourceLocality>(locality) ?: SourceLocality.REMOTE,
        confidence = confidence,
    )
}

/** Attaches tracks and external subtitles loaded separately. */
internal fun VersionInfo.complete(tracks: List<Track>, subtitles: List<External_subtitle>): VersionInfo = copy(
    version = version.copy(
        stream = version.stream.copy(
            audio = tracks.filter { it.type == TRACK_AUDIO }.map {
                AudioStream(
                    codec = enumOrNull<AudioCodec>(it.codec) ?: AudioCodec.UNKNOWN,
                    channels = it.channels?.toInt(),
                    language = it.language,
                    atmos = it.atmos != 0L,
                    default = it.is_default != 0L,
                )
            },
            subtitles = tracks.filter { it.type == TRACK_SUBTITLE }.map {
                SubtitleStream(
                    format = enumOrNull<SubtitleFormat>(it.codec) ?: SubtitleFormat.UNKNOWN,
                    language = it.language,
                    forced = it.forced != 0L,
                    default = it.is_default != 0L,
                )
            },
        ),
        externalSubtitles = subtitles.map { it.toModel() },
    ),
)

internal fun Boolean.toLong(): Long = if (this) 1L else 0L

/** Encodes small string maps as `key=value` lines (keys and values never contain newlines or `=`). */
internal fun encodePairs(pairs: Map<String, String>): String =
    pairs.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }

internal fun decodePairs(encoded: String): Map<String, String> =
    encoded.lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

/** Applies provider metadata to a work's display fields. Identity fields never change. */
internal fun MediaItem.withMetadata(metadata: ItemMetadata?): MediaItem {
    if (metadata == null) return this
    return when (this) {
        is Movie -> copy(title = metadata.title ?: title, year = metadata.releaseDate?.year ?: year)
        is Show -> copy(title = metadata.title ?: title, year = metadata.releaseDate?.year ?: year)
        is Episode -> copy(episodeTitle = metadata.title ?: episodeTitle, airDate = airDate ?: metadata.releaseDate)
        else -> this
    }
}
