package dev.reflux.sources.jellyfin

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioPlan
import dev.reflux.core.playback.Capabilities
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlannedPlayback
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.Support
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoPlan
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * This client as a Jellyfin `DeviceProfile`: a translation of [DeviceCapabilities] and the plan, deciding nothing.
 *
 * Only what is established is claimed. A subtitle format is listed where its renderer is known to work, and unknown
 * ones are left out: the server is then told nothing rather than something untrue. Limits are the hard ones the
 * planner refuses on, frame size and bit depth. A declared level or frame rate is left out because Reflux tries past
 * them, and a server told of them would transcode what the plan says to try.
 */
internal object JellyfinProfile {
    /** What a server's HLS stream can carry for the engines here. */
    private val HLS_VIDEO = listOf(VideoCodec.HEVC, VideoCodec.H264)
    private val HLS_AUDIO = listOf(AudioCodec.AAC, AudioCodec.AC3, AudioCodec.EAC3, AudioCodec.MP3)

    private fun containers(container: Container): List<String> = when (container) {
        Container.MATROSKA -> listOf("mkv")
        Container.MP4 -> listOf("mp4", "m4v", "mov")
        Container.QUICKTIME -> listOf("mov")
        Container.WEBM -> listOf("webm")
        Container.MPEG_TS -> listOf("ts", "mpegts", "m2ts")
        Container.MPEG_PS -> listOf("mpeg", "mpg", "vob")
        Container.AVI -> listOf("avi")
        Container.WMV -> listOf("asf", "wmv")
        Container.FLV -> listOf("flv")
        Container.OGG -> listOf("ogg", "ogv")
        Container.UNKNOWN -> emptyList()
    }

    private fun videoName(codec: VideoCodec): String? = when (codec) {
        VideoCodec.H264 -> "h264"
        VideoCodec.HEVC -> "hevc"
        VideoCodec.AV1 -> "av1"
        VideoCodec.VP9 -> "vp9"
        VideoCodec.VP8 -> "vp8"
        VideoCodec.MPEG2 -> "mpeg2video"
        VideoCodec.MPEG4_PART2 -> "mpeg4"
        VideoCodec.VC1 -> "vc1"
        VideoCodec.UNKNOWN -> null
    }

    private fun audioNames(codec: AudioCodec): List<String> = when (codec) {
        AudioCodec.AAC -> listOf("aac")
        AudioCodec.MP3 -> listOf("mp3")
        AudioCodec.AC3 -> listOf("ac3")
        AudioCodec.EAC3 -> listOf("eac3")
        AudioCodec.DTS, AudioCodec.DTS_HD_MA -> listOf("dts", "dca")
        AudioCodec.TRUEHD -> listOf("truehd")
        AudioCodec.FLAC -> listOf("flac")
        AudioCodec.ALAC -> listOf("alac")
        AudioCodec.PCM -> listOf("pcm_s16le", "pcm_s24le", "pcm_bluray")
        AudioCodec.OPUS -> listOf("opus")
        AudioCodec.VORBIS -> listOf("vorbis")
        AudioCodec.UNKNOWN -> emptyList()
    }

    private fun subtitleNames(format: SubtitleFormat): List<String> = when (format) {
        SubtitleFormat.SRT -> listOf("srt", "subrip")
        SubtitleFormat.ASS -> listOf("ass", "ssa")
        SubtitleFormat.WEBVTT -> listOf("vtt", "webvtt")
        SubtitleFormat.TTML -> listOf("ttml")
        SubtitleFormat.PGS -> listOf("pgssub", "pgs")
        SubtitleFormat.VOBSUB -> listOf("dvdsub")
        SubtitleFormat.DVB -> listOf("dvbsub")
        SubtitleFormat.UNKNOWN -> emptyList()
    }

    /** The extension a server serves a subtitle track under. */
    fun subtitleExtension(format: SubtitleFormat): String = when (format) {
        SubtitleFormat.SRT -> "subrip"
        SubtitleFormat.ASS -> "ass"
        SubtitleFormat.WEBVTT -> "vtt"
        SubtitleFormat.PGS -> "pgssub"
        SubtitleFormat.VOBSUB -> "dvdsub"
        else -> "subrip"
    }

    /**
     * No limit unless a video transcode is planned: a copied stream is the size it is, and a transcode at a gigabit
     * would be a waste of a server. It is allowed what the original used, and some room for a less efficient codec.
     */
    fun bitrateFor(plan: PlannedPlayback, stream: StreamInfo): Long =
        if (plan.video != VideoPlan.TRANSCODE) JellyfinPlaybackAsk.UNLIMITED_BITRATE
        else (stream.bitrateBps ?: TRANSCODE_FALLBACK_BPS).let { it * 2 }.coerceAtLeast(TRANSCODE_FALLBACK_BPS)

    private const val TRANSCODE_FALLBACK_BPS = 10_000_000L

    fun of(device: DeviceCapabilities, plan: PlannedPlayback, stream: StreamInfo): String = JsonObject(
        mapOf(
            "MaxStreamingBitrate" to JsonPrimitive(bitrateFor(plan, stream)),
            "DirectPlayProfiles" to JsonArray(directPlay(device)),
            "TranscodingProfiles" to JsonArray(if (plan.delivery == DeliveryMode.DIRECT_PLAY) emptyList() else listOf(transcoding(device, plan, stream))),
            "CodecProfiles" to JsonArray(codecLimits(device)),
            "SubtitleProfiles" to JsonArray(subtitles(device)),
        ),
    ).toString()

    private fun text(vararg pairs: Pair<String, String>) = JsonObject(pairs.associate { it.first to JsonPrimitive(it.second) })

    private fun audioCodecs(device: DeviceCapabilities): List<String> = AudioCodec.entries.filter { it in device.audioDecoders || it in device.audioPassthrough }.flatMap(::audioNames).distinct()

    private fun directPlay(device: DeviceCapabilities): List<JsonObject> {
        val video = device.videoDecoders.map { it.codec }.distinct().mapNotNull(::videoName).joinToString(",")
        val audio = audioCodecs(device).joinToString(",")
        val containers = device.containers.flatMap(::containers).distinct().joinToString(",")
        if (video.isEmpty() || containers.isEmpty()) return emptyList()
        return listOf(text("Container" to containers, "Type" to "Video", "VideoCodec" to video, "AudioCodec" to audio))
    }

    /** The source's own codecs first, so that what the plan keeps can be copied. */
    private fun transcoding(device: DeviceCapabilities, plan: PlannedPlayback, stream: StreamInfo): JsonObject {
        val kept = stream.video?.codec?.takeIf { plan.video != VideoPlan.TRANSCODE && it in HLS_VIDEO }
        val decodable = HLS_VIDEO.filter { codec -> device.videoDecoders.any { it.codec == codec } }
        // A transcode goes to what is cheapest to make and to decode.
        val video = listOfNotNull(kept) + (if (plan.video == VideoPlan.TRANSCODE) decodable.reversed() else decodable).filter { it != kept }
        val keptAudio = plan.audioIndex?.takeIf { plan.audio != AudioPlan.CONVERT }?.let(stream.audio::getOrNull)
        val audio = listOfNotNull(keptAudio?.codec).flatMap(::audioNames) + HLS_AUDIO.filter { it in device.audioDecoders }.flatMap(::audioNames)
        // Audio the plan keeps is copied as it is: this client downmixes it, and a smaller number here makes the
        // server convert it (`AudioChannelsNotSupported`, seen live on a remux to a stereo desktop).
        val channels = maxOf(device.maxOutputChannels, keptAudio?.channels ?: 0)
        return JsonObject(
            mapOf(
                "Container" to JsonPrimitive("ts"),
                "Type" to JsonPrimitive("Video"),
                "VideoCodec" to JsonPrimitive(video.mapNotNull(::videoName).joinToString(",")),
                "AudioCodec" to JsonPrimitive(audio.distinct().joinToString(",")),
                "Context" to JsonPrimitive("Streaming"),
                "Protocol" to JsonPrimitive("hls"),
                "MaxAudioChannels" to JsonPrimitive(channels.toString()),
                "BreakOnNonKeyFrames" to JsonPrimitive(true),
            ),
        )
    }

    private fun limit(property: String, value: Int) = JsonObject(
        mapOf(
            "Condition" to JsonPrimitive("LessThanEqual"),
            "Property" to JsonPrimitive(property),
            "Value" to JsonPrimitive(value.toString()),
            "IsRequired" to JsonPrimitive(true),
        ),
    )

    private fun codecLimits(device: DeviceCapabilities): List<JsonObject> = device.videoDecoders.groupBy { it.codec }.mapNotNull { (codec, decoders) ->
        val name = videoName(codec) ?: return@mapNotNull null
        JsonObject(
            mapOf(
                "Type" to JsonPrimitive("Video"),
                "Codec" to JsonPrimitive(name),
                "Conditions" to JsonArray(
                    listOf(
                        limit("VideoBitDepth", decoders.maxOf { it.maxBitDepth }),
                        limit("Width", decoders.maxOf { it.maxWidth }),
                        limit("Height", decoders.maxOf { it.maxHeight }),
                    ),
                ),
            ),
        )
    }

    private fun subtitles(device: DeviceCapabilities): List<JsonObject> = SubtitleFormat.entries.flatMap { format ->
        buildList {
            if (Capabilities.subtitle(format, external = false, device).render == Support.SUPPORTED) subtitleNames(format).forEach { add(text("Format" to it, "Method" to "Embed")) }
            if (Capabilities.subtitle(format, external = true, device).render == Support.SUPPORTED) subtitleNames(format).forEach { add(text("Format" to it, "Method" to "External")) }
        }
    }
}
