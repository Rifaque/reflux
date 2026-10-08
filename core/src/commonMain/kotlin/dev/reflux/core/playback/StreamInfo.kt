package dev.reflux.core.playback

/**
 * Technical description of a version's primary streams.
 *
 * Every field may be unknown: a description inferred from file name hints is usually partial,
 * and capability checks treat unknown values as "no evidence of a problem" rather than as failures.
 */
data class StreamInfo(
    val container: Container = Container.UNKNOWN,
    val video: VideoStream? = null,
    val audio: List<AudioStream> = emptyList(),
    val subtitles: List<SubtitleStream> = emptyList(),
    val durationMs: Long? = null,
    val bitrateBps: Long? = null,
)

data class VideoStream(
    val codec: VideoCodec = VideoCodec.UNKNOWN,
    val width: Int? = null,
    val height: Int? = null,
    val bitDepth: Int? = null,
    val dynamicRange: DynamicRange = DynamicRange.SDR,
    /** Dolby Vision profile (5, 7, 8, ...) when [dynamicRange] is Dolby Vision and the profile is known. */
    val dolbyVisionProfile: Int? = null,
    val frameRate: Double? = null,
) {
    /**
     * Whether a Dolby Vision stream carries an HDR10/SDR-compatible base layer that plays correctly without DV support.
     * Profiles 7 and 8 do; profile 5 does not. Unknown profiles are treated as compatible.
     */
    val hasCompatibleBaseLayer: Boolean
        get() = dynamicRange != DynamicRange.DOLBY_VISION || dolbyVisionProfile != 5
}

data class AudioStream(
    val codec: AudioCodec = AudioCodec.UNKNOWN,
    val channels: Int? = null,
    val language: String? = null,
    val atmos: Boolean = false,
    val default: Boolean = false,
)

data class SubtitleStream(
    val format: SubtitleFormat,
    val language: String? = null,
    val forced: Boolean = false,
    val default: Boolean = false,
)

/** Reads real stream information from a playable target (libmpv on desktop, MediaExtractor on Android). */
fun interface MediaProber {
    /** Returns null when the target cannot be opened or read. */
    suspend fun probe(target: dev.reflux.core.source.PlaybackTarget): StreamInfo?
}

/**
 * Combines probed facts with file-name hints. Probed values win; hints only fill what probes cannot see.
 *
 * Probes without Dolby Vision support report a DV profile 7/8 stream as plain HDR10 (its base layer), so a
 * `DV` release tag upgrades an HDR10 probe result to Dolby Vision.
 */
fun StreamInfo.withHints(hints: StreamInfo): StreamInfo {
    val probedVideo = video ?: return copy(video = hints.video)
    val hintedRange = hints.video?.dynamicRange
    val range = if (probedVideo.dynamicRange == DynamicRange.HDR10 && hintedRange == DynamicRange.DOLBY_VISION) {
        DynamicRange.DOLBY_VISION
    } else {
        probedVideo.dynamicRange
    }
    return copy(
        container = if (container == Container.UNKNOWN) hints.container else container,
        video = probedVideo.copy(dynamicRange = range),
        audio = if (audio.isEmpty()) hints.audio else audio.mapIndexed { index, track ->
            // TrueHD/E-AC-3 Atmos is often only visible in the release name.
            if (index == 0 && !track.atmos && hints.audio.firstOrNull()?.atmos == true && track.codec == hints.audio.first().codec) {
                track.copy(atmos = true)
            } else {
                track
            }
        },
    )
}
