package dev.reflux.library

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.Movie
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioDirective
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlanPart
import dev.reflux.core.playback.PlanReason
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleDirective
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleMode
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.TrackDirective
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.MediaSource
import dev.reflux.core.source.NegotiatedDelivery
import dev.reflux.core.source.NegotiatingSource
import dev.reflux.core.source.PlaybackTarget
import dev.reflux.core.source.SourceCapability
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

    private val stereo = StreamInfo(
        Container.MATROSKA, VideoStream(VideoCodec.HEVC, 1920, 1080, 10),
        listOf(AudioStream(AudioCodec.DTS, 2, "en", default = true), AudioStream(AudioCodec.AAC, 2, "fr")),
        listOf(SubtitleStream(SubtitleFormat.SRT, "fr"), SubtitleStream(SubtitleFormat.SRT, "es")),
    )
    private val noDts = device.copy(audioDecoders = setOf(AudioCodec.AAC))
    private val english = TrackPreferences(listOf("en"), SubtitleMode.OFF)

    private suspend fun probed(): Movie {
        file("Heat (1995)/Heat.1995.1080p.x265.mkv")
        library.scan(source)
        val heat = library.movies().single().item as Movie
        library.recordProbe(library.versions(heat.id).single().version.id, stereo)
        return heat
    }

    @Test
    fun aPlanComesWithItsRequestFromTheSourcesRealCapabilities() = runTest {
        val heat = probed()
        val plan = assertNotNull(library.planPlayback(heat.id, device, sources, preferences = english))
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.planned?.delivery)
        assertEquals(source.descriptor.capabilities, plan.requirements?.source)
        assertEquals(TrackDirective(AudioDirective.Track(0), SubtitleDirective.Off), plan.request.tracks)
    }

    @Test
    fun aFileSourcesAutomaticFallbackIsThePlansAndIsNamed() = runTest {
        val heat = probed()
        val plan = assertNotNull(library.planPlayback(heat.id, noDts, sources, preferences = english))
        assertEquals(AudioDirective.Track(1), plan.request.tracks?.audio)
        assertTrue(PlanReason.AUDIO_OTHER_LANGUAGE in plan.planned!!.reasons)
    }

    @Test
    fun aViewersTrackOnAFileSourceIsNotSwapped() = runTest {
        val heat = probed()
        val plan = assertNotNull(library.planPlayback(heat.id, noDts, sources, preferences = english, audio = AudioChoice.Explicit(0)))
        assertEquals(PlanOutcome.PLAYS_WITHOUT_AUDIO, plan.planned?.outcome)
        assertEquals(AudioDirective.None, plan.request.tracks?.audio)
    }

    @Test
    fun aChosenSubtitleBecomesTheEnginesAndASidecarIsFoundByPlace() = runTest {
        val heat = probed()
        val embedded = assertNotNull(library.planPlayback(heat.id, device.copy(subtitles = drawable), sources, preferences = english, subtitle = SubtitleChoice.Explicit(1)))
        assertEquals(SubtitleDirective.Embedded(1), embedded.request.tracks?.subtitle)
    }

    private val drawable = SubtitleCapabilities(mapOf(SubtitleFormat.SRT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED)))

    @Test
    fun whatAFileSourceCannotDoSaysSoAndAskesNobody() = runTest {
        val heat = probed()
        assertNull(library.planPlayback(heat.id, device.copy(videoDecoders = emptyList()), sources, preferences = english), "no decoder and no server")
    }

    /** A server of the test's own: it converts, or refuses to, and says what it was asked. */
    private inner class Negotiating(private val agrees: Boolean) : MediaSource by source, NegotiatingSource {
        val plans = mutableListOf<PlannedPlayback>()
        override val descriptor = source.descriptor.copy(capabilities = source.descriptor.capabilities + SourceCapability.SERVER_AUDIO_CONVERSION + SourceCapability.SERVER_TRANSCODE)

        override suspend fun negotiate(version: MediaVersion, plan: PlannedPlayback, device: DeviceCapabilities): NegotiatedDelivery? {
            plans += plan
            return NegotiatedDelivery(PlaybackTarget("http://server/master.m3u8")).takeIf { agrees || plan.delivery == DeliveryMode.DIRECT_PLAY }
        }

        override suspend fun release(path: String) = Unit
    }

    @Test
    fun aServerDeliversWhatThePlanNeeds() = runTest {
        val heat = probed()
        val server = Negotiating(agrees = true)
        val plan = assertNotNull(library.planPlayback(heat.id, noDts, { server }, preferences = english))
        assertEquals(AudioPlan.CONVERT, plan.planned?.audio)
        assertEquals(0, plan.planned?.audioIndex, "the viewer's language, converted, over French that plays")
        assertEquals("http://server/master.m3u8", plan.request.target.uri)
        assertEquals(AudioDirective.EngineChoice, plan.request.tracks?.audio, "a converted stream has its one track")
    }

    @Test
    fun aServerThatWillNotConvertLeavesWhatThisClientCanDoAndSaysSo() = runTest {
        val heat = probed()
        val server = Negotiating(agrees = false)
        val plan = assertNotNull(library.planPlayback(heat.id, noDts, { server }, preferences = english))
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.planned?.delivery)
        assertEquals(1, plan.planned?.audioIndex)
        assertTrue(PlanReason.NEGOTIATION_FAILED in plan.planned!!.reasons)
    }

    @Test
    fun aVersionOnlyAServerCanPlayIsPlayableThroughIt() = runTest {
        val heat = probed()
        val server = Negotiating(agrees = true)
        val noVideo = noDts.copy(videoDecoders = emptyList())
        val plan = assertNotNull(library.planPlayback(heat.id, noVideo, { server }, preferences = english))
        assertEquals(DeliveryMode.VIDEO_TRANSCODE, plan.planned?.delivery)
        assertNull(library.planPlayback(heat.id, noVideo, sources, preferences = english), "and the same work on files alone is not")
    }

    @Test
    fun whatFailedStaysFailedInThePlan() = runTest {
        val heat = probed()
        val server = Negotiating(agrees = true)
        val plan = assertNotNull(library.planPlayback(heat.id, device, { server }, preferences = english, originalFailed = setOf(PlanPart.VIDEO)))
        assertEquals(VideoPlan.TRANSCODE, plan.planned?.video)
        assertTrue(PlanReason.ORIGINAL_FAILED in plan.planned!!.reasons)
    }

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
