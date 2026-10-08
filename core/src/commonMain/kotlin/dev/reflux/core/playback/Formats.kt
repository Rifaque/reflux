package dev.reflux.core.playback

enum class Container {
    MATROSKA, MP4, AVI, MPEG_TS, MPEG_PS, QUICKTIME, WEBM, WMV, FLV, OGG, UNKNOWN;

    companion object {
        fun fromExtension(extension: String): Container = when (extension.lowercase()) {
            "mkv", "mk3d" -> MATROSKA
            "mp4", "m4v" -> MP4
            "avi", "divx" -> AVI
            "ts", "m2ts", "mts" -> MPEG_TS
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
