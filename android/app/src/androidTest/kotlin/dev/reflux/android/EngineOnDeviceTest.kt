package dev.reflux.android

import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.reflux.android.playback.AndroidMediaProber
import dev.reflux.core.identify.MediaPathParser
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.input.GamepadButton
import dev.reflux.core.input.Key
import dev.reflux.core.model.Availability
import dev.reflux.core.model.StableIds
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Chapter
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.SkipSegments
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.search.SearchVocabulary
import dev.reflux.core.search.SmartQueryParser
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.watch.Cuts
import dev.reflux.core.watch.ResumeBasis
import dev.reflux.core.watch.ResumePoint
import dev.reflux.library.planPlayback
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A stand-in for a media server: lists identified works and can go away. Nothing of any particular server. */
private class HomeServer : CatalogSource {
    private val entries = ArrayList<CatalogEntry>()
    var reachable = true
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://home.example"), "server", "Home Server", SourceLocality.LOCAL_NETWORK, setOf(SourceCapability.PROVIDES_CATALOG),
    )

    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://home.example/$path")
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    fun movie(id: String, title: String, year: Int, stream: StreamInfo) {
        entries += CatalogEntry(ParsedMedia(ParsedKind.MOVIE, title, year), listOf(CatalogVersion("items/$id", 2_000_000_000, 1, stream)))
    }
}

private fun stream(minutes: Double) = StreamInfo(
    container = Container.MATROSKA,
    video = VideoStream(VideoCodec.H264, 1920, 1080, 8),
    audio = listOf(AudioStream(AudioCodec.AAC, 2, "eng")),
    durationMs = (minutes * MINUTE).toLong(),
)

/**
 * The shared engine on a real Android runtime: its regular expressions on Android's regex engine, the
 * library on Android's SQLite, a folder source, the platform prober, and the work's watch state carried
 * between a local copy and a server's. The same code the desktop runs, where it has not run before.
 */
@RunWith(AndroidJUnit4::class)
class EngineOnDeviceTest {
    private val worlds = ArrayList<DeviceWorld>()
    private fun world(name: String) = DeviceWorld(name).also { worlds += it }

    @After
    fun cleanUp() = worlds.forEach(DeviceWorld::close)

    @Test
    fun sharedParsingBehavesHereAsOnTheDesktop() {
        // Android's regular expressions are a different engine from the desktop JVM's and stricter about syntax;
        // every pattern in these classes is compiled here, and a representative name of each kind goes through.
        val parser = MediaPathParser(maxYear = 2027)
        val heat = parser.parse("Movies/Heat (1995)/Heat.1995.2160p.UHD.BluRay.HDR.x265.mkv")!!
        assertEquals(listOf(ParsedKind.MOVIE, "Heat", 1995), listOf(heat.kind, heat.title, heat.year))
        val extended = parser.parse("Aliens (1986) {edition-Extended Edition} [imdbid-tt0090605].mkv")!!
        assertEquals("Aliens" to "Extended Edition", extended.title to extended.edition)
        assertEquals("extended", Cuts.of(extended.edition))
        val episode = parser.parse("Shows/Breaking Bad/Season 1/Breaking.Bad.S01E03.And.the.Bags.in.the.River.1080p.mkv")!!
        assertEquals(listOf(ParsedKind.EPISODE, "Breaking Bad", 1, 3), listOf(episode.kind, episode.title, episode.season, episode.episode))
        val anime = parser.parse("[SubGroup] Frieren - 07 [1080p][HEVC].mkv")!!
        assertEquals(ParsedKind.EPISODE, anime.kind)
        assertEquals(ParsedKind.EPISODE, parser.parse("The Daily Show/The.Daily.Show.2024.03.14.Guest.Name.mkv")!!.kind)

        val query = SmartQueryParser.parse("4k movies from the 90s under 2 hours I haven't watched", SearchVocabulary(emptySet(), emptySet()), 2026)
        assertTrue(query.structured)
        assertNotNull(SkipSegments.from(listOf(Chapter("Opening Credits", 0), Chapter("Chapter 1", 60_000)), 600_000).singleOrNull())
    }

    @Test
    fun aFolderBecomesALibraryOnAndroidsOwnDatabase() = runBlocking {
        val world = world("library")
        world.clip("Movies/Night Drive (2021)/Night Drive (2021).mp4")
        world.stub("Movies/Night Drive (2021)/poster.jpg")
        world.stub("Shows/Signal Fires/Season 1/Signal.Fires.S01E01.mkv")
        world.stub("Shows/Signal Fires/Season 1/Signal.Fires.S01E02.mkv")
        val library = world.environment.library
        world.environment.pipeline.refresh()

        val movie = library.movies().single()
        assertEquals("Night Drive" to 2021, movie.item.title to (movie.item as dev.reflux.core.model.Movie).year)
        val show = library.shows().single()
        assertEquals(2, library.showDetail(show.item.id)!!.seasons.single().episodes.size)

        // The platform's own extractor read the file: that is where the running time comes from.
        val version = library.versions(movie.item.id).single().version
        assertEquals(VideoCodec.H264, version.stream.video?.codec)
        assertEquals(320 to 180, version.stream.video?.width to version.stream.video?.height)
        assertEquals("eng", version.stream.audio.single().language)
        assertTrue(version.stream.durationMs in 175_000L..185_000L, "about three minutes: ${version.stream.durationMs}")
        // A file that is not media is unreadable to the prober and stays described by its name.
        assertNull(AndroidMediaProber(context).probe(PlaybackTarget(world.tree.open(world.stub("Movies/Broken (2001).mkv").path))))

        val plan = assertNotNull(library.planPlayback(movie.item.id, world.environment.capabilities, world.sources))
        assertTrue(plan.request.target.uri.startsWith("file:///"))
        assertTrue(plan.version.playable, "this device can play what its own extractor described: ${plan.version.assessment.issues}")

        // The folder going away takes nothing with it.
        library.stopped(movie.item.id, 100_000, version.stream.durationMs, version)
        world.tree.present = false
        world.environment.pipeline.refresh()
        assertEquals(Availability.UNAVAILABLE, library.movies().single().availability)
        assertEquals(100_000L, library.watchState(movie.item.id)?.positionMs)
        assertNull(library.planPlayback(movie.item.id, world.environment.capabilities, world.sources))
        world.tree.present = true
        world.environment.pipeline.refresh()
        assertEquals(ResumePoint(100_000, ResumeBasis.SAME_VERSION), library.planPlayback(movie.item.id, world.environment.capabilities, world.sources)?.resume)
    }

    @Test
    fun progressFollowsTheWorkBetweenThisDeviceAndAServer() = runBlocking {
        val world = world("handoff")
        val server = HomeServer()
        world.add(server, "https://home.example")
        world.stub("Movies/Heat (1995)/Heat.1995.1080p.x264.mkv")
        server.movie("heat", "Heat", 1995, stream(170.0))
        val library = world.environment.library
        library.scan(world.folder)
        library.scan(server)
        val heat = world.movie("Heat")
        assertEquals(2, library.versions(heat).size, "one work, a copy on the device and one on the server")
        val local = world.copyOn(world.folder, heat)
        library.recordProbe(local.id, stream(170.0))
        val device = world.environment.capabilities

        // With both there, the copy on the device plays.
        val both = assertNotNull(library.planPlayback(heat, device, world.sources))
        assertEquals(world.folder.descriptor.id, both.version.version.location.sourceId)
        assertEquals(SelectionCriterion.LOCALITY, both.selection.decidedBy)

        // Watched on the device to 00:30:12; later the folder is not there, and the server's copy continues from it.
        library.stopped(heat, 30 * MINUTE + 12_000, 170 * MINUTE, world.copyOn(world.folder, heat))
        world.tree.present = false
        library.scan(world.folder)
        val onServer = assertNotNull(library.planPlayback(heat, device, world.sources))
        assertEquals(server.descriptor.id, onServer.version.version.location.sourceId)
        assertEquals(ResumePoint(30 * MINUTE + 12_000, ResumeBasis.SAME_TIMELINE), onServer.resume)
        assertEquals(30 * MINUTE + 12_000, onServer.request.startPositionMs)

        // Watched on from the server to 00:45:00; back with the folder, the local copy continues from that.
        library.stopped(heat, 45 * MINUTE, 170 * MINUTE, world.copyOn(server, heat))
        world.tree.present = true
        library.scan(world.folder)
        server.reachable = false
        library.scan(server)
        val backHome = assertNotNull(library.planPlayback(heat, device, world.sources))
        assertEquals(world.folder.descriptor.id, backHome.version.version.location.sourceId)
        assertEquals(ResumePoint(45 * MINUTE, ResumeBasis.SAME_TIMELINE), backHome.resume)
        assertEquals(heat, library.continueWatching().single().item.id, "one thing to continue, whichever copy it was")
    }

    @Test
    fun androidInputBecomesRefluxInput() {
        fun key(code: Int, source: Int = android.view.InputDevice.SOURCE_KEYBOARD) =
            KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, 0, 0, 0, 0, source)
        assertEquals(Key.ARROW_LEFT, Input.key(key(KeyEvent.KEYCODE_DPAD_LEFT)))
        assertEquals(Key.MEDIA_PLAY_PAUSE, Input.key(key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        assertNull(Input.key(key(KeyEvent.KEYCODE_VOLUME_UP)), "volume stays the system's")
        assertNull(Input.gamepadButton(key(KeyEvent.KEYCODE_DPAD_LEFT)), "a keyboard's arrows are not a controller")
        assertEquals(GamepadButton.DPAD_LEFT, Input.gamepadButton(key(KeyEvent.KEYCODE_DPAD_LEFT, android.view.InputDevice.SOURCE_GAMEPAD)))
        // By position, whatever the controller prints on the button.
        assertEquals(GamepadButton.FACE_SOUTH, Input.gamepadButton(key(KeyEvent.KEYCODE_BUTTON_A, android.view.InputDevice.SOURCE_GAMEPAD)))
        assertEquals(GamepadButton.FACE_EAST, Input.gamepadButton(key(KeyEvent.KEYCODE_BUTTON_B, android.view.InputDevice.SOURCE_GAMEPAD)))
    }
}
