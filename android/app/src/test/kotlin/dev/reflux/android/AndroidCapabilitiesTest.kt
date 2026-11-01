package dev.reflux.android

import android.media.AudioFormat
import android.media.MediaCodecInfo.CodecProfileLevel
import androidx.media3.common.MimeTypes
import dev.reflux.android.playback.AndroidCapabilities
import dev.reflux.android.playback.AndroidProfiles
import dev.reflux.android.playback.AudioOutputCapabilities
import dev.reflux.android.playback.AudioOutputDescription
import dev.reflux.android.playback.CapabilityReading
import dev.reflux.android.playback.DecoderDescription
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioDecoderLimit
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Capabilities
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.FrameRateLimit
import dev.reflux.core.playback.SubtitleFidelity
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoLimit
import dev.reflux.core.playback.VideoProfile
import dev.reflux.core.playback.VideoStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What a device lists, turned into the shared model. The decoders here are as dumped from real devices. */
class AndroidCapabilitiesTest {
    private val display = DisplayCapabilities(3840, 2160)

    private val hevc = DecoderDescription(
        "video/hevc", hardware = true, maxWidth = 3840, maxHeight = 2160, tenBit = true,
        profileLevels = listOf(
            CodecProfileLevel.HEVCProfileMain to CodecProfileLevel.HEVCMainTierLevel51,
            CodecProfileLevel.HEVCProfileMain10 to CodecProfileLevel.HEVCMainTierLevel51,
            CodecProfileLevel.HEVCProfileMain10HDR10 to CodecProfileLevel.HEVCHighTierLevel41,
        ),
        frameRates = listOf(FrameRateLimit(1920, 1080, 120.0), FrameRateLimit(3840, 2160, 60.0)),
    )

    @Test
    fun aDecodersProfilesAndLevelsAreItsOwnBestForEachProfile() {
        val decoder = AndroidCapabilities.of(listOf(hevc), display, pictureInPicture = false).videoDecoders.single()
        assertEquals(mapOf(VideoProfile.MAIN to 153, VideoProfile.MAIN_10 to 153), decoder.profiles)
        assertEquals(hevc.frameRates, decoder.frameRates)
        assertFalse(decoder.frameAndDepthOnly, "every limit here is what the decoder declares")
    }

    @Test
    fun theReferenceClassOfFileIsAnsweredFromDeclaredLimits() {
        val device = AndroidCapabilities.of(listOf(hevc), display, pictureInPicture = false)
        val reference = VideoStream(VideoCodec.HEVC, 1920, 1080, 10, frameRate = 23.976, profile = VideoProfile.MAIN_10, level = 120)
        assertEquals(Support.SUPPORTED, Capabilities.video(reference, device).hardware)
        assertEquals(setOf(VideoLimit.LEVEL), Capabilities.video(reference.copy(level = 180), device).limits)
    }

    @Test
    fun aDecoderDeclaringNothingLeavesItsLimitsUnknown() {
        val bare = DecoderDescription("video/avc", hardware = true, maxWidth = 1920, maxHeight = 1080, frameRates = null)
        val decoder = AndroidCapabilities.of(listOf(bare), display, pictureInPicture = false).videoDecoders.single()
        assertNull(decoder.profiles)
        assertNull(decoder.frameRates)
    }

    @Test
    fun aDolbyVisionDecoderIsSeen() {
        // As the Fire TV lists them: one decoder per profile, under a type of their own.
        val decoders = listOf(
            DecoderDescription("video/dolby-vision", hardware = true, maxWidth = 3840, maxHeight = 2160, profileLevels = listOf(CodecProfileLevel.DolbyVisionProfileDvheStn to 0)),
            DecoderDescription("video/dolby-vision", hardware = true, maxWidth = 3840, maxHeight = 2160, profileLevels = listOf(CodecProfileLevel.DolbyVisionProfileDvheSt to 0)),
        )
        val device = AndroidCapabilities.of(decoders, display, pictureInPicture = false)
        assertEquals(setOf(5, 8), device.dolbyVisionProfiles)
        assertEquals(emptyList(), device.videoDecoders, "not a codec of its own")
        assertEquals(emptySet(), AndroidCapabilities.of(listOf(hevc), display, pictureInPicture = false).dolbyVisionProfiles, "the list was read: there are none")
    }

    @Test
    fun anAudioDecodersLimitsAreTheMostAnyDecoderOfThatCodecTakes() {
        val decoders = listOf(
            DecoderDescription("audio/mp4a-latm", hardware = false, maxChannels = 2, maxSampleRate = 48_000),
            DecoderDescription("audio/mp4a-latm", hardware = true, maxChannels = 8, maxSampleRate = 96_000),
            DecoderDescription("audio/eac3", hardware = false, maxChannels = null, maxSampleRate = 48_000),
        )
        val limits = AndroidCapabilities.of(decoders, display, pictureInPicture = false).audioDecoderLimits
        assertEquals(AudioDecoderLimit(8, 96_000), limits[AudioCodec.AAC])
        assertEquals(AudioDecoderLimit(null, 48_000), limits[AudioCodec.EAC3], "not declared stays not declared")
    }

    @Test
    fun anOutputThatListsNoEncodingsIsNotAnOutputWithoutThem() {
        // The platform documents an empty list as arbitrary encodings.
        val output = AndroidCapabilities.audioOutput(listOf(AudioOutputDescription(8, emptyList())))
        assertFalse(output.passthroughKnown)
        val device = AndroidCapabilities.of(emptyList(), display, pictureInPicture = false, audio = output)
        assertEquals(Support.UNKNOWN, Capabilities.audio(AudioStream(AudioCodec.AC3), device).passthrough)
        assertFalse(AndroidCapabilities.audioOutput(listOf(AudioOutputDescription(0, listOf(AudioFormat.ENCODING_AC3)))).channelsKnown, "no channel counts is not two")
        assertTrue(AndroidCapabilities.audioOutput(emptyList()).passthroughKnown, "and with no output at all, there is nothing to pass through to")
    }

    @Test
    fun theEngineDecidesWhatIsPassedThrough() {
        val listed = AudioOutputCapabilities(8, setOf(AudioCodec.AC3, AudioCodec.DTS))
        val answer = AndroidCapabilities.withEngine(listed) { mime, _ -> mime == MimeTypes.AUDIO_AC3 || mime == MimeTypes.AUDIO_E_AC3 }
        assertEquals(setOf(AudioCodec.AC3, AudioCodec.EAC3), answer.passthrough, "what it will do, whatever the output lists")
        assertEquals(Support.UNSUPPORTED, answer.atmos)
        val atmos = AndroidCapabilities.withEngine(listed) { mime, _ -> mime == MimeTypes.AUDIO_E_AC3_JOC }
        assertEquals(Support.SUPPORTED, atmos.atmos)
    }

    @Test
    fun anEngineThatSaysNothingLeavesAnOutputWithNoListUnknown() {
        val answer = AndroidCapabilities.withEngine(AudioOutputCapabilities(8, passthroughKnown = false)) { _, _ -> false }
        assertFalse(answer.passthroughKnown)
        assertEquals(Support.UNKNOWN, answer.atmos)
    }

    @Test
    fun bitmapSubtitlesAreNotClaimedBeyondWhatWasSeen() {
        val device = AndroidCapabilities.of(emptyList(), display, pictureInPicture = false)
        assertEquals(Support.UNSUPPORTED, Capabilities.subtitle(SubtitleFormat.PGS, external = true, device).render, "the player would read one as SubRip")
        assertEquals(Support.UNKNOWN, Capabilities.subtitle(SubtitleFormat.PGS, external = false, device).render)
        assertEquals(Support.UNKNOWN, Capabilities.subtitle(SubtitleFormat.VOBSUB, external = false, device).render)
        assertEquals(SubtitleFidelity.PLAIN, Capabilities.subtitle(SubtitleFormat.ASS, external = false, device).fidelity)
        assertEquals(Support.SUPPORTED, Capabilities.subtitle(SubtitleFormat.SRT, external = true, device).render)
    }

    @Test
    fun flvIsTheContainerKnownNotToSeek() {
        val device = AndroidCapabilities.of(emptyList(), display, pictureInPicture = false)
        assertEquals(Support.UNSUPPORTED, Capabilities.container(Container.FLV, device).seek)
        assertEquals(Support.UNKNOWN, Capabilities.container(Container.MATROSKA, device).seek)
        assertEquals(Support.UNSUPPORTED, Capabilities.container(Container.WMV, device).demux)
    }

    @Test
    fun levelsBecomeTheBitstreamsOwnNumbers() {
        assertEquals(120, AndroidProfiles.level("video/hevc", CodecProfileLevel.HEVCMainTierLevel4))
        assertEquals(120, AndroidProfiles.level("video/hevc", CodecProfileLevel.HEVCHighTierLevel4))
        assertEquals(93, AndroidProfiles.level("video/hevc", CodecProfileLevel.HEVCMainTierLevel31))
        assertEquals(41, AndroidProfiles.level("video/avc", CodecProfileLevel.AVCLevel41))
        assertEquals(40, AndroidProfiles.level("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Level4))
        assertEquals(8, AndroidProfiles.level("video/av01", CodecProfileLevel.AV1Level4))
        assertNull(AndroidProfiles.level("video/mpeg2", 1))
    }

    @Test
    fun tenBitsAreSeenOnEveryCodecThatHasThem() {
        // The prober once called each of the last three 8-bit.
        assertTrue(AndroidProfiles.tenBit("video/hevc", CodecProfileLevel.HEVCProfileMain10))
        assertTrue(AndroidProfiles.tenBit("video/av01", CodecProfileLevel.AV1ProfileMain10))
        assertTrue(AndroidProfiles.tenBit("video/avc", CodecProfileLevel.AVCProfileHigh10))
        assertTrue(AndroidProfiles.tenBit("video/x-vnd.on2.vp9", CodecProfileLevel.VP9Profile3))
        assertFalse(AndroidProfiles.tenBit("video/av01", CodecProfileLevel.AV1ProfileMain8))
        assertEquals(VideoProfile.MAIN, AndroidProfiles.profile("video/av01", CodecProfileLevel.AV1ProfileMain10), "AV1 has one Main profile for both")
    }

    @Test
    fun aReadingIsKeptUntilSomethingItReadFromChanged() {
        var reads = 0
        val reading = CapabilityReading { ++reads }
        assertEquals(1, reading.current())
        assertEquals(1, reading.current())
        reading.invalidate()
        assertEquals(2, reading.current())
    }
}
