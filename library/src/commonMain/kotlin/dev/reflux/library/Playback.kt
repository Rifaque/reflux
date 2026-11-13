package dev.reflux.library

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.ProfileId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioDirective
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlanPart
import dev.reflux.core.playback.PlanReason
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.SubtitleAttachment
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.TrackDirective
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.NegotiatedDelivery
import dev.reflux.core.source.NegotiatingSource
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.versions.RankedVersion
import dev.reflux.core.versions.VersionSelection
import dev.reflux.core.watch.Handoff
import dev.reflux.core.watch.ResumePoint

/** Everything needed to start playing a work, and why this version was chosen. */
data class PlaybackPlan(
    val itemId: MediaId,
    val version: RankedVersion,
    val selection: VersionSelection,
    val request: PlaybackRequest,
    /** The source holding the version; pass it to [SyncingWatchReporter]. */
    val source: MediaSource,
    /** Where playback starts and why: the work's progress, carried to this version's timeline. */
    val resume: ResumePoint = ResumePoint.START,
    /** What was decided for this version; [request] carries it out. */
    val planned: PlannedPlayback? = null,
    /** What the viewer chose and what a failed attempt showed, for planning this playback again. */
    val requirements: PlaybackRequirements? = null,
)

/**
 * Turns "play this work" into a concrete [PlaybackRequest]: Best Version for [device], the resume position,
 * and external subtitles, resolved through the version's source adapter.
 *
 * Progress belongs to the work, so it applies whichever version is chosen: the copy that was playing last
 * time may be away, and playback continues on another from the same moment ([Handoff]).
 *
 * @param versionId plays a specific version instead of Best Version (from the version picker).
 * @param resume false starts from the beginning ("Play from start").
 * @param continuing picks up a playback that was interrupted (Try again after a failure): the position is
 *   where the viewer is even near the end, where Play would otherwise start the work over.
 * @param preferences the viewer's languages.
 * @param audio the viewer's own audio track, if they chose one.
 * @param subtitle the viewer's own subtitle, if they chose one now; otherwise what [profile] chose for the work before.
 * @param originalFailed what a real attempt showed cannot be played as it is.
 * @return null when no version is currently playable (e.g. its source is offline).
 */
suspend fun Library.planPlayback(
    itemId: MediaId,
    device: DeviceCapabilities,
    sources: (SourceId) -> MediaSource?,
    versionId: VersionId? = null,
    resume: Boolean = true,
    continuing: Boolean = false,
    preferences: TrackPreferences? = null,
    audio: AudioChoice = AudioChoice.Automatic,
    subtitle: SubtitleChoice = SubtitleChoice.Automatic,
    originalFailed: Set<PlanPart> = emptySet(),
    scope: LibraryScope = LibraryScope.Unified,
    profile: ProfileId = activeProfile(),
): PlaybackPlan? {
    val capabilities = { id: SourceId -> sources(id)?.descriptor?.capabilities.orEmpty() }
    val selection = selectVersion(itemId, device, resuming = resume, continuing = continuing, preferences = preferences, sourceCapabilities = capabilities, scope = scope)
    val chosen = if (versionId != null) {
        selection.ranked.firstOrNull { it.version.id == versionId && it.playable }
    } else {
        selection.best
    } ?: return null
    val version = chosen.version
    val source = sources(version.location.sourceId) ?: return null
    // What this viewer chose for the work before stands in for the automatic choice; their choice now outranks it.
    val wanted = if (subtitle == SubtitleChoice.Automatic) subtitlePreference(itemId, profile)?.choice() ?: subtitle else subtitle
    val requirements = PlaybackRequirements(
        version, preferences ?: TrackPreferences(emptyList()), source.descriptor.capabilities.conversionsOf(source), audio, wanted, originalFailed,
    )
    var planned = PlaybackPlanner.plan(requirements, device)
    if (planned.outcome == PlanOutcome.CANNOT_PLAY) return null
    var delivery = (source as? NegotiatingSource)?.negotiate(version, planned, device)
    if (delivery == null && planned.delivery != DeliveryMode.DIRECT_PLAY) {
        // The server would not, or could not be asked: what this client can do alone, said in the plan.
        val alone = PlaybackPlanner.plan(requirements.copy(source = requirements.source.filterNot { it in CONVERSIONS }.toSet()), device)
        if (alone.outcome == PlanOutcome.CANNOT_PLAY) return null
        planned = alone.copy(reasons = alone.reasons + PlanReason.NEGOTIATION_FAILED)
        delivery = (source as? NegotiatingSource)?.negotiate(version, planned, device)
    }
    val attachments = version.externalSubtitles.mapNotNull { sidecar ->
        val subtitleSource = sources(sidecar.location.sourceId) ?: return@mapNotNull null
        SubtitleAttachment(
            target = subtitleSource.playbackTarget(sidecar.location.path),
            language = sidecar.language,
            forced = sidecar.forced,
            hearingImpaired = sidecar.hearingImpaired,
        )
    }
    val delivered = delivery?.subtitle?.let { SubtitleAttachment(it, chosenLanguage(version, planned), arrivesLate = true) }
    val start = if (resume) Handoff.resume(watchState(itemId), version, continuing) else ResumePoint.START
    val request = PlaybackRequest(
        target = delivery?.target ?: source.playbackTarget(version.location.path),
        title = playbackTitle(itemId),
        startPositionMs = start.positionMs,
        subtitles = attachments + listOfNotNull(delivered),
        tracks = directive(version, planned, requirements, attachments.size, delivered != null),
    )
    return PlaybackPlan(itemId, chosen, selection, request, source, start, planned, requirements)
}

private val CONVERSIONS = setOf(
    SourceCapability.SERVER_REMUX, SourceCapability.SERVER_AUDIO_CONVERSION, SourceCapability.SERVER_TRANSCODE,
    SourceCapability.SERVER_SUBTITLE_EXTRACTION, SourceCapability.SERVER_SUBTITLE_CONVERSION, SourceCapability.SERVER_BURN_IN,
)

/** A conversion is only as real as the way to ask for it: a source that cannot negotiate delivers its files. */
private fun Set<SourceCapability>.conversionsOf(source: MediaSource): Set<SourceCapability> =
    if (source is NegotiatingSource) this else filterNot { it in CONVERSIONS }.toSet()

private fun chosenLanguage(version: MediaVersion, planned: PlannedPlayback): String? {
    val index = planned.subtitleIndex ?: return null
    return if (planned.subtitleExternal) version.externalSubtitles.getOrNull(index)?.language else version.stream.subtitles.getOrNull(index)?.language
}

/** The plan's tracks, for the engine: this is where a plan's choice stops being a choice. */
private fun directive(version: MediaVersion, planned: PlannedPlayback, requirements: PlaybackRequirements, attachments: Int, delivered: Boolean): TrackDirective {
    val index = planned.audioIndex
    val audio = when {
        planned.outcome == PlanOutcome.PLAYS_WITHOUT_AUDIO -> AudioDirective.None
        // A remuxed or converted stream carries the one track it was asked for.
        planned.delivery != DeliveryMode.DIRECT_PLAY || planned.audio == AudioPlan.NONE || index == null -> AudioDirective.EngineChoice
        else -> AudioDirective.Track(index, explicit = requirements.audio is AudioChoice.Explicit)
    }
    val described = version.stream.subtitles.isNotEmpty() || version.externalSubtitles.isNotEmpty()
    val subtitleIndex = planned.subtitleIndex
    val subtitle = when {
        planned.subtitles == SubtitlePlan.NONE && !described && (requirements.subtitle == SubtitleChoice.Automatic || requirements.subtitle is SubtitleChoice.Preferred) -> SubtitleDirective.EngineChoice
        delivered -> SubtitleDirective.Attached(attachments)
        subtitleIndex == null || planned.subtitles == SubtitlePlan.NONE || planned.subtitles == SubtitlePlan.BURN_IN -> SubtitleDirective.Off
        planned.subtitleExternal -> SubtitleDirective.Attached(subtitleIndex)
        else -> SubtitleDirective.Embedded(subtitleIndex)
    }
    return TrackDirective(audio, subtitle)
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
