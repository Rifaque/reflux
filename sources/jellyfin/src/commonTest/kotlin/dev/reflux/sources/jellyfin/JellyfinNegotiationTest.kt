package dev.reflux.sources.jellyfin

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.net.HttpFetcher
import dev.reflux.core.net.HttpRequest
import dev.reflux.core.net.HttpResult
import dev.reflux.core.playback.AudioChoice
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlanMismatch
import dev.reflux.core.playback.PlanOutcome
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.PlaybackComparison
import dev.reflux.core.playback.PlaybackPlanner
import dev.reflux.core.playback.PlaybackRequirements
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleChoice
import dev.reflux.core.playback.SubtitleFidelity
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitlePlan
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import dev.reflux.core.playback.VideoPlan
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Planning, then negotiation, then what the server says it did, against answers shaped like a real server's. */
class JellyfinNegotiationTest {
    private val path = "items/m1/sources/s1"
    private val requests = mutableListOf<HttpRequest>()
    private var answer = ""
    private var sessions = "[]"

    private val http = HttpFetcher { request ->
        requests += request
        when {
            "PlaybackInfo" in request.url -> HttpResult(200, answer.encodeToByteArray())
            request.url.substringBefore('?').endsWith("/Sessions") -> HttpResult(200, sessions.encodeToByteArray())
            else -> HttpResult(204, ByteArray(0))
        }
    }
    private val source = JellyfinSource(JellyfinCredentials("https://jf.example.com", "srv1", "u1", "tok", "dev1"), http)

    private val hevc = VideoStream(VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120)
    private val eac3 = AudioStream(AudioCodec.EAC3, 6, "en", default = true, sampleRate = 48_000, sourceIndex = 1)
    private val pgs = SubtitleStream(SubtitleFormat.PGS, "en", sourceIndex = 2)

    private fun version(stream: StreamInfo) =
        MediaVersion(VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), path), 1, 0, stream, StreamInfoOrigin.SOURCE)

    private val client = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, true, frameAndDepthOnly = true),
            VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, true, frameAndDepthOnly = true),
        ),
        audioDecoders = setOf(AudioCodec.AAC, AudioCodec.EAC3),
        maxOutputChannels = 6,
        display = DisplayCapabilities(3840, 2160),
        containers = setOf(Container.MATROSKA, Container.MP4),
        subtitleFormats = emptySet(),
        subtitles = SubtitleCapabilities(mapOf(SubtitleFormat.SRT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL))),
    )

    private fun plan(stream: StreamInfo, device: DeviceCapabilities = client, audio: AudioChoice = AudioChoice.Automatic, subtitle: SubtitleChoice = SubtitleChoice.Off) =
        PlaybackPlanner.plan(PlaybackRequirements(version(stream), TrackPreferences(listOf("en")), source.descriptor.capabilities, audio, subtitle), device)

    private fun url(reasons: String, subtitle: String = "") =
        "/videos/m1/master.m3u8?VideoCodec=hevc,h264&AudioCodec=aac&ApiKey=secret-key&SubtitleMethod=Encode$subtitle&TranscodeReasons=$reasons"

    private fun server(url: String? = null, directPlay: Boolean = url == null) = """
        {"PlaySessionId":"ps1","MediaSources":[{"Id":"s1","SupportsDirectPlay":$directPlay,"SupportsDirectStream":false,"SupportsTranscoding":true,
         ${url?.let { "\"TranscodingUrl\":\"$it\"," }.orEmpty()}"MediaStreams":[{"Type":"Subtitle","Index":2,"Codec":"PGSSUB","DeliveryMethod":"External"}]}]}
    """.trimIndent()

    private fun converting(video: Boolean, audio: Boolean, reasons: String) =
        """[{"DeviceId":"dev1","NowPlayingItem":{"Id":"m1"},"TranscodingInfo":{"IsVideoDirect":$video,"IsAudioDirect":$audio,"VideoCodec":"hevc","AudioCodec":"aac","Container":"ts","TranscodeReasons":["$reasons"]}}]"""

    private fun asked() = requests.last { "PlaybackInfo" in it.url }.body!!.decodeToString()

    private suspend fun compare(plan: PlannedPlayback): PlaybackComparison {
        source.reportProgress(path, 0, paused = false)
        return PlaybackComparison.of(plan, assertNotNull(source.playbackEvidence(path)), PlayerState())
    }

    @Test
    fun theOriginalIsPlayedAndConfirmed() = runTest {
        answer = server()
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(eac3))
        val plan = plan(stream)
        assertEquals(DeliveryMode.DIRECT_PLAY, plan.delivery)
        val delivery = assertNotNull(source.negotiate(version(stream), plan, client))
        assertEquals("https://jf.example.com/Videos/m1/stream?static=true&mediaSourceId=s1", delivery.target.uri)
        assertTrue("\"EnableTranscoding\":false" in asked() && "\"EnableDirectPlay\":true" in asked(), asked())
        assertTrue("\"TranscodingProfiles\":[]" in asked(), "a plan for the original offers the server nothing to convert to")
        sessions = """[{"DeviceId":"dev1","NowPlayingItem":{"Id":"m1"},"PlayState":{"PlayMethod":"DirectPlay"}}]"""
        val compared = compare(plan)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(StreamFate.PRESERVED, compared.evidence?.video)
    }

    @Test
    fun aServerThatConvertsAPlannedOriginalIsAMismatch() = runTest {
        answer = server(directPlay = false)
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(eac3))
        val plan = plan(stream)
        source.negotiate(version(stream), plan, client)
        sessions = converting(video = false, audio = true, "VideoCodecNotSupported")
        val compared = compare(plan)
        assertEquals(setOf(PlanMismatch.DELIVERY, PlanMismatch.VIDEO), compared.mismatches)
    }

    @Test
    fun aRemuxKeepsBothStreams() = runTest {
        answer = server(url("ContainerNotSupported"))
        val stream = StreamInfo(Container.AVI, hevc, listOf(eac3))
        val plan = plan(stream)
        assertEquals(DeliveryMode.REMUX, plan.delivery)
        val delivery = assertNotNull(source.negotiate(version(stream), plan, client))
        assertTrue("master.m3u8" in delivery.target.uri)
        assertTrue("\"AllowVideoStreamCopy\":true" in asked() && "\"AllowAudioStreamCopy\":true" in asked(), asked())
        sessions = converting(video = true, audio = true, "ContainerNotSupported")
        val compared = compare(plan)
        assertEquals(emptySet(), compared.mismatches)
        assertTrue("\"PlayMethod\":\"DirectStream\"" in requests.last { it.url.endsWith("Sessions/Playing") }.body!!.decodeToString())
    }

    @Test
    fun audioThePlanKeepsIsNotLimitedToTheOutputsChannels() = runTest {
        // Seen live: a server told of a stereo output converted the 5.1 a remux was to keep.
        answer = server(url("ContainerNotSupported"))
        val stereo = client.copy(maxOutputChannels = 2)
        val stream = StreamInfo(Container.AVI, hevc, listOf(eac3))
        source.negotiate(version(stream), plan(stream, stereo), stereo)
        assertTrue(""""MaxAudioChannels":"6"""" in asked(), asked())
    }

    @Test
    fun audioIsConvertedAndEvidenceConfirmsTheVideoWasKept() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val dts = AudioStream(AudioCodec.DTS, 6, "en", default = true, sourceIndex = 1)
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(dts))
        val plan = plan(stream)
        assertEquals(AudioPlan.CONVERT, plan.audio)
        assertEquals(VideoPlan.ORIGINAL_HARDWARE_DECODE, plan.video)
        assertNotNull(source.negotiate(version(stream), plan, client))
        assertTrue("\"AudioStreamIndex\":1" in asked() && "\"AllowVideoStreamCopy\":true" in asked() && "\"AllowAudioStreamCopy\":false" in asked(), asked())
        assertTrue(""""VideoCodec":"hevc,h264"""" in asked(), "the original first, so that it can be copied: ${asked()}")
        sessions = converting(video = true, audio = false, "AudioCodecNotSupported")
        val compared = compare(plan)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(StreamFate.PRESERVED, compared.evidence?.video)
        assertEquals(StreamFate.CONVERTED, compared.evidence?.audio)
    }

    @Test
    fun aVideoTranscodeGoesToH264AndIsConfirmed() = runTest {
        answer = server(url("VideoCodecNotSupported"))
        val stream = StreamInfo(Container.MATROSKA, hevc.copy(codec = VideoCodec.AV1), listOf(eac3), bitrateBps = 6_000_000)
        val plan = plan(stream)
        assertEquals(VideoPlan.TRANSCODE, plan.video)
        assertNotNull(source.negotiate(version(stream), plan, client))
        assertTrue(""""VideoCodec":"h264,hevc"""" in asked(), asked())
        assertTrue("\"AllowVideoStreamCopy\":false" in asked() && "\"AllowAudioStreamCopy\":true" in asked(), asked())
        assertTrue("\"MaxStreamingBitrate\":12000000" in asked(), "a transcode is given a sensible size: ${asked()}")
        sessions = converting(video = false, audio = true, "VideoCodecNotSupported")
        val compared = compare(plan)
        assertEquals(emptySet(), compared.mismatches)
        assertEquals(StreamFate.CONVERTED, compared.evidence?.video)
    }

    @Test
    fun pgsTravelsBesideTheStreamWithNoSubtitleAskedOfTheServer() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val drawing = client.copy(subtitles = SubtitleCapabilities(client.subtitles.renderers + (SubtitleFormat.PGS to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL))))
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)), listOf(pgs))
        val plan = plan(stream, drawing, subtitle = SubtitleChoice.Explicit(0))
        assertEquals(SubtitlePlan.EXTERNAL, plan.subtitles)
        val delivery = assertNotNull(source.negotiate(version(stream), plan, drawing))
        assertTrue("\"SubtitleStreamIndex\":-1" in asked(), asked())
        assertEquals("https://jf.example.com/Videos/m1/s1/Subtitles/2/0/Stream.pgssub", delivery.subtitle?.uri)
        assertTrue("Token=\"tok\"" in delivery.subtitle!!.headers.getValue("Authorization"))
        sessions = converting(video = true, audio = false, "AudioCodecNotSupported")
        assertEquals(emptySet(), compare(plan).mismatches)
    }

    @Test
    fun aBurnInIsAskedForOnlyWhenPlanned() = runTest {
        answer = server(url("SubtitleCodecNotSupported", "&SubtitleStreamIndex=2"))
        val undrawable = client.copy(subtitles = SubtitleCapabilities(client.subtitles.renderers + (SubtitleFormat.PGS to SubtitleRenderer(Support.UNSUPPORTED, Support.UNSUPPORTED))))
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(eac3), listOf(pgs))
        val plan = plan(stream, undrawable, subtitle = SubtitleChoice.Explicit(0))
        assertEquals(SubtitlePlan.BURN_IN, plan.subtitles)
        val delivery = assertNotNull(source.negotiate(version(stream), plan, undrawable))
        assertNull(delivery.subtitle)
        assertTrue("\"SubtitleStreamIndex\":2" in asked(), asked())
        sessions = converting(video = false, audio = true, "SubtitleCodecNotSupported")
        assertEquals(emptySet(), compare(plan).mismatches)
    }

    @Test
    fun anExplicitTrackIsTheOneAskedFor() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val dts = AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)
        val french = AudioStream(AudioCodec.EAC3, 6, "fr", sourceIndex = 2)
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(dts, french))
        val plan = plan(stream, audio = AudioChoice.Explicit(0))
        assertEquals(0, plan.audioIndex)
        assertEquals(PlanOutcome.PLAYS, plan.outcome)
        source.negotiate(version(stream), plan, client)
        assertTrue("\"AudioStreamIndex\":1" in asked(), "not the French that would have played: ${asked()}")
    }

    @Test
    fun theProfileClaimsWhatIsEstablishedAndNoMore() = runTest {
        answer = server()
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(eac3))
        source.negotiate(version(stream), plan(stream), client)
        val profile = asked()
        assertTrue(""""Container":"mkv,mp4,m4v,mov"""" in profile || """"Container":"mp4,m4v,mov,mkv"""" in profile, profile)
        assertTrue(""""AudioCodec":"aac,eac3"""" in profile, profile)
        assertFalse("pgssub" in profile, "PGS is not claimed for being possible: $profile")
        assertTrue(""""Format":"subrip","Method":"Embed"""" in profile && """"Format":"subrip","Method":"External"""" in profile, profile)
        assertTrue(""""Property":"VideoBitDepth","Value":"8"""" in profile, "H.264 is limited to what decodes it: $profile")
        assertFalse("VideoLevel" in profile || "VideoFramerate" in profile, "those are tried past, so a server is not told them: $profile")
    }

    @Test
    fun aConversionTheServerWillNotOfferIsNotPretended() = runTest {
        answer = server(directPlay = false)
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)))
        assertNull(source.negotiate(version(stream), plan(stream), client))
    }

    @Test
    fun anEncodeStopsWithThePlayback() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)))
        source.negotiate(version(stream), plan(stream), client)
        source.reportProgress(path, 1_000, paused = false)
        source.reportStopped(path, 2_000)
        assertTrue(requests.any { it.method == "DELETE" && "Videos/ActiveEncodings" in it.url && "playSessionId=ps1" in it.url })
        val deletes = requests.count { it.method == "DELETE" }
        source.release(path)
        assertEquals(deletes, requests.count { it.method == "DELETE" }, "once")
    }

    @Test
    fun anEncodeNeverReportedIsReleasedAnyway() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)))
        source.negotiate(version(stream), plan(stream), client)
        source.release(path)
        assertTrue(requests.any { it.method == "DELETE" })
    }

    @Test
    fun aPositionUpdateDoesNotEndAStreamBeingOpened() = runTest {
        answer = server(url("AudioCodecNotSupported"))
        val stream = StreamInfo(Container.MATROSKA, hevc, listOf(AudioStream(AudioCodec.DTS, 6, "en", sourceIndex = 1)))
        source.negotiate(version(stream), plan(stream), client)
        source.updatePosition(path, 1_000)
        assertFalse(requests.any { it.method == "DELETE" })
    }
}
