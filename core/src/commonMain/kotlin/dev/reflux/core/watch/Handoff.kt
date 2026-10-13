package dev.reflux.core.watch

import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.WatchState

/** Why playback starts where it does. */
enum class ResumeBasis {
    /** Nothing to resume. */
    NONE,

    /** The position was measured on this very version. */
    SAME_VERSION,

    /** Another copy on the same timeline: the position is the same moment. */
    SAME_TIMELINE,

    /** Another copy with nothing known against it; a running time is missing to confirm it. */
    UNVERIFIED,

    /**
     * A different timeline (another cut, or a materially different running time). The position was moved to
     * the latest point that cannot be past what the viewer has seen.
     */
    ADJUSTED,

    /**
     * A different timeline with nothing to measure the difference by. Playback starts at the beginning and
     * the remembered position stays as it is.
     */
    NOT_TRANSFERABLE,
}

/** Where playback of a version starts, and why. A null [positionMs] is the beginning. */
data class ResumePoint(val positionMs: Long?, val basis: ResumeBasis) {
    /** Whether the viewer lands on the moment they left, as opposed to a cautious approximation of it. */
    val exact: Boolean
        get() = basis == ResumeBasis.SAME_VERSION || basis == ResumeBasis.SAME_TIMELINE || basis == ResumeBasis.UNVERIFIED

    companion object {
        val START = ResumePoint(null, ResumeBasis.NONE)
    }
}

/**
 * Carries a work's watch position from the copy it was measured on to the copy about to play.
 *
 * Progress belongs to the work, so it follows the viewer to whichever version plays next. The position
 * itself is a time on one copy's timeline: it is carried over unchanged to a copy on the same timeline, and
 * never blindly to a different one.
 */
object Handoff {
    /** How [version]'s timeline relates to the one the viewer is part-way through, or null when nothing is in progress. */
    fun relation(state: WatchState?, version: MediaVersion): TimelineMatch? {
        if (state == null || WatchRules.resumePosition(state) == null) return null
        val match = Timelines.compare(Timeline.of(state), Timeline.of(version))
        return if (sameVersion(state, version, match)) TimelineMatch.SAME else match
    }

    /** Where playing [version] should start for a viewer whose progress is [state]. */
    fun resume(state: WatchState?, version: MediaVersion): ResumePoint {
        val position = WatchRules.resumePosition(state) ?: return ResumePoint.START
        val match = Timelines.compare(Timeline.of(state!!), Timeline.of(version))
        return when {
            sameVersion(state, version, match) -> ResumePoint(position, ResumeBasis.SAME_VERSION)
            match == TimelineMatch.SAME -> ResumePoint(position, ResumeBasis.SAME_TIMELINE)
            match == TimelineMatch.UNVERIFIED -> ResumePoint(position, ResumeBasis.UNVERIFIED)
            else -> acrossTimelines(position, state.durationMs, version.stream.durationMs)
        }
    }

    /**
     * A version is a location, and what is at a location can be replaced: the same path may hold another
     * cut tomorrow. The version the position was measured on only counts as that while it still runs as long.
     */
    private fun sameVersion(state: WatchState, version: MediaVersion, match: TimelineMatch): Boolean =
        state.versionId == version.id && match != TimelineMatch.DIFFERENT

    /**
     * Cuts of a work differ by material that is in one and not in the other. Going to a longer copy, the
     * same position is at or before the moment the viewer left; going to a shorter one, at most the
     * difference in running time has gone missing before it. Either way the viewer may see a little again
     * and is never put past something unseen.
     */
    private fun acrossTimelines(position: Long, from: Long?, to: Long?): ResumePoint {
        if (from == null || to == null || from <= 0 || to <= 0) return ResumePoint(null, ResumeBasis.NOT_TRANSFERABLE)
        val cautious = position - maxOf(0, from - to)
        if (cautious < WatchRules.MIN_RESUME_MS) return ResumePoint(null, ResumeBasis.NOT_TRANSFERABLE)
        return ResumePoint(cautious, ResumeBasis.ADJUSTED)
    }
}
