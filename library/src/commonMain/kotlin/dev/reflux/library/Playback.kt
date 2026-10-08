package dev.reflux.library

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.source.MediaSource
import dev.reflux.core.versions.RankedVersion
import dev.reflux.core.versions.VersionSelection
import dev.reflux.core.watch.WatchRules

/** Everything needed to start playing a work, and why this version was chosen. */
data class PlaybackPlan(
    val itemId: MediaId,
    val version: RankedVersion,
    val selection: VersionSelection,
    val request: PlaybackRequest,
    /** The source holding the version; pass it to [SyncingWatchReporter]. */
    val source: MediaSource,
)

/**
 * Turns "play this work" into a concrete [PlaybackRequest]: Best Version for [device], the resume position,
 * and external subtitles, resolved through the version's source adapter.
 *
 * @param versionId plays a specific version instead of Best Version (from the version picker).
 * @param resume false starts from the beginning ("Play from start").
 * @return null when no version is currently playable (e.g. its source is offline).
 */
suspend fun Library.planPlayback(
    itemId: MediaId,
    device: DeviceCapabilities,
    sources: (SourceId) -> MediaSource?,
    versionId: VersionId? = null,
    resume: Boolean = true,
): PlaybackPlan? {
    val selection = selectVersion(itemId, device)
    val chosen = if (versionId != null) {
        selection.ranked.firstOrNull { it.version.id == versionId && it.playable }
    } else {
        selection.best
    } ?: return null
    val version = chosen.version
    val source = sources(version.location.sourceId) ?: return null
    val subtitles = version.externalSubtitles.mapNotNull { subtitle ->
        val subtitleSource = sources(subtitle.location.sourceId) ?: return@mapNotNull null
        SubtitleAttachment(
            target = subtitleSource.playbackTarget(subtitle.location.path),
            language = subtitle.language,
            forced = subtitle.forced,
            hearingImpaired = subtitle.hearingImpaired,
        )
    }
    val request = PlaybackRequest(
        target = source.playbackTarget(version.location.path),
        title = playbackTitle(itemId),
        startPositionMs = if (resume) WatchRules.resumePosition(watchState(itemId)) else null,
        subtitles = subtitles,
    )
    return PlaybackPlan(itemId, chosen, selection, request, source)
}

/** "Heat (1995)" or "Breaking Bad · S1 E2 · Grilled". */
internal fun Library.playbackTitle(itemId: MediaId): String = when (val item = item(itemId)) {
    is Movie -> if (item.year != null) "${item.title} (${item.year})" else item.title
    is Episode -> {
        val show = item(item.showId)?.title
        val number = item.episodeNumber?.let { "S${item.seasonNumber} E$it" } ?: item.airDate?.toString()
        listOfNotNull(show, number, item.episodeTitle).joinToString(" · ")
    }
    null -> ""
    else -> item.title
}
