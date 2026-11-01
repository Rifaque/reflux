package dev.reflux.core.playback

/** What one decoder on the current device can handle. */
data class VideoDecoderSupport(
    val codec: VideoCodec,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int = 8,
    /**
     * Whether it decodes in hardware: true or false where the platform says, null where that is not known
     * (a desktop before its engine has been asked). Unknown is neither counted against a version nor for it.
     */
    val hardware: Boolean?,
    /** Each profile this decoder declares with the highest level declared for it (null: no level). Null: none declared. */
    val profiles: Map<VideoProfile, Int?>? = null,
    /** The highest frame rate declared at each frame size it was asked about. Null: none declared. */
    val frameRates: List<FrameRateLimit>? = null,
    /**
     * Frame size and bit depth are all that is known to limit it: an engine's software decoder, or a hardware decoder
     * this machine was seen decoding this codec and depth with. Otherwise a limit it does not declare is unknown.
     */
    val frameAndDepthOnly: Boolean = false,
)

/** A decoder's declared frame rate for frames up to this size. */
data class FrameRateLimit(val width: Int, val height: Int, val maxFrameRate: Double)

/** What one audio decoder declares. Null: not declared. */
data class AudioDecoderLimit(val maxChannels: Int? = null, val maxSampleRate: Int? = null)

/** How much of a subtitle format's styling survives its renderer. */
enum class SubtitleFidelity {
    /** As authored: positions, styles, fonts, animation, or the image itself. */
    FULL,

    /** The words, and some position and style. */
    PLAIN,
}

/** One renderer's ability with one subtitle format, in a stream and in a file beside it. */
data class SubtitleRenderer(
    val embedded: Support,
    val external: Support,
    val fidelity: SubtitleFidelity? = null,
)

/** What this client can do with subtitles. A format not in [renderers] is unknown. */
data class SubtitleCapabilities(
    val renderers: Map<SubtitleFormat, SubtitleRenderer> = emptyMap(),
    /** Whether the engine tells forced tracks from the rest. */
    val forcedTracks: Support = Support.UNKNOWN,
    /** Whether the engine tells hearing-impaired tracks from the rest. */
    val hearingImpairedTracks: Support = Support.UNKNOWN,
)

/** What the display playback goes to can present. */
data class DisplayCapabilities(
    /** Its size in pixels; null when it is not known which display that is, and nothing is capped by it. */
    val maxWidth: Int?,
    val maxHeight: Int?,
    val dynamicRanges: Set<DynamicRange> = setOf(DynamicRange.SDR),
    /** Whether the playback stack can tone-map HDR content for a display that cannot show it. */
    val canToneMap: Boolean = false,
    /** False when the display could not be asked: a range not listed is then unknown, not absent. */
    val dynamicRangesKnown: Boolean = true,
)

/** Platform features the product can offer only when present. */
enum class PlaybackFeature {
    PICTURE_IN_PICTURE,
    BACKGROUND_PLAYBACK,
    FRAME_RATE_MATCHING,
    AUDIO_PASSTHROUGH,
    EXTERNAL_SUBTITLES,
    NETWORK_STREAMING,
}

/**
 * The playback capabilities of the current device and playback engine: what this client can handle, as far as
 * anything shows. Questions are asked through [Capabilities], which answers supported, unsupported, or unknown.
 *
 * Produced by the platform playback layer (Media3 on Android, libmpv on desktop) and consumed by
 * version selection and the player UI. Nothing in the product should assume a capability not listed here.
 */
data class DeviceCapabilities(
    val videoDecoders: List<VideoDecoderSupport>,
    val audioDecoders: Set<AudioCodec>,
    val audioPassthrough: Set<AudioCodec> = emptySet(),
    val maxOutputChannels: Int = 2,
    val display: DisplayCapabilities,
    val containers: Set<Container>,
    val subtitleFormats: Set<SubtitleFormat>,
    val features: Set<PlaybackFeature> = emptySet(),
    /** Whether [audioPassthrough] is the whole answer. False: what is not in it is unknown, not absent. */
    val audioPassthroughKnown: Boolean = true,
    /** Whether Atmos objects on E-AC-3 survive passthrough, which plain E-AC-3 passthrough does not say. */
    val atmosPassthrough: Support = Support.UNKNOWN,
    /** Whether [maxOutputChannels] is the output's limit. False: it has been seen to take that many, and more is unknown. */
    val maxOutputChannelsKnown: Boolean = true,
    val audioDecoderLimits: Map<AudioCodec, AudioDecoderLimit> = emptyMap(),
    /** The Dolby Vision profiles there are decoders for. Null: not known. */
    val dolbyVisionProfiles: Set<Int>? = null,
    val subtitles: SubtitleCapabilities = SubtitleCapabilities(),
    /** Where a container's seeking matters for planning. Not listed: unknown. */
    val containerSeek: Map<Container, Support> = emptyMap(),
) {
    /** The best decoder for [codec]: known hardware first, then unknown, then software; then the largest supported frame. */
    fun decoderFor(codec: VideoCodec): VideoDecoderSupport? =
        videoDecoders.filter { it.codec == codec }
            .sortedWith(compareByDescending<VideoDecoderSupport> { hardwareRank(it.hardware) }.thenByDescending { it.maxWidth.toLong() * it.maxHeight })
            .firstOrNull()

    private fun hardwareRank(hardware: Boolean?): Int = when (hardware) {
        true -> 2
        null -> 1
        false -> 0
    }
}
