package dev.reflux.core.playback

/** Overall playback expectation for a version on a device, ordered best first. */
enum class PlaybackVerdict { OPTIMAL, DEGRADED, UNSUPPORTED }

/** A specific reason a version will not play optimally. */
enum class PlaybackIssue(val verdict: PlaybackVerdict) {
    CONTAINER_UNSUPPORTED(PlaybackVerdict.UNSUPPORTED),
    VIDEO_CODEC_UNSUPPORTED(PlaybackVerdict.UNSUPPORTED),
    VIDEO_RESOLUTION_UNSUPPORTED(PlaybackVerdict.UNSUPPORTED),
    VIDEO_BIT_DEPTH_UNSUPPORTED(PlaybackVerdict.UNSUPPORTED),

    /** Dolby Vision without a compatible base layer on a device that can neither show nor convert it. */
    DOLBY_VISION_UNSUPPORTED(PlaybackVerdict.UNSUPPORTED),
    NO_PLAYABLE_AUDIO(PlaybackVerdict.DEGRADED),
    SOFTWARE_VIDEO_DECODE(PlaybackVerdict.DEGRADED),

    /** HDR content shown on an SDR display through tone mapping. */
    HDR_TONE_MAPPED(PlaybackVerdict.DEGRADED),

    /** HDR content on an SDR display without tone mapping: colors will look washed out. */
    HDR_NOT_DISPLAYABLE(PlaybackVerdict.DEGRADED),

    /** Dolby Vision played through its HDR10/SDR base layer. */
    DOLBY_VISION_BASE_LAYER(PlaybackVerdict.DEGRADED),

    /** More channels than the output has. Normal for stereo devices, so informational only. */
    AUDIO_DOWNMIXED(PlaybackVerdict.OPTIMAL),
}

data class PlaybackAssessment(val issues: List<PlaybackIssue>) {
    val verdict: PlaybackVerdict = issues.maxOfOrNull { it.verdict } ?: PlaybackVerdict.OPTIMAL

    /** Issues that actually reduce quality (excludes informational ones). */
    val compromises: List<PlaybackIssue> get() = issues.filter { it.verdict != PlaybackVerdict.OPTIMAL }
}

/**
 * Deterministic compatibility check between a version's streams and the device.
 *
 * Unknown stream properties never produce issues: Reflux does not refuse to play a file because
 * it could not inspect it. The platform player remains the final authority.
 */
object PlaybackAssessor {
    fun assess(stream: StreamInfo, device: DeviceCapabilities): PlaybackAssessment {
        val issues = mutableListOf<PlaybackIssue>()
        if (stream.container != Container.UNKNOWN && stream.container !in device.containers) {
            issues += PlaybackIssue.CONTAINER_UNSUPPORTED
        }
        stream.video?.let { issues += assessVideo(it, device) }
        issues += assessAudio(stream.audio, device)
        return PlaybackAssessment(issues.distinct())
    }

    private fun assessVideo(video: VideoStream, device: DeviceCapabilities): List<PlaybackIssue> {
        val issues = mutableListOf<PlaybackIssue>()
        if (video.codec != VideoCodec.UNKNOWN) {
            val decoders = device.videoDecoders.filter { it.codec == video.codec }
            if (decoders.isEmpty()) return listOf(PlaybackIssue.VIDEO_CODEC_UNSUPPORTED)
            // Only frame size and bit depth refuse a version here. A declared level or frame rate is what a decoder
            // promises, not all it does: engines try past it, so it is an answer for [Capabilities], not a refusal.
            val fits = decoders.map { it to it.fit(video).limits }
            val fitting = fits.filter { (_, limits) -> VideoLimit.FRAME_SIZE !in limits && VideoLimit.BIT_DEPTH !in limits }.map { it.first }
            when {
                fitting.isEmpty() && fits.all { (_, limits) -> VideoLimit.FRAME_SIZE in limits } ->
                    return listOf(PlaybackIssue.VIDEO_RESOLUTION_UNSUPPORTED)
                fitting.isEmpty() -> return listOf(PlaybackIssue.VIDEO_BIT_DEPTH_UNSUPPORTED)
                // Only where software is all there is known to be; a decoder that may be hardware is not a compromise.
                fitting.all { it.hardware == false } -> issues += PlaybackIssue.SOFTWARE_VIDEO_DECODE
            }
        }
        issues += assessDynamicRange(video, device)
        return issues
    }

    private fun assessDynamicRange(video: VideoStream, device: DeviceCapabilities): List<PlaybackIssue> {
        val display = device.display
        val range = video.dynamicRange
        if (range == DynamicRange.SDR) return emptyList()
        if (range in display.dynamicRanges && Capabilities.display(video, device).show != Support.UNSUPPORTED) return emptyList()
        if (range == DynamicRange.DOLBY_VISION) {
            if (video.hasCompatibleBaseLayer) {
                val baseShown = DynamicRange.HDR10 in display.dynamicRanges
                return when {
                    baseShown -> listOf(PlaybackIssue.DOLBY_VISION_BASE_LAYER)
                    display.canToneMap -> listOf(PlaybackIssue.DOLBY_VISION_BASE_LAYER, PlaybackIssue.HDR_TONE_MAPPED)
                    else -> listOf(PlaybackIssue.DOLBY_VISION_BASE_LAYER, PlaybackIssue.HDR_NOT_DISPLAYABLE)
                }
            }
            return if (display.canToneMap) listOf(PlaybackIssue.HDR_TONE_MAPPED)
            else listOf(PlaybackIssue.DOLBY_VISION_UNSUPPORTED)
        }
        // HDR10+ falls back to its HDR10 base on HDR10 displays.
        if (range == DynamicRange.HDR10_PLUS && DynamicRange.HDR10 in display.dynamicRanges) return emptyList()
        return listOf(if (display.canToneMap) PlaybackIssue.HDR_TONE_MAPPED else PlaybackIssue.HDR_NOT_DISPLAYABLE)
    }

    private fun assessAudio(tracks: List<AudioStream>, device: DeviceCapabilities): List<PlaybackIssue> {
        val known = tracks.filter { it.codec != AudioCodec.UNKNOWN }
        if (known.isEmpty()) return emptyList()
        val playable = known.filter { it.codec in device.audioDecoders || it.codec in device.audioPassthrough }
        if (playable.isEmpty()) return listOf(PlaybackIssue.NO_PLAYABLE_AUDIO)
        val best = playable.maxBy { it.channels ?: 0 }
        val passthrough = best.codec in device.audioPassthrough
        val channels = best.channels
        return if (!passthrough && channels != null && channels > device.maxOutputChannels) {
            listOf(PlaybackIssue.AUDIO_DOWNMIXED)
        } else {
            emptyList()
        }
    }
}
