package dev.reflux.core.source

import dev.reflux.core.model.Availability
import dev.reflux.core.model.SourceId
import dev.reflux.core.net.Redaction
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

    /** Can transcode video on the server side. */
    SERVER_TRANSCODE,

    /** Can put original streams in another container. */
    SERVER_REMUX,

    /** Can convert audio and leave the video as it is. */
    SERVER_AUDIO_CONVERSION,

    /** Can hand out a track inside a file as a file of its own. */
    SERVER_SUBTITLE_EXTRACTION,

    /** Can convert a text subtitle to another text format. */
    SERVER_SUBTITLE_CONVERSION,

    /** Can draw a subtitle into the picture, which means transcoding the video. */
    SERVER_BURN_IN,

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
) {
    /** Addresses carry keys and headers carry tokens: what prints is neither. */
    override fun toString(): String = "PlaybackTarget(${Redaction.redact(uri)}, ${headers.size} headers)"
}

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

/** A source that can say what really happened to a playback (docs/PLAYBACK.md). */
interface PlaybackEvidenceSource : MediaSource {
    /** What the source says of the playback of [path] now; null when [path] is not a playback. Never fails. */
    suspend fun playbackEvidence(path: String): dev.reflux.core.playback.PlaybackEvidence?
}

/** What a source agreed to deliver for a plan. */
data class NegotiatedDelivery(
    val target: PlaybackTarget,
    /** A subtitle the source delivers beside the stream, for a plan that needs one. */
    val subtitle: PlaybackTarget? = null,
)

/**
 * A source that delivers what a plan needs, rather than only its files. The plan is decided already: this carries it
 * out with the source, and chooses nothing.
 */
interface NegotiatingSource : MediaSource {
    /** What to open for [plan], or null when the source could not agree to it. Never fails. */
    suspend fun negotiate(
        version: dev.reflux.core.model.MediaVersion,
        plan: dev.reflux.core.playback.PlannedPlayback,
        device: dev.reflux.core.playback.DeviceCapabilities,
    ): NegotiatedDelivery?

    /** The playback of [path] is over: nothing may keep converting for it. Never fails. */
    suspend fun release(path: String)
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

open class SourceUnavailableException(sourceId: SourceId, cause: Throwable? = null, message: String = "source unavailable: $sourceId") :
    Exception(message, cause)

/**
 * The source answered, and no longer accepts what Reflux signs in with (an expired or revoked token, a changed
 * password). It cannot be read until the user signs in again; nothing else about it changes.
 */
class SourceSignInRequiredException(sourceId: SourceId, cause: Throwable? = null) :
    SourceUnavailableException(sourceId, cause, "source needs signing in again: $sourceId")
