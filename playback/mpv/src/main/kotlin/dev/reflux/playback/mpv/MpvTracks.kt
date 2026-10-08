package dev.reflux.playback.mpv

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.TrackType
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Translates mpv's `track-list` (JSON) and FFmpeg codec names into Media Core types. */
internal object MpvTracks {
    fun parse(json: String?): List<JsonObject> {
        if (json.isNullOrBlank()) return emptyList()
        val element = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonArray ?: return emptyList()
        return element.mapNotNull { it as? JsonObject }
    }

    fun typeOf(track: JsonObject): String? = track.string("type")
    fun isAlbumArt(track: JsonObject): Boolean = track.bool("albumart")
    fun isExternal(track: JsonObject): Boolean = track.bool("external")

    fun playerTracks(json: String?): List<PlayerTrack> = parse(json).mapNotNull { track ->
        val type = when (typeOf(track)) {
            "video" -> TrackType.VIDEO
            "audio" -> TrackType.AUDIO
            "sub" -> TrackType.SUBTITLE
            else -> return@mapNotNull null
        }
        if (type == TrackType.VIDEO && track.bool("albumart")) return@mapNotNull null
        PlayerTrack(
            id = track.int("id")?.toString() ?: return@mapNotNull null,
            type = type,
            language = track.string("lang"),
            title = track.string("title"),
            codec = track.string("codec"),
            channels = track.int("demux-channel-count") ?: track.int("audio-channels"),
            default = track.bool("default"),
            forced = track.bool("forced"),
            hearingImpaired = track.bool("hearing-impaired"),
            external = track.bool("external"),
        )
    }

    /** mpv `chapter-list`: `[{"title": "Intro", "time": 0.0}, ...]`. */
    fun chapters(json: String?): List<dev.reflux.core.playback.Chapter> = parse(json).mapNotNull { chapter ->
        val time = chapter.double("time") ?: return@mapNotNull null
        dev.reflux.core.playback.Chapter(chapter.string("title"), (time * 1000).toLong())
    }

    fun video(track: JsonObject): VideoStream = VideoStream(
        codec = videoCodec(track.string("codec")),
        width = track.int("demux-w"),
        height = track.int("demux-h"),
        frameRate = track.double("demux-fps"),
        dolbyVisionProfile = track.int("dolby-vision-profile"),
    )

    fun audio(track: JsonObject): AudioStream {
        val profile = track.string("codec-profile").orEmpty().lowercase()
        return AudioStream(
            codec = audioCodec(track.string("codec"), profile),
            channels = track.int("demux-channel-count"),
            language = track.string("lang"),
            atmos = "atmos" in profile,
            default = track.bool("default"),
        )
    }

    fun subtitle(track: JsonObject): SubtitleStream = SubtitleStream(
        format = subtitleFormat(track.string("codec")),
        language = track.string("lang"),
        forced = track.bool("forced"),
        default = track.bool("default"),
    )

    fun videoCodec(name: String?): VideoCodec = when (name?.lowercase()) {
        "h264" -> VideoCodec.H264
        "hevc", "h265" -> VideoCodec.HEVC
        "av1" -> VideoCodec.AV1
        "vp9" -> VideoCodec.VP9
        "vp8" -> VideoCodec.VP8
        "mpeg2video", "mpeg1video" -> VideoCodec.MPEG2
        "mpeg4", "msmpeg4v3", "msmpeg4v2" -> VideoCodec.MPEG4_PART2
        "vc1", "wmv3" -> VideoCodec.VC1
        else -> VideoCodec.UNKNOWN
    }

    fun audioCodec(name: String?, profile: String = ""): AudioCodec = when (name?.lowercase()) {
        "aac" -> AudioCodec.AAC
        "mp3", "mp3float" -> AudioCodec.MP3
        "ac3" -> AudioCodec.AC3
        "eac3" -> AudioCodec.EAC3
        "dts" -> if ("ma" in profile || "x" in profile.split(' ', ':')) AudioCodec.DTS_HD_MA else AudioCodec.DTS
        "truehd", "mlp" -> AudioCodec.TRUEHD
        "flac" -> AudioCodec.FLAC
        "alac" -> AudioCodec.ALAC
        "opus" -> AudioCodec.OPUS
        "vorbis" -> AudioCodec.VORBIS
        null -> AudioCodec.UNKNOWN
        else -> if (name.startsWith("pcm_")) AudioCodec.PCM else AudioCodec.UNKNOWN
    }

    fun subtitleFormat(name: String?): SubtitleFormat = when (name?.lowercase()) {
        "subrip", "srt" -> SubtitleFormat.SRT
        "ass", "ssa" -> SubtitleFormat.ASS
        "webvtt" -> SubtitleFormat.WEBVTT
        "hdmv_pgs_subtitle", "pgssub" -> SubtitleFormat.PGS
        "dvd_subtitle", "vobsub" -> SubtitleFormat.VOBSUB
        "dvb_subtitle" -> SubtitleFormat.DVB
        "ttml" -> SubtitleFormat.TTML
        else -> SubtitleFormat.UNKNOWN
    }

    private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull
    private fun JsonObject.double(key: String): Double? = this[key]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull ?: false

}
