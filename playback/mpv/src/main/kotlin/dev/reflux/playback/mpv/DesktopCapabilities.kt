package dev.reflux.playback.mpv

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.SubtitleCapabilities
import dev.reflux.core.playback.SubtitleFidelity
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleRenderer
import dev.reflux.core.playback.Support

/** What playbacks have shown of the audio output. */
data class ObservedAudioOutput(
    val channels: Int,
    /** A playback came out with fewer channels than it went in: this is the limit, not only what has been used. */
    val exact: Boolean,
) {
    /** With what one more playback showed: [source] channels decoded, [output] sent to the device. */
    fun with(source: Int, output: Int): ObservedAudioOutput = first(source, output).let { next ->
        if (next.exact || next.channels > channels) next else this
    }

    companion object {
        fun first(source: Int, output: Int) = ObservedAudioOutput(output, exact = output < source)
    }
}

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
     * What libmpv does with subtitles, as the engine is built. Text goes through libass and bitmaps through its own
     * renderer. Files beside a video are opened by content, which has been seen for text only.
     */
    private val subtitles = SubtitleCapabilities(
        renderers = mapOf(
            SubtitleFormat.SRT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.ASS to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.WEBVTT to SubtitleRenderer(Support.SUPPORTED, Support.SUPPORTED, SubtitleFidelity.FULL),
            SubtitleFormat.PGS to SubtitleRenderer(Support.SUPPORTED, Support.UNKNOWN, SubtitleFidelity.FULL),
            SubtitleFormat.VOBSUB to SubtitleRenderer(Support.SUPPORTED, Support.UNKNOWN, SubtitleFidelity.FULL),
            SubtitleFormat.DVB to SubtitleRenderer(Support.SUPPORTED, Support.UNKNOWN, SubtitleFidelity.FULL),
        ),
        forcedTracks = Support.SUPPORTED,
        hearingImpairedTracks = Support.SUPPORTED,
    )

    /**
     * @param displayShowsHdr whether the display the window is on is showing HDR now, as the system reports it;
     *   HDR is claimed only where that and [hdrOutput] are both true.
     * @param audioOutput what playbacks have shown of the output; null before any has.
     */
    fun of(
        display: DisplayCapabilities,
        decode: DecodeEvidence = DecodeEvidence.UNKNOWN,
        displayShowsHdr: Boolean = false,
        hdrOutput: Boolean = ENGINE_OUTPUTS_HDR,
        audioOutput: ObservedAudioOutput? = null,
    ): DeviceCapabilities = DeviceCapabilities(
        videoDecoders = decode.decoders(),
        audioDecoders = decode.audio ?: AudioCodec.entries.filter { it != AudioCodec.UNKNOWN }.toSet(),
        // Not a limit of the output: Reflux does not ask the engine to pass anything through (`audio-spdif`).
        audioPassthrough = emptySet(),
        atmosPassthrough = Support.UNSUPPORTED,
        maxOutputChannels = maxOf(2, audioOutput?.channels ?: 2),
        maxOutputChannelsKnown = audioOutput?.exact == true,
        display = display.copy(
            dynamicRanges = if (displayShowsHdr && hdrOutput) setOf(DynamicRange.SDR, DynamicRange.HDR10, DynamicRange.HLG) else setOf(DynamicRange.SDR),
            canToneMap = true, // libplacebo tone-maps HDR and Dolby Vision
        ),
        containers = Container.entries.filter { it != Container.UNKNOWN }.toSet(),
        subtitleFormats = SubtitleFormat.entries.toSet(),
        subtitles = subtitles,
        features = setOf(PlaybackFeature.EXTERNAL_SUBTITLES, PlaybackFeature.NETWORK_STREAMING),
    )
}
