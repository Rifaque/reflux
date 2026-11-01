package dev.reflux.core.playback

/**
 * What is known of something this client may or may not be able to do.
 *
 * [UNKNOWN] is an answer, not a missing one: it does not mean supported and it does not mean unsupported. Whoever
 * reads it decides what to do about not knowing (docs/PLAYBACK.md, section 6).
 */
enum class Support {
    SUPPORTED,
    UNSUPPORTED,
    UNKNOWN;

    companion object {
        /** The best of several ways of doing one thing: any that works is enough, and only all failing is a failure. */
        fun best(options: Iterable<Support>): Support = when {
            SUPPORTED in options -> SUPPORTED
            UNKNOWN in options -> UNKNOWN
            else -> UNSUPPORTED
        }
    }
}

/** A limit a video stream goes past on a decoder. */
enum class VideoLimit { CODEC, FRAME_SIZE, BIT_DEPTH, PROFILE, LEVEL, FRAME_RATE }

/** Can this client decode a video stream, and how. [limits] says why not, when it cannot. */
data class VideoCapability(
    val decode: Support,
    val hardware: Support,
    val software: Support,
    val limits: Set<VideoLimit> = emptySet(),
)

data class AudioCapability(
    val decode: Support,
    val passthrough: Support,
    /** Whether the output takes this many channels. [Support.UNSUPPORTED] is a downmix, which still plays. */
    val channels: Support,
    /** Whether Atmos objects survive; only asked of a track that has them. */
    val atmos: Support,
) {
    /** Sound will come out, one way or the other. */
    val plays: Support get() = Support.best(listOf(decode, passthrough))
}

data class SubtitleCapability(
    val render: Support,
    /** Only for what renders: how much of the format's styling survives. Null when that is not known. */
    val fidelity: SubtitleFidelity?,
    val bitmap: Boolean,
)

data class ContainerCapability(val demux: Support, val seek: Support)

data class DisplayCapability(
    /** This range reaches the display as it is. */
    val show: Support,
    /** Otherwise, the playback stack can tone-map it. */
    val toneMap: Support,
)

/** One stream's answers together. */
data class StreamCapability(
    val container: ContainerCapability,
    val video: VideoCapability?,
    val display: DisplayCapability?,
    val audio: List<AudioCapability>,
    val subtitles: List<SubtitleCapability>,
)

/**
 * The one place that compares media facts with what a client can do. Pure: it holds no decisions, and what to do
 * with an answer belongs to whoever asks. [PlaybackAssessor] and Best Version read their compatibility from here.
 *
 * A fact the media does not state, or a limit the client does not declare, gives [Support.UNKNOWN]. A limit gone
 * past is [Support.UNSUPPORTED], and decides even where other things are unknown.
 */
object Capabilities {
    fun of(stream: StreamInfo, device: DeviceCapabilities): StreamCapability = StreamCapability(
        container = container(stream.container, device),
        video = stream.video?.let { video(it, device) },
        display = stream.video?.let { display(it, device) },
        audio = stream.audio.map { audio(it, device) },
        subtitles = stream.subtitles.map { subtitle(it.format, external = false, device) },
    )

    fun video(video: VideoStream, device: DeviceCapabilities): VideoCapability {
        if (video.codec == VideoCodec.UNKNOWN) return VideoCapability(Support.UNKNOWN, Support.UNKNOWN, Support.UNKNOWN)
        val decoders = device.videoDecoders.filter { it.codec == video.codec }
        if (decoders.isEmpty()) return VideoCapability(Support.UNSUPPORTED, Support.UNSUPPORTED, Support.UNSUPPORTED, setOf(VideoLimit.CODEC))
        val fits = decoders.map { it to it.fit(video) }
        // A decoder that may be either counts as an unknown for both, unless the stream is past it anyway.
        val maybe = fits.filter { (decoder, _) -> decoder.hardware == null }.map { (_, fit) -> if (fit.support == Support.UNSUPPORTED) Support.UNSUPPORTED else Support.UNKNOWN }
        val hardware = Support.best(fits.filter { (decoder, _) -> decoder.hardware == true }.map { (_, fit) -> fit.support } + maybe)
        val software = Support.best(fits.filter { (decoder, _) -> decoder.hardware == false }.map { (_, fit) -> fit.support } + maybe)
        val decode = Support.best(listOf(hardware, software))
        return VideoCapability(decode, hardware, software, if (decode == Support.UNSUPPORTED) fits.flatMap { (_, fit) -> fit.limits }.toSet() else emptySet())
    }

    fun audio(audio: AudioStream, device: DeviceCapabilities): AudioCapability {
        if (audio.codec == AudioCodec.UNKNOWN) return AudioCapability(Support.UNKNOWN, Support.UNKNOWN, Support.UNKNOWN, Support.UNKNOWN)
        val passthrough = when {
            audio.codec in device.audioPassthrough -> Support.SUPPORTED
            device.audioPassthroughKnown -> Support.UNSUPPORTED
            else -> Support.UNKNOWN
        }
        val channels = audio.channels
        return AudioCapability(
            decode = decode(audio, device),
            passthrough = passthrough,
            channels = when {
                channels == null -> Support.UNKNOWN
                passthrough == Support.SUPPORTED || channels <= device.maxOutputChannels -> Support.SUPPORTED
                device.maxOutputChannelsKnown -> Support.UNSUPPORTED
                else -> Support.UNKNOWN
            },
            atmos = when {
                !audio.atmos -> Support.UNSUPPORTED
                passthrough == Support.UNSUPPORTED -> Support.UNSUPPORTED
                // TrueHD carries its objects in what passes through; on E-AC-3 they are a separate ability.
                audio.codec == AudioCodec.EAC3 -> device.atmosPassthrough
                else -> passthrough
            },
        )
    }

    private fun decode(audio: AudioStream, device: DeviceCapabilities): Support {
        if (audio.codec !in device.audioDecoders) return Support.UNSUPPORTED
        val limit = device.audioDecoderLimits[audio.codec] ?: return Support.SUPPORTED
        val pastChannels = audio.channels != null && limit.maxChannels != null && audio.channels > limit.maxChannels
        val pastRate = audio.sampleRate != null && limit.maxSampleRate != null && audio.sampleRate > limit.maxSampleRate
        return if (pastChannels || pastRate) Support.UNSUPPORTED else Support.SUPPORTED
    }

    /** @param external a file beside the stream, rather than a track inside it. */
    fun subtitle(format: SubtitleFormat, external: Boolean, device: DeviceCapabilities): SubtitleCapability {
        val renderer = device.subtitles.renderers[format]
        val render = when {
            renderer == null -> Support.UNKNOWN
            external -> renderer.external
            else -> renderer.embedded
        }
        return SubtitleCapability(render, renderer?.fidelity?.takeIf { render != Support.UNSUPPORTED }, format.bitmap)
    }

    fun container(container: Container, device: DeviceCapabilities): ContainerCapability = when {
        container == Container.UNKNOWN -> ContainerCapability(Support.UNKNOWN, Support.UNKNOWN)
        container !in device.containers -> ContainerCapability(Support.UNSUPPORTED, Support.UNSUPPORTED)
        else -> ContainerCapability(Support.SUPPORTED, device.containerSeek[container] ?: Support.UNKNOWN)
    }

    fun display(video: VideoStream, device: DeviceCapabilities): DisplayCapability {
        val display = device.display
        val range = video.dynamicRange
        val toneMap = if (display.canToneMap) Support.SUPPORTED else if (display.dynamicRangesKnown) Support.UNSUPPORTED else Support.UNKNOWN
        val show = when {
            range == DynamicRange.SDR -> Support.SUPPORTED
            range !in display.dynamicRanges -> if (display.dynamicRangesKnown) Support.UNSUPPORTED else Support.UNKNOWN
            range == DynamicRange.DOLBY_VISION -> dolbyVision(video, device)
            else -> Support.SUPPORTED
        }
        return DisplayCapability(show, toneMap)
    }

    /** A display showing Dolby Vision is not a device decoding it: the two are read separately. */
    private fun dolbyVision(video: VideoStream, device: DeviceCapabilities): Support {
        val profiles = device.dolbyVisionProfiles ?: return Support.UNKNOWN
        val profile = video.dolbyVisionProfile ?: return if (profiles.isEmpty()) Support.UNSUPPORTED else Support.UNKNOWN
        return if (profile in profiles) Support.SUPPORTED else Support.UNSUPPORTED
    }
}

/** What one decoder makes of a stream. */
internal class DecoderFit(val support: Support, val limits: Set<VideoLimit>)

internal fun VideoDecoderSupport.fit(video: VideoStream): DecoderFit {
    val limits = mutableSetOf<VideoLimit>()
    var unknown = false
    val width = video.width
    val height = video.height
    when {
        width == null || height == null -> unknown = true
        // Portrait frames fit a decoder the other way round.
        !((width <= maxWidth && height <= maxHeight) || (width <= maxHeight && height <= maxWidth)) -> limits += VideoLimit.FRAME_SIZE
    }
    val depth = video.bitDepth
    if (depth == null) unknown = true else if (depth > maxBitDepth) limits += VideoLimit.BIT_DEPTH
    if (!frameAndDepthOnly) {
        val profile = video.profile
        val levels = profiles
        when {
            profile == null || levels == null -> unknown = true
            profile !in levels -> limits += VideoLimit.PROFILE
            else -> {
                val most = levels[profile]
                val level = video.level
                if (most == null || level == null) unknown = true else if (level > most) limits += VideoLimit.LEVEL
            }
        }
        val rate = video.frameRate
        val most = frameRates?.filter { width != null && height != null && width <= it.width && height <= it.height }?.minByOrNull { it.width.toLong() * it.height }
        if (rate == null || most == null) unknown = true else if (rate > most.maxFrameRate + FRAME_RATE_MARGIN) limits += VideoLimit.FRAME_RATE
    }
    return DecoderFit(if (limits.isNotEmpty()) Support.UNSUPPORTED else if (unknown) Support.UNKNOWN else Support.SUPPORTED, limits)
}

/** 59.94 and 60 are one frame rate as far as a decoder's limits go. */
private const val FRAME_RATE_MARGIN = 0.5
