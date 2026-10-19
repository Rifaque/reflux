package dev.reflux.playback.mpv

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.SubtitleFormat

/**
 * Capabilities of libmpv on a desktop machine, from what has been observed there (docs/DECISIONS.md,
 * "Desktop capabilities are observed, not declared").
 *
 * Video decoders come from [DecodeEvidence]: software decoders as the engine lists them, hardware decoding as
 * the probe and real playbacks showed it, and unknown where neither said. The display is the one the window is
 * on, as the shell reads it; it is never assumed to show HDR: see [of].
 */
object DesktopCapabilities {
    /**
     * Whether the engine is set up to send HDR to a display that is showing HDR. It is not: Reflux does not ask
     * libmpv to switch its output to the stream's colour space (`target-colorspace-hint`), so HDR is tone-mapped
     * to SDR by libplacebo on every display, HDR or not, and that is what version selection is told.
     */
    const val ENGINE_OUTPUTS_HDR = false

    /**
     * @param displayShowsHdr whether the display the window is on is showing HDR now, as the system reports it;
     *   HDR is claimed only where that and [hdrOutput] are both true.
     */
    fun of(
        display: DisplayCapabilities,
        decode: DecodeEvidence = DecodeEvidence.UNKNOWN,
        displayShowsHdr: Boolean = false,
        hdrOutput: Boolean = ENGINE_OUTPUTS_HDR,
        maxOutputChannels: Int = 2,
    ): DeviceCapabilities = DeviceCapabilities(
        videoDecoders = decode.decoders(),
        audioDecoders = AudioCodec.entries.filter { it != AudioCodec.UNKNOWN }.toSet(),
        audioPassthrough = emptySet(), // needs knowledge of the user's receiver; off by default
        maxOutputChannels = maxOutputChannels,
        display = display.copy(
            dynamicRanges = if (displayShowsHdr && hdrOutput) setOf(DynamicRange.SDR, DynamicRange.HDR10, DynamicRange.HLG) else setOf(DynamicRange.SDR),
            canToneMap = true, // libplacebo tone-maps HDR and Dolby Vision
        ),
        containers = Container.entries.filter { it != Container.UNKNOWN }.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
        features = setOf(PlaybackFeature.EXTERNAL_SUBTITLES, PlaybackFeature.NETWORK_STREAMING),
    )
}
