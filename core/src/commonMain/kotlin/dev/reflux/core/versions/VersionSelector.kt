package dev.reflux.core.versions

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.VersionId
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackAssessment
import dev.reflux.core.playback.PlaybackAssessor
import dev.reflux.core.playback.PlaybackVerdict
import dev.reflux.core.source.SourceLocality

/** A version together with the state of the source that holds it. */
data class VersionCandidate(
    val version: MediaVersion,
    val availability: Availability,
    val locality: SourceLocality,
)

/** The criteria Best Version uses, in priority order. The first one that differs decides. */
enum class SelectionCriterion {
    USER_PREFERENCE,
    AVAILABILITY,
    COMPATIBILITY,
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
 * Local copies win only when versions are otherwise equivalent (docs/DECISIONS.md). A higher resolution than
 * the display can show is not treated as better.
 */
object VersionSelector {
    fun select(
        candidates: List<VersionCandidate>,
        device: DeviceCapabilities,
        preferred: VersionId? = null,
    ): VersionSelection {
        val ranked = candidates.map { RankedVersion(it, PlaybackAssessor.assess(it.version.stream, device)) }
        val criteria = criteria(device, preferred)
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
    ): List<Pair<SelectionCriterion, (RankedVersion, RankedVersion) -> Int>> = listOf(
        SelectionCriterion.USER_PREFERENCE to { a, b -> better(isPreferred(a, preferred), isPreferred(b, preferred)) },
        SelectionCriterion.AVAILABILITY to { a, b -> availabilityRank(a).compareTo(availabilityRank(b)) },
        SelectionCriterion.COMPATIBILITY to { a, b -> a.assessment.verdict.compareTo(b.assessment.verdict) },
        SelectionCriterion.FEWER_COMPROMISES to { a, b -> a.assessment.compromises.size.compareTo(b.assessment.compromises.size) },
        SelectionCriterion.RESOLUTION to { a, b -> effectiveHeight(b, device).compareTo(effectiveHeight(a, device)) },
        SelectionCriterion.DYNAMIC_RANGE to { a, b -> shownRange(b, device).compareTo(shownRange(a, device)) },
        SelectionCriterion.AUDIO to { a, b -> audioScore(b, device).compareTo(audioScore(a, device)) },
        SelectionCriterion.LOCALITY to { a, b -> a.candidate.locality.compareTo(b.candidate.locality) },
        SelectionCriterion.BITRATE to { a, b -> b.version.sizeBytes.compareTo(a.version.sizeBytes) },
        SelectionCriterion.STABLE_ORDER to { a, b -> a.version.id.value.compareTo(b.version.id.value) },
    )

    private fun better(a: Boolean, b: Boolean): Int = b.compareTo(a)

    private fun isPreferred(version: RankedVersion, preferred: VersionId?): Boolean =
        version.version.id == preferred && version.playable

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
