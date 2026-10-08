package dev.reflux.library

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.playback.WatchReporter
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.WatchStateSyncSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Reports playback to the library and, when the playing version lives on a source that keeps its own watch
 * state (a media server), to that source as well. The library is always updated first and synchronously;
 * source reports are best-effort and never block or fail playback.
 */
class SyncingWatchReporter(
    private val library: Library,
    private val version: MediaVersion,
    private val source: MediaSource?,
    private val scope: CoroutineScope,
) : WatchReporter {
    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        library.progress(itemId, positionMs, durationMs)
        sync { it.reportProgress(version.location.path, positionMs, paused = false) }
    }

    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        library.stopped(itemId, positionMs, durationMs)
        sync { it.reportStopped(version.location.path, positionMs) }
    }

    private fun sync(action: suspend (WatchStateSyncSource) -> Unit) {
        val syncing = source as? WatchStateSyncSource ?: return
        scope.launch { bestEffort { action(syncing) } }
    }
}

/**
 * Marks a work (or every episode of a show or season) watched or unwatched in Reflux, then on every syncing
 * source that holds one of its versions. Returns the number of source updates that failed (e.g. offline).
 */
suspend fun Library.setWatchedEverywhere(itemId: MediaId, watched: Boolean, sources: (SourceId) -> MediaSource?): Int {
    setWatched(itemId, watched)
    var failures = 0
    for (playable in playableIdsOf(itemId)) {
        for (info in versions(playable)) {
            val source = sources(info.version.location.sourceId) as? WatchStateSyncSource ?: continue
            if (!bestEffort { source.setPlayed(info.version.location.path, watched) }) failures++
        }
    }
    return failures
}

private suspend fun bestEffort(action: suspend () -> Unit): Boolean = try {
    action()
    true
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    false
}
