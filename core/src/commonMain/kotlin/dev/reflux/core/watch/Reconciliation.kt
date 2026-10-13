package dev.reflux.core.watch

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.WatchState
import dev.reflux.core.source.SourceUserState
import kotlin.math.abs

/**
 * What a source said about a work the last time it was compared with Reflux's state.
 *
 * Remembering it is what tells "the source changed" from "the source still says what it said": a value that
 * was already weighed once never gets to overwrite anything again. [reconciledAtEpochMs] is when the two
 * last had nothing left to settle; a change in Reflux after that moment is one the source has not heard of.
 */
data class SyncBaseline(
    val played: Boolean,
    val positionMs: Long,
    val lastPlayedAtEpochMs: Long?,
    val reconciledAtEpochMs: Long,
)

/** What a source has to be told so that it agrees with Reflux. A null field is left as the source has it. */
data class SourceUpdate(val played: Boolean?, val positionMs: Long?)

enum class SyncDecision {
    /** Reflux and the source say the same. */
    IN_AGREEMENT,

    /** The source's state is the newer one and became Reflux's. */
    SOURCE_ADOPTED,

    /** Reflux's state is the newer one and stays. */
    REFLUX_KEPT,
}

data class Reconciliation(
    val decision: SyncDecision,
    /** Reflux's new state, or null when it stays as it is. */
    val state: WatchState?,
    /** What the source should be told, or null when nothing. */
    val update: SourceUpdate?,
    /** What to remember about the source once [update], if any, has been delivered. */
    val baseline: SyncBaseline,
)

/**
 * Settles Reflux's watch state for a work against what one source says about it.
 *
 * Reflux's state is the canonical one. A source's record is one more witness of what the user did: it is
 * adopted when it is news (it changed, it says something, and Reflux's state did not change after it), and
 * otherwise it is the source that is behind. Deterministic, and independent of what kind of source it is: adapters translate
 * their native records into [SourceUserState] and nothing here knows where it came from.
 */
object WatchReconciler {
    /**
     * @param local Reflux's state for the work, if any.
     * @param source what the source says now.
     * @param baseline what the source said at the last comparison; null the first time.
     * @param sourceVersion the source's copy of the work; the source's position is a time on its timeline.
     */
    fun reconcile(
        itemId: MediaId,
        local: WatchState?,
        source: SourceUserState,
        baseline: SyncBaseline?,
        sourceVersion: MediaVersion?,
        nowMs: Long,
    ): Reconciliation {
        val seen = SyncBaseline(source.played, source.positionMs, source.lastPlayedAtEpochMs, nowMs)
        if (agree(local, source, sourceVersion)) return Reconciliation(SyncDecision.IN_AGREEMENT, null, null, seen)

        val localAt = local?.let(WatchRules::changedAt)
        val sourceChanged = baseline == null || baseline.played != source.played ||
            baseline.positionMs != source.positionMs || baseline.lastPlayedAtEpochMs != source.lastPlayedAtEpochMs
        val localChanged = if (baseline == null) local != null else localAt != null && localAt > baseline.reconciledAtEpochMs
        val sourceAt = source.lastPlayedAtEpochMs
        val sourceWins = when {
            !sourceChanged -> false
            // An empty, undated record is a source that was reset, rebuilt, or un-marked, and there is no
            // telling which. Reflux's history is never erased by a source falling silent about a work.
            !localChanged -> saysSomething(source) || sourceAt != null
            // Both moved since they last agreed: the later one is where the user is. An undated source record
            // cannot show that it is the later one.
            else -> sourceAt != null && (localAt == null || sourceAt > localAt)
        }
        if (sourceWins) return Reconciliation(SyncDecision.SOURCE_ADOPTED, adopt(itemId, local, source, sourceVersion, nowMs), null, seen)

        // Reflux's state stands. The source is told only once the two have been in step before; a first
        // meeting never rewrites what a server knows.
        val update = if (baseline != null && localChanged && local != null) updateFor(local, source, sourceVersion) else null
        // Until the update is delivered, the moment of the last agreement stays where it was, so that a
        // failed delivery is tried again.
        val remembered = if (update != null) seen.copy(reconciledAtEpochMs = baseline!!.reconciledAtEpochMs) else seen
        return Reconciliation(SyncDecision.REFLUX_KEPT, null, update, remembered)
    }

    private fun saysSomething(source: SourceUserState): Boolean = source.played || resumable(source.positionMs) > 0

    /** Positions too early to resume from are the beginning, on either side. */
    private fun resumable(positionMs: Long): Long = if (positionMs < WatchRules.MIN_RESUME_MS) 0 else positionMs

    private fun agree(local: WatchState?, source: SourceUserState, sourceVersion: MediaVersion?): Boolean {
        if (local == null) return !saysSomething(source)
        if (local.completed != source.played) return false
        if (local.completed) return true
        val here = positionOn(local, sourceVersion) ?: return false
        return abs(here - resumable(source.positionMs)) < POSITION_TOLERANCE_MS
    }

    /** Reflux's position as a time on the source's copy, or null when it cannot be stated exactly there. */
    private fun positionOn(local: WatchState, sourceVersion: MediaVersion?): Long? {
        val position = resumable(local.positionMs)
        if (position == 0L || sourceVersion == null) return position
        return Handoff.resume(local, sourceVersion).takeIf { it.exact }?.positionMs
    }

    private fun adopt(itemId: MediaId, local: WatchState?, source: SourceUserState, sourceVersion: MediaVersion?, nowMs: Long) = WatchState(
        itemId = itemId,
        positionMs = if (source.played) 0 else source.positionMs,
        durationMs = sourceVersion?.stream?.durationMs,
        completed = source.played,
        playCount = maxOf(local?.playCount ?: 0, source.playCount, if (source.played) 1 else 0),
        lastPlayedAtEpochMs = source.lastPlayedAtEpochMs ?: local?.lastPlayedAtEpochMs,
        // The state is as old as what the user did, not as old as the moment Reflux heard of it, so that a
        // later record from another source can still be recognized as later.
        updatedAtEpochMs = source.lastPlayedAtEpochMs ?: nowMs,
        versionId = sourceVersion?.id,
        cut = Cuts.of(sourceVersion?.edition),
    )

    private fun updateFor(local: WatchState, source: SourceUserState, sourceVersion: MediaVersion?): SourceUpdate? {
        val played = local.completed.takeIf { it != source.played }
        // Marking a source's item not played makes it forget its position as well.
        val sourcePosition = if (played == false) 0 else resumable(source.positionMs)
        // A position is only sent where it is the same moment on the source's copy.
        val position = if (local.completed) null else positionOn(local, sourceVersion)?.takeIf { abs(it - sourcePosition) >= POSITION_TOLERANCE_MS }
        return if (played == null && position == null) null else SourceUpdate(played, position)
    }

    /** Positions closer than this are the same position (sources store them in their own units). */
    const val POSITION_TOLERANCE_MS: Long = 1_000
}
