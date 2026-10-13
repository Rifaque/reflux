package dev.reflux.library

import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.offline.DownloadFailure
import dev.reflux.core.offline.DownloadState
import dev.reflux.core.offline.OfflineReason
import dev.reflux.core.offline.OfflineSkipReason
import dev.reflux.core.offline.StorageSnapshot
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.DownloadableSource
import dev.reflux.core.source.FileEnumeratingSource
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceFile
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.core.watch.ResumePoint
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L
private const val KB = 1024

private fun stream(minutes: Double, height: Int = 1080, codec: VideoCodec = VideoCodec.H264, bitDepth: Int = 8) = StreamInfo(
    container = Container.MATROSKA,
    video = VideoStream(codec, height * 16 / 9, height, bitDepth),
    audio = listOf(AudioStream(AudioCodec.AC3, 6, "eng")),
    durationMs = (minutes * MINUTE).toLong(),
)

/** Recognizable bytes of a given length, so that a copy can be compared with what it was made from. */
private fun content(seed: Int, length: Int) = ByteArray(length) { ((it * 31 + seed) % 251).toByte() }

/** What a source serves, by address. Shared by the sources below and the transport that fetches from them. */
private class Wire {
    val files = HashMap<String, ByteArray>()
    val unreachable = HashSet<String>()

    /** Whether a transfer can continue from part-way, as with a server that honors range requests. */
    var ranges = true

    /** Break the next transfer after this many bytes. */
    var breakAfter: Int? = null

    /** Hold the next transfer after its first chunk until this completes. */
    var hold: CompletableDeferred<Unit>? = null
    val reached = CompletableDeferred<Unit>()
    val fetched = ArrayList<Pair<String, Long>>()
}

private class WireTransport(private val wire: Wire) : DownloadTransport {
    override suspend fun fetch(target: PlaybackTarget, offset: Long, receiver: DownloadReceiver) {
        if (wire.unreachable.any { target.uri.startsWith(it) }) throw DownloadException(DownloadFailure.SOURCE_UNAVAILABLE)
        val bytes = wire.files[target.uri] ?: throw DownloadException(DownloadFailure.MEDIA_GONE)
        val from = if (wire.ranges) offset.toInt().coerceAtMost(bytes.size) else 0
        wire.fetched += target.uri to from.toLong()
        receiver.started(from.toLong(), bytes.size.toLong())
        var sent = 0
        var position = from
        while (position < bytes.size) {
            val length = minOf(16 * KB, bytes.size - position)
            receiver.bytes(bytes.copyOfRange(position, position + length), length)
            position += length
            sent += length
            wire.breakAfter?.let { limit ->
                if (sent >= limit) {
                    wire.breakAfter = null
                    throw DownloadException(DownloadFailure.INTERRUPTED)
                }
            }
            wire.hold?.let { hold ->
                wire.hold = null
                wire.reached.complete(Unit)
                hold.await()
            }
        }
    }
}

/** A media server: identifies its own works, keeps its own watch state, and hands out its files. */
private class KeepingServer(private val wire: Wire) : CatalogSource, WatchStateSyncSource, DownloadableSource {
    private val entries = linkedMapOf<String, CatalogEntry>()
    var reachable = true
        set(value) {
            field = value
            if (value) wire.unreachable -= "server://" else wire.unreachable += "server://"
        }
    val told = mutableListOf<String>()

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://home.example"), "server", "Home Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC, SourceCapability.DOWNLOAD),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("server://$path")
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.values.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }
    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) = Unit
    override suspend fun reportStopped(path: String, positionMs: Long) = tell("position $path ${positionMs / MINUTE}")
    override suspend fun setPlayed(path: String, played: Boolean) = tell("played $path $played")

    private fun tell(what: String) {
        if (!reachable) throw SourceUnavailableException(descriptor.id)
        told += what
    }

    private val untouched = SourceUserState(played = false, positionMs = 0, playCount = 0, lastPlayedAtEpochMs = null, favorite = false)

    /** A film in one or more versions, each `id to stream`, with [bytes] of content each. */
    fun movie(title: String, year: Int, vararg versions: Pair<String, StreamInfo>, bytes: Int = 300 * KB, edition: (String) -> String? = { null }, subtitles: List<CatalogSubtitle> = emptyList()) {
        entries[title] = CatalogEntry(
            ParsedMedia(ParsedKind.MOVIE, title, year),
            versions.mapIndexed { index, (id, stream) ->
                wire.files["server://items/$id"] = content(id.hashCode(), bytes + index * 10 * KB)
                CatalogVersion("items/$id", (bytes + index * 10 * KB).toLong(), 1, stream, edition(id), subtitles)
            },
            userState = untouched,
        )
    }

    fun episode(show: String, season: Int, episode: Int, bytes: Int = 100 * KB) {
        val id = "$show-$season-$episode".lowercase().replace(' ', '-')
        wire.files["server://items/$id"] = content(id.hashCode(), bytes)
        entries[id] = CatalogEntry(
            ParsedMedia(ParsedKind.EPISODE, show, null, season = season, episode = episode),
            listOf(CatalogVersion("items/$id", bytes.toLong(), 1, stream(45.0))),
            userState = untouched,
        )
    }

    fun remove(title: String) {
        entries.remove(title)
    }

    /** What the server's own record says, as after watching on one of its own clients. */
    fun says(key: String, minutes: Long, at: Long) {
        entries[key] = entries.getValue(key).copy(userState = SourceUserState(false, minutes * MINUTE, 0, at, false))
    }
}

/** A share: files Reflux identifies itself, served as they are. */
private class Share(private val wire: Wire, downloadable: Boolean = true) : FileEnumeratingSource, DownloadableSource {
    private val listing = ArrayList<SourceFile>()
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("share", "nas"), "share", "NAS", SourceLocality.LOCAL_NETWORK,
        setOfNotNull(SourceCapability.ENUMERATE_FILES, SourceCapability.DOWNLOAD.takeIf { downloadable }),
    )

    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("share://$path")
    override suspend fun downloadTarget(path: String) = playbackTarget(path)
    override fun files(): Flow<SourceFile> = listing.toList().asFlow()

    fun file(path: String, bytes: ByteArray = content(path.hashCode(), 64 * KB)) {
        wire.files["share://$path"] = bytes
        listing += SourceFile(path, bytes.size.toLong(), 1)
    }
}

/** Downloads in a real directory, with as much room as the test says there is. */
private class TestStore(val directory: File) : DownloadStore {
    private val files = FileDownloadStore(directory)
    var room = StorageSnapshot(freeBytes = 64L * 1024 * 1024 * 1024, totalBytes = 128L * 1024 * 1024 * 1024)
    var away = false

    override fun available() = !away && files.available()
    override fun uri(path: String) = files.uri(path)
    override fun length(path: String) = if (away) null else files.length(path)
    override fun open(path: String, offset: Long) = files.open(path, offset)
    override fun move(from: String, to: String) = files.move(from, to)
    override fun delete(path: String) = files.delete(path)
    override fun storage() = room
}

/**
 * Smart Offline through the library, as a shell uses it: a copy of a version is one more version of the
 * same work, chosen like any other and resumed from the work's own progress. Sources are stand-ins with
 * nothing of any particular server or protocol.
 */
class OfflineTest {
    private val root: File = Files.createTempDirectory("reflux-offline").toFile()
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val wire = Wire()
    private val server = KeepingServer(wire)
    private val share = Share(wire)
    private val store = TestStore(File(root, "downloads"))
    private val registered = HashMap<SourceId, MediaSource>()
    private val sources = { id: SourceId ->
        when (id) {
            server.descriptor.id -> server
            share.descriptor.id -> share
            else -> registered[id]
        }
    }
    private val reports = ArrayList<String>()

    /** Plays H.264 and HEVC in hardware on a 1080p display. */
    private val device = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true),
        ),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(1920, 1080),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    init {
        library.addSource(server, "https://home.example")
        library.addSource(share, "nas")
    }

    @AfterTest
    fun cleanUp() {
        root.deleteRecursively()
    }

    private fun TestScope.offline(languages: List<String> = listOf("en")): Offline =
        Offline(library, store, WireTransport(wire), sources, { device }, languages, { registered[it.descriptor.id] = it }, UnconfinedTestDispatcher(testScheduler)) { problem, _ -> reports += problem }

    private fun TestScope.started(languages: List<String> = listOf("en")): Offline = offline(languages).also { it.start(backgroundScope) }

    private fun movie(title: String): MediaId = library.movies().single { it.item.title == title }.item.id
    private fun copyOn(source: SourceId, item: MediaId): MediaVersion = library.versions(item).single { it.version.location.sourceId == source }.version
    private fun onServer(item: MediaId) = copyOn(server.descriptor.id, item)
    private fun downloaded(item: MediaId) = copyOn(DownloadsSource.ID, item)
    private fun watch(version: MediaVersion, minutes: Long) {
        clock += 60_000
        library.stopped(version.itemId, minutes * MINUTE, version.stream.durationMs, version)
    }

    private fun bytesOf(version: MediaVersion): ByteArray = File(java.net.URI(store.uri(version.location.path))).readBytes()

    private suspend fun serverGoesAway() {
        server.reachable = false
        library.scan(server)
    }

    private suspend fun serverComesBack() {
        server.reachable = true
        library.scan(server)
    }

    @Test
    fun aDownloadedCopyIsOneMoreVersionOfTheSameWork() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        val offline = started()
        assertTrue(offline.possible())
        assertFalse(library.sources().any { it.isDownloads() }, "nothing is set up for downloads until there is one")

        val plan = offline.planFor(listOf(heat))
        assertEquals(listOf(heat), plan.downloads.map { it.itemId })
        assertEquals(300L * KB, plan.requiredBytes)
        val queued = offline.enqueue(plan).single()
        runCurrent()

        val done = assertNotNull(library.download(queued.id))
        assertEquals(DownloadState.COMPLETED, done.state)
        assertEquals(300L * KB, done.bytesDownloaded)
        assertEquals(done.bytesDownloaded, done.totalBytes)
        assertNotNull(done.completedAtEpochMs)
        assertEquals(onServer(heat).id, done.originVersionId)

        // One work, two versions: the server's and the copy. Nothing new appeared in the library.
        assertEquals(listOf("Heat"), library.movies().map { it.item.title })
        val copy = downloaded(heat)
        assertEquals(2, library.versions(heat).size)
        assertEquals(onServer(heat).stream, copy.stream, "the copy is the same file, so it is described the same way")
        assertEquals(300L * KB, copy.sizeBytes)
        assertContentEquals(wire.files.getValue("server://items/heat"), bytesOf(copy), "byte for byte")
        assertEquals(SourceLocality.DEVICE, library.versions(heat).single { it.version.id == copy.id }.locality)

        // Where downloads are kept is Reflux's own, not one of the user's sources.
        assertEquals(listOf("Home Server", "NAS"), library.userSources().map { it.displayName })
        assertTrue(library.sources().any { it.isDownloads() })
        // The copy is private to Reflux: inside its downloads directory and nowhere else.
        assertTrue(File(java.net.URI(store.uri(copy.location.path))).canonicalPath.startsWith(store.directory.canonicalPath))

        // Asking again changes nothing: it is here.
        assertEquals(OfflineSkipReason.ALREADY_ON_DEVICE, offline.planFor(listOf(heat)).skipped.single().reason)
        assertTrue(offline.enqueue(offline.planFor(listOf(heat))).isEmpty())
        assertEquals(1, library.downloads().size)
    }

    @Test
    fun theCopyPlaysWhenItsSourceIsAwayAndProgressIsTheWorks() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        // Watched from the server to 00:30:12, before leaving the house.
        clock += 60_000
        library.stopped(heat, 30 * MINUTE + 12_000, 170 * MINUTE, onServer(heat))
        val offline = started()
        offline.enqueue(offline.planFor(listOf(heat)))
        runCurrent()

        // With both there, the copy on the device is the one played: the same thing, closer.
        val both = assertNotNull(library.planPlayback(heat, device, sources))
        assertEquals(DownloadsSource.ID, both.version.version.location.sourceId)
        assertEquals(SelectionCriterion.LOCALITY, both.selection.decidedBy)

        // The server is out of reach. The library is as it was, and the film plays from the device, from 00:30:12.
        serverGoesAway()
        assertEquals(listOf("Heat"), library.movies().map { it.item.title })
        assertEquals(Availability.AVAILABLE, library.movies().single().availability)
        val away = assertNotNull(library.planPlayback(heat, device, sources))
        assertEquals(DownloadsSource.ID, away.version.version.location.sourceId)
        assertEquals(ResumePoint(30 * MINUTE + 12_000, ResumeBasis.SAME_TIMELINE), away.resume)
        assertTrue(away.request.target.uri.startsWith("file:"))

        // Watched on to 00:50:00 with no server in sight. Nothing is lost and nothing is sent.
        watch(downloaded(heat), minutes = 50)
        assertEquals(50 * MINUTE, library.watchState(heat)?.positionMs)
        assertTrue(server.told.isEmpty())

        // The server is back, still saying 00:30. That is not news: it is the server that is behind, and it is told.
        server.says("Heat", minutes = 30, at = clock - 3_600_000)
        serverComesBack()
        assertEquals(50 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(listOf("position items/heat 50"), server.told)
        // And watching on the server's own client afterwards comes back here as it always did.
        clock += 3_600_000
        server.says("Heat", minutes = 80, at = clock)
        library.scan(server)
        assertEquals(80 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(ResumePoint(80 * MINUTE, ResumeBasis.SAME_TIMELINE), assertNotNull(library.planPlayback(heat, device, sources)).resume)
    }

    @Test
    fun aPinnedVersionStaysTheUsersChoiceOnlineAndOffline() = runTest {
        server.movie("Dune", 2021, "dune-1080" to stream(155.0), "dune-4k" to stream(155.0, 2160, VideoCodec.HEVC, 10))
        library.scan(server)
        val dune = movie("Dune")
        val versions = library.versions(dune).map { it.version }
        val uhd = versions.single { it.location.path == "items/dune-4k" }
        val hd = versions.single { it.location.path == "items/dune-1080" }
        val offline = started()

        // On this 1080p display Reflux would keep the 1080p file. The user pinned the 4K one, so that is what is kept.
        assertEquals(hd.id, offline.planFor(listOf(dune)).downloads.single().version.id)
        library.setPreferredVersion(dune, uhd.id)
        assertEquals(uhd.id, offline.planFor(listOf(dune)).downloads.single().version.id)
        offline.enqueue(offline.planFor(listOf(dune)))
        runCurrent()

        // The copy of the pinned version is the pinned version: it plays from the device, not from the server again.
        val online = assertNotNull(library.planPlayback(dune, device, sources))
        assertEquals(DownloadsSource.ID, online.version.version.location.sourceId)
        assertEquals(uhd.stream, online.version.version.stream)

        // A pin on something else is not overridden by a download having been made.
        library.setPreferredVersion(dune, hd.id)
        assertEquals(hd.id, assertNotNull(library.planPlayback(dune, device, sources)).version.version.id)
        // And with the server away, a pin on what cannot be reached gives way to what can.
        serverGoesAway()
        assertEquals(DownloadsSource.ID, assertNotNull(library.planPlayback(dune, device, sources)).version.version.location.sourceId)
    }

    @Test
    fun aCopyOfOneCutNeverResumesAnotherCutsPosition() = runTest {
        server.movie("Aliens", 1986, "aliens-theatrical" to stream(137.0), "aliens-extended" to stream(154.0), edition = { if ("extended" in it) "Extended Edition" else null })
        library.scan(server)
        val aliens = movie("Aliens")
        val extended = library.versions(aliens).map { it.version }.single { it.edition != null }
        val theatrical = library.versions(aliens).map { it.version }.single { it.edition == null }
        val offline = started()

        // Part-way through the extended cut: that is the cut that is kept.
        watch(extended, minutes = 100)
        assertEquals(extended.id, offline.planFor(listOf(aliens)).downloads.single().version.id)
        offline.enqueue(offline.planFor(listOf(aliens)))
        runCurrent()
        serverGoesAway()
        val continued = assertNotNull(library.planPlayback(aliens, device, sources))
        assertEquals("extended", library.watchState(aliens)?.cut)
        assertEquals(ResumePoint(100 * MINUTE, ResumeBasis.SAME_TIMELINE), continued.resume)

        // Had only the theatrical cut been kept, minute 100 would not be reused on it.
        offline.remove(library.downloads().single().id)
        serverComesBack()
        library.setPreferredVersion(aliens, theatrical.id)
        offline.enqueue(offline.planFor(listOf(aliens)))
        runCurrent()
        library.setPreferredVersion(aliens, null)
        serverGoesAway()
        val other = assertNotNull(library.planPlayback(aliens, device, sources))
        assertEquals(theatrical.stream.durationMs, other.version.version.stream.durationMs)
        assertEquals(ResumeBasis.ADJUSTED, other.resume.basis)
        assertTrue(other.resume.positionMs!! < 100 * MINUTE)
    }

    @Test
    fun whatIsBeingWatchedAndWhatComesNextIsProposed() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        server.movie("Arrival", 2016, "arrival" to stream(116.0))
        for (number in 1..5) server.episode("Breaking Bad", 1, number)
        share.file("Movies/Whiplash (2014)/Whiplash.2014.1080p.mkv")
        library.scan(server)
        library.scan(share)
        val offline = started()
        assertTrue(offline.suggest().empty, "nothing is in progress, so nothing is presumed")

        // 35% through a film, 60% through the second episode of a show.
        val heat = movie("Heat")
        val episodes = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.map { it.item.id }
        watch(onServer(heat), minutes = 60)
        watch(onServer(episodes[1]), minutes = 27)

        val plan = offline.suggest()
        assertEquals(listOf(episodes[1], episodes[2], heat), plan.downloads.map { it.itemId }, "most recent first, and the episode after the one in progress")
        assertEquals(listOf(OfflineReason.IN_PROGRESS, OfflineReason.NEXT_EPISODE, OfflineReason.IN_PROGRESS), plan.downloads.map { it.reason })
        assertEquals(100L * KB * 2 + 300L * KB, plan.requiredBytes)
        assertTrue(plan.skipped.isEmpty())
        assertFalse(movie("Arrival") in plan.downloads.map { it.itemId }, "what was not started is not presumed either")

        // With room for only some of it, what matters most is kept and the rest is said to be left out.
        store.room = StorageSnapshot(freeBytes = 500L * 1024 * 1024 + 250L * KB, totalBytes = 1024L * 1024 * 1024)
        val tight = offline.suggest()
        assertEquals(listOf(episodes[1], episodes[2]), tight.downloads.map { it.itemId })
        assertEquals(listOf(heat to OfflineSkipReason.NOT_ENOUGH_STORAGE), tight.skipped.map { it.itemId to it.reason })
        assertEquals(250L * KB, tight.usableBytes)
        assertTrue(tight.requiredBytes <= tight.usableBytes)

        // Choosing a show keeps the next few episodes, not the whole run.
        store.room = StorageSnapshot(freeBytes = 64L * 1024 * 1024 * 1024)
        assertEquals(listOf(episodes[1], episodes[2], episodes[3]), offline.planFor(listOf(library.shows().single().item.id)).downloads.map { it.itemId })
        // A file on a share is kept the same way as one on a server.
        assertEquals(listOf(movie("Whiplash")), offline.planFor(listOf(movie("Whiplash"))).downloads.map { it.itemId })

        offline.enqueue(plan)
        runCurrent()
        assertTrue(library.downloads().all { it.state == DownloadState.COMPLETED })
        assertTrue(offline.suggest().empty, "what is here is not proposed again")
        assertEquals(setOf(OfflineSkipReason.ALREADY_ON_DEVICE), offline.suggest().skipped.map { it.reason }.toSet())
        assertEquals(1, library.shows().size)
    }

    @Test
    fun roomIsCheckedAgainWhenTheCopyStartsAndWhatIsOnItsWayCounts() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        server.movie("Arrival", 2016, "arrival" to stream(116.0))
        library.scan(server)
        val offline = started()
        val plan = offline.planFor(listOf(movie("Heat")))

        // Planned with room, started without: the device filled up in between.
        store.room = StorageSnapshot(freeBytes = 500L * 1024 * 1024 + 100L * KB)
        val download = offline.enqueue(plan).single()
        runCurrent()
        val failed = assertNotNull(library.download(download.id))
        assertEquals(DownloadState.FAILED to DownloadFailure.INSUFFICIENT_STORAGE, failed.state to failed.failure)
        assertEquals(0, failed.bytesDownloaded)
        assertEquals(1, library.versions(movie("Heat")).size, "nothing half-made is in the library")

        // Room again: trying again is all it takes.
        store.room = StorageSnapshot(freeBytes = 64L * 1024 * 1024 * 1024)
        offline.resume(download.id)
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(download.id)?.state)

        // A download that is paused still has its room spoken for.
        wire.hold = CompletableDeferred()
        val second = offline.enqueue(offline.planFor(listOf(movie("Arrival")))).single()
        runCurrent()
        offline.pause(second.id)
        assertEquals(DownloadState.PAUSED, library.download(second.id)?.state)
        assertEquals(300L * KB - 16 * KB, offline.storage().committedBytes)
    }

    @Test
    fun aBrokenTransferContinuesFromWhereItStopped() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        val offline = started()

        wire.breakAfter = 96 * KB
        val download = offline.enqueue(offline.planFor(listOf(heat))).single()
        runCurrent()
        val broken = assertNotNull(library.download(download.id))
        assertEquals(DownloadState.FAILED to DownloadFailure.INTERRUPTED, broken.state to broken.failure)
        assertEquals(96L * KB, broken.bytesDownloaded)
        assertEquals(300L * KB, broken.totalBytes)
        assertEquals(1, library.versions(heat).size)

        // What passes by itself is tried again by itself, and from the 96 KB that are already here.
        offline.retryWaiting()
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(download.id)?.state)
        assertEquals(listOf(0L, 96L * KB), wire.fetched.map { it.second })
        assertContentEquals(wire.files.getValue("server://items/heat"), bytesOf(downloaded(heat)))

        // A source that cannot continue part-way is fetched from the start, and the result is still the file.
        offline.remove(download.id)
        wire.ranges = false
        wire.breakAfter = 64 * KB
        val again = offline.enqueue(offline.planFor(listOf(heat))).single()
        runCurrent()
        offline.resume(again.id)
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(again.id)?.state)
        assertContentEquals(wire.files.getValue("server://items/heat"), bytesOf(downloaded(heat)))
    }

    @Test
    fun aSourceThatIsAwayIsWaitedForNotGivenUpOn() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        val offline = started()
        val plan = offline.planFor(listOf(heat))

        server.reachable = false
        val download = offline.enqueue(plan).single()
        runCurrent()
        val waiting = assertNotNull(library.download(download.id))
        assertEquals(DownloadState.FAILED to DownloadFailure.SOURCE_UNAVAILABLE, waiting.state to waiting.failure)
        // While it is away there is nothing to plan either, and the reason is given.
        library.scan(server)
        offline.remove(download.id)
        assertEquals(OfflineSkipReason.SOURCE_UNAVAILABLE, offline.planFor(listOf(heat)).skipped.single().reason)

        serverComesBack()
        val retried = offline.enqueue(offline.planFor(listOf(heat))).single()
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(retried.id)?.state)

        // A file the source no longer has is said to be gone, which waiting will not fix.
        server.movie("Arrival", 2016, "arrival" to stream(116.0))
        library.scan(server)
        val arrival = offline.planFor(listOf(movie("Arrival")))
        wire.files.remove("server://items/arrival")
        val gone = offline.enqueue(arrival).single()
        runCurrent()
        assertEquals(DownloadState.FAILED to DownloadFailure.MEDIA_GONE, library.download(gone.id)!!.let { it.state to it.failure })
        offline.retryWaiting()
        runCurrent()
        assertEquals(DownloadState.FAILED, library.download(gone.id)?.state, "not retried by itself")
        assertTrue(reports.isNotEmpty(), "what went wrong is kept for diagnostics")
    }

    @Test
    fun aSourceThatDoesNotHandOutFilesIsNeverAskedTo() = runTest {
        val closed = Share(wire, downloadable = false)
        closed.file("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        val only = { id: SourceId -> if (id == closed.descriptor.id) closed else registered[id] }
        library.scan(closed)
        val offline = Offline(library, store, WireTransport(wire), only, { device }, listOf("en"), { registered[it.descriptor.id] = it }, UnconfinedTestDispatcher(testScheduler))
        offline.start(backgroundScope)
        assertFalse(offline.possible())
        val plan = offline.planFor(listOf(movie("Heat")))
        assertTrue(plan.empty)
        assertEquals(OfflineSkipReason.NOT_DOWNLOADABLE, plan.skipped.single().reason)
        assertTrue(wire.fetched.isEmpty())
    }

    @Test
    fun cancellingLeavesNothingBehindAndPausingKeepsWhatItHas() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        val offline = started()

        // Paused after the first chunk: it stops, keeps its bytes, and continues from them.
        wire.hold = CompletableDeferred()
        val download = offline.enqueue(offline.planFor(listOf(heat))).single()
        runCurrent()
        assertEquals(DownloadState.DOWNLOADING, library.download(download.id)?.state)
        offline.pause(download.id)
        runCurrent()
        val paused = assertNotNull(library.download(download.id))
        assertEquals(DownloadState.PAUSED, paused.state)
        assertEquals(16L * KB, paused.bytesDownloaded)
        assertEquals(16L * KB, store.length(paused.path + ".part"))
        assertEquals(1, library.versions(heat).size)
        offline.resume(download.id)
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(download.id)?.state)
        assertEquals(listOf(0L, 16L * KB), wire.fetched.map { it.second })
        assertContentEquals(wire.files.getValue("server://items/heat"), bytesOf(downloaded(heat)))

        // Cancelled part-way: no record, no file, no version.
        offline.remove(download.id)
        wire.hold = CompletableDeferred()
        val second = offline.enqueue(offline.planFor(listOf(heat))).single()
        runCurrent()
        offline.cancel(second.id)
        runCurrent()
        assertTrue(library.downloads().isEmpty())
        assertTrue(store.directory.listFiles().orEmpty().isEmpty(), "left behind: ${store.directory.list()?.toList()}")
        assertEquals(1, library.versions(heat).size)
        assertEquals(listOf("Heat"), library.movies().map { it.item.title })
    }

    @Test
    fun aDownloadInterruptedByTheApplicationStoppingContinuesAtTheNextStart() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        val first = offline()
        val running: Job = first.start(backgroundScope)
        wire.hold = CompletableDeferred()
        val download = first.enqueue(first.planFor(listOf(heat))).single()
        runCurrent()
        assertEquals(DownloadState.DOWNLOADING, library.download(download.id)?.state)

        // The process ends in the middle. What was written is on disk; the record says it was under way.
        running.cancel()
        runCurrent()
        assertEquals(16L * KB, store.length(download.path + ".part"))

        // A new start finds it, and the rest arrives.
        val second = started()
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.download(download.id)?.state)
        assertEquals(16L * KB, wire.fetched.last().second)
        assertContentEquals(wire.files.getValue("server://items/heat"), bytesOf(downloaded(heat)))
        assertEquals(listOf(DownloadsSource.ID), registered.keys.toList(), "the copies are playable again without anything being asked")
        assertNotNull(second.source)
    }

    @Test
    fun subtitlesInSeparateFilesComeAlongInTheLanguagesTheViewerReads() = runTest {
        share.file("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        share.file("Movies/Heat (1995)/Heat.1995.1080p.en.srt", "1\n00:00:01,000 --> 00:00:02,000\nHello\n".toByteArray())
        share.file("Movies/Heat (1995)/Heat.1995.1080p.en.forced.srt", "forced".toByteArray())
        share.file("Movies/Heat (1995)/Heat.1995.1080p.de.srt", "Hallo".toByteArray())
        share.file("Movies/Heat (1995)/Heat.1995.1080p.fr.srt", "Bonjour".toByteArray())
        library.scan(share)
        val heat = movie("Heat")
        assertEquals(4, copyOn(share.descriptor.id, heat).externalSubtitles.size)
        val offline = started(languages = listOf("en-GB"))

        // One of the wanted subtitles is not there to be had. The film is still worth keeping.
        wire.files.remove("share://Movies/Heat (1995)/Heat.1995.1080p.en.forced.srt")
        offline.enqueue(offline.planFor(listOf(heat)))
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.downloads().single().state)

        val copy = downloaded(heat)
        assertEquals(listOf("en" to false), copy.externalSubtitles.map { it.language to it.forced }, "English, and not the two languages nobody asked for")
        val subtitle = copy.externalSubtitles.single()
        assertEquals(DownloadsSource.ID, subtitle.location.sourceId)
        assertEquals(SubtitleFormat.SRT, subtitle.format)
        val fetched = wire.fetched.map { it.first }
        assertFalse(fetched.any { it.endsWith(".de.srt") || it.endsWith(".fr.srt") })

        // Offline, the player is handed the film and its subtitle from the device.
        val plan = assertNotNull(library.planPlayback(heat, device, { id -> if (id == share.descriptor.id) null else sources(id) }))
        assertEquals(DownloadsSource.ID, plan.version.version.location.sourceId)
        assertTrue(plan.request.subtitles.single().target.uri.startsWith("file:"))
        assertTrue(File(java.net.URI(plan.request.subtitles.single().target.uri)).readText().contains("Hello"))

        // Removing the download removes what came with it.
        offline.remove(library.downloads().single().id)
        assertTrue(store.directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun removingADownloadOrItsSourceNeverTakesTheWorkOrItsHistoryWithIt() = runTest {
        server.movie("Heat", 1995, "heat" to stream(170.0))
        server.movie("Arrival", 2016, "arrival" to stream(116.0))
        library.scan(server)
        val heat = movie("Heat")
        val arrival = movie("Arrival")
        val offline = started()
        offline.enqueue(offline.planFor(listOf(heat, arrival)))
        runCurrent()
        watch(downloaded(heat), minutes = 40)

        // The copy is removed: the file is gone, the work and where the viewer was are not.
        val heatDownload = library.downloads().single { it.itemId == heat }
        val file = File(java.net.URI(store.uri(heatDownload.path)))
        assertTrue(file.isFile)
        offline.remove(heatDownload.id)
        assertFalse(file.exists())
        assertEquals(listOf(server.descriptor.id), library.versions(heat).map { it.version.location.sourceId })
        assertEquals(40 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(ResumePoint(40 * MINUTE, ResumeBasis.SAME_TIMELINE), assertNotNull(library.planPlayback(heat, device, sources)).resume)

        // The server is removed altogether. What was copied from it is still the user's to watch.
        library.removeSource(server.descriptor.id)
        assertEquals(listOf("Arrival"), library.movies().map { it.item.title })
        assertEquals(DownloadsSource.ID, assertNotNull(library.planPlayback(arrival, device, sources)).version.version.location.sourceId)
        library.syncDownloads()
        assertEquals(listOf("Arrival"), library.movies().map { it.item.title }, "a copy outlives what it was copied from")

        // Removing the last copy removes the work from the library, and never its history.
        watch(downloaded(arrival), minutes = 20)
        offline.remove(library.downloads().single().id)
        assertTrue(library.movies().isEmpty())
        assertEquals(20 * MINUTE, library.watchState(arrival)?.positionMs)
    }

    @Test
    fun aCopyFollowsItsOriginThroughCorrectionsAndSurvivesItsStorageGoingAway() = runTest {
        share.file("Movies/Heat.1995.1080p.mkv")
        library.scan(share)
        val before = movie("Heat")
        val offline = started()
        offline.enqueue(offline.planFor(listOf(before)))
        runCurrent()
        assertEquals(2, library.versions(before).size)

        // The file turns out to be something else. The copy is the same file, so it is that too.
        library.identify(copyOn(share.descriptor.id, before).location, IdentityOverride(ParsedKind.MOVIE, "Ronin", 1998))
        val after = movie("Ronin")
        assertEquals(listOf("Ronin"), library.movies().map { it.item.title })
        assertEquals(2, library.versions(after).size)
        assertEquals(after, library.downloads().single().itemId)

        // Where downloads are kept is away (storage ejected): the library keeps everything, marked as away.
        store.away = true
        library.syncDownloads()
        assertEquals(2, library.versions(after).size)
        assertEquals(Availability.UNAVAILABLE, library.versions(after).single { it.version.location.sourceId == DownloadsSource.ID }.availability)
        assertEquals(DownloadState.COMPLETED, library.downloads().single().state)
        assertEquals(share.descriptor.id, assertNotNull(library.planPlayback(after, device, sources)).version.version.location.sourceId)
        store.away = false
        library.syncDownloads()
        assertEquals(DownloadsSource.ID, assertNotNull(library.planPlayback(after, device, sources)).version.version.location.sourceId)

        // The file itself is gone while the storage is there (cleared behind Reflux's back): the copy is no
        // longer a version, the download says so, and downloading again puts it back.
        File(java.net.URI(store.uri(library.downloads().single().path))).delete()
        library.syncDownloads()
        assertEquals(DownloadState.UNAVAILABLE, library.downloads().single().state)
        assertEquals(listOf(share.descriptor.id), library.versions(after).map { it.version.location.sourceId })
        offline.resume(library.downloads().single().id)
        runCurrent()
        assertEquals(DownloadState.COMPLETED, library.downloads().single().state)
        assertEquals(2, library.versions(after).size)
    }
}
