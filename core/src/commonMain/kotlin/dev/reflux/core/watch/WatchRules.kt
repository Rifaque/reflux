package dev.reflux.core.watch

import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
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
     */
    fun onProgress(previous: WatchState?, itemId: MediaId, positionMs: Long, durationMs: Long?, nowMs: Long): WatchState {
        val base = previous ?: WatchState(itemId)
        val position = if (positionMs < MIN_RESUME_MS) 0 else positionMs
        return base.copy(
            positionMs = position,
            durationMs = durationMs ?: base.durationMs,
            lastPlayedAtEpochMs = nowMs,
            // Re-watching a completed work keeps it completed until the user marks it unwatched.
            completed = base.completed,
        )
    }

    /** Records the end of a playback session. */
    fun onStop(previous: WatchState?, itemId: MediaId, positionMs: Long, durationMs: Long?, nowMs: Long): WatchState {
        val progressed = onProgress(previous, itemId, positionMs, durationMs, nowMs)
        if (!isCompletedAt(positionMs, progressed.durationMs)) return progressed
        return progressed.copy(positionMs = 0, completed = true, playCount = progressed.playCount + 1)
    }

    fun markWatched(previous: WatchState?, itemId: MediaId, nowMs: Long): WatchState =
        (previous ?: WatchState(itemId)).let {
            it.copy(positionMs = 0, completed = true, playCount = maxOf(it.playCount, 1), lastPlayedAtEpochMs = nowMs)
        }

    fun markUnwatched(previous: WatchState?, itemId: MediaId): WatchState =
        (previous ?: WatchState(itemId)).copy(positionMs = 0, completed = false)

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
