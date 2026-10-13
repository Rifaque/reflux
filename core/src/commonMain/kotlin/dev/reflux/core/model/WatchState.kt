package dev.reflux.core.model

/**
 * Reflux-owned playback history for a work: the one record of how far the user is, whichever copy played.
 * It belongs to the work, never to a file or a source, and survives source disconnection and
 * re-identification.
 *
 * [positionMs] is a time on the timeline of the copy that was playing, so the state remembers which timeline
 * that was ([durationMs], [cut], [versionId]). That is what lets the position follow the user to another
 * copy safely (`dev.reflux.core.watch.Handoff`).
 */
data class WatchState(
    val itemId: MediaId,
    val positionMs: Long = 0,
    /** The running time of the copy the position was measured on. */
    val durationMs: Long? = null,
    val completed: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAtEpochMs: Long? = null,
    /** When the user last changed this state (by playing, or by marking it), wherever that happened. */
    val updatedAtEpochMs: Long? = null,
    /** The version the position was measured on, when known. The version itself may be gone. */
    val versionId: VersionId? = null,
    /** The cut the position was measured on; null is the work's ordinary cut. */
    val cut: String? = null,
) {
    val inProgress: Boolean get() = !completed && positionMs > 0
}
