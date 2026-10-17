package dev.reflux.core.source

import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import kotlinx.coroutines.flow.Flow

/** How close a source is to the device. Used to prefer local copies when versions are otherwise equivalent. */
enum class SourceLocality { DEVICE, LOCAL_NETWORK, REMOTE }

/**
 * Things a source may or may not be able to do.
 *
 * Sources are allowed to lack capabilities; product features check for them instead of assuming them.
 */
enum class SourceCapability {
    /** Lists files that Reflux parses and identifies itself (local folders, SMB, WebDAV). */
    ENUMERATE_FILES,

    /** Can notify about changes instead of requiring full rescans. */
    CHANGE_NOTIFICATIONS,

    /** Provides its own identified catalog and metadata (media servers). */
    PROVIDES_CATALOG,

    /** Can synchronize watch state back to the source. */
    WATCH_STATE_SYNC,

    /** Can transcode on the server side. */
    SERVER_TRANSCODE,

    /** Hands out its files whole, so that a copy can be kept on the device (see [DownloadableSource]). */
    DOWNLOAD,
}

data class SourceDescriptor(
    val id: SourceId,
    /** Adapter type, e.g. `local`. Only adapters interpret this value. */
    val type: String,
    val displayName: String,
    val locality: SourceLocality,
    val capabilities: Set<SourceCapability>,
)

/** Something a playback engine can open. */
data class PlaybackTarget(
    /** A `file:` URI, `content:` URI, or network URL. */
    val uri: String,
    val headers: Map<String, String> = emptyMap(),
)

/** A source adapter. Adapters translate a concrete backend into Media Core concepts. */
interface MediaSource {
    val descriptor: SourceDescriptor

    /** Cheap reachability check. Must not throw for ordinary unavailability. */
    suspend fun availability(): Availability

    /** Resolves a source-relative path into something a player can open. */
    suspend fun playbackTarget(path: String): PlaybackTarget
}

/**
 * A source whose files can be fetched whole to keep a copy on the device. A source that can only show a
 * file (a transcoding stream, a protected service) is not one, and nothing assumes it is.
 */
interface DownloadableSource : MediaSource {
    /** What to fetch to get the file at [path] as it is, byte for byte: never a transcode of it. */
    suspend fun downloadTarget(path: String): PlaybackTarget
}

/** A file reported by a file-enumerating source. */
data class SourceFile(
    /** Source-relative path with `/` separators. */
    val path: String,
    val sizeBytes: Long,
    val modifiedAtEpochMs: Long,
    /**
     * Not a file: a folder at [path] that is there but could not be listed this time (a connection lost
     * partway through a scan, a mount that stopped answering, permissions). Not being able to look is not
     * evidence that anything below it is gone.
     */
    val unreadableFolder: Boolean = false,
) {
    companion object {
        /** A folder at [path] that is there but could not be listed (see [unreadableFolder]). */
        fun folderNotRead(path: String) = SourceFile(path, 0, 0, unreadableFolder = true)
    }
}

/**
 * A source that lists files for Reflux to identify.
 *
 * [files] walks the source, applying [ScanRules] to skip directories and irrelevant files early.
 * It throws [SourceUnavailableException] if the source cannot be reached at all. A folder below the root
 * that cannot be listed is reported with [SourceFile.folderNotRead] and the walk goes on; a scan that met
 * one keeps what it knew and did not see. Other unreadable entries (a file that vanished, a broken link)
 * are skipped.
 */
interface FileEnumeratingSource : MediaSource {
    fun files(): Flow<SourceFile>
}

/**
 * A source that can signal changes, so the library updates without polling.
 *
 * Signals are coarse ("something changed") and may be spurious; consumers debounce them and rescan.
 * The flow completes or fails when watching is impossible; consumers then fall back to periodic scans.
 */
interface ChangeNotifyingSource : MediaSource {
    fun changes(): Flow<Unit>
}

class SourceUnavailableException(sourceId: SourceId, cause: Throwable? = null) :
    Exception("source unavailable: $sourceId", cause)
