package dev.reflux.library

import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
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
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.core.source.WatchStateSyncSource
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.core.watch.ResumePoint
import dev.reflux.library.db.RefluxDatabase
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L

/** A 1080p or 4K picture with 5.1 sound, [minutes] long. */
private fun stream(minutes: Double?, height: Int = 1080, codec: VideoCodec = VideoCodec.H264, bitDepth: Int = 8) = StreamInfo(
    container = Container.MATROSKA,
    video = VideoStream(codec, height * 16 / 9, height, bitDepth),
    audio = listOf(AudioStream(AudioCodec.AC3, 6, "eng")),
    durationMs = minutes?.let { (it * MINUTE).toLong() },
)

/**
 * A source that lists identified works, keeps its own watch state, and accepts updates: the shape of a
 * media server, with nothing of any particular one.
 */
private class HomeServer : CatalogSource, WatchStateSyncSource {
    private val entries = linkedMapOf<String, CatalogEntry>()
    var reachable = true
    var acceptsUpdates = true

    /** How long the server takes to answer a progress report. */
    var progressLatencyMs = 0L
    val told = mutableListOf<String>()

    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://home.example"), "server", "Home Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://home.example/$path")
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.values.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) {
        delay(progressLatencyMs)
        tell("progress $path ${positionMs / MINUTE}")
    }
    override suspend fun reportStopped(path: String, positionMs: Long) = tell("position $path ${positionMs / MINUTE}")
    override suspend fun setPlayed(path: String, played: Boolean) = tell("played $path $played")

    private fun tell(what: String) {
        if (!reachable || !acceptsUpdates) throw SourceUnavailableException(descriptor.id)
        told += what
    }

    /** As a server reports an item nobody has played. */
    private val untouched = SourceUserState(played = false, positionMs = 0, playCount = 0, lastPlayedAtEpochMs = null, favorite = false)

    fun movie(id: String, title: String, year: Int, stream: StreamInfo, edition: String? = null) {
        entries[id] = CatalogEntry(
            ParsedMedia(ParsedKind.MOVIE, title, year),
            listOf(CatalogVersion("items/$id", 2_000_000_000, 1, stream, edition)),
            userState = untouched,
        )
    }

    fun episode(id: String, show: String, season: Int, episode: Int, stream: StreamInfo) {
        entries[id] = CatalogEntry(
            ParsedMedia(ParsedKind.EPISODE, show, null, season = season, episode = episode),
            listOf(CatalogVersion("items/$id", 500_000_000, 1, stream)),
            userState = untouched,
        )
    }

    /** What the server's own record for [id] says, as after watching on one of its own clients. */
    fun says(id: String, played: Boolean = false, minutes: Long = 0, at: Long? = null) {
        entries[id] = entries.getValue(id).copy(userState = SourceUserState(played, minutes * MINUTE, if (played) 1 else 0, at, favorite = false))
    }
}

/**
 * Playback progress follows the work, not the file or the source. Everything here goes through the library
 * as a shell uses it, with a local folder and a source-independent stand-in for a media server.
 */
class WatchHandoffTest {
    private val root: Path = Files.createTempDirectory("reflux-handoff")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val local = LocalFolderSource(root)
    private val server = HomeServer()
    private var localAway = false
    private val sources = { id: SourceId ->
        when (id) {
            local.descriptor.id -> local.takeUnless { localAway }
            server.descriptor.id -> server
            else -> null
        }
    }

    /** Plays H.264 and HEVC in hardware on a 4K display. */
    private val device = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true),
        ),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    /** Plays H.264 up to 1080p and nothing else. */
    private val modestDevice = device.copy(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 1920, 1080, 8, hardware = true)),
        display = DisplayCapabilities(1920, 1080),
    )

    init {
        library.addSource(local, root.toString())
        library.addSource(server, "https://home.example")
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    /** A local file whose streams have been probed as [stream]. */
    private suspend fun localFile(relative: String, stream: StreamInfo) {
        root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }
        library.scan(local)
        val version = library.movies().flatMap { library.versions(it.item.id) }.plus(episodeVersions())
            .first { it.version.location.sourceId == local.descriptor.id && it.version.location.path == relative }
        library.recordProbe(version.version.id, stream)
    }

    private fun episodeVersions() = library.shows().flatMap { show ->
        library.showDetail(show.item.id)!!.seasons.flatMap { season -> season.episodes.flatMap { library.versions(it.item.id) } }
    }

    private fun movie(title: String): MediaId = library.movies().single { it.item.title == title }.item.id

    private fun copyOn(source: MediaSource, item: MediaId): MediaVersion =
        library.versions(item).single { it.version.location.sourceId == source.descriptor.id }.version

    /** The viewer watches [version] and stops [minutes] in. */
    private fun watch(version: MediaVersion, minutes: Long) {
        clock += 60_000
        library.stopped(version.itemId, minutes * MINUTE, version.stream.durationMs, version)
    }

    private suspend fun localGoesAway() {
        localAway = true
        root.toFile().deleteRecursively()
        library.refreshAvailability(local)
    }

    private suspend fun plan(item: MediaId, on: DeviceCapabilities = device) = assertNotNull(library.planPlayback(item, on, sources))

    @Test
    fun progressIsSavedAndRestored() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 30)

        val state = assertNotNull(library.watchState(heat))
        assertEquals(30 * MINUTE, state.positionMs)
        assertEquals(170 * MINUTE, state.durationMs)
        assertEquals(copyOn(local, heat).id, state.versionId)
        assertEquals(clock, state.updatedAtEpochMs)

        val plan = plan(heat)
        assertEquals(30 * MINUTE, plan.request.startPositionMs)
        assertEquals(ResumeBasis.SAME_VERSION, plan.resume.basis)
        assertEquals(heat, library.continueWatching().single().item.id)
    }

    @Test
    fun progressFollowsTheWorkFromALocalFileToAServer() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        assertEquals(2, library.versions(heat).size, "one work, two copies")

        // Watched from the local file to 00:30:12.
        library.stopped(heat, 30 * MINUTE + 12_000, 170 * MINUTE, copyOn(local, heat))
        assertEquals(local.descriptor.id, plan(heat).version.version.location.sourceId)

        // Later the local copy is not there. The same press of Play continues from the server's copy.
        localGoesAway()
        val plan = plan(heat)
        assertEquals(server.descriptor.id, plan.version.version.location.sourceId)
        assertEquals(30 * MINUTE + 12_000, plan.request.startPositionMs)
        assertEquals(ResumePoint(30 * MINUTE + 12_000, ResumeBasis.SAME_TIMELINE), plan.resume)
        assertEquals(SelectionCriterion.AVAILABILITY, plan.selection.decidedBy)
        assertEquals(heat, library.continueWatching().single().item.id, "still one thing to continue")

        // ...and back again: what was watched on the server continues on the local file.
        watch(copyOn(server, heat), minutes = 95)
        assertEquals(95 * MINUTE, library.watchState(heat)?.positionMs)
    }

    @Test
    fun progressFollowsTheWorkFromAServerToALocalFile() = runTest(UnconfinedTestDispatcher()) {
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        // Played from the server, through the reporter a player session uses.
        val reporter = SyncingWatchReporter(library, copyOn(server, heat), server, this)
        reporter.stopped(heat, 48 * MINUTE, 170 * MINUTE)
        assertEquals(listOf("position items/heat 48"), server.told)

        // The file arrives locally (a download, a drive plugged in) and the server goes away.
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.05))
        server.reachable = false
        library.scan(server)
        val plan = plan(heat)
        assertEquals(local.descriptor.id, plan.version.version.location.sourceId)
        assertEquals(48 * MINUTE, plan.request.startPositionMs)
    }

    @Test
    fun reportsReachTheServerInTheOrderTheyWereMade() = runTest {
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        // The server is slow to take a progress report, and playback stops right after it.
        server.progressLatencyMs = 2_000
        val reporter = SyncingWatchReporter(library, copyOn(server, heat), server, this)
        reporter.progress(heat, 47 * MINUTE, 170 * MINUTE)
        reporter.stopped(heat, 48 * MINUTE, 170 * MINUTE)
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("progress items/heat 47", "position items/heat 48"), server.told, "the stop is the server's last word")
        assertEquals(48 * MINUTE, library.watchState(heat)?.positionMs, "and Reflux's own state never waited for it")
    }

    @Test
    fun whenOneSourceIsAwayAnotherPlays() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 30)

        server.reachable = false
        library.scan(server)
        assertEquals(local.descriptor.id, plan(heat).version.version.location.sourceId)

        server.reachable = true
        library.scan(server)
        localGoesAway()
        assertEquals(server.descriptor.id, plan(heat).version.version.location.sourceId)

        server.reachable = false
        library.scan(server)
        assertNull(library.planPlayback(heat, device, sources), "nothing is reachable")
        assertEquals(30 * MINUTE, library.watchState(heat)?.positionMs, "and the progress waits")
    }

    @Test
    fun theLocalCopyWinsWhenTheTwoAreEquivalent() = runTest {
        // 1080p H.264 8-bit here, the same from the server.
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val plan = plan(movie("Heat"))
        assertEquals(local.descriptor.id, plan.version.version.location.sourceId)
        assertEquals(SelectionCriterion.LOCALITY, plan.selection.decidedBy)
    }

    @Test
    fun theServerWinsWhenTheLocalCopyIsMateriallyLessSuitable() = runTest {
        // 4K HEVC 10-bit here, 1080p H.264 from the server.
        localFile("Heat (1995)/Heat.1995.2160p.x265.10bit.mkv", stream(170.0, height = 2160, codec = VideoCodec.HEVC, bitDepth = 10))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")

        // A device without HEVC plays the stream; one that decodes it well plays the local file.
        val modest = plan(heat, on = modestDevice)
        assertEquals(server.descriptor.id, modest.version.version.location.sourceId)
        assertEquals(SelectionCriterion.COMPATIBILITY, modest.selection.decidedBy)
        assertEquals(local.descriptor.id, plan(heat).version.version.location.sourceId)

        // Either way the progress is the work's.
        watch(copyOn(server, heat), minutes = 40)
        assertEquals(40 * MINUTE, plan(heat, on = modestDevice).request.startPositionMs)
        assertEquals(40 * MINUTE, plan(heat).request.startPositionMs)
    }

    @Test
    fun whatTheServerAlreadySaidDoesNotOverwriteNewerProgress() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        server.says("heat", minutes = 10, at = clock - 86_400_000)
        library.scan(server)
        val heat = movie("Heat")
        assertEquals(10 * MINUTE, library.watchState(heat)?.positionMs, "watched on the server's own client yesterday")

        // Today the viewer goes on from the local file. The server still says ten minutes.
        watch(copyOn(local, heat), minutes = 55)
        clock += 60_000
        library.scan(server)
        assertEquals(55 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(copyOn(local, heat).id, library.watchState(heat)?.versionId)
        // The server is the one that was behind, and is told.
        assertEquals(listOf("position items/heat 55"), server.told)

        // Nothing left to settle: another scan tells it nothing more.
        server.says("heat", minutes = 55, at = clock)
        clock += 60_000
        library.scan(server)
        assertEquals(1, server.told.size)
        assertEquals(55 * MINUTE, library.watchState(heat)?.positionMs)
    }

    @Test
    fun aServerThatDoesNotTakeAnUpdateIsNotBelievedLater() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        server.says("heat", minutes = 10, at = clock - 86_400_000)
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 55)

        // The update cannot be delivered; it stays pending and is tried again on the next scan.
        server.acceptsUpdates = false
        clock += 60_000
        library.scan(server)
        assertTrue(server.told.isEmpty())
        server.acceptsUpdates = true
        clock += 60_000
        library.scan(server)
        assertEquals(listOf("position items/heat 55"), server.told)

        // The server accepted it and still reports its old record. That record is not news.
        clock += 60_000
        library.scan(server)
        assertEquals(55 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(1, server.told.size, "and it is not told again and again")
    }

    @Test
    fun newsFromTheServerIsAdopted() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 30)
        clock += 60_000
        library.scan(server)
        server.told.clear()

        // Watched further on the server's own client.
        clock += 3_600_000
        server.says("heat", minutes = 90, at = clock)
        clock += 60_000
        library.scan(server)
        assertEquals(90 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(copyOn(server, heat).id, library.watchState(heat)?.versionId)
        assertTrue(server.told.isEmpty())
        // It continues from the local file, which wins among equivalent copies.
        val plan = plan(heat)
        assertEquals(local.descriptor.id, plan.version.version.location.sourceId)
        assertEquals(90 * MINUTE, plan.request.startPositionMs)
    }

    @Test
    fun watchedIsWatchedWhicheverCopyWasPlayed() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 60)
        clock += 60_000
        library.scan(server)
        server.told.clear()

        // Finished on the server's copy.
        watch(copyOn(server, heat), minutes = 168)
        val state = assertNotNull(library.watchState(heat))
        assertTrue(state.completed)
        assertEquals(1, state.playCount)
        assertTrue(library.movies().single().watchState!!.completed)
        assertTrue(library.continueWatching().isEmpty())
        assertNull(plan(heat).request.startPositionMs, "the local file starts from the beginning: there is nothing to resume")

        // Marked unwatched while the server is away: the server's "played" does not come back, and it is told.
        server.says("heat", played = true, at = clock)
        clock += 60_000
        library.scan(server)
        server.reachable = false
        clock += 60_000
        assertEquals(1, library.setWatchedEverywhere(heat, false, sources), "the server could not be told")
        server.reachable = true
        clock += 60_000
        library.scan(server)
        assertFalse(library.watchState(heat)!!.completed)
        assertEquals(listOf("played items/heat false"), server.told)
    }

    @Test
    fun episodeProgressBelongsToEachEpisode() = runTest {
        localFile("Severance/Season 1/Severance.S01E01.1080p.mkv", stream(52.0))
        localFile("Severance/Season 1/Severance.S01E02.1080p.mkv", stream(49.0))
        server.episode("s1e1", "Severance", 1, 1, stream(52.0))
        server.episode("s1e2", "Severance", 1, 2, stream(49.0))
        library.scan(server)
        val show = library.shows().single().item.id
        val (first, second) = library.showDetail(show)!!.seasons.single().episodes.map { it.item.id }
        assertEquals(2, library.versions(first).size)

        watch(copyOn(local, first), minutes = 51)
        watch(copyOn(local, second), minutes = 20)
        assertTrue(library.watchState(first)!!.completed)
        assertEquals(20 * MINUTE, library.watchState(second)?.positionMs)

        localGoesAway()
        val detail = library.showDetail(show)!!
        assertEquals(second, detail.nextUp?.id)
        val plan = plan(second)
        assertEquals("items/s1e2", plan.version.version.location.path)
        assertEquals(20 * MINUTE, plan.request.startPositionMs)
        assertNull(plan(first).request.startPositionMs)
        assertEquals(listOf(true, false), detail.seasons.single().episodes.map { it.watchState?.completed })
    }

    @Test
    fun aDifferentCutDoesNotReuseTheTimestamp() = runTest {
        // The theatrical cut here, the extended one (17 minutes longer) on the server.
        localFile("Aliens (1986)/Aliens.1986.1080p.x264.mkv", stream(137.0))
        server.movie("aliens-extended", "Aliens", 1986, stream(154.0), edition = "Extended Edition")
        library.scan(server)
        val aliens = movie("Aliens")
        val extended = copyOn(server, aliens)

        // Watched 100 minutes of the extended cut.
        watch(extended, minutes = 100)
        assertEquals("extended", library.watchState(aliens)?.cut)
        // While both are there, playback stays on the cut in progress.
        val staying = plan(aliens)
        assertEquals(extended.id, staying.version.version.id)
        assertEquals(SelectionCriterion.CONTINUITY, staying.selection.decidedBy)
        assertEquals(ResumePoint(100 * MINUTE, ResumeBasis.SAME_VERSION), staying.resume)

        // With only the theatrical cut left, minute 100 of the extended cut is not minute 100 here. Up to 17
        // minutes may be missing before it, so playback picks up where nothing unseen can have been skipped.
        server.reachable = false
        library.scan(server)
        val shorter = plan(aliens)
        assertEquals(local.descriptor.id, shorter.version.version.location.sourceId)
        assertEquals(ResumePoint(83 * MINUTE, ResumeBasis.ADJUSTED), shorter.resume)
        assertEquals(83 * MINUTE, shorter.request.startPositionMs)

        // A glance at the other cut does not erase where the viewer was in theirs.
        clock += 60_000
        library.stopped(aliens, 15_000, 137 * MINUTE, copyOn(local, aliens))
        assertEquals(100 * MINUTE, library.watchState(aliens)?.positionMs)
        assertEquals(extended.id, library.watchState(aliens)?.versionId)

        // Going on in the theatrical cut moves the progress onto it; the longer cut is then entered at the
        // same position, which is at or before the same moment.
        watch(copyOn(local, aliens), minutes = 90)
        server.reachable = true
        library.scan(server)
        localGoesAway()
        assertEquals(ResumePoint(90 * MINUTE, ResumeBasis.ADJUSTED), plan(aliens).resume)
    }

    @Test
    fun aPositionIsNotPushedOntoAServersDifferentCut() = runTest {
        localFile("Aliens (1986)/Aliens.1986.1080p.x264.mkv", stream(137.0))
        server.movie("aliens-extended", "Aliens", 1986, stream(154.0), edition = "Extended Edition")
        library.scan(server)
        val aliens = movie("Aliens")
        watch(copyOn(local, aliens), minutes = 70)
        clock += 60_000
        library.scan(server)
        assertTrue(server.told.isEmpty(), "minute 70 of the theatrical cut means nothing in the server's extended one")

        // Having finished the film is true of the work, and the server hears that.
        watch(copyOn(local, aliens), minutes = 135)
        clock += 60_000
        library.scan(server)
        assertEquals(listOf("played items/aliens-extended true"), server.told)
    }

    @Test
    fun smallDifferencesInRunningTimeAreTheSameFilm() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        // Another encode: four seconds longer.
        server.movie("heat", "Heat", 1995, stream(170.0 + 4.0 / 60))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 95)
        localGoesAway()
        assertEquals(ResumePoint(95 * MINUTE, ResumeBasis.SAME_TIMELINE), plan(heat).resume)
    }

    @Test
    fun aCopyThatRunsMinutesShorterIsTreatedAsADifferentEdit() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        // No label says so, but seven minutes are missing (a sped-up transfer, a trimmed broadcast copy).
        server.movie("heat", "Heat", 1995, stream(163.0))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(local, heat), minutes = 95)
        localGoesAway()
        assertEquals(ResumePoint(88 * MINUTE, ResumeBasis.ADJUSTED), plan(heat).resume)
    }

    @Test
    fun aCopyThatWasNeverInspectedStillResumes() = runTest {
        // The local file has only what its name says: no running time.
        root.resolve("Heat (1995)").createDirectories().resolve("Heat.1995.1080p.x264.mkv").writeBytes(ByteArray(8))
        library.scan(local)
        server.movie("heat", "Heat", 1995, stream(170.0))
        library.scan(server)
        val heat = movie("Heat")
        watch(copyOn(server, heat), minutes = 95)
        server.reachable = false
        library.scan(server)
        assertEquals(ResumePoint(95 * MINUTE, ResumeBasis.UNVERIFIED), plan(heat).resume)
    }

    @Test
    fun disconnectedAndRemovedSourcesDoNotTakeTheHistoryWithThem() = runTest {
        localFile("Heat (1995)/Heat.1995.1080p.x264.mkv", stream(170.0))
        server.movie("heat", "Heat", 1995, stream(170.0))
        server.movie("arrival", "Arrival", 2016, stream(116.0))
        library.scan(server)
        val heat = movie("Heat")
        val arrival = movie("Arrival")
        watch(copyOn(server, heat), minutes = 30)
        watch(copyOn(server, arrival), minutes = 114)

        // Unreachable, then reachable but listing nothing (a server mid-rebuild).
        server.reachable = false
        library.scan(server)
        assertEquals(30 * MINUTE, library.watchState(heat)?.positionMs)
        assertEquals(Availability.UNAVAILABLE, library.movies().single { it.item.id == arrival }.availability)
        assertTrue(library.watchState(arrival)!!.completed)

        // Removed altogether: its works with no other copy leave the library, the history stays.
        library.removeSource(server.descriptor.id)
        assertEquals(listOf(heat), library.movies().map { it.item.id })
        assertEquals(30 * MINUTE, plan(heat).request.startPositionMs)
        assertTrue(library.watchState(arrival)!!.completed)

        // Connected again later, the work is back with its history.
        library.addSource(server, "https://home.example")
        server.reachable = true
        library.scan(server)
        assertTrue(library.movies().single { it.item.title == "Arrival" }.watchState!!.completed)
    }

    @Test
    fun twoWorksThatTurnOutToBeOneKeepOneHistory() = runTest {
        localFile("Movies/The Thing (1982).mkv", stream(109.0))
        localFile("Movies/Das Ding aus einer anderen Welt (1982).mkv", stream(109.0))
        val thing = movie("The Thing")
        val other = library.movies().single { it.item.id != thing }.item.id
        watch(copyOn(local, thing), minutes = 20)
        clock += 3_600_000
        watch(copyOn(local, other), minutes = 70)

        // "Identify": the second file is the same film.
        library.identify(copyOn(local, other).location, IdentityOverride(ParsedKind.MOVIE, "The Thing", 1982))
        assertEquals(listOf(thing), library.movies().map { it.item.id })
        assertEquals(2, library.versions(thing).size)
        val state = assertNotNull(library.watchState(thing))
        assertEquals(70 * MINUTE, state.positionMs, "the more recent of the two is where the viewer is")
        assertNull(library.watchState(other))
    }

    @Test
    fun aLibraryFromBeforeTimelinesWereRecordedIsCarriedForward() = runTest {
        // A database as the previous schema left it: watch_state without timeline columns, no watch_sync.
        val file = Files.createTempDirectory("reflux-migrate").also { it.toFile().deleteOnExit() }.resolve("library.db")
        JdbcSqliteDriver("jdbc:sqlite:$file", Properties()).use { driver ->
            RefluxDatabase.Schema.create(driver)
            driver.execute(null, "DROP TABLE watch_sync", 0)
            driver.execute(null, "DROP TABLE watch_state", 0)
            driver.execute(
                null,
                "CREATE TABLE watch_state (item_id TEXT NOT NULL PRIMARY KEY, position_ms INTEGER NOT NULL, duration_ms INTEGER, " +
                    "completed INTEGER NOT NULL, play_count INTEGER NOT NULL, last_played_at INTEGER)",
                0,
            )
            driver.execute(null, "INSERT INTO watch_state VALUES ('m1', 1800000, 10200000, 0, 0, 1700000000000)", 0)
            driver.execute(null, "PRAGMA user_version = 1", 0)
        }

        val upgraded = Library(LibraryDatabase.open(file)) { clock }
        val state = assertNotNull(upgraded.watchState(MediaId("m1")))
        assertEquals(30 * MINUTE, state.positionMs)
        assertEquals(170 * MINUTE, state.durationMs)
        assertNull(state.versionId)
        assertNull(state.updatedAtEpochMs)
        // The old record keeps working and new ones carry their timeline.
        upgraded.stopped(MediaId("m1"), 40 * MINUTE, 170 * MINUTE)
        assertEquals(clock, upgraded.watchState(MediaId("m1"))?.updatedAtEpochMs)
    }
}
