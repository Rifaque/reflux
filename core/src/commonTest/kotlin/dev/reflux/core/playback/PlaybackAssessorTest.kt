package dev.reflux.core.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackAssessorTest {
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
