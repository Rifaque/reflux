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
)

/** What the display playback goes to can present. */
data class DisplayCapabilities(
    /** Its size in pixels; null when it is not known which display that is, and nothing is capped by it. */
    val maxWidth: Int?,
    val maxHeight: Int?,
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
