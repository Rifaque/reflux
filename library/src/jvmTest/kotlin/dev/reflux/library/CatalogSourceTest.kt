package dev.reflux.library

import dev.reflux.core.identify.IdentityOverride
import dev.reflux.core.identify.ParsedKind
import dev.reflux.core.identify.ParsedMedia
import dev.reflux.core.model.ArtworkKind
import dev.reflux.core.model.ArtworkLocator
import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.CatalogEntry
import dev.reflux.core.source.CatalogSource
import dev.reflux.core.source.CatalogSubtitle
import dev.reflux.core.source.CatalogVersion
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
import dev.reflux.core.source.SourceDescriptor
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.source.SourceUnavailableException
import dev.reflux.core.source.SourceUserState
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeServer : CatalogSource {
    var entries = mutableListOf<CatalogEntry>()
    var reachable = true
    override val descriptor = SourceDescriptor(
        StableIds.sourceId("server", "https://media.example"), "server", "Home Server", SourceLocality.LOCAL_NETWORK,
        setOf(SourceCapability.PROVIDES_CATALOG, SourceCapability.WATCH_STATE_SYNC),
    )
    override suspend fun availability() = if (reachable) Availability.AVAILABLE else Availability.UNAVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://media.example/$path", mapOf("X-Token" to "t"))
    override fun catalog(): Flow<CatalogEntry> = if (reachable) entries.toList().asFlow() else flow { throw SourceUnavailableException(descriptor.id) }

    fun movie(id: String, title: String, year: Int, height: Int = 1080, state: SourceUserState? = null, tmdb: String? = null) {
        entries += CatalogEntry(
            identity = ParsedMedia(ParsedKind.MOVIE, title, year, externalIds = listOfNotNull(tmdb?.let { "tmdb" to it }).toMap()),
            versions = listOf(
                CatalogVersion(
                    path = "items/$id/sources/$id",
                    sizeBytes = 4_000_000_000,
                    modifiedAtEpochMs = 1,
                    stream = StreamInfo(
                        container = Container.MATROSKA,
                        video = VideoStream(VideoCodec.HEVC, height * 16 / 9, height, 10),
                        audio = listOf(AudioStream(AudioCodec.EAC3, 6, "eng")),
                        durationMs = 6_000_000,
                    ),
                    subtitles = listOf(CatalogSubtitle("items/$id/subtitles/3", SubtitleFormat.SRT, "eng")),
                ),
            ),
            artwork = mapOf(ArtworkKind.POSTER to "items/$id/images/primary"),
            userState = state,
        )
    }

    fun episode(id: String, show: String, season: Int, episode: Int, title: String) {
        entries += CatalogEntry(
            identity = ParsedMedia(ParsedKind.EPISODE, show, 2008, season = season, episode = episode, episodeTitle = title),
            versions = listOf(CatalogVersion("items/$id/sources/$id", 1_000_000_000, 1, StreamInfo(container = Container.MATROSKA))),
            artwork = mapOf(ArtworkKind.THUMBNAIL to "items/$id/images/primary"),
            showArtwork = mapOf(ArtworkKind.POSTER to "items/series-$show/images/primary"),
        )
    }
}

class CatalogSourceTest {
    private val root: Path = Files.createTempDirectory("reflux-catalog")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val local = LocalFolderSource(root)
    private val server = FakeServer()

    private val device = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 1920, 1080, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true),
        ),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    init {
        library.addSource(local, root.toString())
        library.addSource(server, "https://media.example")
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    @Test
    fun serverMoviesMergeWithLocalCopies() = runTest {
        file("Movies/Heat (1995).1080p.x265.mkv")
        server.movie("abc", "Heat", 1995, height = 2160)
        library.scan(local)
        assertEquals(1, library.scan(server).added)

        val heat = library.movies().single()
        val versions = library.versions(heat.item.id)
        assertEquals(setOf(SourceLocality.DEVICE, SourceLocality.LOCAL_NETWORK), versions.map { it.locality }.toSet())
        assertEquals(StreamInfoOrigin.SOURCE, versions.single { it.locality == SourceLocality.LOCAL_NETWORK }.version.streamOrigin)

        val plan = library.planPlayback(heat.item.id, device, sources = { id -> listOf(local, server).firstOrNull { it.descriptor.id == id } })!!
        assertEquals(2160, plan.version.version.stream.video?.height, "the 4K server copy wins on a 4K display")
        assertEquals(mapOf("X-Token" to "t"), plan.request.target.headers)
        assertEquals("eng", plan.request.subtitles.single().language)
    }

    @Test
    fun serverArtworkAndEpisodes() = runTest {
        server.episode("e1", "Breaking Bad", 1, 1, "Pilot")
        server.episode("e2", "Breaking Bad", 1, 2, "Cat's in the Bag...")
        library.scan(server)
        val show = library.shows().single()
        assertEquals(
            ArtworkLocator.SourceFile(MediaLocation(server.descriptor.id, "items/series-Breaking Bad/images/primary")),
            show.artwork[ArtworkKind.POSTER],
        )
        val episodes = library.showDetail(show.item.id)!!.seasons.single().episodes
        assertEquals(listOf("Pilot", "Cat's in the Bag..."), episodes.map { it.item.title })
        assertTrue(episodes.all { it.artwork.containsKey(ArtworkKind.THUMBNAIL) })
    }

    @Test
    fun newerServerWatchStateIsImported() = runTest {
        server.movie("a", "Heat", 1995, state = SourceUserState(played = false, positionMs = 1_200_000, playCount = 0, lastPlayedAtEpochMs = 1_000, favorite = true))
        server.movie("b", "Alien", 1979, state = SourceUserState(played = true, positionMs = 0, playCount = 2, lastPlayedAtEpochMs = null, favorite = false))
        library.scan(server)
        val heat = library.movies().single { it.item.title == "Heat" }
        assertEquals(1_200_000, heat.watchState?.positionMs)
        assertTrue(heat.favorite)
        assertEquals(true, library.movies().single { it.item.title == "Alien" }.watchState?.completed)

        // Watching locally afterwards is newer than the server's record and is kept on the next scan.
        clock += 1
        library.stopped(heat.item.id, 3_000_000, 6_000_000)
        library.scan(server)
        assertEquals(3_000_000, library.watchState(heat.item.id)?.positionMs)
    }

    @Test
    fun unreachableOrEmptyServerKeepsTheLibrary() = runTest {
        server.movie("a", "Heat", 1995)
        library.scan(server)
        server.reachable = false
        assertEquals(ScanReport.Status.UNAVAILABLE, library.scan(server).status)
        server.reachable = true
        server.entries.clear()
        assertEquals(ScanReport.Status.EMPTY_KEPT, library.scan(server).status)
        assertEquals(1, library.movies().size)
    }

    @Test
    fun serverStreamsAreNotProbed() = runTest {
        server.movie("a", "Heat", 1995)
        library.scan(server)
        var probes = 0
        library.probePending({ server }, MediaProber { probes++; null })
        assertEquals(0, probes)
    }

    @Test
    fun correctionsWorkOfflineForServerItems() = runTest {
        server.movie("a", "Heat", 1995, tmdb = "949")
        server.movie("b", "Untitled", 2004)
        library.scan(server)
        server.reachable = false
        library.refreshAvailability(server)

        library.identify(MediaLocation(server.descriptor.id, "items/b/sources/b"), IdentityOverride(ParsedKind.MOVIE, "Primer", 2004))
        assertEquals(listOf("Heat", "Primer"), library.movies().map { it.item.title })
        val heat = library.movies().first()
        assertEquals("eng", library.versions(heat.item.id).single().version.externalSubtitles.single().language)
        assertTrue(heat.artwork.containsKey(ArtworkKind.POSTER), "server artwork survives re-identification")
    }
}

private class SyncingServer : dev.reflux.core.source.WatchStateSyncSource {
    val calls = mutableListOf<String>()
    var failing = false
    override val descriptor = FakeServer().descriptor
    override suspend fun availability() = Availability.AVAILABLE
    override suspend fun playbackTarget(path: String) = PlaybackTarget("https://media.example/$path")
    override suspend fun reportProgress(path: String, positionMs: Long, paused: Boolean) = record("progress:$path:$positionMs")
    override suspend fun reportStopped(path: String, positionMs: Long) = record("stopped:$path:$positionMs")
    override suspend fun setPlayed(path: String, played: Boolean) = record("played:$path:$played")
    private fun record(call: String) {
        if (failing) error("offline")
        calls += call
    }
}

class WatchSyncTest {
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val server = FakeServer().apply { movie("a", "Heat", 1995) }
    private val syncing = SyncingServer()

    @Test
    fun playbackIsReportedToTheLibraryAndTheServer() = runTest(kotlinx.coroutines.test.UnconfinedTestDispatcher()) {
        library.addSource(server, "x")
        library.scan(server)
        val heat = library.movies().single()
        val version = library.versions(heat.item.id).single().version
        val reporter = SyncingWatchReporter(library, version, syncing, this)
        reporter.progress(heat.item.id, 120_000, 6_000_000)
        reporter.stopped(heat.item.id, 300_000, 6_000_000)
        assertEquals(listOf("progress:items/a/sources/a:120000", "stopped:items/a/sources/a:300000"), syncing.calls)
        assertEquals(300_000, library.watchState(heat.item.id)?.positionMs)

        syncing.failing = true
        reporter.progress(heat.item.id, 400_000, 6_000_000)
        assertEquals(400_000, library.watchState(heat.item.id)?.positionMs, "a failing server never blocks local state")
    }

    @Test
    fun markingWatchedReachesTheServer() = runTest {
        library.addSource(server, "x")
        library.scan(server)
        val heat = library.movies().single()
        assertEquals(0, library.setWatchedEverywhere(heat.item.id, true) { syncing })
        assertEquals(listOf("played:items/a/sources/a:true"), syncing.calls)
        assertEquals(true, library.watchState(heat.item.id)?.completed)
        syncing.failing = true
        assertEquals(1, library.setWatchedEverywhere(heat.item.id, false) { syncing })
        assertEquals(false, library.watchState(heat.item.id)?.completed)
    }
}
