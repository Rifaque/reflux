package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.model.Movie
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoStream
import dev.reflux.sources.local.LocalFolderSource
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackPlanTest {
    private val root: Path = Files.createTempDirectory("reflux-plan")
    private var clock = 1_790_000_000_000L
    private val library = Library(testDatabase()) { clock }
    private val source = LocalFolderSource(root)
    private val sources = { id: dev.reflux.core.model.SourceId -> source.takeIf { it.descriptor.id == id } }

    private val device = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 1920, 1080, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true),
        ),
        audioDecoders = AudioCodec.entries.toSet(),
        display = DisplayCapabilities(3840, 2160, setOf(DynamicRange.SDR, DynamicRange.HDR10)),
        containers = Container.entries.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    init {
        library.addSource(source, root.toString())
    }

    @AfterTest
    fun cleanUp() {
        root.toFile().deleteRecursively()
    }

    private fun file(relative: String) = root.resolve(relative).also { it.parent.createDirectories(); it.writeBytes(ByteArray(8)) }

    @Test
    fun plansBestVersionWithResumeAndSubtitles() = runTest {
        file("Heat (1995)/Heat.1995.1080p.x264.mkv")
        file("Heat (1995)/Heat.1995.2160p.HDR.x265.mkv")
        file("Heat (1995)/Heat.1995.2160p.HDR.x265.en.srt")
        library.scan(source)
        val heat = library.movies().single().item
        library.stopped(heat.id, 30 * 60_000L, 170 * 60_000L)

        val plan = assertNotNull(library.planPlayback(heat.id, device, sources))
        assertEquals(2160, plan.version.version.stream.video?.height)
        assertEquals("Heat (1995)", plan.request.title)
        assertEquals(30 * 60_000L, plan.request.startPositionMs)
        assertEquals("en", plan.request.subtitles.single().language)
        assertTrue(plan.request.target.uri.startsWith("file:"))

        val fromStart = assertNotNull(library.planPlayback(heat.id, device, sources, resume = false))
        assertNull(fromStart.request.startPositionMs)

        val other = library.versions(heat.id).single { it.version.id != plan.version.version.id }
        assertEquals(other.version.id, library.planPlayback(heat.id, device, sources, versionId = other.version.id)?.version?.version?.id)
    }

    @Test
    fun episodeTitles() = runTest {
        file("Breaking Bad/Season 1/Breaking.Bad.S01E03.And.the.Bags.in.the.River.mkv")
        library.scan(source)
        val episode = library.showDetail(library.shows().single().item.id)!!.seasons.single().episodes.single()
        assertEquals("Breaking Bad · S1 E3 · And the Bags in the River", library.planPlayback(episode.item.id, device, sources)?.request?.title)
    }

    @Test
    fun offlineSourceHasNoPlan() = runTest {
        file("Heat (1995).mkv")
        library.scan(source)
        val heat = library.movies().single().item
        assertNull(library.planPlayback(heat.id, device, sources = { null }))
    }

    @Test
    fun probingReplacesHintsOnceAndKeepsThemAcrossRescans() = runTest {
        file("Movies/Arrival.2016.DV.2160p.mkv")
        library.scan(source)
        val arrival = library.movies().single().item as Movie
        var calls = 0
        val prober = MediaProber {
            calls++
            StreamInfo(
                container = Container.MATROSKA,
                video = VideoStream(VideoCodec.HEVC, 3840, 2160, 10, DynamicRange.HDR10),
                audio = listOf(AudioStream(AudioCodec.EAC3, 6, "eng")),
                durationMs = 6_960_000,
            )
        }
        assertEquals(1, library.probePending(sources, prober))
        assertEquals(0, library.probePending(sources, prober))
        assertEquals(1, calls)

        val version = library.versions(arrival.id).single().version
        assertEquals(StreamInfoOrigin.PROBE, version.streamOrigin)
        assertEquals(DynamicRange.DOLBY_VISION, version.stream.video?.dynamicRange, "DV tag upgrades an HDR10 probe")
        assertEquals(AudioCodec.EAC3, version.stream.audio.single().codec)
        assertEquals(6_960_000, version.stream.durationMs)

        library.scan(source)
        assertEquals(StreamInfoOrigin.PROBE, library.versions(arrival.id).single().version.streamOrigin)
    }

    @Test
    fun failedProbesAreNotRetriedUntilTheFileChanges() = runTest {
        file("Broken (2001).mkv")
        library.scan(source)
        var calls = 0
        val failing = MediaProber { calls++; null }
        library.probePending(sources, failing)
        library.probePending(sources, failing)
        assertEquals(1, calls)

        clock += 1000
        root.resolve("Broken (2001).mkv").writeBytes(ByteArray(64))
        library.scan(source)
        library.probePending(sources, failing)
        assertEquals(2, calls)
    }

    @Test
    fun unavailableSourcesAreNotProbed() = runTest {
        file("Heat (1995).mkv")
        library.scan(source)
        root.toFile().deleteRecursively()
        library.refreshAvailability(source)
        assertEquals(Availability.UNAVAILABLE, library.source(source.descriptor.id)?.availability)
        var calls = 0
        library.probePending(sources, MediaProber { calls++; null })
        assertEquals(0, calls)
    }
}
