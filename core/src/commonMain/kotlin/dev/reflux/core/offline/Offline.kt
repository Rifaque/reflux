package dev.reflux.core.offline

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.VersionId
import dev.reflux.core.model.WatchState
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.versions.RankedVersion
import dev.reflux.core.versions.VersionCandidate
import dev.reflux.core.versions.VersionSelector
import dev.reflux.core.watch.Handoff
import dev.reflux.core.watch.Timeline
import dev.reflux.core.watch.TimelineMatch
import dev.reflux.core.watch.Timelines

/** Where a download is in its life. Every state is one the user can be told about in a sentence. */
enum class DownloadState {
    /** Waiting its turn. */
    QUEUED,

    /** Being set up: its source is asked for the file and room is made for it. */
    PREPARING,
    DOWNLOADING,

    /** Stopped by the user, to be continued from where it was. */
    PAUSED,

    /** The copy is on the device and is a version of its work. */
    COMPLETED,

    /** Stopped by a problem, kept with what it has so that it can be tried again. See [DownloadFailure]. */
    FAILED,

    /** Given up by the user. A cancelled download leaves nothing behind. */
    CANCELLED,

    /** It was completed, but its file is not there now (the storage it is on is away). */
    UNAVAILABLE,
}

/** Why a download stopped, in terms of what the user can do about it. */
enum class DownloadFailure {
    /** The device does not have the room, counting the reserve Reflux never fills. */
    INSUFFICIENT_STORAGE,

    /** The source could not be reached. Tried again by itself when the source is back. */
    SOURCE_UNAVAILABLE,

    /** The connection broke part-way. Tried again by itself, from where it stopped. */
    INTERRUPTED,

    /** The place downloads are kept cannot be written to. */
    STORAGE_UNAVAILABLE,

    /** The source no longer has the file. */
    MEDIA_GONE,

    /** The source does not hand out this file, or no longer allows it. */
    NOT_ALLOWED,
    UNKNOWN,
}

/**
 * One copy being made, or made, of one version of a work. It is not a work and not a second library: when
 * it completes, the copy is one more version of [itemId], on the device.
 */
data class Download(
    val id: String,
    val itemId: MediaId,
    /** The version being copied, and where it lives. The copy outlives both. */
    val originVersionId: VersionId,
    val origin: MediaLocation,
    /** Where the copy is kept, relative to the place downloads are stored. */
    val path: String,
    val state: DownloadState,
    val failure: DownloadFailure? = null,
    val bytesDownloaded: Long = 0,
    /** Null until the source has said how large the file is. */
    val totalBytes: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
) {
    /** How far along it is, 0 to 1, or null while the size is not known. */
    val progress: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (bytesDownloaded.toFloat() / it).coerceIn(0f, 1f) }

    /** Whether it still has work to do without anyone asking. */
    val active: Boolean get() = state == DownloadState.QUEUED || state == DownloadState.PREPARING || state == DownloadState.DOWNLOADING

    /** The bytes it will still write if it runs to the end. */
    val remainingBytes: Long get() = if (state == DownloadState.COMPLETED) 0 else ((totalBytes ?: 0) - bytesDownloaded).coerceAtLeast(0)
}

/** How much room the device has, as the platform reports it. */
data class StorageSnapshot(
    val freeBytes: Long,
    /** The size of the volume downloads are kept on, when known. */
    val totalBytes: Long? = null,
    /** What downloads already under way will still write. */
    val committedBytes: Long = 0,
)

/**
 * How much of the device Reflux is willing to fill. A reserve is always left free, because a device that
 * is full stops working properly and the user did not ask for that: the larger of [minimumReserveBytes]
 * and [reserveFraction] of the volume. What downloads under way will still write is already spoken for.
 */
data class StoragePolicy(
    val minimumReserveBytes: Long = 500L * 1024 * 1024,
    val reserveFraction: Double = 0.05,
) {
    fun reserveBytes(storage: StorageSnapshot): Long =
        maxOf(minimumReserveBytes, ((storage.totalBytes ?: 0) * reserveFraction).toLong())

    /** The bytes a new download may take. Never negative. */
    fun usableBytes(storage: StorageSnapshot): Long =
        (storage.freeBytes - reserveBytes(storage) - storage.committedBytes).coerceAtLeast(0)
}

/** Why a work is in a preparation plan. */
enum class OfflineReason {
    /** The user asked for it. */
    CHOSEN,

    /** They are part-way through it. */
    IN_PROGRESS,

    /** It is what comes after what they are watching. */
    NEXT_EPISODE,
}

/** Why a work that was considered is not being downloaded. */
enum class OfflineSkipReason {
    /** A copy this device can play is already on it. */
    ALREADY_ON_DEVICE,
    ALREADY_DOWNLOADING,
    NOT_ENOUGH_STORAGE,

    /** Every copy is on a source that cannot be reached right now. */
    SOURCE_UNAVAILABLE,

    /** Its sources do not hand out files to keep. */
    NOT_DOWNLOADABLE,

    /** No copy of it plays on this device. */
    NOT_PLAYABLE_HERE,
}

/** A work to consider for offline, with everything the decision needs. */
data class OfflineRequest(
    val itemId: MediaId,
    val reason: OfflineReason,
    val versions: List<VersionCandidate>,
    /** The versions whose source hands out the file itself. */
    val downloadable: Set<VersionId>,
    /** The version the user pinned for this work. */
    val pinned: VersionId? = null,
    val watchState: WatchState? = null,
    /** Whether a download of this work is already waiting or under way. */
    val pending: Boolean = false,
)

data class PlannedDownload(
    val itemId: MediaId,
    val reason: OfflineReason,
    val version: MediaVersion,
    /** What it will take on the device; an estimate when [sizeKnown] is false. */
    val bytes: Long,
    val sizeKnown: Boolean,
    /** Whether a lesser version was taken because the first choice does not fit in the room there is. */
    val madeToFit: Boolean = false,
)

data class OfflineSkip(val itemId: MediaId, val reason: OfflineSkipReason)

/** What Reflux proposes to download, what it will cost, and what it left out and why. */
data class OfflinePlan(
    val downloads: List<PlannedDownload>,
    val skipped: List<OfflineSkip>,
    val requiredBytes: Long,
    /** What may be used: free space less the reserve and less what is already on its way. */
    val usableBytes: Long,
    val freeBytes: Long,
) {
    val empty: Boolean get() = downloads.isEmpty()
}

/**
 * Decides what to download so that the given works can be watched without their sources. Deterministic and
 * explainable: the same library, device, and storage always give the same plan.
 *
 * The version is the one Best Version would play on this device, with two differences that only make sense
 * for a copy that is kept: among versions the device shows and plays equally well the smallest is taken,
 * and when the first choice does not fit, the best one that does (of the same cut) is taken instead. A
 * version the user pinned is never swapped for another: it is downloaded, or the work is left out and the
 * reason given.
 */
object OfflinePlanner {
    fun plan(
        requests: List<OfflineRequest>,
        device: DeviceCapabilities,
        storage: StorageSnapshot,
        policy: StoragePolicy = StoragePolicy(),
    ): OfflinePlan {
        val usable = policy.usableBytes(storage)
        var remaining = usable
        val downloads = ArrayList<PlannedDownload>()
        val skipped = ArrayList<OfflineSkip>()
        for (request in requests.distinctBy { it.itemId }) {
            when (val outcome = decide(request, device, remaining)) {
                is Outcome.Take -> {
                    downloads += outcome.download
                    remaining -= outcome.download.bytes
                }
                is Outcome.Skip -> skipped += OfflineSkip(request.itemId, outcome.reason)
            }
        }
        return OfflinePlan(downloads, skipped, downloads.sumOf { it.bytes }, usable, storage.freeBytes)
    }

    private sealed interface Outcome {
        data class Take(val download: PlannedDownload) : Outcome
        data class Skip(val reason: OfflineSkipReason) : Outcome
    }

    private fun decide(request: OfflineRequest, device: DeviceCapabilities, remaining: Long): Outcome {
        val ranked = VersionSelector.select(request.versions, device, request.pinned, request.watchState).ranked
        val supported = ranked.filter { it.assessment.verdict != dev.reflux.core.playback.PlaybackVerdict.UNSUPPORTED }
        if (supported.isEmpty()) return Outcome.Skip(OfflineSkipReason.NOT_PLAYABLE_HERE)
        // A copy that is here and plays is all that offline means. A copy on the device whose storage is
        // away right now does not count.
        if (supported.any { it.candidate.locality == SourceLocality.DEVICE && it.candidate.availability != Availability.UNAVAILABLE && continues(it, request) }) {
            return Outcome.Skip(OfflineSkipReason.ALREADY_ON_DEVICE)
        }
        if (request.pending) return Outcome.Skip(OfflineSkipReason.ALREADY_DOWNLOADING)

        val remote = supported.filter { it.candidate.locality != SourceLocality.DEVICE }
        val downloadable = remote.filter { it.version.id in request.downloadable }
        if (downloadable.isEmpty()) return Outcome.Skip(if (remote.isEmpty()) OfflineSkipReason.NOT_PLAYABLE_HERE else OfflineSkipReason.NOT_DOWNLOADABLE)
        val reachable = downloadable.filter { it.playable }
        if (reachable.isEmpty()) return Outcome.Skip(OfflineSkipReason.SOURCE_UNAVAILABLE)

        fun planned(version: RankedVersion, madeToFit: Boolean): PlannedDownload {
            val (bytes, known) = sizeOf(version.version)
            return PlannedDownload(request.itemId, request.reason, version.version, bytes, known, madeToFit)
        }

        // What the user pinned is what they get, or nothing: never something else in its place.
        reachable.firstOrNull { it.version.id == request.pinned }?.let { pinned ->
            val download = planned(pinned, madeToFit = false)
            return if (download.bytes <= remaining) Outcome.Take(download) else Outcome.Skip(OfflineSkipReason.NOT_ENOUGH_STORAGE)
        }

        val first = reachable.first()
        // Only copies of the cut that would be played are stand-ins for it; a different cut is a different thing to watch.
        val sameCut = reachable.filter { Timelines.compare(Timeline.of(it.version), Timeline.of(first.version)) != TimelineMatch.DIFFERENT }
        // Best Version's order, except that versions equal in everything this device can show are told
        // apart by size, smallest first: on a shelf of limited room, the same picture in fewer bytes wins.
        val ordered = buildList {
            var start = 0
            while (start < sameCut.size) {
                var end = start + 1
                while (end < sameCut.size && VersionSelector.equivalent(sameCut[start], sameCut[end], device, request.pinned, request.watchState)) end++
                addAll(sameCut.subList(start, end).sortedBy { sizeOf(it.version).first })
                start = end
            }
        }
        val choice = ordered.firstOrNull { sizeOf(it.version).first <= remaining } ?: return Outcome.Skip(OfflineSkipReason.NOT_ENOUGH_STORAGE)
        return Outcome.Take(planned(choice, madeToFit = choice.version.id != ordered.first().version.id))
    }

    /** A copy on the device only stands for the work if it does not put the viewer on a different cut from the one they are in. */
    private fun continues(version: RankedVersion, request: OfflineRequest): Boolean =
        Handoff.relation(request.watchState, version.version) != TimelineMatch.DIFFERENT

    /** The size a version will take, and whether that is known or worked out from its bitrate or running time. */
    fun sizeOf(version: MediaVersion): Pair<Long, Boolean> {
        if (version.sizeBytes > 0) return version.sizeBytes to true
        val duration = version.stream.durationMs
        val bitrate = version.stream.bitrateBps
        return when {
            duration != null && bitrate != null && bitrate > 0 -> duration / 1000 * bitrate / 8
            duration != null -> duration / 1000 * ASSUMED_BYTES_PER_SECOND
            else -> ASSUMED_BYTES
        } to false
    }

    /** About 2 GB an hour, for a file whose source gives neither its size nor its bitrate. */
    private const val ASSUMED_BYTES_PER_SECOND = 600_000L
    private const val ASSUMED_BYTES = 4L * 1024 * 1024 * 1024
}

/** Which of a version's separate subtitle files are worth keeping with a copy of it. */
object OfflineAssets {
    /**
     * Those in a language the viewer reads (forced ones included), and those that do not say what language
     * they are in; never every language a release happens to carry. With no preference known, all of them.
     */
    fun <T> subtitles(available: List<T>, languageOf: (T) -> String?, preferredLanguages: List<String>): List<T> {
        if (preferredLanguages.isEmpty()) return available
        val wanted = preferredLanguages.map(::primary).toSet()
        return available.filter { subtitle -> languageOf(subtitle)?.let { primary(it) in wanted } ?: true }
    }

    /** "en-US", "eng", and "en" are the same language for this purpose. */
    private fun primary(language: String): String = language.lowercase().substringBefore('-').substringBefore('_').let { ISO_639_2[it] ?: it }

    private val ISO_639_2 = mapOf(
        "eng" to "en", "fra" to "fr", "fre" to "fr", "deu" to "de", "ger" to "de", "spa" to "es", "ita" to "it", "por" to "pt",
        "nld" to "nl", "dut" to "nl", "jpn" to "ja", "kor" to "ko", "zho" to "zh", "chi" to "zh", "rus" to "ru", "ara" to "ar",
        "hin" to "hi", "swe" to "sv", "nor" to "no", "dan" to "da", "fin" to "fi", "pol" to "pl", "tur" to "tr",
    )
}
