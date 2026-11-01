package dev.reflux.core.playback

import dev.reflux.core.playback.Support.SUPPORTED
import dev.reflux.core.playback.Support.UNKNOWN
import dev.reflux.core.playback.Support.UNSUPPORTED
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaybackCapabilitiesTest {
    private val nothing = DeviceCapabilities(emptyList(), emptySet(), display = DisplayCapabilities(1920, 1080), containers = emptySet(), subtitleFormats = emptySet())

    private val mainTen = VideoStream(VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120)

    /** Declares everything, as an Android decoder does. */
    private fun declared(
        hardware: Boolean? = true,
        depth: Int = 10,
        profiles: Map<VideoProfile, Int?>? = mapOf(VideoProfile.MAIN to 153, VideoProfile.MAIN_10 to 153),
        rates: List<FrameRateLimit>? = listOf(FrameRateLimit(1920, 1080, 120.0), FrameRateLimit(3840, 2160, 30.0)),
    ) = VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, depth, hardware, profiles, rates)

    private fun device(vararg decoders: VideoDecoderSupport) = nothing.copy(videoDecoders = decoders.toList())

    @Test
    fun aCodecWithADeclaredDecoderIsSupported() {
        val answer = Capabilities.video(mainTen, device(declared()))
        assertEquals(VideoCapability(SUPPORTED, SUPPORTED, UNSUPPORTED), answer)
    }

    @Test
    fun aCodecWithNoDecoderIsUnsupportedAndSaysWhy() {
        val answer = Capabilities.video(mainTen.copy(codec = VideoCodec.AV1), device(declared()))
        assertEquals(VideoCapability(UNSUPPORTED, UNSUPPORTED, UNSUPPORTED, setOf(VideoLimit.CODEC)), answer)
    }

    @Test
    fun aCodecNobodyNamedIsUnknownEvenOnADeviceWithNoDecoders() {
        val answer = Capabilities.video(VideoStream(VideoCodec.UNKNOWN), nothing)
        assertEquals(VideoCapability(UNKNOWN, UNKNOWN, UNKNOWN), answer)
    }

    @Test
    fun softwareAndHardwareAreAnsweredApart() {
        val answer = Capabilities.video(mainTen, device(declared(hardware = false)))
        assertEquals(SUPPORTED, answer.decode, "software decoding is decoding")
        assertEquals(UNSUPPORTED, answer.hardware)
        assertEquals(SUPPORTED, answer.software)
        val both = Capabilities.video(mainTen, device(declared(hardware = true), declared(hardware = false)))
        assertEquals(listOf(SUPPORTED, SUPPORTED), listOf(both.hardware, both.software))
    }

    @Test
    fun aDecoderThatMayBeEitherLeavesBothUnknown() {
        val answer = Capabilities.video(mainTen, device(declared(hardware = null)))
        assertEquals(listOf(UNKNOWN, UNKNOWN), listOf(answer.hardware, answer.software))
        assertEquals(UNKNOWN, answer.decode, "not supported: nothing says this decoder exists as hardware or software")
        val past = Capabilities.video(mainTen.copy(bitDepth = 12), device(declared(hardware = null)))
        assertEquals(UNSUPPORTED, past.decode, "and unknown is no shelter from a limit it goes past")
    }

    @Test
    fun aHardwareDecoderTooSmallLeavesSoftwareToDoIt() {
        val small = VideoDecoderSupport(VideoCodec.HEVC, 1920, 1080, 10, true, frameAndDepthOnly = true)
        val soft = VideoDecoderSupport(VideoCodec.HEVC, 8192, 4320, 12, false, frameAndDepthOnly = true)
        val answer = Capabilities.video(mainTen.copy(width = 3840, height = 2160), device(small, soft))
        assertEquals(VideoCapability(SUPPORTED, UNSUPPORTED, SUPPORTED), answer)
    }

    @Test
    fun aBitDepthOrProfilePastTheDecoderIsNamed() {
        val eightBit = declared(depth = 8, profiles = mapOf(VideoProfile.MAIN to 153))
        val answer = Capabilities.video(mainTen, device(eightBit))
        assertEquals(UNSUPPORTED, answer.decode)
        assertEquals(setOf(VideoLimit.BIT_DEPTH, VideoLimit.PROFILE), answer.limits)
        val profile = Capabilities.video(mainTen, device(declared(profiles = mapOf(VideoProfile.MAIN to 153))))
        assertEquals(setOf(VideoLimit.PROFILE), profile.limits, "ten bits fit, and that profile is not declared")
    }

    @Test
    fun aLevelAndAFrameRatePastTheDecoderAreNamed() {
        val answer = Capabilities.video(mainTen.copy(level = 156), device(declared()))
        assertEquals(setOf(VideoLimit.LEVEL), answer.limits)
        val uhd60 = Capabilities.video(mainTen.copy(width = 3840, height = 2160, frameRate = 59.94), device(declared()))
        assertEquals(setOf(VideoLimit.FRAME_RATE), uhd60.limits)
        assertEquals(SUPPORTED, Capabilities.video(mainTen.copy(frameRate = 59.94), device(declared())).decode, "the same rate at a size it reaches")
    }

    @Test
    fun aFrameSizePastTheDecoderIsNamedAndPortraitIsNotPastIt() {
        assertEquals(setOf(VideoLimit.FRAME_SIZE), Capabilities.video(mainTen.copy(width = 7680, height = 4320), device(declared())).limits)
        assertEquals(SUPPORTED, Capabilities.video(mainTen.copy(width = 1080, height = 1920), device(declared())).decode)
    }

    @Test
    fun aFactTheFileDoesNotStateIsUnknownAndNotSupported() {
        val cases = listOf(mainTen.copy(width = null), mainTen.copy(bitDepth = null), mainTen.copy(profile = null), mainTen.copy(level = null), mainTen.copy(frameRate = null))
        for (video in cases) assertEquals(UNKNOWN, Capabilities.video(video, device(declared())).decode, "$video")
    }

    @Test
    fun aLimitTheDecoderDoesNotDeclareIsUnknownAndNotSupported() {
        assertEquals(UNKNOWN, Capabilities.video(mainTen, device(declared(profiles = null))).decode)
        assertEquals(UNKNOWN, Capabilities.video(mainTen, device(declared(rates = null))).decode)
        assertEquals(UNKNOWN, Capabilities.video(mainTen, device(declared(profiles = mapOf(VideoProfile.MAIN_10 to null)))).decode)
    }

    @Test
    fun aDecoderWhoseOnlyLimitsAreFrameAndDepthDoesNotNeedTheRestDeclared() {
        val ffmpeg = VideoDecoderSupport(VideoCodec.HEVC, 8192, 4320, 12, false, frameAndDepthOnly = true)
        assertEquals(SUPPORTED, Capabilities.video(mainTen, device(ffmpeg)).software)
        assertEquals(UNSUPPORTED, Capabilities.video(mainTen.copy(bitDepth = 16), device(ffmpeg)).software)
    }

    private val eac3 = AudioStream(AudioCodec.EAC3, channels = 6, sampleRate = 48_000)
    private val stereoOnly = nothing.copy(audioDecoders = setOf(AudioCodec.EAC3), maxOutputChannels = 2)

    @Test
    fun audioDecodingIsAskedOfTheDecoders() {
        assertEquals(SUPPORTED, Capabilities.audio(eac3, stereoOnly).decode)
        assertEquals(UNSUPPORTED, Capabilities.audio(AudioStream(AudioCodec.TRUEHD), stereoOnly).decode)
        assertEquals(UNKNOWN, Capabilities.audio(AudioStream(), stereoOnly).decode)
    }

    @Test
    fun aDecoderDeclaresItsChannelsAndSampleRate() {
        val limited = stereoOnly.copy(audioDecoderLimits = mapOf(AudioCodec.EAC3 to AudioDecoderLimit(maxChannels = 6, maxSampleRate = 48_000)))
        assertEquals(SUPPORTED, Capabilities.audio(eac3, limited).decode)
        assertEquals(UNSUPPORTED, Capabilities.audio(eac3.copy(channels = 8), limited).decode)
        assertEquals(UNSUPPORTED, Capabilities.audio(eac3.copy(sampleRate = 96_000), limited).decode)
    }

    @Test
    fun moreChannelsThanTheOutputIsADownmixWhereTheLimitIsKnown() {
        val answer = Capabilities.audio(eac3, stereoOnly)
        assertEquals(UNSUPPORTED, answer.channels)
        assertEquals(SUPPORTED, answer.plays, "a downmix still plays")
        assertEquals(UNKNOWN, Capabilities.audio(eac3, stereoOnly.copy(maxOutputChannelsKnown = false)).channels, "what has been seen so far is not a limit")
        assertEquals(SUPPORTED, Capabilities.audio(eac3.copy(channels = 2), stereoOnly.copy(maxOutputChannelsKnown = false)).channels)
        assertEquals(UNKNOWN, Capabilities.audio(eac3.copy(channels = null), stereoOnly).channels)
    }

    @Test
    fun passthroughIsAvailableUnavailableOrNotKnown() {
        val receiver = stereoOnly.copy(audioPassthrough = setOf(AudioCodec.EAC3), maxOutputChannels = 2)
        val passed = Capabilities.audio(eac3, receiver)
        assertEquals(SUPPORTED, passed.passthrough)
        assertEquals(SUPPORTED, passed.channels, "the receiver decodes them, not this device")
        assertEquals(UNSUPPORTED, Capabilities.audio(eac3, stereoOnly).passthrough)
        assertEquals(UNKNOWN, Capabilities.audio(eac3, stereoOnly.copy(audioPassthroughKnown = false)).passthrough)
    }

    @Test
    fun soundComesOutThroughEitherPath() {
        val onlyPassthrough = nothing.copy(audioPassthrough = setOf(AudioCodec.TRUEHD))
        assertEquals(SUPPORTED, Capabilities.audio(AudioStream(AudioCodec.TRUEHD), onlyPassthrough).plays)
        assertEquals(UNSUPPORTED, Capabilities.audio(AudioStream(AudioCodec.DTS), onlyPassthrough).plays)
        val unsure = nothing.copy(audioPassthroughKnown = false)
        assertEquals(UNKNOWN, Capabilities.audio(AudioStream(AudioCodec.DTS), unsure).plays, "no decoder, and passthrough may yet carry it")
    }

    @Test
    fun atmosOnEac3NeedsMoreThanPlainPassthrough() {
        val atmos = eac3.copy(atmos = true)
        val plain = nothing.copy(audioPassthrough = setOf(AudioCodec.EAC3), atmosPassthrough = UNSUPPORTED)
        assertEquals(UNSUPPORTED, Capabilities.audio(atmos, plain).atmos)
        assertEquals(SUPPORTED, Capabilities.audio(atmos, plain.copy(atmosPassthrough = SUPPORTED)).atmos)
        assertEquals(UNKNOWN, Capabilities.audio(atmos, plain.copy(atmosPassthrough = UNKNOWN)).atmos)
        val truehd = nothing.copy(audioPassthrough = setOf(AudioCodec.TRUEHD))
        assertEquals(SUPPORTED, Capabilities.audio(AudioStream(AudioCodec.TRUEHD, atmos = true), truehd).atmos, "its objects travel in what passes through")
        assertEquals(UNSUPPORTED, Capabilities.audio(atmos, nothing).atmos, "a local decode keeps the bed only")
    }

    private val renderers = nothing.copy(
        subtitles = SubtitleCapabilities(
            mapOf(
                SubtitleFormat.ASS to SubtitleRenderer(SUPPORTED, SUPPORTED, SubtitleFidelity.PLAIN),
                SubtitleFormat.PGS to SubtitleRenderer(SUPPORTED, UNSUPPORTED, SubtitleFidelity.FULL),
                SubtitleFormat.VOBSUB to SubtitleRenderer(UNKNOWN, UNKNOWN),
            ),
        ),
    )

    @Test
    fun aRendererAnswersForEmbeddedAndExternalApart() {
        assertEquals(SUPPORTED, Capabilities.subtitle(SubtitleFormat.PGS, external = false, renderers).render)
        val external = Capabilities.subtitle(SubtitleFormat.PGS, external = true, renderers)
        assertEquals(UNSUPPORTED, external.render)
        assertNull(external.fidelity, "no fidelity for what does not render")
        assertTrue(external.bitmap)
    }

    @Test
    fun aRendererSaysHowMuchOfAFormatSurvives() {
        val ass = Capabilities.subtitle(SubtitleFormat.ASS, external = false, renderers)
        assertEquals(SubtitleFidelity.PLAIN, ass.fidelity)
        assertEquals(false, ass.bitmap)
    }

    @Test
    fun aFormatNothingWasSaidAboutIsUnknownAndNotClaimed() {
        assertEquals(UNKNOWN, Capabilities.subtitle(SubtitleFormat.DVB, external = false, renderers).render)
        assertEquals(UNKNOWN, Capabilities.subtitle(SubtitleFormat.VOBSUB, external = false, renderers).render)
        assertEquals(UNKNOWN, Capabilities.subtitle(SubtitleFormat.PGS, external = false, nothing).render)
    }

    @Test
    fun containersAnswerForDemuxingAndSeeking() {
        val device = nothing.copy(containers = setOf(Container.MATROSKA, Container.FLV), containerSeek = mapOf(Container.FLV to UNSUPPORTED))
        assertEquals(ContainerCapability(SUPPORTED, UNKNOWN), Capabilities.container(Container.MATROSKA, device))
        assertEquals(ContainerCapability(SUPPORTED, UNSUPPORTED), Capabilities.container(Container.FLV, device))
        assertEquals(UNSUPPORTED, Capabilities.container(Container.WMV, device).demux)
        assertEquals(UNKNOWN, Capabilities.container(Container.UNKNOWN, device).demux)
    }

    private val hdr = VideoStream(VideoCodec.HEVC, dynamicRange = DynamicRange.HDR10)
    private val dolby = VideoStream(VideoCodec.HEVC, dynamicRange = DynamicRange.DOLBY_VISION, dolbyVisionProfile = 8)
    private fun showing(vararg ranges: DynamicRange, toneMap: Boolean = false, known: Boolean = true) =
        nothing.copy(display = DisplayCapabilities(3840, 2160, ranges.toSet() + DynamicRange.SDR, toneMap, known))

    @Test
    fun aDisplayShowsWhatItLists() {
        assertEquals(DisplayCapability(SUPPORTED, UNSUPPORTED), Capabilities.display(hdr, showing(DynamicRange.HDR10)))
        assertEquals(DisplayCapability(UNSUPPORTED, UNSUPPORTED), Capabilities.display(hdr, showing()))
        assertEquals(DisplayCapability(UNSUPPORTED, SUPPORTED), Capabilities.display(hdr, showing(toneMap = true)))
        assertEquals(SUPPORTED, Capabilities.display(VideoStream(), showing()).show, "SDR needs nothing")
    }

    @Test
    fun aDisplayThatCouldNotBeAskedIsNotAnSdrDisplay() {
        assertEquals(DisplayCapability(UNKNOWN, UNKNOWN), Capabilities.display(hdr, showing(known = false)))
    }

    @Test
    fun aDisplayShowingDolbyVisionIsNotADeviceDecodingIt() {
        val display = showing(DynamicRange.DOLBY_VISION)
        assertEquals(UNKNOWN, Capabilities.display(dolby, display).show, "decoders not read")
        assertEquals(SUPPORTED, Capabilities.display(dolby, display.copy(dolbyVisionProfiles = setOf(8))).show)
        assertEquals(UNSUPPORTED, Capabilities.display(dolby, display.copy(dolbyVisionProfiles = setOf(5))).show)
        assertEquals(UNSUPPORTED, Capabilities.display(dolby.copy(dolbyVisionProfile = null), display.copy(dolbyVisionProfiles = emptySet())).show)
        assertEquals(UNKNOWN, Capabilities.display(dolby.copy(dolbyVisionProfile = null), display.copy(dolbyVisionProfiles = setOf(8))).show)
    }

    @Test
    fun theSameFileGetsADifferentAnswerPerClient() {
        // The reference file's class: HEVC Main 10, E-AC-3 with Atmos, Matroska, PGS.
        val stream = StreamInfo(
            container = Container.MATROSKA,
            video = mainTen,
            audio = listOf(eac3.copy(atmos = true), AudioStream(AudioCodec.DTS, channels = 6)),
            subtitles = listOf(SubtitleStream(SubtitleFormat.PGS), SubtitleStream(SubtitleFormat.SRT, forced = true)),
        )
        val tablet = nothing.copy(
            videoDecoders = listOf(declared()),
            audioDecoders = setOf(AudioCodec.EAC3),
            containers = setOf(Container.MATROSKA),
            subtitles = SubtitleCapabilities(mapOf(SubtitleFormat.SRT to SubtitleRenderer(SUPPORTED, SUPPORTED, SubtitleFidelity.FULL))),
        )
        val answer = Capabilities.of(stream, tablet)
        assertEquals(SUPPORTED, answer.container.demux)
        assertEquals(SUPPORTED, answer.video?.hardware)
        assertEquals(listOf(SUPPORTED, UNSUPPORTED), answer.audio.map { it.plays }, "one track plays and the other does not")
        assertEquals(listOf(UNKNOWN, SUPPORTED), answer.subtitles.map { it.render }, "and PGS is not claimed for being in the file")

        val noVideo = Capabilities.of(stream, tablet.copy(videoDecoders = emptyList()))
        assertEquals(UNSUPPORTED, noVideo.video?.decode)
        assertEquals(SUPPORTED, noVideo.audio.first().plays, "one stream's answer does not touch another's")
        assertEquals(setOf(VideoLimit.CODEC), noVideo.video?.limits)
    }

    @Test
    fun aStreamWithNoVideoHasNoVideoAnswer() {
        val answer = Capabilities.of(StreamInfo(audio = listOf(eac3)), stereoOnly)
        assertNull(answer.video)
        assertNull(answer.display)
    }

    @Test
    fun profilesAreReadFromTheNamesEnginesUse() {
        assertEquals(VideoProfile.MAIN_10, VideoProfile.fromName("Main 10"))
        assertEquals(VideoProfile.HIGH_10, VideoProfile.fromName("High 10"))
        assertEquals(VideoProfile.BASELINE, VideoProfile.fromName("Constrained Baseline"))
        assertEquals(VideoProfile.PROFILE_2, VideoProfile.fromName("Profile 2"))
        assertNull(VideoProfile.fromName("Dolby Digital Plus + Dolby Atmos"))
        assertNull(VideoProfile.fromName(null))
    }

    @Test
    fun supportPrefersWhatWorksThenWhatMayWork() {
        assertEquals(SUPPORTED, Support.best(listOf(UNSUPPORTED, UNKNOWN, SUPPORTED)))
        assertEquals(UNKNOWN, Support.best(listOf(UNSUPPORTED, UNKNOWN)))
        assertEquals(UNSUPPORTED, Support.best(listOf(UNSUPPORTED)))
        assertEquals(UNSUPPORTED, Support.best(emptyList()))
    }
}
