package dev.reflux.core.watch

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.WatchState

/**
 * Deterministic watch-state rules. Defaults are chosen so users never need to tune them.
 */
object WatchRules {
    /** Fraction of the runtime after which a work counts as watched (end credits are usually past this point). */
    const val COMPLETION_RATIO: Double = 0.90

    /** Positions earlier than this are not worth resuming. */
    const val MIN_RESUME_MS: Long = 60_000

    /** Whether a position reached during playback means the work has been watched. */
    fun isCompletedAt(positionMs: Long, durationMs: Long?): Boolean =
        durationMs != null && durationMs > 0 && positionMs >= durationMs * COMPLETION_RATIO

    /**
     * Records a periodic progress report. Never marks a work as watched: that only happens when playback ends,
     * so repeated reports past the threshold cannot inflate the play count.
     *
     * @param version the copy that is playing, when the caller knows it. The position is a time on that
     *   copy's timeline, and the state remembers which.
     */
    fun onProgress(
        previous: WatchState?,
        itemId: MediaId,
        positionMs: Long,
        durationMs: Long?,
        nowMs: Long,
        version: MediaVersion? = null,
    ): WatchState {
        val base = previous ?: WatchState(itemId)
        val position = if (positionMs < MIN_RESUME_MS) 0 else positionMs
        // The first moments of another cut do not erase where the viewer was in the one they were watching.
        if (position == 0L && version != null && Handoff.relation(base, version) == TimelineMatch.DIFFERENT) {
            return base.copy(lastPlayedAtEpochMs = nowMs)
        }
        val sameCopy = version == null || base.versionId == null || base.versionId == version.id
        return base.copy(
            positionMs = position,
            // A running time describes one copy; it is not carried over to another.
            durationMs = durationMs ?: version?.stream?.durationMs ?: base.durationMs.takeIf { sameCopy },
            lastPlayedAtEpochMs = nowMs,
            updatedAtEpochMs = nowMs,
            versionId = version?.id,
            cut = Cuts.of(version?.edition),
            // Re-watching a completed work keeps it completed until the user marks it unwatched.
            completed = base.completed,
        )
    }

    /** Records the end of a playback session. */
    fun onStop(
        previous: WatchState?,
        itemId: MediaId,
        positionMs: Long,
        durationMs: Long?,
        nowMs: Long,
        version: MediaVersion? = null,
    ): WatchState {
        val progressed = onProgress(previous, itemId, positionMs, durationMs, nowMs, version)
        if (!isCompletedAt(positionMs, progressed.durationMs)) return progressed
        // Watching a work to its end completes it, whichever copy or cut that was.
        return progressed.copy(positionMs = 0, completed = true, playCount = progressed.playCount + 1)
    }

    fun markWatched(previous: WatchState?, itemId: MediaId, nowMs: Long): WatchState =
        (previous ?: WatchState(itemId)).let {
            it.copy(positionMs = 0, completed = true, playCount = maxOf(it.playCount, 1), lastPlayedAtEpochMs = nowMs, updatedAtEpochMs = nowMs)
        }

    fun markUnwatched(previous: WatchState?, itemId: MediaId, nowMs: Long): WatchState =
        (previous ?: WatchState(itemId)).copy(positionMs = 0, completed = false, updatedAtEpochMs = nowMs)

    /** When the user last did something to [state]: the moment synchronization compares. */
    fun changedAt(state: WatchState): Long? = state.updatedAtEpochMs ?: state.lastPlayedAtEpochMs

    /**
     * One state for two records of the same work, for when two works turn out to be one. The more recent
     * activity is where the user is; nothing either record knew about having watched it is lost.
     */
    fun merge(itemId: MediaId, a: WatchState, b: WatchState): WatchState {
        val (recent, other) = if ((changedAt(b) ?: Long.MIN_VALUE) > (changedAt(a) ?: Long.MIN_VALUE)) b to a else a to b
        return recent.copy(
            itemId = itemId,
            playCount = maxOf(recent.playCount, other.playCount),
            lastPlayedAtEpochMs = listOfNotNull(recent.lastPlayedAtEpochMs, other.lastPlayedAtEpochMs).maxOrNull(),
        )
    }

    /** Where playback should start, or null to start from the beginning. */
    fun resumePosition(state: WatchState?): Long? {
        if (state == null || state.positionMs < MIN_RESUME_MS) return null
        if (isCompletedAt(state.positionMs, state.durationMs)) return null
        return state.positionMs
    }
}

/** Episode ordering and "Next Up" for shows. */
object NextUp {
    /** Canonical viewing order: by season (specials last), then episode, absolute number, or air date. */
    val episodeOrder: Comparator<Episode> = compareBy<Episode>(
        { if (it.seasonNumber == 0) Int.MAX_VALUE else it.seasonNumber },
        { it.episodeNumber ?: it.absoluteNumber ?: Int.MAX_VALUE },
        { it.airDate },
        { it.id.value },
    )

    /**
     * The episode to continue a show with, or null if the user has not started it or has finished it.
     *
     * Anchored on the most recently played regular episode: if unfinished, resume it; otherwise the next
     * unwatched episode after it. Specials never drive Next Up.
     */
    fun of(episodes: List<Episode>, states: Map<MediaId, WatchState>): Episode? {
        val ordered = episodes.filter { it.seasonNumber != 0 }.sortedWith(episodeOrder)
        val anchorIndex = ordered.indices
            .filter { states[ordered[it].id]?.lastPlayedAtEpochMs != null }
            .maxByOrNull { states.getValue(ordered[it].id).lastPlayedAtEpochMs!! }
            ?: return null
        val anchorState = states.getValue(ordered[anchorIndex].id)
        if (!anchorState.completed) return ordered[anchorIndex]
        return ordered.drop(anchorIndex + 1).firstOrNull { states[it.id]?.completed != true }
    }
}
