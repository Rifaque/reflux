package dev.reflux.core.versions

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackAssessment
import dev.reflux.core.playback.PlaybackAssessor
import dev.reflux.core.playback.PlaybackVerdict
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.watch.Handoff
import dev.reflux.core.watch.TimelineMatch

/** A version together with the state of the source that holds it. */
data class VersionCandidate(
    val version: MediaVersion,
    val availability: Availability,
    val locality: SourceLocality,
)

/** The criteria Best Version uses, in priority order. The first one that differs decides. */
enum class SelectionCriterion {
    /** Its source can be reached. */
    AVAILABILITY,

    /** This device can play it at all. */
    COMPATIBILITY,

    /** The viewer is part-way through one cut; another copy of that cut continues exactly where they are. */
    CONTINUITY,

    /** The version the user pinned for this work. */
    USER_PREFERENCE,

    /** How well this device plays it: without software decoding, tone mapping, and the like. */
    FEWER_COMPROMISES,
    RESOLUTION,
    DYNAMIC_RANGE,
    AUDIO,
    LOCALITY,
    BITRATE,
    STABLE_ORDER,
}

data class RankedVersion(
    val candidate: VersionCandidate,
    val assessment: PlaybackAssessment,
) {
    val version: MediaVersion get() = candidate.version

    /** Whether Reflux should offer to play this version at all. */
    val playable: Boolean
        get() = candidate.availability != Availability.UNAVAILABLE && assessment.verdict != PlaybackVerdict.UNSUPPORTED
}

/** The outcome of Best Version: an ordered list plus why the winner beat the runner-up. */
data class VersionSelection(
    val ranked: List<RankedVersion>,
    /** The criterion that separated the first and second versions, when there are at least two. */
    val decidedBy: SelectionCriterion?,
) {
    val best: RankedVersion? get() = ranked.firstOrNull()?.takeIf { it.playable }
}

/**
 * Best Version: deterministic, explainable ranking of the versions of one work for the current device.
 *
 * In order: what can be played here at all; the cut the viewer is part-way through; the version the user
 * pinned; how well and in what quality this device plays it; and, among versions that are equivalent by all
 * of that, the closest copy.
 *
 * Quality is what this device can actually show and play: a version it plays with compromises loses to one
 * it plays cleanly, and resolution, dynamic range, and audio only count as far as the display and outputs
 * reach (a higher resolution than the display can show is not better). A local file is therefore preferred
 * to the same thing streamed, and never to something materially better (docs/WATCH_STATE.md).
 */
object VersionSelector {
    /**
     * @param preferred the version the user pinned for this work.
     * @param resuming the viewer's progress, when playback is going to continue from it.
     */
    fun select(
        candidates: List<VersionCandidate>,
        device: DeviceCapabilities,
        preferred: VersionId? = null,
        resuming: WatchState? = null,
    ): VersionSelection {
        val ranked = candidates.map { RankedVersion(it, PlaybackAssessor.assess(it.version.stream, device)) }
        val criteria = criteria(device, preferred, resuming)
        val comparator = Comparator<RankedVersion> { a, b ->
            for ((_, compare) in criteria) {
                val result = compare(a, b)
                if (result != 0) return@Comparator result
            }
            0
        }
        val sorted = ranked.sortedWith(comparator)
        val decidedBy = if (sorted.size >= 2) {
            criteria.firstOrNull { (_, compare) -> compare(sorted[0], sorted[1]) != 0 }?.first
        } else {
            null
        }
        return VersionSelection(sorted, decidedBy)
    }

    /** Each criterion returns a negative number when the first argument is better. */
    private fun criteria(
        device: DeviceCapabilities,
        preferred: VersionId?,
        resuming: WatchState?,
    ): List<Pair<SelectionCriterion, (RankedVersion, RankedVersion) -> Int>> = listOf(
        SelectionCriterion.AVAILABILITY to { a, b -> availabilityRank(a).compareTo(availabilityRank(b)) },
        SelectionCriterion.COMPATIBILITY to { a, b -> better(supported(a), supported(b)) },
        SelectionCriterion.CONTINUITY to { a, b -> breaksContinuity(a, resuming).compareTo(breaksContinuity(b, resuming)) },
        SelectionCriterion.USER_PREFERENCE to { a, b -> better(isPreferred(a, preferred), isPreferred(b, preferred)) },
        SelectionCriterion.FEWER_COMPROMISES to { a, b ->
            compareValuesBy(a, b, { it.assessment.verdict }, { it.assessment.compromises.size })
        },
        SelectionCriterion.RESOLUTION to { a, b -> effectiveHeight(b, device).compareTo(effectiveHeight(a, device)) },
        SelectionCriterion.DYNAMIC_RANGE to { a, b -> shownRange(b, device).compareTo(shownRange(a, device)) },
        SelectionCriterion.AUDIO to { a, b -> audioScore(b, device).compareTo(audioScore(a, device)) },
        SelectionCriterion.LOCALITY to { a, b -> a.candidate.locality.compareTo(b.candidate.locality) },
        SelectionCriterion.BITRATE to { a, b -> b.version.sizeBytes.compareTo(a.version.sizeBytes) },
        SelectionCriterion.STABLE_ORDER to { a, b -> a.version.id.value.compareTo(b.version.id.value) },
    )

    private fun better(a: Boolean, b: Boolean): Int = b.compareTo(a)

    private fun supported(version: RankedVersion): Boolean = version.assessment.verdict != PlaybackVerdict.UNSUPPORTED

    private fun isPreferred(version: RankedVersion, preferred: VersionId?): Boolean =
        version.version.id == preferred && version.playable

    private fun breaksContinuity(version: RankedVersion, resuming: WatchState?): Boolean =
        Handoff.relation(resuming, version.version) == TimelineMatch.DIFFERENT

    private fun availabilityRank(version: RankedVersion): Int = when (version.candidate.availability) {
        Availability.AVAILABLE -> 0
        Availability.UNKNOWN -> 1
        Availability.UNAVAILABLE -> 2
    }

    private fun effectiveHeight(version: RankedVersion, device: DeviceCapabilities): Int {
        val height = version.version.stream.video?.nominalHeight ?: return 0
        return minOf(height, device.display.maxHeight)
    }

    private fun shownRange(version: RankedVersion, device: DeviceCapabilities): Int {
        val range = version.version.stream.video?.dynamicRange ?: DynamicRange.SDR
        return if (range in device.display.dynamicRanges) range.ordinal else DynamicRange.SDR.ordinal
    }

    private fun audioScore(version: RankedVersion, device: DeviceCapabilities): Int =
        version.version.stream.audio.maxOfOrNull { track ->
            val passthrough = track.codec in device.audioPassthrough
            val channels = (track.channels ?: 2).let { if (passthrough) it else minOf(it, device.maxOutputChannels) }
            val lossless = if (track.codec.lossless && (passthrough || track.codec in device.audioDecoders)) 1 else 0
            val atmos = if (track.atmos && passthrough) 1 else 0
            channels * 100 + atmos * 10 + lossless
        } ?: 0
}
