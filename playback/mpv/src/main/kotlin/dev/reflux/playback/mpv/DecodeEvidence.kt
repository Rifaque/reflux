package dev.reflux.playback.mpv

import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One question about hardware decoding: a codec at a bit depth (8, 10, or 12). */
data class DecodeKey(val codec: VideoCodec, val bitDepth: Int)

/** What has been seen of hardware decoding for one [DecodeKey] on this machine. */
sealed interface HardwareDecode {
    /** The hardware decoded frames this large. Larger ones are not known to fit. */
    data class Yes(val maxWidth: Int, val maxHeight: Int) : HardwareDecode

    /** The engine decoded it in software, although hardware decoding was asked for. */
    data object No : HardwareDecode
}

/** A playback, as the engine reported it once its first frame was decoded. */
data class DecodeObservation(val codec: VideoCodec, val bitDepth: Int, val width: Int, val height: Int, val hardware: Boolean)

/**
 * What this machine's playback engine is known to decode, and how: the software decoders the engine was built
 * with, and for each codec and bit depth whether the hardware decoded it, from the probe at start
 * ([MpvDecodeProbe]) and from real playbacks since. What has not been seen is unknown, and stays so: it is
 * neither counted against a version nor for it (`VideoDecoderSupport.hardware` null).
 *
 * @param software null when the engine was not asked; every codec is then taken to have a software decoder,
 *   as libmpv's FFmpeg has for all of them.
 */
data class DecodeEvidence(val software: Set<VideoCodec>?, val hardware: Map<DecodeKey, HardwareDecode>) {
    /** The decoders version selection sees. */
    fun decoders(): List<VideoDecoderSupport> = CODECS.flatMap { codec ->
        buildList {
            if (software == null || codec in software) add(VideoDecoderSupport(codec, MAX_WIDTH, MAX_HEIGHT, maxBitDepth = 12, hardware = false))
            var frame: HardwareDecode.Yes? = null
            for (depth in DEPTHS) {
                when (val known = hardware[DecodeKey(codec, depth)]) {
                    is HardwareDecode.Yes -> {
                        frame = known
                        add(VideoDecoderSupport(codec, known.maxWidth, known.maxHeight, maxBitDepth = depth, hardware = true))
                    }
                    // A decoder without this depth has none deeper either.
                    HardwareDecode.No -> break
                    null -> add(VideoDecoderSupport(codec, frame?.maxWidth ?: MAX_WIDTH, frame?.maxHeight ?: MAX_HEIGHT, maxBitDepth = depth, hardware = null))
                }
            }
        }
    }

    /**
     * With what a real playback showed. A playback decoded in hardware proves it, at least that large; one
     * decoded in software at a size the hardware was believed to take disproves it for that codec and depth.
     * Real playback is the better witness: the probe asks for hardware decoding with a copy back to memory,
     * playback for the engine's own output path.
     */
    fun with(observation: DecodeObservation): DecodeEvidence {
        val key = DecodeKey(observation.codec, depthClass(observation.bitDepth))
        val known = hardware[key]
        val next: HardwareDecode = if (observation.hardware) {
            val frame = known as? HardwareDecode.Yes
            if (frame != null && frame.maxWidth.toLong() * frame.maxHeight >= observation.width.toLong() * observation.height) frame
            else HardwareDecode.Yes(observation.width, observation.height)
        } else {
            val frame = known as? HardwareDecode.Yes
            if (frame != null && !fits(frame, observation.width, observation.height)) frame else HardwareDecode.No
        }
        return if (next == known) this else copy(hardware = hardware + (key to next))
    }

    /** One line for the diagnostics log: what is known, in a developer's words, and nothing about the machine. */
    fun summary(): String {
        val parts = hardware.entries.sortedWith(compareBy({ it.key.codec.ordinal }, { it.key.bitDepth })).map { (key, value) ->
            "${key.codec} ${key.bitDepth}-bit " + when (value) {
                is HardwareDecode.Yes -> "hardware up to ${value.maxWidth}x${value.maxHeight}"
                HardwareDecode.No -> "software"
            }
        }
        return (if (parts.isEmpty()) "nothing known" else parts.joinToString(", ")) +
            (software?.let { "; software decoders: ${it.sortedBy { c -> c.ordinal }.joinToString(" ")}" } ?: "; software decoders not read")
    }

    companion object {
        val UNKNOWN = DecodeEvidence(software = null, hardware = emptyMap())

        private const val MAX_WIDTH = 8192
        private const val MAX_HEIGHT = 4320
        private val DEPTHS = listOf(8, 10, 12)
        private val CODECS = VideoCodec.entries.filter { it != VideoCodec.UNKNOWN }

        fun depthClass(bitDepth: Int): Int = when {
            bitDepth <= 8 -> 8
            bitDepth <= 10 -> 10
            else -> 12
        }

        private fun fits(frame: HardwareDecode.Yes, width: Int, height: Int) =
            (width <= frame.maxWidth && height <= frame.maxHeight) || (width <= frame.maxHeight && height <= frame.maxWidth)

        /** The codec an engine's codec name stands for (`h264`, `hevc`, `av1`, ...), or null. */
        fun codecOf(name: String?): VideoCodec? = when (name?.lowercase()) {
            "h264" -> VideoCodec.H264
            "hevc" -> VideoCodec.HEVC
            "av1" -> VideoCodec.AV1
            "vp9" -> VideoCodec.VP9
            "vp8" -> VideoCodec.VP8
            "mpeg2video" -> VideoCodec.MPEG2
            "mpeg4", "msmpeg4v3" -> VideoCodec.MPEG4_PART2
            "vc1", "wmv3" -> VideoCodec.VC1
            else -> null
        }

        /**
         * The codecs mpv's `decoder-list` (JSON: codec, driver, description) can decode in software. Drivers
         * that are hardware wrappers do not count, nor FFmpeg's own `av1` decoder, which decodes only
         * through hardware acceleration.
         */
        fun softwareCodecs(decoderList: String?): Set<VideoCodec>? {
            val entries = runCatching { Json.parseToJsonElement(decoderList ?: return null) as? JsonArray }.getOrNull() ?: return null
            return entries.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val driver = (obj["driver"] as? JsonPrimitive)?.contentOrNull.orEmpty()
                if (HARDWARE_DRIVERS.any { driver.endsWith(it) } || driver == "av1") return@mapNotNull null
                codecOf((obj["codec"] as? JsonPrimitive)?.contentOrNull)
            }.toSet()
        }

        private val HARDWARE_DRIVERS = listOf("_cuvid", "_qsv", "_mediacodec", "_v4l2m2m", "_mmal", "_rkmpp", "_amf", "_vulkan", "_mf", "_ohcodec")

        /**
         * What a playback showed, from the engine's properties once its first frame was decoded: the decoder in
         * use, the codec, the frame, and the decoded pixel format (a hardware frame's underlying one first).
         * Null when one of them is missing, the codec is not one Reflux knows, or the chroma is not 4:2:0: a
         * hardware decoder may refuse 4:2:2 or 4:4:4 that it would take at 4:2:0, and that says nothing about
         * the usual case.
         */
        fun observation(hwdecCurrent: String?, codec: String?, width: Int?, height: Int?, hwPixelFormat: String?, pixelFormat: String?): DecodeObservation? {
            val known = codecOf(codec) ?: return null
            val (depth, yuv420) = pixelFormat(hwPixelFormat?.takeIf { it.isNotBlank() } ?: pixelFormat) ?: return null
            if (!yuv420 || width == null || height == null || width <= 0 || height <= 0) return null
            return DecodeObservation(known, depth, width, height, MpvDecodeProbe.hardwareInUse(hwdecCurrent))
        }

        /**
         * Bit depth and whether chroma is 4:2:0, from the decoded frames' pixel format: a hardware frame's
         * underlying format (`nv12`, `p010`) or a software one (`yuv420p10`). Null when it says neither.
         */
        fun pixelFormat(format: String?): Pair<Int, Boolean>? {
            val name = format?.lowercase()?.removeSuffix("le")?.removeSuffix("be") ?: return null
            return when {
                name == "nv12" -> 8 to true
                name == "p010" -> 10 to true
                name == "p012" -> 12 to true
                name == "p016" -> 16 to true
                name.startsWith("yuv420p") || name.startsWith("yuvj420p") -> (name.substringAfter("420p").toIntOrNull() ?: 8) to true
                name.startsWith("yuv422p") || name.startsWith("yuv444p") || name.startsWith("yuvj4") ->
                    (name.substringAfterLast('p').toIntOrNull() ?: 8) to false
                name.startsWith("nv16") || name.startsWith("nv24") || name.startsWith("p210") || name.startsWith("p410") || name.startsWith("y210") ->
                    (if ("10" in name) 10 else 8) to false
                name.startsWith("gray") -> (name.removePrefix("gray").toIntOrNull() ?: 8) to false
                else -> null
            }
        }
    }
}
