@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.reflux.android.playback

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.audio.AudioCapabilities
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioDecoderLimit
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.FrameRateLimit
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleFidelity
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport

/** One decoder as the device lists it, reduced to what capabilities are derived from. */
data class DecoderDescription(
    val mimeType: String,
    val hardware: Boolean,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val tenBit: Boolean = false,
    /** Its `MediaCodecInfo.CodecProfileLevel` pairs, as the platform's constants. */
    val profileLevels: List<Pair<Int, Int>> = emptyList(),
    /** For video: what it declares at the sizes that matter; null when it could not be asked. */
    val frameRates: List<FrameRateLimit>? = null,
    /** For audio: what it declares. */
    val maxChannels: Int? = null,
    val maxSampleRate: Int? = null,
)

/** One audio output as the device lists it: how many channels it takes, and which encodings (`AudioFormat.ENCODING_*`). */
data class AudioOutputDescription(val maxChannels: Int, val encodings: List<Int>)

/** What can be sent out of the device: channels of decoded sound, and compressed audio passed through untouched. */
data class AudioOutputCapabilities(
    val channels: Int = 2,
    val passthrough: Set<AudioCodec> = emptySet(),
    val passthroughKnown: Boolean = true,
    val channelsKnown: Boolean = true,
    val atmos: Support = Support.UNSUPPORTED,
)

/**
 * What this device can play, as the device itself reports it. Nothing is assumed from the Android version
 * or the model: a codec is listed when a decoder for it exists, with the frame size, bit depth, profiles, levels,
 * and frame rates that decoder declares; what can be passed through is what Media3 says it will pass through; and
 * a display's HDR formats are the ones it reports.
 */
object AndroidCapabilities {
    /** What the playback engine's own extractors and renderers handle, independent of the device. */
    private val containers = setOf(
        Container.MATROSKA, Container.WEBM, Container.MP4, Container.QUICKTIME, Container.MPEG_TS, Container.MPEG_PS,
        Container.AVI, Container.FLV, Container.OGG,
    )
    private val subtitles = setOf(SubtitleFormat.SRT, SubtitleFormat.ASS, SubtitleFormat.WEBVTT, SubtitleFormat.TTML, SubtitleFormat.PGS, SubtitleFormat.DVB)

    /**
     * What Media3 and the shell's drawing make of subtitles, independent of the device. A file beside a video is
     * typed by [Media3Player.subtitleMimeType], which has no bitmap format: one would be read as SubRip. ASS keeps
     * words, position, and some style, as Media3's parser does. Formats with a parser that nobody has seen
     * work here are unknown.
     */
    private val subtitleRenderers = SubtitleCapabilities(
        renderers = mapOf(
            SubtitleFormat.SRT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.ASS to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.PLAIN),
            SubtitleFormat.WEBVTT to SubtitleRenderer(Support.UNKNOWN, Support.SUPPORTED, SubtitleFidelity.PLAIN),
            SubtitleFormat.TTML to SubtitleRenderer(Support.UNKNOWN, Support.SUPPORTED, SubtitleFidelity.PLAIN),
            SubtitleFormat.PGS to SubtitleRenderer(Support.UNKNOWN, Support.UNSUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.VOBSUB to SubtitleRenderer(Support.UNKNOWN, Support.UNSUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.DVB to SubtitleRenderer(Support.UNKNOWN, Support.UNSUPPORTED, SubtitleFidelity.FULL),
        ),
        forcedTracks = Support.SUPPORTED,
        hearingImpairedTracks = Support.UNKNOWN,
    )

    /** Media3's FLV reader cannot seek; the others have not been measured on a stream. */
    private val containerSeek = mapOf(Container.FLV to Support.UNSUPPORTED)

    /** The frame sizes a decoder is asked about for its frame rates. */
    private val FRAME_SIZES = listOf(1920 to 1080, 3840 to 2160)

    /** What Media3 is asked it would pass through, each as it comes in real files. */
    private val passthroughFormats = listOf(
        Triple(AudioCodec.AC3, MimeTypes.AUDIO_AC3, 6), Triple(AudioCodec.EAC3, MimeTypes.AUDIO_E_AC3, 6),
        Triple(AudioCodec.DTS, MimeTypes.AUDIO_DTS, 6), Triple(AudioCodec.DTS_HD_MA, MimeTypes.AUDIO_DTS_HD, 8),
        Triple(AudioCodec.TRUEHD, MimeTypes.AUDIO_TRUEHD, 8),
    )

    private val videoCodecs = mapOf(
        "video/avc" to VideoCodec.H264,
        "video/hevc" to VideoCodec.HEVC,
        "video/av01" to VideoCodec.AV1,
        "video/x-vnd.on2.vp9" to VideoCodec.VP9,
        "video/x-vnd.on2.vp8" to VideoCodec.VP8,
        "video/mpeg2" to VideoCodec.MPEG2,
        "video/mp4v-es" to VideoCodec.MPEG4_PART2,
        "video/wvc1" to VideoCodec.VC1,
    )
    private val audioCodecs = mapOf(
        "audio/mp4a-latm" to AudioCodec.AAC,
        "audio/mpeg" to AudioCodec.MP3,
        "audio/ac3" to AudioCodec.AC3,
        "audio/eac3" to AudioCodec.EAC3,
        "audio/eac3-joc" to AudioCodec.EAC3,
        "audio/vnd.dts" to AudioCodec.DTS,
        "audio/vnd.dts.hd" to AudioCodec.DTS_HD_MA,
        "audio/true-hd" to AudioCodec.TRUEHD,
        "audio/flac" to AudioCodec.FLAC,
        "audio/alac" to AudioCodec.ALAC,
        "audio/raw" to AudioCodec.PCM,
        "audio/opus" to AudioCodec.OPUS,
        "audio/vorbis" to AudioCodec.VORBIS,
    )

    /** The compressed audio an output takes as it is, to decode itself (a receiver or soundbar behind HDMI). */
    private val passthroughEncodings = mapOf(
        AudioFormat.ENCODING_AC3 to AudioCodec.AC3,
        AudioFormat.ENCODING_E_AC3 to AudioCodec.EAC3,
        AudioFormat.ENCODING_E_AC3_JOC to AudioCodec.EAC3,
        AudioFormat.ENCODING_DTS to AudioCodec.DTS,
        AudioFormat.ENCODING_DTS_HD to AudioCodec.DTS_HD_MA,
        AudioFormat.ENCODING_DOLBY_TRUEHD to AudioCodec.TRUEHD,
    )

    /** What the outputs that carry sound out of the device (HDMI) report. Nothing attached reports nothing. */
    fun audioOutput(outputs: List<AudioOutputDescription>): AudioOutputCapabilities = AudioOutputCapabilities(
        // Stereo is what every device can do by itself; more is claimed only when an output says so.
        channels = maxOf(2, outputs.maxOfOrNull { it.maxChannels } ?: 2),
        passthrough = outputs.flatMap { it.encodings }.mapNotNull(passthroughEncodings::get).toSet(),
        // The platform documents an empty list as "any": it does not say which, so it is not read as none.
        passthroughKnown = outputs.none { it.encodings.isEmpty() },
        channelsKnown = outputs.none { it.maxChannels <= 0 },
        atmos = if (outputs.any { AudioFormat.ENCODING_E_AC3_JOC in it.encodings }) Support.SUPPORTED else Support.UNSUPPORTED,
    )

    /**
     * What the engine itself says it will pass through, for each of the formats: this is what will really happen,
     * so it replaces what the outputs list. Where it says nothing, an output that gave no list stays unknown.
     */
    fun withEngine(output: AudioOutputCapabilities, passes: (mimeType: String, channels: Int) -> Boolean): AudioOutputCapabilities {
        val passthrough = passthroughFormats.filter { (_, mime, channels) -> passes(mime, channels) }.map { it.first }.toSet()
        val atmos = passes(MimeTypes.AUDIO_E_AC3_JOC, 6)
        return output.copy(
            passthrough = passthrough,
            passthroughKnown = output.passthroughKnown || passthrough.isNotEmpty(),
            atmos = when {
                atmos -> Support.SUPPORTED
                output.passthroughKnown -> Support.UNSUPPORTED
                else -> Support.UNKNOWN
            },
        )
    }

    fun of(
        decoders: List<DecoderDescription>,
        display: DisplayCapabilities,
        pictureInPicture: Boolean,
        audio: AudioOutputCapabilities = AudioOutputCapabilities(),
    ): DeviceCapabilities = DeviceCapabilities(
        videoDecoders = decoders.mapNotNull { decoder ->
            val codec = videoCodecs[decoder.mimeType.lowercase()] ?: return@mapNotNull null
            if (decoder.maxWidth <= 0 || decoder.maxHeight <= 0) return@mapNotNull null
            VideoDecoderSupport(
                codec, decoder.maxWidth, decoder.maxHeight, if (decoder.tenBit) 10 else 8, decoder.hardware,
                profiles = AndroidProfiles.declared(decoder.mimeType, decoder.profileLevels), frameRates = decoder.frameRates,
            )
        }.distinct(),
        // Uncompressed audio needs no decoder.
        audioDecoders = decoders.mapNotNull { audioCodecs[it.mimeType.lowercase()] }.toSet() + AudioCodec.PCM,
        // A device's own speakers and most headphones are stereo; more is what a connected output reports.
        audioPassthrough = audio.passthrough,
        audioPassthroughKnown = audio.passthroughKnown,
        atmosPassthrough = audio.atmos,
        maxOutputChannels = audio.channels,
        maxOutputChannelsKnown = audio.channelsKnown,
        audioDecoderLimits = audioLimits(decoders),
        dolbyVisionProfiles = decoders.filter { it.mimeType.equals(AndroidProfiles.DOLBY_VISION, ignoreCase = true) }
            .flatMap { it.profileLevels }.mapNotNull { (profile, _) -> AndroidProfiles.dolbyVision(profile) }.toSet(),
        display = display,
        containers = containers,
        subtitleFormats = subtitles,
        subtitles = subtitleRenderers,
        containerSeek = containerSeek,
        // Playing on with the screen off or the app gone is not built, so it is not claimed.
        features = setOfNotNull(
            PlaybackFeature.EXTERNAL_SUBTITLES,
            PlaybackFeature.NETWORK_STREAMING,
            PlaybackFeature.AUDIO_PASSTHROUGH.takeIf { audio.passthrough.isNotEmpty() },
            PlaybackFeature.PICTURE_IN_PICTURE.takeIf { pictureInPicture },
        ),
    )

    /** The most any decoder of each codec takes: several may serve one codec, and any of them will do. */
    private fun audioLimits(decoders: List<DecoderDescription>): Map<AudioCodec, AudioDecoderLimit> =
        decoders.mapNotNull { decoder -> audioCodecs[decoder.mimeType.lowercase()]?.let { it to decoder } }.groupBy({ it.first }, { it.second })
            .mapValues { (_, all) ->
                AudioDecoderLimit(
                    maxChannels = all.map { it.maxChannels }.takeIf { null !in it }?.filterNotNull()?.max(),
                    maxSampleRate = all.map { it.maxSampleRate }.takeIf { null !in it }?.filterNotNull()?.max(),
                )
            }

    fun of(context: Context): DeviceCapabilities = of(decoders(), context)

    fun decoders(): List<DecoderDescription> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
        .flatMap { info ->
            info.supportedTypes.mapNotNull { type ->
                val capabilities = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
                val video = capabilities.videoCapabilities
                val audio = capabilities.audioCapabilities
                DecoderDescription(
                    mimeType = type,
                    hardware = isHardware(info),
                    maxWidth = video?.supportedWidths?.upper ?: 0,
                    maxHeight = video?.supportedHeights?.upper ?: 0,
                    tenBit = capabilities.profileLevels.any { AndroidProfiles.tenBit(type, it.profile) },
                    profileLevels = capabilities.profileLevels.map { it.profile to it.level },
                    frameRates = video?.let { sizes ->
                        FRAME_SIZES.filter { (width, height) -> sizes.isSizeSupported(width, height) }.mapNotNull { (width, height) ->
                            runCatching { FrameRateLimit(width, height, sizes.getSupportedFrameRatesFor(width, height).upper) }.getOrNull()
                        }
                    },
                    maxChannels = audio?.maxInputChannelCount,
                    maxSampleRate = audio?.supportedSampleRateRanges?.maxOfOrNull { it.upper },
                )
            }
        }

    /** With [decoders] given: what is asked of the system each time is only what can change, the outputs and the display. */
    fun of(decoders: List<DecoderDescription>, context: Context): DeviceCapabilities {
        val attributes = AudioAttributes.DEFAULT
        val engine = AudioCapabilities.getCapabilities(context, attributes, null)
        val audio = withEngine(audioOutput(outputs(context))) { mimeType, channels ->
            val format = Format.Builder().setSampleMimeType(mimeType).setChannelCount(channels).setSampleRate(48_000).build()
            runCatching { engine.isPassthroughPlaybackSupported(format, attributes) }.getOrDefault(false)
        }
        return of(decoders, display(context), context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE), audio)
    }

    private fun outputs(context: Context): List<AudioOutputDescription> =
        context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
            .filter { it.type == AudioDeviceInfo.TYPE_HDMI || it.type == AudioDeviceInfo.TYPE_HDMI_ARC || (Build.VERSION.SDK_INT >= 31 && it.type == AudioDeviceInfo.TYPE_HDMI_EARC) }
            .map { AudioOutputDescription(it.channelCounts.maxOrNull() ?: 0, it.encodings.toList()) }

    private val decoderHardware by lazy {
        MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }.associate { it.name to isHardware(it) }
    }

    /** Whether a decoder Media3 named is hardware; null for a name the device does not list. */
    fun isHardware(decoderName: String): Boolean? = decoderHardware[decoderName]

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.", ignoreCase = true) && !info.name.startsWith("c2.android.", ignoreCase = true)

    @Suppress("DEPRECATION")
    private fun display(context: Context): DisplayCapabilities {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return DisplayCapabilities(1920, 1080, dynamicRangesKnown = false)
        val modes = display.supportedModes.ifEmpty { arrayOf(display.mode) }
        val width = modes.maxOf { maxOf(it.physicalWidth, it.physicalHeight) }
        val height = modes.maxOf { minOf(it.physicalWidth, it.physicalHeight) }
        val types = if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes else display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        val ranges = types.toList().mapNotNull {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> DynamicRange.HDR10
                Display.HdrCapabilities.HDR_TYPE_HLG -> DynamicRange.HLG
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> DynamicRange.HDR10_PLUS
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> DynamicRange.DOLBY_VISION
                else -> null
            }
        }
        // Whether HDR is tone-mapped well on an SDR panel differs by device and is not reported, so it is not claimed.
        return DisplayCapabilities(width, height, ranges.toSet() + DynamicRange.SDR, canToneMap = false)
    }
}
