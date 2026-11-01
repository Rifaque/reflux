package dev.reflux.core.playback

import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.model.VersionId
import dev.reflux.core.source.SourceCapability
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlaybackAssessorTest {
    private val noDts = Devices.oldPhone

    private val dtsAndFrench = StreamInfo(audio = listOf(AudioStream(AudioCodec.DTS, 2, "en", default = true), AudioStream(AudioCodec.AC3, 2, "fr")))
    private fun version(stream: StreamInfo) = MediaVersion(VersionId("v"), MediaId("m"), MediaLocation(SourceId("s"), "a.mkv"), 1, 0, stream, StreamInfoOrigin.PROBE)
    private fun requirements(audio: AudioChoice = AudioChoice.Automatic, source: Set<SourceCapability> = emptySet()) =
        PlaybackRequirements(version(dtsAndFrench), TrackPreferences(listOf("en")), source, audio)

    @Test
    fun theTrackAskedAboutIsTheOneAssessedAndNotAnyOther() {
        assertEquals(emptyList(), PlaybackAssessor.assess(dtsAndFrench, noDts).issues, "without one, any track will do")
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), PlaybackAssessor.assess(dtsAndFrench, noDts, audioIndex = 0).issues)
        assertEquals(emptyList(), PlaybackAssessor.assess(dtsAndFrench, noDts, audioIndex = 1).issues)
    }

    @Test
    fun anExplicitTrackIsAssessedAsItselfAndNoOtherLanguageRescuesIt() {
        val assessed = PlaybackPlanner.assess(requirements(AudioChoice.Explicit(0)), noDts)
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), assessed.issues)
        assertEquals(emptyList(), PlaybackPlanner.assess(requirements(AudioChoice.Explicit(1)), noDts).issues)
    }

    @Test
    fun aTrackThatIsGoneIsNotPlayable() {
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), PlaybackPlanner.assess(requirements(AudioChoice.Explicit(5)), noDts).issues)
    }

    @Test
    fun automaticSelectionIsAssessedByThePlannersTrack() {
        val assessed = PlaybackPlanner.assess(requirements(), noDts)
        assertEquals(listOf(PlaybackIssue.AUDIO_OTHER_LANGUAGE), assessed.issues, "French is what would play, and that is a compromise")
        val own = PlaybackRequirements(version(StreamInfo(audio = listOf(AudioStream(AudioCodec.DTS, 2, "en"), AudioStream(AudioCodec.AC3, 2, "en")))), TrackPreferences(listOf("en")))
        assertEquals(emptyList(), PlaybackPlanner.assess(own, noDts).issues, "the other English track plays")
    }


    @Test
    fun audioThatMayStillPassThroughIsNotCalledUnplayable() {
        val dts = StreamInfo(audio = listOf(AudioStream(AudioCodec.DTS, 6)))
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), PlaybackAssessor.assess(dts, noDts).issues)
        val unsure = PlaybackAssessor.assess(dts, noDts.copy(audioPassthroughKnown = false))
        assertTrue(PlaybackIssue.NO_PLAYABLE_AUDIO !in unsure.issues, "unknown is not counted against it: ${unsure.issues}")
    }

    @Test
    fun audioPastEveryDecodersDeclaredLimitIsNotPlayable() {
        val aac = StreamInfo(audio = listOf(AudioStream(AudioCodec.AAC, 8)))
        val limited = noDts.copy(audioDecoderLimits = mapOf(AudioCodec.AAC to AudioDecoderLimit(maxChannels = 6)))
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), PlaybackAssessor.assess(aac, limited).issues)
    }

    private fun video(codec: VideoCodec, height: Int, range: DynamicRange = DynamicRange.SDR, bitDepth: Int? = null, dv: Int? = null) =
        StreamInfo(
            container = Container.MATROSKA,
            video = VideoStream(codec, width = height * 16 / 9, height = height, bitDepth = bitDepth, dynamicRange = range, dolbyVisionProfile = dv),
        )

    @Test
    fun unknownStreamsAreNotPenalized() {
        assertEquals(PlaybackVerdict.OPTIMAL, PlaybackAssessor.assess(StreamInfo(), Devices.oldPhone).verdict)
    }

    @Test
    fun hdrOnCapableTvIsOptimal() {
        val stream = video(VideoCodec.HEVC, 2160, DynamicRange.HDR10, 10)
        assertEquals(PlaybackVerdict.OPTIMAL, PlaybackAssessor.assess(stream, Devices.tv4kHdr).verdict)
    }

    @Test
    fun hdrOnSdrLaptopIsToneMappedAndSoftwareDecoded() {
        val stream = video(VideoCodec.HEVC, 2160, DynamicRange.HDR10, 10)
        val assessment = PlaybackAssessor.assess(stream, Devices.laptop1080)
        assertEquals(PlaybackVerdict.DEGRADED, assessment.verdict)
        assertEquals(listOf(PlaybackIssue.SOFTWARE_VIDEO_DECODE, PlaybackIssue.HDR_TONE_MAPPED), assessment.issues)
    }

    @Test
    fun unsupportedCodecAndResolution() {
        assertEquals(
            listOf(PlaybackIssue.VIDEO_CODEC_UNSUPPORTED),
            PlaybackAssessor.assess(video(VideoCodec.HEVC, 1080), Devices.oldPhone).issues,
        )
        assertEquals(
            listOf(PlaybackIssue.VIDEO_RESOLUTION_UNSUPPORTED),
            PlaybackAssessor.assess(video(VideoCodec.H264, 2160), Devices.oldPhone).issues,
        )
        assertEquals(
            listOf(PlaybackIssue.VIDEO_BIT_DEPTH_UNSUPPORTED),
            PlaybackAssessor.assess(video(VideoCodec.H264, 1080, bitDepth = 10), Devices.oldPhone).issues,
        )
    }

    @Test
    fun dolbyVisionProfiles() {
        val p5 = video(VideoCodec.HEVC, 2160, DynamicRange.DOLBY_VISION, 10, dv = 5)
        val p8 = video(VideoCodec.HEVC, 2160, DynamicRange.DOLBY_VISION, 10, dv = 8)
        val hdrOnlyTv = Devices.tv4kHdr.copy(
            display = Devices.tv4kHdr.display.copy(dynamicRanges = setOf(DynamicRange.SDR, DynamicRange.HDR10)),
        )
        assertEquals(PlaybackVerdict.OPTIMAL, PlaybackAssessor.assess(p5, Devices.tv4kHdr).verdict)
        assertEquals(listOf(PlaybackIssue.DOLBY_VISION_UNSUPPORTED), PlaybackAssessor.assess(p5, hdrOnlyTv).issues)
        assertEquals(listOf(PlaybackIssue.DOLBY_VISION_BASE_LAYER), PlaybackAssessor.assess(p8, hdrOnlyTv).issues)
        assertEquals(PlaybackVerdict.DEGRADED, PlaybackAssessor.assess(p5, Devices.laptop1080).verdict)
    }

    @Test
    fun hdr10PlusFallsBackToHdr10Silently() {
        val tv = Devices.tv4kHdr.copy(display = Devices.tv4kHdr.display.copy(dynamicRanges = setOf(DynamicRange.SDR, DynamicRange.HDR10)))
        assertEquals(emptyList(), PlaybackAssessor.assess(video(VideoCodec.HEVC, 2160, DynamicRange.HDR10_PLUS, 10), tv).issues)
    }

    @Test
    fun audio() {
        val truehd = StreamInfo(audio = listOf(AudioStream(AudioCodec.TRUEHD, channels = 8)))
        assertEquals(emptyList(), PlaybackAssessor.assess(truehd, Devices.tv4kHdr).issues)
        val laptop = PlaybackAssessor.assess(truehd, Devices.laptop1080)
        assertEquals(listOf(PlaybackIssue.AUDIO_DOWNMIXED), laptop.issues)
        assertEquals(PlaybackVerdict.OPTIMAL, laptop.verdict)
        assertEquals(listOf(PlaybackIssue.NO_PLAYABLE_AUDIO), PlaybackAssessor.assess(truehd, Devices.oldPhone).issues)
    }

    @Test
    fun unsupportedContainer() {
        val avi = StreamInfo(container = Container.AVI)
        assertEquals(PlaybackVerdict.UNSUPPORTED, PlaybackAssessor.assess(avi, Devices.oldPhone).verdict)
    }

    @Test
    fun aDecoderNotKnownToBeHardwareOrSoftwareIsNoCompromise() {
        val unsure = Devices.laptop1080.copy(
            videoDecoders = listOf(
                VideoDecoderSupport(VideoCodec.HEVC, 7680, 4320, 12, hardware = false),
                VideoDecoderSupport(VideoCodec.HEVC, 7680, 4320, 10, hardware = null),
            ),
        )
        assertEquals(emptyList(), PlaybackAssessor.assess(video(VideoCodec.HEVC, 1080, bitDepth = 10), unsure).issues, "unknown is not counted against it")
        assertEquals(
            listOf(PlaybackIssue.SOFTWARE_VIDEO_DECODE),
            PlaybackAssessor.assess(video(VideoCodec.HEVC, 1080, bitDepth = 12), unsure).issues,
            "where only software is known to fit, that is what it is",
        )
        assertEquals(null, unsure.decoderFor(VideoCodec.HEVC)?.hardware, "an unknown decoder is offered before a known software one")
        val known = unsure.copy(videoDecoders = unsure.videoDecoders + VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true))
        assertEquals(true, known.decoderFor(VideoCodec.HEVC)?.hardware, "and a known hardware one before both")
    }
}

class NominalHeightTest {
    @Test
    fun scopeFramesKeepTheirResolutionClass() {
        kotlin.test.assertEquals(2160, VideoStream(width = 3840, height = 1600).nominalHeight)
        kotlin.test.assertEquals(1080, VideoStream(width = 1920, height = 800).nominalHeight)
        kotlin.test.assertEquals(1080, VideoStream(width = 1440, height = 1080).nominalHeight)
        kotlin.test.assertEquals(720, VideoStream(height = 720).nominalHeight)
    }
}
