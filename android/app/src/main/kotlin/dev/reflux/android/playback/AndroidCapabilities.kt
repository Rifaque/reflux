package dev.reflux.android.playback

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.view.Display
import dev.reflux.core.playback.AudioCodec
import dev.reflux.core.playback.Container
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.DisplayCapabilities
import dev.reflux.core.playback.DynamicRange
import dev.reflux.core.playback.PlaybackFeature
import dev.reflux.core.playback.SubtitleFormat
import dev.reflux.core.playback.VideoCodec
import dev.reflux.core.playback.VideoDecoderSupport

/** One decoder as the device lists it, reduced to what capabilities are derived from. */
data class DecoderDescription(
    val mimeType: String,
    val hardware: Boolean,
    val maxWidth: Int = 0,
    val maxHeight: Int = 0,
    val tenBit: Boolean = false,
)

/**
 * What this device can play, as the device itself reports it. Nothing is assumed from the Android version
 * or the model: a codec is listed when a decoder for it exists, with the frame size and bit depth that
 * decoder declares, and a display's HDR formats are the ones it reports.
 */
object AndroidCapabilities {
    /** What the playback engine's own extractors and renderers handle, independent of the device. */
    private val containers = setOf(
        Container.MATROSKA, Container.WEBM, Container.MP4, Container.QUICKTIME, Container.MPEG_TS, Container.MPEG_PS,
        Container.AVI, Container.FLV, Container.OGG,
    )
    private val subtitles = setOf(SubtitleFormat.SRT, SubtitleFormat.ASS, SubtitleFormat.WEBVTT, SubtitleFormat.TTML, SubtitleFormat.PGS, SubtitleFormat.DVB)

    private val videoCodecs = mapOf(
        "video/avc" to VideoCodec.H264,
        "video/hevc" to VideoCodec.HEVC,
        "video/av01" to VideoCodec.AV1,
        "video/x-vnd.on2.vp9" to VideoCodec.VP9,
        "video/x-vnd.on2.vp8" to VideoCodec.VP8,
        "video/mpeg2" to VideoCodec.MPEG2,
        "video/mp4v-es" to VideoCodec.MPEG4_PART2,
        "video/wvc1" to VideoCodec.VC1,
    )
    private val audioCodecs = mapOf(
        "audio/mp4a-latm" to AudioCodec.AAC,
        "audio/mpeg" to AudioCodec.MP3,
        "audio/ac3" to AudioCodec.AC3,
        "audio/eac3" to AudioCodec.EAC3,
        "audio/eac3-joc" to AudioCodec.EAC3,
        "audio/vnd.dts" to AudioCodec.DTS,
        "audio/vnd.dts.hd" to AudioCodec.DTS_HD_MA,
        "audio/true-hd" to AudioCodec.TRUEHD,
        "audio/flac" to AudioCodec.FLAC,
        "audio/alac" to AudioCodec.ALAC,
        "audio/raw" to AudioCodec.PCM,
        "audio/opus" to AudioCodec.OPUS,
        "audio/vorbis" to AudioCodec.VORBIS,
    )

    fun of(decoders: List<DecoderDescription>, display: DisplayCapabilities, pictureInPicture: Boolean): DeviceCapabilities = DeviceCapabilities(
        videoDecoders = decoders.mapNotNull { decoder ->
            val codec = videoCodecs[decoder.mimeType.lowercase()] ?: return@mapNotNull null
            if (decoder.maxWidth <= 0 || decoder.maxHeight <= 0) return@mapNotNull null
            VideoDecoderSupport(codec, decoder.maxWidth, decoder.maxHeight, if (decoder.tenBit) 10 else 8, decoder.hardware)
        }.distinct(),
        // Uncompressed audio needs no decoder.
        audioDecoders = decoders.mapNotNull { audioCodecs[it.mimeType.lowercase()] }.toSet() + AudioCodec.PCM,
        // A phone's own speakers and most headphones are stereo; more is only claimed once the output is known.
        maxOutputChannels = 2,
        display = display,
        containers = containers,
        subtitleFormats = subtitles,
        // Playing on with the screen off or the app gone is not built, so it is not claimed.
        features = setOfNotNull(
            PlaybackFeature.EXTERNAL_SUBTITLES,
            PlaybackFeature.NETWORK_STREAMING,
            PlaybackFeature.PICTURE_IN_PICTURE.takeIf { pictureInPicture },
        ),
    )

    fun of(context: Context): DeviceCapabilities =
        of(decoders(), display(context), context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))

    private fun decoders(): List<DecoderDescription> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
        .filter { !it.isEncoder }
        .flatMap { info ->
            info.supportedTypes.mapNotNull { type ->
                val capabilities = runCatching { info.getCapabilitiesForType(type) }.getOrNull() ?: return@mapNotNull null
                val video = capabilities.videoCapabilities
                DecoderDescription(
                    mimeType = type,
                    hardware = isHardware(info),
                    maxWidth = video?.supportedWidths?.upper ?: 0,
                    maxHeight = video?.supportedHeights?.upper ?: 0,
                    tenBit = capabilities.profileLevels.any { isTenBit(type, it.profile) },
                )
            }
        }

    private fun isHardware(info: MediaCodecInfo): Boolean =
        if (Build.VERSION.SDK_INT >= 29) info.isHardwareAccelerated
        else !info.name.startsWith("OMX.google.", ignoreCase = true) && !info.name.startsWith("c2.android.", ignoreCase = true)

    private fun isTenBit(mimeType: String, profile: Int): Boolean = when (mimeType.lowercase()) {
        "video/hevc" -> profile == CodecProfileLevel.HEVCProfileMain10 || profile == CodecProfileLevel.HEVCProfileMain10HDR10 ||
            profile == CodecProfileLevel.HEVCProfileMain10HDR10Plus
        "video/x-vnd.on2.vp9" -> profile == CodecProfileLevel.VP9Profile2 || profile == CodecProfileLevel.VP9Profile2HDR ||
            profile == CodecProfileLevel.VP9Profile3
        "video/av01" -> Build.VERSION.SDK_INT >= 29 && (
            profile == CodecProfileLevel.AV1ProfileMain10 || profile == CodecProfileLevel.AV1ProfileMain10HDR10 ||
                profile == CodecProfileLevel.AV1ProfileMain10HDR10Plus
            )
        "video/avc" -> profile == CodecProfileLevel.AVCProfileHigh10
        else -> false
    }

    @Suppress("DEPRECATION")
    private fun display(context: Context): DisplayCapabilities {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return DisplayCapabilities(1920, 1080)
        val modes = display.supportedModes.ifEmpty { arrayOf(display.mode) }
        val width = modes.maxOf { maxOf(it.physicalWidth, it.physicalHeight) }
        val height = modes.maxOf { minOf(it.physicalWidth, it.physicalHeight) }
        val types = if (Build.VERSION.SDK_INT >= 34) display.mode.supportedHdrTypes else display.hdrCapabilities?.supportedHdrTypes ?: IntArray(0)
        val ranges = types.toList().mapNotNull {
            when (it) {
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> DynamicRange.HDR10
                Display.HdrCapabilities.HDR_TYPE_HLG -> DynamicRange.HLG
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> DynamicRange.HDR10_PLUS
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> DynamicRange.DOLBY_VISION
                else -> null
            }
        }
        // Whether HDR is tone-mapped well on an SDR panel differs by device and is not reported, so it is not claimed.
        return DisplayCapabilities(width, height, ranges.toSet() + DynamicRange.SDR, canToneMap = false)
    }
}
