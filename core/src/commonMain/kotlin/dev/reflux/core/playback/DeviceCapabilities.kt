package dev.reflux.core.playback

/** What one decoder on the current device can handle. */
data class VideoDecoderSupport(
    val codec: VideoCodec,
    val maxWidth: Int,
    val maxHeight: Int,
    val maxBitDepth: Int = 8,
    val hardware: Boolean,
)

/** What the attached display can present. */
data class DisplayCapabilities(
    val maxWidth: Int,
    val maxHeight: Int,
    val dynamicRanges: Set<DynamicRange> = setOf(DynamicRange.SDR),
    /** Whether the playback stack can tone-map HDR content for a display that cannot show it. */
    val canToneMap: Boolean = false,
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
 * The playback capabilities of the current device and playback engine.
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
) {
    /** The best decoder for [codec]: hardware first, then the largest supported frame. */
    fun decoderFor(codec: VideoCodec): VideoDecoderSupport? =
        videoDecoders.filter { it.codec == codec }
            .sortedWith(compareByDescending<VideoDecoderSupport> { it.hardware }.thenByDescending { it.maxWidth * it.maxHeight })
            .firstOrNull()
}
