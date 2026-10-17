package dev.reflux.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
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
import dev.reflux.library.ScanReport
import dev.reflux.library.planPlayback
import dev.reflux.ui.state.FolderChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A server double that keeps its own watch records and takes updates: the shape of one, none in particular. */
private class RecordingServer : CatalogSource, WatchStateSyncSource {
    private val entries = LinkedHashMap<String, CatalogEntry>()
    var reachable = true
    val told = ArrayList<String>()
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://home.example"), "server", "Home Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://home.example/$path")
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.values.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }
    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) = Unit
    override suspend fun reportStopped(path: String, positionMs: Long) { told += "position $path ${positionMs / MINUTE}" }
    override suspend fun setPlayed(path: String, played: Boolean) { told += "played $path $played" }

    fun movie(id: String, title: String, year: Int, minutes: Double, edition: String? = null) {
        val stream = StreamInfo(
            container = Container.MATROSKA,
            video = VideoStream(VideoCodec.H264, 1920, 1080, 8),
            audio = listOf(AudioStream(AudioCodec.AAC, 2, "eng")),
            durationMs = (minutes * MINUTE).toLong(),
        )
        entries[id] = CatalogEntry(
            ParsedMedia(ParsedKind.MOVIE, title, year),
            listOf(CatalogVersion("items/$id", 2_000_000_000, 1, stream, edition)),
            userState = SourceUserState(played = false, positionMs = 0, playCount = 0, lastPlayedAtEpochMs = null, favorite = false),
        )
    }

    /** What the server's own record says, as after watching on another of its clients. */
    fun says(id: String, minutes: Long, at: Long) {
        entries[id] = entries.getValue(id).copy(userState = SourceUserState(false, minutes * MINUTE, 0, at, false))
    }
}

/**
 * Storage that comes and goes, and a server that has its own idea of where the viewer is: the library and
 * the watch state keep their heads on Android as they do everywhere, on Android's database and runtime.
 */
@RunWith(AndroidJUnit4::class)
class StorageAndSyncOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private fun world(name: String) = DeviceWorld(name).also { worlds += it }

    @After
    fun cleanUp() = worlds.forEach(DeviceWorld::close)

    @Test
    fun storageThatGoesAwayOrComesBackEmptyKeepsTheLibrary() = runBlocking {
        val world = world("storage")
        val file = world.stub("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        world.stub("Shows/Severance/Season 1/Severance.S01E01.mkv")
        val library = world.environment.library
        world.environment.pipeline.refresh()
        val heat = world.movie("Heat")
        library.stopped(heat, 40 * MINUTE, 170 * MINUTE, world.copyOn(world.folder, heat))

        // The card is ejected, or the grant is taken back: the folder cannot be listed at all.
        world.tree.present = false
        assertEquals(ScanReport.Status.UNAVAILABLE, world.environment.pipeline.refresh().single().status)
        assertEquals(listOf(Availability.UNAVAILABLE), library.movies().map { it.availability })
        assertEquals(1, library.shows().size)
        assertNull(library.planPlayback(heat, world.environment.capabilities, world.sources), "nothing to play, and no crash")

        // The folder is back but empty (a card not mounted yet, a share still connecting): nothing is deleted.
        world.tree.present = true
        val parked = File(world.directory, "parked")
        assertTrue(world.media.renameTo(parked) && world.media.mkdirs())
        assertEquals(ScanReport.Status.EMPTY_KEPT, world.environment.pipeline.refresh().single().status)
        assertEquals(1, library.movies().size)
        assertEquals(40 * MINUTE, library.watchState(heat)?.positionMs)

        // Everything is there again: the library is as it was, progress included.
        assertTrue(world.media.deleteRecursively() && parked.renameTo(world.media))
        assertEquals(ScanReport.Status.COMPLETED, world.environment.pipeline.refresh().single().status)
        assertEquals(Availability.AVAILABLE, library.movies().single().availability)
        assertEquals(ResumePoint(40 * MINUTE, ResumeBasis.SAME_VERSION), library.planPlayback(heat, world.environment.capabilities, world.sources)?.resume)

        // A file the user really deleted does leave, while the folder is otherwise there. Its history stays.
        assertTrue(file.delete())
        world.environment.pipeline.refresh()
        assertTrue(library.movies().isEmpty())
        assertEquals(40 * MINUTE, library.watchState(heat)?.positionMs)
    }

    @Test
    fun aSubfolderThatCannotBeReadKeepsWhatWasBelowIt() = runBlocking {
        val world = world("partial")
        world.stub("Shows/Severance/Season 1/Severance.S01E01.mkv")
        val heat = world.stub("Movies/Heat (1995)/Heat.1995.1080p.mkv")
        // A film that stays: a listing with nothing playable in it at all reads as the storage being away.
        world.stub("Movies/Arrival (2016)/Arrival.2016.1080p.mkv")
        world.environment.library.scan(world.folder)
        assertEquals(1, world.environment.library.shows().size)

        // The drive goes partway through the next scan: the shows folder cannot be listed. A film was deleted.
        world.tree.unreadable += File(world.media, "Shows").path
        heat.delete()
        world.environment.library.scan(world.folder)
        assertEquals(1, world.environment.library.shows().size, "what was not seen is not gone")
        assertEquals(listOf("Arrival"), world.environment.library.movies().map { it.item.title }, "what was seen to be gone is gone")
    }

    @Test
    fun choosingNoFolderChangesNothing() = runBlocking {
        val world = world("cancel")
        val platform = AndroidPlatform(world.environment)
        assertEquals(FolderChoice.Cancelled, platform.addMediaFolder(), "no picker to ask")
        platform.pickFolder = { null }
        assertEquals(FolderChoice.Cancelled, platform.addMediaFolder(), "the picker was dismissed")
        assertEquals(1, world.environment.library.sources().size)
    }

    @Test
    fun aDifferentCutAndAStaleServerRecordAreHandledHereAsEverywhere() = runBlocking {
        val world = world("cuts")
        val server = RecordingServer()
        world.add(server, "https://home.example")
        world.stub("Movies/Aliens (1986)/Aliens.1986.1080p.x264.mkv")
        server.movie("aliens-extended", "Aliens", 1986, minutes = 154.0, edition = "Extended Edition")
        val library = world.environment.library
        var now = System.currentTimeMillis()
        library.scan(world.folder)
        server.says("aliens-extended", minutes = 10, at = now - 86_400_000)
        library.scan(server)
        val aliens = world.movie("Aliens")
        val theatrical = world.copyOn(world.folder, aliens)
        library.recordProbe(theatrical.id, theatrical.stream.copy(durationMs = 137 * MINUTE))
        val device = world.environment.capabilities
        assertEquals(10 * MINUTE, library.watchState(aliens)?.positionMs, "watched on one of the server's own clients yesterday")
        assertEquals("extended", library.watchState(aliens)?.cut)

        // Part-way through the extended cut, playback stays on it while it is there...
        library.stopped(aliens, 100 * MINUTE, 154 * MINUTE, world.copyOn(server, aliens))
        val staying = assertNotNull(library.planPlayback(aliens, device, world.sources))
        assertEquals(server.descriptor.id, staying.version.version.location.sourceId)
        assertEquals(SelectionCriterion.CONTINUITY, staying.selection.decidedBy)

        // ...and with only the shorter theatrical cut left, minute 100 is not reused: playback picks up
        // where nothing unseen can have been skipped.
        server.reachable = false
        library.scan(server)
        val shorter = assertNotNull(library.planPlayback(aliens, device, world.sources))
        assertEquals(world.folder.descriptor.id, shorter.version.version.location.sourceId)
        assertEquals(ResumePoint(83 * MINUTE, ResumeBasis.ADJUSTED), shorter.resume)

        // The server comes back still saying ten minutes. That is not news and does not come back; it is
        // the server that is behind, and it is told where the viewer got to in its own copy.
        server.reachable = true
        library.scan(server)
        assertEquals(100 * MINUTE, library.watchState(aliens)?.positionMs)
        assertEquals(listOf("position items/aliens-extended 100"), server.told)

        // Watching on in the theatrical cut is not sent as a position: minute 90 there is not minute 90
        // in the server's extended cut.
        server.told.clear()
        Thread.sleep(5)
        library.stopped(aliens, 90 * MINUTE, 137 * MINUTE, world.copyOn(world.folder, aliens))
        library.scan(server)
        assertTrue(server.told.none { it.startsWith("position") }, "told: ${server.told}")
        assertEquals(90 * MINUTE, library.watchState(aliens)?.positionMs)
    }
}
