package dev.reflux.android.playback

import android.content.Context
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.MediaProber
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.SubtitleStream
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream
import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads what a file really contains with the platform's extractor: codecs, frame size, dynamic range,
 * audio layouts, languages, and above all the running time, which is what lets watch progress follow a
 * work from one copy to another.
 *
 * A file the extractor cannot open is reported as unreadable (null); it stays described by its name and
 * can still be played.
 */
class AndroidMediaProber(private val context: Context) : MediaProber {
    override suspend fun probe(target: PlaybackTarget): StreamInfo? = withContext(Dispatchers.IO) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, Uri.parse(target.uri), target.headers)
            val formats = (0 until extractor.trackCount).map(extractor::getTrackFormat)
            val video = formats.firstOrNull { it.mime().startsWith("video/") }
            StreamInfo(
                video = video?.let(::video),
                audio = formats.filter { it.mime().startsWith("audio/") }.map(::audio),
                subtitles = formats.mapNotNull(::subtitle),
                durationMs = formats.mapNotNull { it.long(MediaFormat.KEY_DURATION) }.maxOrNull()?.div(1000)?.takeIf { it > 0 },
            )
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun video(format: MediaFormat): VideoStream {
        val mime = format.mime()
        val dolbyVision = mime == "video/dolby-vision"
        val transfer = format.int(MediaFormat.KEY_COLOR_TRANSFER)
        val profile = format.int(MediaFormat.KEY_PROFILE)
        val level = format.int(MediaFormat.KEY_LEVEL)
        val tenBit = profile != null && AndroidProfiles.tenBit(mime, profile)
        val range = when {
            dolbyVision -> DynamicRange.DOLBY_VISION
            profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus -> DynamicRange.HDR10_PLUS
            transfer == MediaFormat.COLOR_TRANSFER_ST2084 -> DynamicRange.HDR10
            transfer == MediaFormat.COLOR_TRANSFER_HLG -> DynamicRange.HLG
            else -> DynamicRange.SDR
        }
        return VideoStream(
            codec = videoCodec(mime),
            width = format.int(MediaFormat.KEY_WIDTH),
            height = format.int(MediaFormat.KEY_HEIGHT),
            bitDepth = when {
                tenBit || range != DynamicRange.SDR -> 10
                profile != null -> 8
                else -> null
            },
            dynamicRange = range,
            dolbyVisionProfile = profile?.takeIf { dolbyVision }?.let(AndroidProfiles::dolbyVision),
            frameRate = format.int(MediaFormat.KEY_FRAME_RATE)?.toDouble()?.takeIf { it > 0 },
            profile = profile?.let { AndroidProfiles.profile(mime, it) },
            level = level?.let { AndroidProfiles.level(mime, it) },
        )
    }

    private fun audio(format: MediaFormat) = AudioStream(
        codec = audioCodec(format.mime()),
        channels = format.int(MediaFormat.KEY_CHANNEL_COUNT),
        language = format.string(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" && it.isNotBlank() },
        atmos = format.mime() == "audio/eac3-joc",
        sampleRate = format.int(MediaFormat.KEY_SAMPLE_RATE),
    )

    private fun subtitle(format: MediaFormat): SubtitleStream? {
        val kind = when (format.mime()) {
            "application/x-subrip" -> SubtitleFormat.SRT
            "text/x-ssa" -> SubtitleFormat.ASS
            "text/vtt" -> SubtitleFormat.WEBVTT
            "application/ttml+xml" -> SubtitleFormat.TTML
            "application/pgs" -> SubtitleFormat.PGS
            "application/vobsub" -> SubtitleFormat.VOBSUB
            "application/dvbsubs" -> SubtitleFormat.DVB
            else -> return null
        }
        return SubtitleStream(
            kind, format.string(MediaFormat.KEY_LANGUAGE)?.takeIf { it != "und" && it.isNotBlank() },
            forced = format.int(MediaFormat.KEY_IS_FORCED_SUBTITLE) == 1,
            default = format.int(MediaFormat.KEY_IS_DEFAULT) == 1,
        )
    }

    private fun videoCodec(mime: String): VideoCodec = when (mime) {
        "video/avc" -> VideoCodec.H264
        "video/hevc", "video/dolby-vision" -> VideoCodec.HEVC
        "video/av01" -> VideoCodec.AV1
        "video/x-vnd.on2.vp9" -> VideoCodec.VP9
        "video/x-vnd.on2.vp8" -> VideoCodec.VP8
        "video/mpeg2" -> VideoCodec.MPEG2
        "video/mp4v-es" -> VideoCodec.MPEG4_PART2
        "video/wvc1" -> VideoCodec.VC1
        else -> VideoCodec.UNKNOWN
    }

    private fun audioCodec(mime: String): AudioCodec = when (mime) {
        "audio/mp4a-latm" -> AudioCodec.AAC
        "audio/mpeg" -> AudioCodec.MP3
        "audio/ac3" -> AudioCodec.AC3
        "audio/eac3", "audio/eac3-joc" -> AudioCodec.EAC3
        "audio/vnd.dts" -> AudioCodec.DTS
        "audio/vnd.dts.hd" -> AudioCodec.DTS_HD_MA
        "audio/true-hd" -> AudioCodec.TRUEHD
        "audio/flac" -> AudioCodec.FLAC
        "audio/alac" -> AudioCodec.ALAC
        "audio/raw" -> AudioCodec.PCM
        "audio/opus" -> AudioCodec.OPUS
        "audio/vorbis" -> AudioCodec.VORBIS
        else -> AudioCodec.UNKNOWN
    }

    private fun MediaFormat.mime(): String = getString(MediaFormat.KEY_MIME).orEmpty().lowercase()
    private fun MediaFormat.int(key: String): Int? = if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
    private fun MediaFormat.long(key: String): Long? = if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
    private fun MediaFormat.string(key: String): String? = if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null
}
