package dev.reflux.core.playback

/** Representative device profiles for tests. */
object Devices {
    private val allAudio = AudioCodec.entries.filter { it != AudioCodec.UNKNOWN }.toSet()
    private val allContainers = Container.entries.filter { it != Container.UNKNOWN }.toSet()

    /** A 4K HDR TV box with Dolby Vision, passthrough to a 7.1 receiver. */
    val tv4kHdr = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 3840, 2160, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 3840, 2160, 10, hardware = true),
            VideoDecoderSupport(VideoCodec.AV1, 3840, 2160, 10, hardware = true),
        ),
        audioDecoders = allAudio - setOf(AudioCodec.TRUEHD, AudioCodec.DTS_HD_MA),
        audioPassthrough = setOf(AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.DTS, AudioCodec.DTS_HD_MA, AudioCodec.TRUEHD),
        maxOutputChannels = 8,
        display = DisplayCapabilities(
            3840, 2160,
            setOf(DynamicRange.SDR, DynamicRange.HDR10, DynamicRange.HDR10_PLUS, DynamicRange.DOLBY_VISION, DynamicRange.HLG),
        ),
        containers = allContainers,
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    /** A 1080p SDR laptop with software HEVC, stereo output, and a tone-mapping capable player. */
    val laptop1080 = DeviceCapabilities(
        videoDecoders = listOf(
            VideoDecoderSupport(VideoCodec.H264, 4096, 2304, 8, hardware = true),
            VideoDecoderSupport(VideoCodec.HEVC, 7680, 4320, 10, hardware = false),
        ),
        audioDecoders = allAudio,
        maxOutputChannels = 2,
        display = DisplayCapabilities(1920, 1080, setOf(DynamicRange.SDR), canToneMap = true),
        containers = allContainers,
        subtitleFormats = SubtitleFormat.entries.toSet(),
    )

    /** An older phone: H.264 only up to 1080p. */
    val oldPhone = DeviceCapabilities(
        videoDecoders = listOf(VideoDecoderSupport(VideoCodec.H264, 1920, 1080, 8, hardware = true)),
        audioDecoders = setOf(AudioCodec.AAC, AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.MP3, AudioCodec.OPUS),
        display = DisplayCapabilities(2400, 1080, setOf(DynamicRange.SDR)),
        containers = setOf(Container.MP4, Container.MATROSKA, Container.WEBM),
        subtitleFormats = setOf(SubtitleFormat.SRT, SubtitleFormat.WEBVTT, SubtitleFormat.ASS),
    )
}
