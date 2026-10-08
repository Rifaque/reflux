package dev.reflux.core.model

/** Reflux-owned playback history for a work. Survives source disconnection and re-identification. */
data class WatchState(
    val itemId: MediaId,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val completed: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAtEpochMs: Long? = null,
) {
    val inProgress: Boolean get() = !completed && positionMs > 0
}
