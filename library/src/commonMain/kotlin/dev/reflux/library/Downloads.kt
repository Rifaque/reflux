package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.model.Episode
import dev.reflux.core.model.ExternalSubtitle
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.VersionId
import dev.reflux.core.offline.Download
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflineReason
import dev.reflux.core.offline.OfflineRequest
import dev.reflux.core.offline.StorageSnapshot
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.versions.VersionCandidate
import dev.reflux.core.watch.NextUp

/**
 * The place downloads are kept, as a platform provides it: private to Reflux, written only by Reflux.
 * Paths are relative to it and use `/`.
 */
interface DownloadStore {
    /** Whether the place can be used right now. False when its storage is away. */
    fun available(): Boolean

    /** What a player opens to play the file at [path]. */
    fun uri(path: String): String

    /** The size of the file at [path], or null when there is none. */
    fun length(path: String): Long?

    /** Opens [path] for writing from [offset], keeping what is before it and dropping what is after. */
    fun open(path: String, offset: Long): DownloadSink

    /** Makes the finished file at [from] the file at [to]. */
    fun move(from: String, to: String)

    fun delete(path: String)

    /** The room on the storage downloads are kept on. */
    fun storage(): StorageSnapshot
}

interface DownloadSink {
    fun write(bytes: ByteArray, length: Int)
    fun close()
}

/** Receives a file as it arrives. */
interface DownloadReceiver {
    /**
     * Called once, before any bytes: where in the file the transfer really starts (a source that cannot
     * continue part-way starts again from 0), and how large the whole file is, when the source says.
     */
    suspend fun started(offset: Long, totalBytes: Long?)

    suspend fun bytes(chunk: ByteArray, length: Int)
}

/** Fetches what a source hands out. The one thing a download needs from the network. */
interface DownloadTransport {
    /** Fetches [target] from [offset] on, or throws [DownloadException] saying what went wrong. */
    suspend fun fetch(target: PlaybackTarget, offset: Long, receiver: DownloadReceiver)
}

class DownloadException(val failure: DownloadFailure, message: String? = null, cause: Throwable? = null) : Exception(message ?: failure.name, cause)

/**
 * The copies Reflux has made, as a source: on the device, read-only to everything but Reflux, and with
 * nothing to scan, because each copy is registered as a version of the work it was made from.
 */
class DownloadsSource(private val store: DownloadStore) : MediaSource {
    override val descriptor = SourceDescriptor(ID, TYPE, "Downloaded", SourceLocality.DEVICE, emptySet())

    override suspend fun availability(): Availability = if (store.available()) Availability.AVAILABLE else Availability.UNAVAILABLE

    override suspend fun playbackTarget(path: String): PlaybackTarget = PlaybackTarget(store.uri(path))

    companion object {
        const val TYPE: String = "downloads"
        val ID: SourceId = StableIds.sourceId(TYPE, "reflux")
    }
}

internal fun dev.reflux.library.db.Download.toModel() = Download(
    id = id,
    itemId = MediaId(item_id),
    originVersionId = VersionId(origin_version_id),
    origin = MediaLocation(SourceId(origin_source_id), origin_path),
    path = path,
    state = enumOrNull<DownloadState>(state) ?: DownloadState.FAILED,
    failure = enumOrNull<DownloadFailure>(failure),
    bytesDownloaded = bytes_downloaded,
    totalBytes = total_bytes,
    createdAtEpochMs = created_at,
    updatedAtEpochMs = updated_at,
    completedAtEpochMs = completed_at,
)

/** Every download there is, oldest first. */
fun Library.downloads(): List<Download> = queries.allDownloads().executeAsList().map { it.toModel() }

fun Library.download(id: String): Download? = queries.downloadById(id).executeAsOneOrNull()?.toModel()

/** The downloads of a movie or episode, or of every episode of a show or season. */
fun Library.downloadsOf(itemId: MediaId): List<Download> =
    playableIdsOf(itemId).flatMap { queries.downloadsOfItem(it.value).executeAsList() }.map { it.toModel() }

/** The version a completed download is in the library, when its copy is registered. */
fun Library.copyOf(download: Download): VersionInfo? = version(StableIds.versionId(MediaLocation(DownloadsSource.ID, download.path)))

/** The version pinned for [itemId] and, when it was kept on the device, its copy: either is the user's choice. */
fun Library.pinnedVersions(itemId: MediaId): Set<VersionId> {
    val pin = queries.preferredVersion(itemId.value).executeAsOneOrNull() ?: return emptySet()
    val copy = queries.downloadOfOrigin(pin).executeAsOneOrNull()?.let { StableIds.versionId(MediaLocation(DownloadsSource.ID, it.path)) }
    return setOfNotNull(VersionId(pin), copy)
}

/**
 * Makes [versionId] the version Play always uses for [itemId], or with null lets Best Version choose again.
 * A copy kept on the device stands for the version it was made from, so the pin is the origin's and outlives
 * the copy being removed (and a pinned origin is played from its copy while there is one, [Library.selectVersion]).
 */
fun Library.pinVersion(itemId: MediaId, versionId: VersionId?) {
    val origin = versionId?.let { id ->
        queries.downloadsOfItem(itemId.value).executeAsList()
            .firstOrNull { StableIds.versionId(MediaLocation(DownloadsSource.ID, it.path)) == id }
            ?.origin_version_id?.let(::VersionId)
    }
    setPreferredVersion(itemId, origin ?: versionId)
}

/**
 * What each copy kept on the device stands for when it is chosen: the version it was made from ([pinVersion]),
 * so a copy and its origin are one choice, not two.
 */
private fun copyOrigins(downloads: List<Download>): Map<VersionId, VersionId> =
    downloads.associate { StableIds.versionId(MediaLocation(DownloadsSource.ID, it.path)) to it.originVersionId }

/** How many of [versions] (of [itemId]) there are to choose between, a copy on the device and its origin counting once. */
fun Library.versionChoices(itemId: MediaId, versions: List<VersionId>): Int {
    val origins = copyOrigins(downloadsOf(itemId))
    return versions.map { origins[it] ?: it }.distinct().size
}

/** For every episode of [showId] that has a version, how many there are to choose between, counted as [versionChoices] does. */
fun Library.episodeVersionChoices(showId: MediaId): Map<MediaId, Int> {
    val origins = copyOrigins(downloads())
    return queries.versionIdsOfShow(showId.value).executeAsList()
        .groupBy({ MediaId(it.item_id) }, { origins[VersionId(it.id)] ?: VersionId(it.id) })
        .mapValues { (_, choices) -> choices.distinct().size }
}

/** Whether a work's sources are the user's own, as opposed to Reflux's copies. */
fun SourceRecord.isDownloads(): Boolean = type == DownloadsSource.TYPE

/** The sources the user added. Where downloads are kept is Reflux's own and is not one of them. */
fun Library.userSources(): List<SourceRecord> = sources().filterNot { it.isDownloads() }

/**
 * Brings the versions in the downloads source in line with the downloads that are complete: each is a
 * version of the work its origin belongs to now (so a correction to the origin's identity carries over),
 * or of the work it last belonged to when the origin is gone. A copy whose file is missing while the
 * storage is there is no longer a version; with the storage away, everything is kept and marked as away.
 */
fun Library.syncDownloads() {
    val source = queries.sourceOfType(DownloadsSource.TYPE).executeAsOneOrNull() ?: return
    val sourceId = SourceId(source.id)
    val store = downloadStore
    val reachable = store?.available() ?: true
    queries.setSourceAvailability((if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE).name, now(), source.id)
    if (!reachable) return
    database.transaction {
        val versions = queries.allDownloads().executeAsList().mapNotNull { row ->
            val state = enumOrNull<DownloadState>(row.state)
            if (state != DownloadState.COMPLETED && state != DownloadState.UNAVAILABLE) return@mapNotNull null
            val size = if (store == null) row.total_bytes ?: row.bytes_downloaded else store.length(row.path)
            if (size == null) {
                if (state != DownloadState.UNAVAILABLE) queries.setDownloadState(DownloadState.UNAVAILABLE.name, null, now(), row.id)
                return@mapNotNull null
            }
            if (state == DownloadState.UNAVAILABLE) queries.setDownloadState(DownloadState.COMPLETED.name, null, now(), row.id)
            val location = MediaLocation(sourceId, row.path)
            val origin = version(VersionId(row.origin_version_id))?.version
            val known = origin ?: version(StableIds.versionId(location))?.version ?: return@mapNotNull null
            val itemId = origin?.itemId ?: MediaId(row.item_id)
            if (rawItem(itemId) == null) return@mapNotNull null
            if (itemId.value != row.item_id) queries.setDownloadItem(itemId.value, row.id)
            MediaVersion(
                id = StableIds.versionId(location),
                itemId = itemId,
                location = location,
                sizeBytes = size,
                modifiedAtEpochMs = row.completed_at ?: row.updated_at,
                stream = known.stream,
                streamOrigin = known.streamOrigin,
                edition = known.edition,
                part = known.part,
                externalSubtitles = queries.subtitlesOfDownload(row.id).executeAsList().filter { it.fetched != 0L }.map { subtitle ->
                    ExternalSubtitle(
                        MediaLocation(sourceId, subtitle.path), enumOrNull<SubtitleFormat>(subtitle.format) ?: SubtitleFormat.UNKNOWN,
                        subtitle.language, subtitle.forced != 0L, subtitle.hearing_impaired != 0L,
                    )
                },
            )
        }
        writer.applyCopies(sourceId, versions)
    }
}

/**
 * What the planner needs to know about [itemId] (a movie or an episode) to decide on a download for it.
 * A version is downloadable when its source says it hands out files and is there to be asked.
 */
fun Library.offlineRequest(
    itemId: MediaId,
    reason: OfflineReason,
    sources: (SourceId) -> MediaSource?,
    scope: LibraryScope = LibraryScope.Unified,
): OfflineRequest {
    val versions = versions(itemId, scope)
    val pinned = queries.preferredVersion(itemId.value).executeAsOneOrNull()?.let(::VersionId)
    return OfflineRequest(
        itemId = itemId,
        reason = reason,
        versions = versions.map { VersionCandidate(it.version, it.availability, it.locality) },
        downloadable = versions.filter { info ->
            val source = sources(info.version.location.sourceId)
            source is DownloadableSource && SourceCapability.DOWNLOAD in source.descriptor.capabilities
        }.map { it.version.id }.toSet(),
        pinned = pinned,
        watchState = watchState(itemId),
        pending = queries.downloadsOfItem(itemId.value).executeAsList().any { row ->
            enumOrNull<DownloadState>(row.state).let { it != DownloadState.COMPLETED && it != DownloadState.UNAVAILABLE }
        },
    )
}

/**
 * What to keep for watching without a connection, when the user has not said: what they are in the middle
 * of, most recent first, and for a show the episode after the one they are on. No more than [limit] works.
 */
fun Library.offlineSuggestions(limit: Int = 8): List<Pair<MediaId, OfflineReason>> {
    val result = LinkedHashMap<MediaId, OfflineReason>()
    for (entry in continueWatching(limit)) {
        if (result.size >= limit) break
        val item = entry.item
        val started = entry.watchState?.inProgress == true
        result.getOrPut(item.id) { if (started) OfflineReason.IN_PROGRESS else OfflineReason.NEXT_EPISODE }
        // Someone part-way through an episode will want the next one before they are back online.
        if (item is Episode && started) following(item, 1).forEach { result.getOrPut(it) { OfflineReason.NEXT_EPISODE } }
    }
    return result.entries.take(limit).map { it.key to it.value }
}

/**
 * The works a request for [itemId] stands for: a movie or an episode is itself; a season is every episode
 * of it, which is what choosing a season means; a show is the episode to watch next and the ones after it,
 * [episodes] in all, never the whole run by default.
 */
fun Library.offlineSelection(itemId: MediaId, episodes: Int = 3, scope: LibraryScope = LibraryScope.Unified): List<MediaId> = when (val item = item(itemId)) {
    is Movie, is Episode -> listOf(item.id)
    is Show -> showDetail(item.id, scope)?.let { detail ->
        val all = detail.seasons.flatMap { season -> season.episodes.map { it.item as Episode } }.sortedWith(NextUp.episodeOrder)
        val start = detail.nextUp ?: all.firstOrNull { watchState(it.id)?.completed != true } ?: all.firstOrNull()
        start?.let { first -> listOf(first.id) + following(first, episodes - 1, scope) }
    }.orEmpty()
    is Season -> worksOf(item.id, scope)
    null -> emptyList()
}

/** The movies and episodes [itemId] is made of, as the library has them now: itself, or every episode of a show or a season, in order. */
fun Library.worksOf(itemId: MediaId, scope: LibraryScope = LibraryScope.Unified): List<MediaId> = when (val item = item(itemId)) {
    is Movie, is Episode -> listOf(item.id)
    is Show -> showDetail(item.id, scope)?.seasons.orEmpty().flatMap { season -> season.episodes.map { it.item.id } }
    is Season -> showDetail(item.showId, scope)?.seasons?.firstOrNull { it.season.id == item.id }?.episodes?.map { it.item.id }.orEmpty()
    null -> emptyList()
}

/**
 * How much of a work is kept on the device: a movie or an episode itself, or each episode of a show or a
 * season. A show is not on the device because one of its episodes is (OFFLINE.md).
 */
data class OfflineCoverage(
    /** The movies and episodes the work is made of. */
    val works: Int,
    /** Those with a finished copy on the device. */
    val kept: Int,
    /** Those with a copy waiting, under way, or paused, and none finished. */
    val pending: Int,
    /** Those whose copy stopped without finishing, or is no longer there, and none finished or on its way. */
    val failed: Int,
) {
    /** Every one of them is on the device. */
    val complete: Boolean get() = works > 0 && kept == works
}

/** The episodes of [showId] with a version off the device: what there is to keep of a show. */
fun Library.episodesOffDevice(showId: MediaId): Set<MediaId> = queries.episodesOffDeviceOfShow(showId.value).executeAsList().map(::MediaId).toSet()

fun Library.offlineCoverage(itemId: MediaId, scope: LibraryScope = LibraryScope.Unified): OfflineCoverage {
    val works = worksOf(itemId, scope)
    val wanted = works.toSet()
    val byWork = downloads().filter { it.itemId in wanted }.groupBy { it.itemId }
    var kept = 0
    var pending = 0
    var failed = 0
    for (work in works) {
        val copies = byWork[work] ?: continue
        when {
            copies.any { it.state == DownloadState.COMPLETED } -> kept++
            copies.any { it.active || it.state == DownloadState.PAUSED } -> pending++
            copies.any { it.state == DownloadState.FAILED || it.state == DownloadState.UNAVAILABLE } -> failed++
        }
    }
    return OfflineCoverage(works.size, kept, pending, failed)
}

/** The [count] episodes after [episode], in watching order. */
private fun Library.following(episode: Episode, count: Int, scope: LibraryScope = LibraryScope.Unified): List<MediaId> {
    if (count <= 0) return emptyList()
    val all = showDetail(episode.showId, scope)?.seasons.orEmpty().flatMap { season -> season.episodes.map { it.item as Episode } }.sortedWith(NextUp.episodeOrder)
    val index = all.indexOfFirst { it.id == episode.id }
    return if (index < 0) emptyList() else all.drop(index + 1).take(count).map { it.id }
}
