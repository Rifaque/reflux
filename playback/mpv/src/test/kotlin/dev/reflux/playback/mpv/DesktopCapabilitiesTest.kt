package dev.reflux.playback.mpv

import dev.reflux.core.model.Availability
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaLocation
import dev.reflux.core.model.MediaVersion
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.StableIds
import dev.reflux.core.model.StreamInfoOrigin
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Capabilities
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackAssessor
import dev.reflux.core.playback.PlaybackIssue
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.SourceLocality
import dev.reflux.core.versions.SelectionCriterion
import dev.reflux.core.versions.VersionCandidate
import dev.reflux.core.versions.VersionSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a desktop is said to decode and show, from what was observed, and what Best Version makes of it. No
 * graphics card or monitor is involved: evidence and displays are written down here.
 */
class DesktopCapabilitiesTest {
    private val fullHdHevc = VideoStream(
        VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120,
    )
    private val fullHdDisplay = DisplayCapabilities(1920, 1080)

    @Test
    fun softwareDecodingIsSupportedAndHardwareIsNotUntilItHasBeenSeen() {
        val unknown = Capabilities.video(fullHdHevc, DesktopCapabilities.of(fullHdDisplay))
        assertEquals(Support.SUPPORTED, unknown.software, "the engine has no limit but the frame")
        assertEquals(Support.UNKNOWN, unknown.hardware, "a GPU being there is not hardware decoding")
    }

    @Test
    fun hardwareDecodingFollowsTheEvidence() {
        val key = DecodeKey(VideoCodec.HEVC, 10)
        val seen = DesktopCapabilities.of(fullHdDisplay, DecodeEvidence(null, mapOf(key to HardwareDecode.Yes(3840, 2160))))
        assertEquals(Support.SUPPORTED, Capabilities.video(fullHdHevc, seen).hardware)
        val refused = DesktopCapabilities.of(fullHdDisplay, DecodeEvidence(null, mapOf(key to HardwareDecode.No)))
        assertEquals(Support.UNSUPPORTED, Capabilities.video(fullHdHevc, refused).hardware)
        val small = DesktopCapabilities.of(fullHdDisplay, DecodeEvidence(null, mapOf(key to HardwareDecode.Yes(1920, 1080))))
        val uhd = Capabilities.video(fullHdHevc.copy(width = 3840, height = 2160), small)
        assertEquals(Support.UNSUPPORTED, uhd.hardware, "seen this large and no larger")
    }

    @Test
    fun audioDecodersAreWhatTheEngineWasBuiltWith() {
        val list = """[{"codec":"aac","driver":"aac"},{"codec":"eac3","driver":"eac3"},{"codec":"dts","driver":"dca"},{"codec":"h264","driver":"h264"},{"codec":"subrip","driver":"srt"}]"""
        val audio = DecodeEvidence.audioCodecs(list)
        assertEquals(setOf(AudioCodec.AAC, AudioCodec.EAC3, AudioCodec.DTS, AudioCodec.DTS_HD_MA), audio)
        val device = DesktopCapabilities.of(fullHdDisplay, DecodeEvidence(null, emptyMap(), audio))
        assertEquals(audio, device.audioDecoders, "an engine built without TrueHD does not claim it")
        assertEquals(Support.UNSUPPORTED, Capabilities.audio(AudioStream(AudioCodec.TRUEHD), device).decode)
        assertEquals(null, DecodeEvidence.audioCodecs(null))
        assertEquals(AudioCodec.entries.size - 1, DesktopCapabilities.of(fullHdDisplay).audioDecoders.size, "not asked: as before")
    }

    @Test
    fun passthroughIsNotDoneRatherThanNotKnown() {
        val device = DesktopCapabilities.of(fullHdDisplay)
        val eac3 = AudioStream(AudioCodec.EAC3, channels = 6, atmos = true)
        val answer = Capabilities.audio(eac3, device)
        assertEquals(Support.UNSUPPORTED, answer.passthrough, "Reflux does not ask the engine for it")
        assertEquals(Support.UNSUPPORTED, answer.atmos)
    }

    @Test
    fun theAudioOutputIsLearnedFromPlaybacks() {
        val fiveOne = AudioStream(AudioCodec.EAC3, channels = 6)
        fun channels(output: ObservedAudioOutput?) = Capabilities.audio(fiveOne, DesktopCapabilities.of(fullHdDisplay, audioOutput = output)).channels
        assertEquals(Support.UNKNOWN, channels(null), "nothing has played")
        assertEquals(2, DesktopCapabilities.of(fullHdDisplay).maxOutputChannels, "and Best Version keeps its stereo floor")

        val stereoFile = ObservedAudioOutput.first(source = 2, output = 2)
        assertEquals(Support.UNKNOWN, channels(stereoFile), "stereo in, stereo out says nothing of more")
        val downmixed = stereoFile.with(source = 6, output = 2)
        assertEquals(ObservedAudioOutput(2, exact = true), downmixed)
        assertEquals(Support.UNSUPPORTED, channels(downmixed), "six in and two out is the limit")
        val surround = stereoFile.with(source = 6, output = 6)
        assertEquals(Support.SUPPORTED, channels(surround))
        assertEquals(6, DesktopCapabilities.of(fullHdDisplay, audioOutput = surround).maxOutputChannels)
        assertEquals(surround, surround.with(source = 2, output = 2), "a stereo file changes nothing")
    }

    @Test
    fun subtitleRenderersAreTheEnginesAndFilesBesideAVideoAreNotClaimedForBitmaps() {
        val device = DesktopCapabilities.of(fullHdDisplay)
        fun render(format: dev.reflux.core.playback.SubtitleFormat, external: Boolean) = Capabilities.subtitle(format, external, device).render
        assertEquals(Support.SUPPORTED, render(SubtitleFormat.ASS, true))
        assertEquals(Support.SUPPORTED, render(SubtitleFormat.PGS, false))
        assertEquals(Support.UNKNOWN, render(SubtitleFormat.PGS, true))
        assertEquals(Support.UNKNOWN, render(SubtitleFormat.TTML, false))
    }

    private val fullHd = DisplayCapabilities(1920, 1080)
    private val uhd = DisplayCapabilities(3840, 2160)

    private fun yes(width: Int = 3840, height: Int = 2160) = HardwareDecode.Yes(width, height)

    private fun evidence(vararg known: Pair<DecodeKey, HardwareDecode>, software: Set<VideoCodec>? = VideoCodec.entries.toSet() - VideoCodec.UNKNOWN) =
        DecodeEvidence(software, known.toMap())

    private fun key(codec: VideoCodec, depth: Int = 8) = DecodeKey(codec, depth)

    private fun copy(name: String, codec: VideoCodec, height: Int = 1080, depth: Int = 8, range: DynamicRange = DynamicRange.SDR, size: Long = 1_000): VersionCandidate {
        val location = MediaLocation(SourceId("disk"), "$name.mkv")
        val version = MediaVersion(
            id = StableIds.versionId(location),
            itemId = MediaId("film"),
            location = location,
            sizeBytes = size,
            modifiedAtEpochMs = 0,
            stream = StreamInfo(Container.MATROSKA, VideoStream(codec, height * 16 / 9, height, depth, range), listOf(AudioStream(AudioCodec.AAC, 2))),
            streamOrigin = StreamInfoOrigin.PROBE,
        )
        return VersionCandidate(version, Availability.AVAILABLE, SourceLocality.DEVICE)
    }

    private fun best(vararg copies: VersionCandidate, decode: DecodeEvidence, display: DisplayCapabilities = fullHd, hdr: Boolean = false, hdrOutput: Boolean = false) =
        VersionSelector.select(copies.toList(), DesktopCapabilities.of(display, decode, displayShowsHdr = hdr, hdrOutput = hdrOutput))

    private val VersionCandidate.name get() = version.location.path.removeSuffix(".mkv")

    // What the decoders are --------------------------------------------------------------------------------

    @Test
    fun nothingObservedIsUnknownNotHardware() {
        val decoders = DecodeEvidence.UNKNOWN.decoders()
        assertTrue(decoders.none { it.hardware == true }, "no codec is hardware because it is on a list")
        assertTrue(decoders.filter { it.codec == VideoCodec.HEVC }.any { it.hardware == null })
        assertTrue(decoders.any { it.codec == VideoCodec.HEVC && it.hardware == false }, "software decoding is still there: everything stays playable")
    }

    @Test
    fun hardwareSeenIsHardwareUpToTheFrameSeen() {
        val decoders = evidence(key(VideoCodec.HEVC, 10) to yes(), key(VideoCodec.HEVC) to yes()).decoders().filter { it.codec == VideoCodec.HEVC && it.hardware == true }
        assertEquals(setOf(8, 10), decoders.map { it.maxBitDepth }.toSet())
        assertTrue(decoders.all { it.maxWidth == 3840 && it.maxHeight == 2160 })
    }

    @Test
    fun aDepthWithoutHardwareHasNoDeeperOne() {
        val hevc = evidence(key(VideoCodec.HEVC) to yes(), key(VideoCodec.HEVC, 10) to HardwareDecode.No).decoders().filter { it.codec == VideoCodec.HEVC }
        assertTrue(hevc.none { it.maxBitDepth >= 10 && it.hardware != false }, "no 10- or 12-bit decoder that might be hardware: $hevc")
    }

    @Test
    fun aCodecTheEngineCannotDecodeInSoftwareHasNoSoftwareDecoder() {
        val decoders = evidence(software = setOf(VideoCodec.H264, VideoCodec.HEVC)).decoders()
        assertTrue(decoders.none { it.codec == VideoCodec.AV1 && it.hardware == false })
    }

    // Best Version, made truthful ----------------------------------------------------------------------------

    @Test
    fun hardwareForOneCodecAndNotTheOtherDecides() {
        val decode = evidence(key(VideoCodec.H264) to yes(), key(VideoCodec.HEVC) to HardwareDecode.No)
        val selection = best(copy("hevc", VideoCodec.HEVC, size = 2_000), copy("avc", VideoCodec.H264, size = 1_000), decode = decode)
        assertEquals("avc", selection.best?.candidate?.name)
        assertEquals(SelectionCriterion.FEWER_COMPROMISES, selection.decidedBy)
        assertEquals(listOf(PlaybackIssue.SOFTWARE_VIDEO_DECODE), selection.ranked.last().assessment.issues)
    }

    @Test
    fun softwareOnlyLeavesTheDecisionToQuality() {
        val decode = evidence(key(VideoCodec.H264) to HardwareDecode.No, key(VideoCodec.HEVC) to HardwareDecode.No)
        val selection = best(copy("avc-hd", VideoCodec.H264, 1080), copy("hevc-uhd", VideoCodec.HEVC, 2160), decode = decode, display = uhd)
        assertEquals("hevc-uhd", selection.best?.candidate?.name, "both are compromises alike; the better picture wins")
        assertEquals(SelectionCriterion.RESOLUTION, selection.decidedBy)
    }

    @Test
    fun unknownDecodingPrefersNoCodecForBeingOnAList() {
        val selection = best(copy("avc", VideoCodec.H264, size = 1_000), copy("vp9", VideoCodec.VP9, size = 2_000), decode = DecodeEvidence.UNKNOWN)
        assertEquals("vp9", selection.best?.candidate?.name, "the larger copy, as with any two equal ones")
        assertEquals(SelectionCriterion.BITRATE, selection.decidedBy)
        assertTrue(selection.ranked.all { it.assessment.issues.isEmpty() })
    }

    @Test
    fun aHardwareCopyWinsWhenAllElseIsEqual() {
        val decode = evidence(key(VideoCodec.AV1) to yes(), key(VideoCodec.VP9) to HardwareDecode.No)
        val selection = best(copy("vp9", VideoCodec.VP9, size = 5_000), copy("av1", VideoCodec.AV1, size = 1_000), decode = decode)
        assertEquals("av1", selection.best?.candidate?.name)
        assertEquals(SelectionCriterion.FEWER_COMPROMISES, selection.decidedBy)
    }

    @Test
    fun knownHardwareDoesNotOutrankUnknown() {
        // Unknown is no compromise, so a copy is not preferred merely because its codec's answer came in first.
        val decode = evidence(key(VideoCodec.H264) to yes())
        val selection = best(copy("avc", VideoCodec.H264, size = 1_000), copy("hevc", VideoCodec.HEVC, size = 2_000), decode = decode)
        assertEquals("hevc", selection.best?.candidate?.name)
        assertEquals(SelectionCriterion.BITRATE, selection.decidedBy)
    }

    @Test
    fun aFrameLargerThanTheHardwareWasSeenToTakeIsSoftware() {
        val decode = evidence(key(VideoCodec.HEVC, 10) to yes(), key(VideoCodec.HEVC) to yes())
        val issues = PlaybackAssessor.assess(copy("8k", VideoCodec.HEVC, 4320, 10).version.stream, DesktopCapabilities.of(uhd, decode)).issues
        assertEquals(listOf(PlaybackIssue.SOFTWARE_VIDEO_DECODE), issues)
    }

    @Test
    fun aCodecWithoutHardwareStillPlaysInSoftware() {
        val decode = evidence(key(VideoCodec.AV1) to HardwareDecode.No, key(VideoCodec.AV1, 10) to HardwareDecode.No)
        val selection = best(copy("av1", VideoCodec.AV1, depth = 10), decode = decode)
        assertTrue(selection.best != null, "playable")
        assertEquals(listOf(PlaybackIssue.SOFTWARE_VIDEO_DECODE), selection.best!!.assessment.issues)
    }

    @Test
    fun aCodecWithNoDecoderAtAllIsNotOffered() {
        val decode = evidence(key(VideoCodec.AV1) to HardwareDecode.No, software = setOf(VideoCodec.H264))
        assertNull(best(copy("av1", VideoCodec.AV1), decode = decode).best)
    }

    @Test
    fun aUhdCopyOnlyCountsOnADisplayThatShowsIt() {
        val decode = evidence(key(VideoCodec.HEVC) to yes())
        val small = best(copy("hd", VideoCodec.HEVC, 1080, size = 2_000), copy("uhd", VideoCodec.HEVC, 2160, size = 1_000), decode = decode, display = fullHd)
        assertEquals("hd", small.best?.candidate?.name, "on 1080p both look the same; the larger file is kept")
        assertEquals(SelectionCriterion.BITRATE, small.decidedBy)
        val large = best(copy("hd", VideoCodec.HEVC, 1080, size = 2_000), copy("uhd", VideoCodec.HEVC, 2160, size = 1_000), decode = decode, display = uhd)
        assertEquals("uhd", large.best?.candidate?.name)
        assertEquals(SelectionCriterion.RESOLUTION, large.decidedBy)
    }

    @Test
    fun hdrIsClaimedOnlyWhereTheDisplayShowsItAndTheEngineSendsIt() {
        assertFalse(DesktopCapabilities.ENGINE_OUTPUTS_HDR, "Reflux's engine does not send HDR to the display today")
        val decode = evidence(key(VideoCodec.HEVC, 10) to yes(), key(VideoCodec.HEVC) to yes())
        val copies = arrayOf(copy("sdr", VideoCodec.HEVC, 2160, 8, size = 2_000), copy("hdr", VideoCodec.HEVC, 2160, 10, DynamicRange.HDR10, size = 1_000))

        val today = best(*copies, decode = decode, display = uhd, hdr = true)
        assertEquals(setOf(DynamicRange.SDR), DesktopCapabilities.of(uhd, decode, displayShowsHdr = true).display.dynamicRanges)
        assertEquals("sdr", today.best?.candidate?.name, "HDR would be tone-mapped, a compromise")
        assertEquals(listOf(PlaybackIssue.HDR_TONE_MAPPED), today.ranked.last().assessment.issues)

        val sdrDisplay = best(*copies, decode = decode, display = uhd, hdr = false, hdrOutput = true)
        assertEquals("sdr", sdrDisplay.best?.candidate?.name, "an engine that could send HDR, to a display that is not showing it")

        val hdrDisplay = best(*copies, decode = decode, display = uhd, hdr = true, hdrOutput = true)
        assertEquals("hdr", hdrDisplay.best?.candidate?.name)
        assertEquals(SelectionCriterion.DYNAMIC_RANGE, hdrDisplay.decidedBy)
    }

    // What a playback adds ------------------------------------------------------------------------------------

    @Test
    fun aPlaybackInHardwareProvesItAndWidensTheFrame() {
        val before = evidence(key(VideoCodec.HEVC, 10) to yes(1920, 1080))
        val after = before.with(DecodeObservation(VideoCodec.HEVC, 10, 3840, 2160, hardware = true))
        assertEquals(yes(), after.hardware[key(VideoCodec.HEVC, 10)])
        assertEquals(yes(), DecodeEvidence.UNKNOWN.with(DecodeObservation(VideoCodec.HEVC, 10, 3840, 2160, true)).hardware[key(VideoCodec.HEVC, 10)])
        assertEquals(after, after.with(DecodeObservation(VideoCodec.HEVC, 10, 1280, 720, true)), "a smaller frame adds nothing")
    }

    @Test
    fun aPlaybackInSoftwareWhereHardwareWasBelievedDisprovesIt() {
        val before = evidence(key(VideoCodec.HEVC, 10) to yes())
        assertEquals(HardwareDecode.No, before.with(DecodeObservation(VideoCodec.HEVC, 10, 1920, 1080, false)).hardware[key(VideoCodec.HEVC, 10)])
        assertEquals(before, before.with(DecodeObservation(VideoCodec.HEVC, 10, 7680, 4320, false)), "beyond the frame seen, software was expected")
        assertEquals(HardwareDecode.No, DecodeEvidence.UNKNOWN.with(DecodeObservation(VideoCodec.VP9, 8, 1280, 720, false)).hardware[key(VideoCodec.VP9)])
        assertEquals(key(VideoCodec.H264, 12), DecodeKey(VideoCodec.H264, DecodeEvidence.depthClass(16)))
    }

    @Test
    fun whatTheEngineReportsIsReadCarefully() {
        assertEquals(DecodeObservation(VideoCodec.HEVC, 10, 3840, 2160, true), DecodeEvidence.observation("d3d11va", "hevc", 3840, 2160, "p010", "d3d11"))
        assertEquals(DecodeObservation(VideoCodec.H264, 8, 1920, 1080, false), DecodeEvidence.observation("no", "h264", 1920, 1080, null, "yuv420p"))
        assertEquals(DecodeObservation(VideoCodec.AV1, 10, 1920, 1080, true), DecodeEvidence.observation("vaapi", "av1", 1920, 1080, "", "p010"))
        assertNull(DecodeEvidence.observation("no", "hevc", 1920, 1080, null, "yuv444p10"), "4:4:4 says nothing of the usual case")
        assertNull(DecodeEvidence.observation("no", "prores", 1920, 1080, null, "yuv422p10"))
        assertNull(DecodeEvidence.observation("d3d11va", "hevc", null, 1080, "p010", "d3d11"))
        assertNull(DecodeEvidence.observation("d3d11va", "hevc", 1920, 1080, null, "d3d11"), "no underlying format: depth unknown")
        assertEquals(10 to true, DecodeEvidence.pixelFormat("yuv420p10le"))
        assertEquals(12 to false, DecodeEvidence.pixelFormat("yuv444p12"))
        assertEquals(8 to true, DecodeEvidence.pixelFormat("nv12"))
        assertTrue(MpvDecodeProbe.hardwareInUse("nvdec-copy"))
        assertFalse(MpvDecodeProbe.hardwareInUse("no"))
        assertFalse(MpvDecodeProbe.hardwareInUse(""))
        assertFalse(MpvDecodeProbe.hardwareInUse(null))
    }

    @Test
    fun theEnginesSoftwareDecodersAreReadFromItsList() {
        val list = """[{"codec":"h264","driver":"h264","description":"H.264"},{"codec":"h264","driver":"h264_cuvid","description":"CUVID"},
            {"codec":"av1","driver":"av1","description":"needs hwaccel"},{"codec":"hevc","driver":"hevc_qsv","description":"QSV"},
            {"codec":"vp9","driver":"libvpx-vp9","description":"VP9"},{"codec":"prores","driver":"prores","description":"ProRes"}]"""
        assertEquals(setOf(VideoCodec.H264, VideoCodec.VP9), DecodeEvidence.softwareCodecs(list), "no hardware wrappers, and FFmpeg's own AV1 decoder needs hardware")
        assertNull(DecodeEvidence.softwareCodecs(null))
        assertNull(DecodeEvidence.softwareCodecs("not json"))
    }

    @Test
    fun theSummaryNamesCodecsNotTheMachine() {
        val summary = evidence(key(VideoCodec.HEVC, 10) to yes(), key(VideoCodec.H264, 10) to HardwareDecode.No).summary()
        assertEquals(
            "H264 10-bit software, HEVC 10-bit hardware up to 3840x2160; software decoders: H264 HEVC AV1 VP9 VP8 MPEG2 MPEG4_PART2 VC1",
            summary,
        )
    }
}
