package dev.reflux.core.identify

import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.AudioStream
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.StreamInfo
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoStream

/**
 * Knowledge about release-name tokens: which tokens end a title, and what technical facts they imply.
 *
 * Only tokens that are very unlikely to appear inside real titles end a title. Ambiguous tokens such as
 * `WEB`, `DV`, or `Max` are only interpreted as technical hints *after* the title has ended.
 */
internal object ReleaseTokens {
    private const val B = """(?<![\p{L}\p{N}])"""
    private const val E = """(?![\p{L}\p{N}])"""

    private fun bounded(alternatives: String) = Regex("$B(?:$alternatives)$E", RegexOption.IGNORE_CASE)

    /** Tokens that reliably mark the end of a title. */
    val titleTerminator: Regex = bounded(
        listOf(
            """\d{3,4}[pi]""", "4k", "8k", "uhd",
            """hdr(?:10(?:\+|plus)?)?""", "dovi", """dolby[ ._-]?vision""",
            """blu[ ._-]?ray""", "bdrip", "brrip", "bdremux", "remux", """web[ ._-]?dl""", "webrip", "hdtv", "pdtv",
            "dvdrip", "dvdscr", "hdrip", "hdcam", "camrip", "telesync",
            "x26[45]", """h[ ._]?26[45]""", "hevc", "xvid", "divx",
            """10[ ._-]?bit""",
            """dts(?:[ ._-]?(?:hd|ma|x|es))*""", "truehd", "e?ac3", """ddp?\d[ ._]\d""", "ddp", """aac(?:\d[ ._]\d)?""",
            "proper", "repack", "rerip", "internal", "multisubs",
        ).joinToString("|"),
    )

    private val editionPattern = bounded(
        listOf(
            """director'?s[ ._-]cut""", """extended(?:[ ._-](?:edition|cut|version))?""",
            """theatrical(?:[ ._-](?:edition|cut|version))?""", "unrated", "uncut", """remastered""",
            """special[ ._-]edition""", """ultimate[ ._-](?:edition|cut)""", """final[ ._-]cut""",
            """collector'?s[ ._-]edition""", """anniversary[ ._-]edition""", """imax(?:[ ._-]edition)?""", "criterion",
        ).joinToString("|"),
    )

    /** Locates an edition phrase. Editions end titles and are reported separately. */
    fun findEdition(text: String): MatchResult? = editionPattern.find(text)

    fun editionLabel(match: MatchResult): String =
        TitleText.display(match.value.replace(Regex("[._-]"), " ").lowercase()).let { label ->
            when (label) {
                "Directors Cut", "Director's Cut" -> "Director's Cut"
                "Collectors Edition", "Collector's Edition" -> "Collector's Edition"
                "Imax", "Imax Edition" -> "IMAX"
                else -> label
            }
        }

    private val resolution = Regex("""$B(?:(\d{3,4})[pi]|(4k|uhd)|(8k))$E""", RegexOption.IGNORE_CASE)
    private val videoCodecs = listOf(
        bounded("""x265|h[ ._]?265|hevc""") to VideoCodec.HEVC,
        bounded("""x264|h[ ._]?264|avc""") to VideoCodec.H264,
        bounded("av1") to VideoCodec.AV1,
        bounded("vp9") to VideoCodec.VP9,
        bounded("xvid|divx") to VideoCodec.MPEG4_PART2,
        bounded("""mpeg-?2""") to VideoCodec.MPEG2,
        bounded("""vc-?1""") to VideoCodec.VC1,
    )
    private val dolbyVision = bounded("""dv|dovi|dolby[ ._-]?vision""")
    private val hdr10Plus = bounded("""hdr10(?:\+|plus)""")
    private val hdr10 = bounded("""hdr10|hdr""")
    private val hlg = bounded("hlg")
    private val tenBit = bounded("""10[ ._-]?bit""")
    private val audioCodecs = listOf(
        bounded("truehd") to AudioCodec.TRUEHD,
        bounded("""dts[ ._-]?(?:hd[ ._-]?ma|x|hd)""") to AudioCodec.DTS_HD_MA,
        bounded("dts") to AudioCodec.DTS,
        bounded("""ddp|dd\+|e-?ac-?3|ddp\d[ ._]\d""") to AudioCodec.EAC3,
        bounded("""dd|ac-?3|dd\d[ ._]\d""") to AudioCodec.AC3,
        bounded("flac") to AudioCodec.FLAC,
        bounded("""l?pcm""") to AudioCodec.PCM,
        bounded("opus") to AudioCodec.OPUS,
        bounded("""aac(?:\d[ ._]\d)?""") to AudioCodec.AAC,
        bounded("mp3") to AudioCodec.MP3,
    )
    private val atmos = bounded("atmos")
    private val channelLayout = Regex("""(?<![\p{N}])([1-7])[ ._]([01])(?![\p{N}])""")

    /**
     * Infers a partial [StreamInfo] from technical tokens.
     *
     * [technical] must contain only text that is known not to be title text.
     */
    fun streamHints(technical: String, extension: String): StreamInfo {
        val container = Container.fromExtension(extension)
        val video = videoHints(technical)
        val audio = audioHints(technical)
        return StreamInfo(container = container, video = video, audio = listOfNotNull(audio))
    }

    private fun videoHints(text: String): VideoStream? {
        val codec = videoCodecs.firstOrNull { it.first.containsMatchIn(text) }?.second
        val res = resolution.find(text)
        val (width, height) = when {
            res == null -> null to null
            res.groupValues[3].isNotEmpty() -> 7680 to 4320
            res.groupValues[2].isNotEmpty() -> 3840 to 2160
            else -> widthFor(res.groupValues[1].toInt())
        }
        val range = when {
            dolbyVision.containsMatchIn(text) -> DynamicRange.DOLBY_VISION
            hdr10Plus.containsMatchIn(text) -> DynamicRange.HDR10_PLUS
            hlg.containsMatchIn(text) -> DynamicRange.HLG
            hdr10.containsMatchIn(text) -> DynamicRange.HDR10
            else -> DynamicRange.SDR
        }
        val bitDepth = when {
            tenBit.containsMatchIn(text) || range != DynamicRange.SDR -> 10
            else -> null
        }
        if (codec == null && height == null && range == DynamicRange.SDR && bitDepth == null) return null
        return VideoStream(
            codec = codec ?: VideoCodec.UNKNOWN,
            width = width,
            height = height,
            bitDepth = bitDepth,
            dynamicRange = range,
        )
    }

    private fun widthFor(height: Int): Pair<Int?, Int?> = when (height) {
        2160 -> 3840 to 2160
        1440 -> 2560 to 1440
        1080 -> 1920 to 1080
        720 -> 1280 to 720
        576 -> 720 to 576
        480 -> 720 to 480
        else -> if (height in 240..4320) null to height else null to null
    }

    private fun audioHints(text: String): AudioStream? {
        val codec = audioCodecs.firstOrNull { it.first.containsMatchIn(text) }?.second ?: return null
        val layout = channelLayout.find(text)
        val channels = layout?.let { it.groupValues[1].toInt() + it.groupValues[2].toInt() }
        return AudioStream(codec = codec, channels = channels, atmos = atmos.containsMatchIn(text))
    }
}
