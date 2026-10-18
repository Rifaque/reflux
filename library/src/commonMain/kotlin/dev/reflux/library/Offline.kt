package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.Download
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflineAssets
import dev.reflux.core.offline.OfflinePlan
import dev.reflux.core.offline.OfflinePlanner
import dev.reflux.core.offline.OfflineReason
import dev.reflux.core.offline.StoragePolicy
import dev.reflux.core.offline.StorageSnapshot
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.SourceCapability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.concurrent.Volatile

/**
 * Smart Offline: decides what to copy to the device so that it can be watched without its source, makes
 * the copies one at a time, and registers each finished copy as one more version of its work. From then on
 * nothing is special about it: Best Version prefers it as the closest copy, and progress on it is the
 * work's progress like on any other version (docs/OFFLINE.md).
 *
 * Nothing here knows what kind of source a file comes from, or what kind of device this is.
 *
 * @param register makes the downloads source known to whoever resolves sources, the first time it is needed.
 * @param dispatcher where files are read and written.
 */
class Offline(
    private val library: Library,
    private val store: DownloadStore,
    private val transport: DownloadTransport,
    private val sources: (SourceId) -> MediaSource?,
    private val device: () -> DeviceCapabilities,
    private val preferredLanguages: List<String>,
    private val register: (MediaSource) -> Unit,
    private val dispatcher: CoroutineDispatcher,
    private val policy: StoragePolicy = StoragePolicy(),
    private val report: (String, Throwable?) -> Unit = { _, _ -> },
) {
    /** The copies, as the source they are played from. */
    val source = DownloadsSource(store)

    private val mutableChanges = MutableStateFlow(0)

    /** Advances whenever a download changes state or makes visible progress. */
    val changes: StateFlow<Int> = mutableChanges.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)

    @Volatile
    private var running: Pair<String, Job>? = null

    /** What the user did to the download under way, so that its end is recorded as that and not as a failure. */
    @Volatile
    private var interruption: DownloadState? = null

    init {
        library.downloadStore = store
        if (library.source(DownloadsSource.ID) != null) register(source)
    }

    /**
     * Starts making copies, in [scope]. Downloads that were under way when the application last stopped
     * continue from where they were, and those that were waiting for their source are tried again.
     */
    fun start(scope: CoroutineScope): Job {
        for (download in library.downloads()) {
            if (download.state == DownloadState.PREPARING || download.state == DownloadState.DOWNLOADING) setState(download.id, DownloadState.QUEUED)
        }
        retryWaiting()
        library.syncDownloads()
        return scope.launch(dispatcher) {
            while (true) {
                val next = library.downloads().firstOrNull { it.state == DownloadState.QUEUED }
                if (next == null) {
                    wake.receive()
                    continue
                }
                interruption = null
                val job = launch { run(next) }
                running = next.id to job
                job.join()
                running = null
            }
        }
    }

    // Deciding -----------------------------------------------------------------------------------

    /** What Reflux would keep for the user if they just said "prepare for offline". */
    fun suggest(): OfflinePlan = plan(library.offlineSuggestions())

    /** What it takes to keep [itemIds] (movies, episodes, shows, or seasons) for offline. */
    fun planFor(itemIds: List<MediaId>): OfflinePlan =
        plan(itemIds.flatMap { library.offlineSelection(it) }.distinct().map { it to OfflineReason.CHOSEN })

    private fun plan(works: List<Pair<MediaId, OfflineReason>>): OfflinePlan =
        OfflinePlanner.plan(works.map { (id, reason) -> library.offlineRequest(id, reason, sources) }, device(), storage(), policy)

    /** The room there is, less what downloads already accepted will still write. */
    fun storage(): StorageSnapshot = store.storage().copy(committedBytes = library.downloads().filter { it.active || it.state == DownloadState.PAUSED }.sumOf { it.remainingBytes })

    /** What new downloads may still take: the room there is, less the reserve and what is already on its way. */
    fun usableBytes(): Long = policy.usableBytes(storage())

    /** Whether any source that is there hands out files to keep; without one there is nothing to offer. */
    fun possible(): Boolean = library.userSources().any { record ->
        sources(record.id).let { it is DownloadableSource && SourceCapability.DOWNLOAD in it.descriptor.capabilities }
    }

    // Doing --------------------------------------------------------------------------------------

    /** Accepts a plan: its downloads wait their turn. A version already being copied is not queued twice. */
    fun enqueue(plan: OfflinePlan): List<Download> {
        if (plan.downloads.isEmpty()) return emptyList()
        if (library.source(DownloadsSource.ID) == null) library.addSource(source, "")
        register(source)
        val time = library.now()
        val queued = plan.downloads.mapNotNull { planned ->
            val version = planned.version
            val id = "d" + StableIds.hash(version.id.value)
            library.database.transaction {
                library.queries.insertDownload(
                    id, planned.itemId.value, version.id.value, version.location.sourceId.value, version.location.path,
                    "$id.${extensionOf(version.stream.container, version.location.fileName)}", DownloadState.QUEUED.name,
                    planned.bytes.takeIf { planned.sizeKnown }, time, time,
                )
                // Only on a new download: one that exists keeps the subtitles it was given.
                if (library.queries.subtitlesOfDownload(id).executeAsList().isEmpty()) {
                    val wanted = OfflineAssets.subtitles(version.externalSubtitles, { it.language }, preferredLanguages)
                    wanted.forEachIndexed { index, subtitle ->
                        val extension = subtitle.location.fileName.substringAfterLast('.', "").lowercase().takeIf { it.length in 2..4 } ?: extensionOf(subtitle.format)
                        library.queries.insertDownloadSubtitle(
                            id, subtitle.location.sourceId.value, subtitle.location.path, "$id.$index.$extension", subtitle.format.name,
                            subtitle.language, if (subtitle.forced) 1 else 0, if (subtitle.hearingImpaired) 1 else 0,
                        )
                    }
                }
            }
            library.download(id)
        }
        changed()
        wake.trySend(Unit)
        return queued
    }

    /** Gives up a download that is not finished. Nothing of it is kept. */
    suspend fun cancel(id: String) = remove(id)

    /** Stops a download where it is, to be continued later. */
    suspend fun pause(id: String) {
        val download = library.download(id) ?: return
        if (!download.active) return
        stop(id, DownloadState.PAUSED)
        setState(id, DownloadState.PAUSED)
    }

    /** Continues a paused download, or tries a failed one again, from what it already has. */
    fun resume(id: String) {
        val download = library.download(id) ?: return
        if (download.state != DownloadState.PAUSED && download.state != DownloadState.FAILED && download.state != DownloadState.UNAVAILABLE) return
        setState(id, DownloadState.QUEUED)
        wake.trySend(Unit)
    }

    /**
     * Picks up again what of [itemIds] (as [planFor] reads them) was paused, did not finish, or is no longer on
     * the device: asking to keep something again asks for that too. Returns how many downloads it picked up.
     */
    fun resumeStopped(itemIds: List<MediaId>): Int {
        val works = itemIds.flatMap { library.offlineSelection(it) }.toSet()
        val stopped = library.downloads().filter {
            it.itemId in works && (it.state == DownloadState.PAUSED || it.state == DownloadState.FAILED || it.state == DownloadState.UNAVAILABLE)
        }
        if (stopped.isEmpty()) return 0
        stopped.forEach { setState(it.id, DownloadState.QUEUED) }
        wake.trySend(Unit)
        return stopped.size
    }

    /** Tries again the downloads that stopped for a reason that passes by itself: a source away, a broken connection. */
    fun retryWaiting() {
        val waiting = library.downloads().filter { it.state == DownloadState.FAILED && (it.failure == DownloadFailure.SOURCE_UNAVAILABLE || it.failure == DownloadFailure.INTERRUPTED) }
        if (waiting.isEmpty()) return
        waiting.forEach { setState(it.id, DownloadState.QUEUED) }
        wake.trySend(Unit)
    }

    /**
     * Removes a download and its copy from the device, whatever state it is in. The work stays in the
     * library for as long as another version of it exists, with everything that was watched.
     */
    suspend fun remove(id: String) {
        val download = library.download(id) ?: return
        stop(id, DownloadState.CANCELLED)
        val subtitles = library.queries.subtitlesOfDownload(id).executeAsList().map { it.path }
        library.database.transaction {
            library.queries.deleteSubtitlesOfDownload(id)
            library.queries.deleteDownload(id)
        }
        runCatching {
            store.delete(download.path)
            store.delete(partOf(download.path))
            subtitles.forEach(store::delete)
        }.onFailure { report("A removed download left files behind", it) }
        library.syncDownloads()
        changed()
    }

    /** Stops the copy of [id] if it is the one under way, and waits until it has let go of its file. */
    private suspend fun stop(id: String, becoming: DownloadState) {
        val (current, job) = running ?: return
        if (current != id) return
        interruption = becoming
        job.cancelAndJoin()
    }

    // The copy itself ----------------------------------------------------------------------------

    private suspend fun run(download: Download) {
        try {
            setState(download.id, DownloadState.PREPARING)
            copy(download)
            if (library.download(download.id) == null) return // removed while its last bytes arrived
            setState(download.id, DownloadState.COMPLETED)
            library.syncDownloads()
            changed()
        } catch (e: CancellationException) {
            // Paused or removed by the user, which has been recorded; or the application is stopping, and
            // the download waits for the next start as it is.
            if (interruption == null && library.download(download.id) != null) setState(download.id, DownloadState.QUEUED)
            throw e
        } catch (e: DownloadException) {
            fail(download, e.failure, e)
        } catch (e: Exception) {
            // Running out of room surfaces as a failed write; say what it was.
            val full = runCatching { policy.usableBytes(store.storage()) <= 0 }.getOrDefault(false)
            fail(download, if (full) DownloadFailure.INSUFFICIENT_STORAGE else DownloadFailure.INTERRUPTED, e)
        }
    }

    private fun fail(download: Download, failure: DownloadFailure, cause: Throwable) {
        if (library.download(download.id) == null) return
        report("A download stopped: $failure", cause)
        setState(download.id, DownloadState.FAILED, failure)
    }

    private suspend fun copy(download: Download) {
        if (!store.available()) throw DownloadException(DownloadFailure.STORAGE_UNAVAILABLE)
        val origin = sources(download.origin.sourceId) ?: throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE)
        if (origin !is DownloadableSource || SourceCapability.DOWNLOAD !in origin.descriptor.capabilities) throw DownloadException(DownloadFailure.NOT_ALLOWED)
        if (origin.availability() == Availability.UNAVAILABLE) throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE)

        val part = partOf(download.path)
        val had = store.length(part) ?: 0L
        val expected = download.totalBytes ?: library.version(download.originVersionId)?.version?.sizeBytes?.takeIf { it > 0 }
        // The room is checked when the copy starts, not only when it was planned: things change in between.
        val others = library.downloads().filter { it.id != download.id && (it.active || it.state == DownloadState.PAUSED) }.sumOf { it.remainingBytes }
        val usable = policy.usableBytes(store.storage().copy(committedBytes = others))
        if (expected != null && expected - had > usable) throw DownloadException(DownloadFailure.INSUFFICIENT_STORAGE)

        val target = origin.downloadTarget(download.origin.path)
        var sink: DownloadSink? = null
        var written = had
        var total = expected
        var unsaved = 0L
        try {
            transport.fetch(target, had, object : DownloadReceiver {
                override suspend fun started(offset: Long, totalBytes: Long?) {
                    // A source that cannot continue part-way starts over; what was there is dropped.
                    sink = store.open(part, offset)
                    written = offset
                    total = totalBytes ?: total
                    library.queries.setDownloadProgress(written, total, library.now(), download.id)
                    setState(download.id, DownloadState.DOWNLOADING)
                }

                override suspend fun bytes(chunk: ByteArray, length: Int) {
                    currentCoroutineContext().ensureActive()
                    (sink ?: store.open(part, written).also { sink = it }).write(chunk, length)
                    written += length
                    unsaved += length
                    if (unsaved >= PROGRESS_STEP_BYTES) {
                        unsaved = 0
                        library.queries.setDownloadProgress(written, total, library.now(), download.id)
                        changed()
                    }
                }
            })
        } finally {
            withContext(kotlinx.coroutines.NonCancellable) {
                runCatching { sink?.close() }
                if (library.download(download.id) != null) library.queries.setDownloadProgress(written, total, library.now(), download.id)
            }
        }
        total?.let { if (written != it) throw DownloadException(DownloadFailure.INTERRUPTED, "got $written of $it bytes") }
        store.move(part, download.path)
        library.queries.setDownloadProgress(written, written, library.now(), download.id)

        // Subtitles kept in separate files come along. One that cannot be had is not worth losing the film for.
        for (subtitle in library.queries.subtitlesOfDownload(download.id).executeAsList().filter { it.fetched == 0L }) {
            try {
                val from = sources(SourceId(subtitle.origin_source_id)) ?: continue
                var out: DownloadSink? = null
                try {
                    transport.fetch(from.playbackTarget(subtitle.origin_path), 0, object : DownloadReceiver {
                        override suspend fun started(offset: Long, totalBytes: Long?) {
                            out = store.open(subtitle.path, 0)
                        }

                        override suspend fun bytes(chunk: ByteArray, length: Int) {
                            (out ?: store.open(subtitle.path, 0).also { out = it }).write(chunk, length)
                        }
                    })
                } finally {
                    runCatching { out?.close() }
                }
                library.queries.markDownloadSubtitleFetched(download.id, subtitle.origin_path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                runCatching { store.delete(subtitle.path) }
                report("A subtitle could not be downloaded", e)
            }
        }
    }

    private fun setState(id: String, state: DownloadState, failure: DownloadFailure? = null) {
        library.queries.setDownloadState(state.name, failure?.name, library.now(), id)
        changed()
    }

    private fun changed() = mutableChanges.update { it + 1 }

    private fun partOf(path: String) = "$path.part"

    private fun extensionOf(container: Container, fileName: String): String {
        val named = fileName.substringAfterLast('.', "").lowercase()
        if (named.length in 2..4 && named.all { it.isLetterOrDigit() }) return named
        return when (container) {
            Container.MATROSKA -> "mkv"
            Container.WEBM -> "webm"
            Container.MP4 -> "mp4"
            Container.QUICKTIME -> "mov"
            Container.MPEG_TS -> "ts"
            Container.AVI -> "avi"
            else -> "bin"
        }
    }

    private fun extensionOf(format: SubtitleFormat): String = when (format) {
        SubtitleFormat.SRT -> "srt"
        SubtitleFormat.ASS -> "ass"
        SubtitleFormat.WEBVTT -> "vtt"
        SubtitleFormat.TTML -> "ttml"
        SubtitleFormat.PGS -> "sup"
        else -> "sub"
    }

    private companion object {
        /** How much arrives between two records of progress: often enough to watch, rare enough not to wear the database. */
        const val PROGRESS_STEP_BYTES = 1L * 1024 * 1024
    }
}
