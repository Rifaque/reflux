package dev.reflux.core.playback

/**
 * How media reached the player, from what the source says it did. Not a plan and not a capability: this is the
 * truth layer under them (docs/PLAYBACK.md). Whether something is decoded in hardware is another question.
 */
enum class DeliveryMode {
    /** The original file, unchanged. */
    DIRECT_PLAY,

    /** Original video and audio in another container. */
    REMUX,

    /** Original video; only the audio was converted. */
    AUDIO_CONVERSION,

    /** The video was converted; the audio may or may not have been. */
    VIDEO_TRANSCODE,

    /** Nothing shows which. Requesting the original is not evidence that it is what happened. */
    UNKNOWN,
}

/** What became of one original stream. */
enum class StreamFate { PRESERVED, CONVERTED, UNKNOWN }

/** How one subtitle track reaches the screen, as far as the source can say. */
enum class SubtitleHandling {
    /** Left in the stream for the player to draw, because a client profile said it can. */
    LOCAL_RENDER,

    /** Delivered beside the stream, as its own file. */
    EXTERNAL,

    /** The server draws it into the picture, which means converting the video. */
    SERVER_BURN_IN,

    /** Nothing says this client can draw it. A track being in a file is not evidence that it can. */
    CAPABILITY_UNKNOWN,
}

/** One subtitle track of what is playing. [index] is the source's own number for it. */
data class SubtitleEvidence(val index: Int, val format: String?, val handling: SubtitleHandling)

/**
 * What a source says happened to a playback, for diagnostics and tests. Each answer is only as good as its
 * evidence: what could not be observed stays [DeliveryMode.UNKNOWN] and [StreamFate.UNKNOWN].
 */
data class PlaybackEvidence(
    /** What Reflux asked for, in a developer's words. */
    val requested: String,
    /** What the source answered, in a developer's words; null when it gave no answer. */
    val answered: String?,
    val delivery: DeliveryMode,
    val video: StreamFate,
    val audio: StreamFate,
    val subtitles: List<SubtitleEvidence> = emptyList(),
    /** A subtitle is why the video is being converted. */
    val subtitleTriggeredConversion: Boolean = false,
    /** The source's own reasons for any conversion. */
    val reasons: List<String> = emptyList(),
    /** Whether the source accepted Reflux's credentials; null when that was not tested. */
    val authenticated: Boolean? = null,
) {
    /** One line for the diagnostics log: no addresses, no credentials, nothing about the media but its formats. */
    fun summary(): String = buildString {
        append("requested ").append(requested)
        append("; answered ").append(answered ?: "nothing")
        append("; delivery ").append(delivery)
        append(", video ").append(video)
        append(", audio ").append(audio)
        append(", authenticated ").append(authenticated ?: "not tested")
        if (reasons.isNotEmpty()) append("; reasons ").append(reasons.joinToString(" "))
        if (subtitleTriggeredConversion) append("; a subtitle caused the conversion")
        if (subtitles.isNotEmpty()) {
            append("; subtitles ").append(subtitles.joinToString(" ") { "${it.index}:${it.format ?: "?"}:${it.handling}" })
        }
    }
}
