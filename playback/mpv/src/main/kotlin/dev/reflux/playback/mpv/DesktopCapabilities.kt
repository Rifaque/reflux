package dev.reflux.playback.mpv

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport

/**
 * Capabilities of libmpv on a desktop machine.
 *
 * mpv decodes every listed codec in software up to 8K and 12-bit. Hardware decoding (`hwdec=auto-safe`) is
 * assumed for the codecs virtually every desktop GPU of the last decade accelerates; it is a declared profile,
 * to be refined by runtime detection (`hwdec-current`) once playback statistics are collected.
 */
object DesktopCapabilities {
    private val commonlyAccelerated = setOf(VideoCodec.H264, VideoCodec.HEVC, VideoCodec.VP9)

    fun of(display: DisplayCapabilities, maxOutputChannels: Int = 2): DeviceCapabilities = DeviceCapabilities(
        videoDecoders = VideoCodec.entries.filter { it != VideoCodec.UNKNOWN }.map { codec ->
            VideoDecoderSupport(codec, 8192, 4320, maxBitDepth = 12, hardware = codec in commonlyAccelerated)
        },
        audioDecoders = AudioCodec.entries.filter { it != AudioCodec.UNKNOWN }.toSet(),
        audioPassthrough = emptySet(), // needs knowledge of the user's receiver; off by default
        maxOutputChannels = maxOutputChannels,
        display = display.copy(canToneMap = true), // libplacebo tone-maps HDR and Dolby Vision
        containers = Container.entries.filter { it != Container.UNKNOWN }.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
        features = setOf(PlaybackFeature.EXTERNAL_SUBTITLES, PlaybackFeature.NETWORK_STREAMING),
    )
}
