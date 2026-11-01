package dev.reflux.core.playback

enum class Container {
    MATROSKA, MP4, AVI, MPEG_TS, MPEG_PS, QUICKTIME, WEBM, WMV, FLV, OGG, UNKNOWN;

    companion object {
        fun fromExtension(extension: String): Container = when (extension.lowercase()) {
            "mkv", "mk3d", "matroska" -> MATROSKA
            "mp4", "m4v" -> MP4
            "avi", "divx" -> AVI
            "ts", "m2ts", "mts", "mpegts" -> MPEG_TS
            "mpg", "mpeg", "vob" -> MPEG_PS
            "mov" -> QUICKTIME
            "webm" -> WEBM
            "wmv", "asf" -> WMV
            "flv" -> FLV
            "ogv", "ogm" -> OGG
            else -> UNKNOWN
        }
    }
}

enum class VideoCodec { H264, HEVC, AV1, VP9, VP8, MPEG2, MPEG4_PART2, VC1, UNKNOWN }

enum class AudioCodec(val lossless: Boolean) {
    AAC(false),
    MP3(false),
    AC3(false),
    EAC3(false),
    DTS(false),
    DTS_HD_MA(true),
    TRUEHD(true),
    FLAC(true),
    ALAC(true),
    PCM(true),
    OPUS(false),
    VORBIS(false),
    UNKNOWN(false),
}

/**
 * A video stream's profile. One name for what several codecs share: which codec it belongs to is the stream's.
 * On AV1, [MAIN] covers 8 and 10 bits, as the format does.
 */
enum class VideoProfile {
    BASELINE, MAIN, MAIN_10, HIGH, HIGH_10, HIGH_422, HIGH_444, PROFILE_0, PROFILE_1, PROFILE_2, PROFILE_3;

    companion object {
        /** From the names FFmpeg gives them, which are also what Jellyfin and mpv report. */
        fun fromName(name: String?): VideoProfile? = when (name?.trim()?.lowercase()) {
            "baseline", "constrained baseline" -> BASELINE
            "main" -> MAIN
            "main 10" -> MAIN_10
            "high" -> HIGH
            "high 10" -> HIGH_10
            "high 4:2:2" -> HIGH_422
            "high 4:4:4", "high 4:4:4 predictive" -> HIGH_444
            "profile 0" -> PROFILE_0
            "profile 1" -> PROFILE_1
            "profile 2" -> PROFILE_2
            "profile 3" -> PROFILE_3
            else -> null
        }
    }
}

/** Dynamic range of a video stream, ordered from least to most capable. */
enum class DynamicRange { SDR, HLG, HDR10, HDR10_PLUS, DOLBY_VISION }

enum class SubtitleFormat(val bitmap: Boolean) {
    SRT(false),
    ASS(false),
    WEBVTT(false),
    TTML(false),
    PGS(true),
    VOBSUB(true),
    DVB(true),
    UNKNOWN(false);

    companion object {
        fun fromExtension(extension: String): SubtitleFormat? = when (extension.lowercase()) {
            "srt" -> SRT
            "ass", "ssa" -> ASS
            "vtt" -> WEBVTT
            "ttml", "dfxp" -> TTML
            "sup" -> PGS
            "sub", "idx" -> VOBSUB
            else -> null
        }
    }
}
