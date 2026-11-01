package dev.reflux.sources.jellyfin

import dev.reflux.core.net.Redaction
import dev.reflux.core.playback.DeliveryMode
import dev.reflux.core.playback.PlaybackEvidence
import dev.reflux.core.playback.StreamFate
import dev.reflux.core.playback.SubtitleEvidence
import dev.reflux.core.playback.SubtitleHandling
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * What Reflux asks of `Items/{id}/PlaybackInfo`.
 *
 * The default is what Reflux does today and no more: it takes the file as it is, and it claims no subtitle format,
 * because nothing yet says what this client can draw. This is not capability negotiation (docs/PLAYBACK.md,
 * section 8); [deviceProfile] is a parameter so that tests can see what a server does with other clients.
 *
 * @param deviceProfile Jellyfin's `DeviceProfile`, as JSON.
 * @param subtitleStreamIndex -1 for none: Reflux never asks a server to deliver a subtitle in a stream.
 */
class JellyfinPlaybackAsk(
    val deviceProfile: String = ORIGINAL_ONLY,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int = -1,
    val allowConversion: Boolean = false,
    /** Whether the original file may be delivered at all. A plan to convert says no. */
    val allowDirectPlay: Boolean = true,
    val copyVideo: Boolean = true,
    val copyAudio: Boolean = true,
    val maxBitrate: Long = UNLIMITED_BITRATE,
) {
    companion object {
        const val ORIGINAL_ONLY: String =
            """{"DirectPlayProfiles":[{"Type":"Video"}],"TranscodingProfiles":[],"CodecProfiles":[],"SubtitleProfiles":[]}"""

        /** Nothing limits a stream unless a server's own rule does. */
        const val UNLIMITED_BITRATE: Long = 1_000_000_000
    }
}

/** One subtitle track as `PlaybackInfo` describes it. [method] is its `DeliveryMethod`. */
data class JellyfinSubtitleTrack(val index: Int, val codec: String?, val method: String?)

/**
 * The server's answer to a [JellyfinPlaybackAsk]: what it would do, which is not what it did.
 *
 * Its flags are kept to be reported and never to be classified from. `SupportsDirectStream` was false on a real
 * server for a stream whose video and audio it then copied. [transcodingUrl] carries the server's key, so
 * this class prints without it.
 */
class JellyfinPlaybackAnswer(
    val playSessionId: String?,
    val supportsDirectPlay: Boolean,
    val supportsDirectStream: Boolean,
    val supportsTranscoding: Boolean,
    /** Relative to the server. Null when there is nothing to convert. */
    val transcodingUrl: String?,
    val subtitles: List<JellyfinSubtitleTrack>,
) {
    private val query: Map<String, String> = transcodingUrl?.substringAfter('?', "").orEmpty().split('&')
        .filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }

    /** Why the server would convert. Only its URL says: the JSON field was absent on a real server. */
    val reasons: List<String> = query["TranscodeReasons"]?.replace("%2C", ",")?.split(',')?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * The subtitle the server plans to draw into the picture. `SubtitleMethod=Encode` alone says nothing: a real
     * server writes it on URLs with no subtitle selected.
     */
    val burnInIndex: Int? = query["SubtitleStreamIndex"]?.toIntOrNull()?.takeIf { it >= 0 && query["SubtitleMethod"] == "Encode" }

    /** One line for diagnostics, without the URL and its key. */
    fun summary(): String = Redaction.redact(
        "SupportsDirectPlay=$supportsDirectPlay SupportsDirectStream=$supportsDirectStream SupportsTranscoding=$supportsTranscoding" +
            " url=${transcodingUrl?.substringBefore('?')?.substringAfterLast('/') ?: "none"}",
    )

    override fun toString(): String = "JellyfinPlaybackAnswer(${summary()})"

    companion object {
        internal fun parse(json: JsonObject, mediaSourceId: String): JellyfinPlaybackAnswer? {
            val sources = json.array("MediaSources")
            val source = sources.firstOrNull { it.string("Id") == mediaSourceId } ?: sources.firstOrNull() ?: return null
            return JellyfinPlaybackAnswer(
                playSessionId = json.string("PlaySessionId"),
                supportsDirectPlay = source.bool("SupportsDirectPlay"),
                supportsDirectStream = source.bool("SupportsDirectStream"),
                supportsTranscoding = source.bool("SupportsTranscoding"),
                transcodingUrl = source.string("TranscodingUrl"),
                subtitles = source.array("MediaStreams").filter { it.string("Type") == "Subtitle" }.mapNotNull { stream ->
                    stream.int("Index")?.let { JellyfinSubtitleTrack(it, stream.string("Codec"), stream.string("DeliveryMethod")) }
                },
            )
        }
    }
}

/** `TranscodingInfo`: what the server is really doing. This, not any flag, is the evidence of a conversion. */
data class JellyfinTranscoding(
    val isVideoDirect: Boolean?,
    val isAudioDirect: Boolean?,
    val videoCodec: String?,
    val audioCodec: String?,
    val container: String?,
    val hardwareAcceleration: String?,
    val reasons: List<String>,
)

/** This device's session as the server lists it. */
data class JellyfinSessionFacts(
    val nowPlayingItemId: String?,
    /** Only what the client reported: the server repeats it, so it is not evidence of anything. */
    val playMethod: String?,
    val transcoding: JellyfinTranscoding?,
) {
    companion object {
        /** This device's entry in `GET Sessions`, the one with a playback if it has several. */
        internal fun parse(sessions: JsonElement, deviceId: String): JellyfinSessionFacts? {
            val mine = (sessions as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.filter { it.string("DeviceId") == deviceId }
            val session = mine.firstOrNull { it["TranscodingInfo"] is JsonObject } ?: mine.firstOrNull { it["NowPlayingItem"] is JsonObject } ?: mine.firstOrNull()
            return session?.let {
                JellyfinSessionFacts(
                    nowPlayingItemId = it.obj("NowPlayingItem")?.string("Id"),
                    playMethod = it.obj("PlayState")?.string("PlayMethod"),
                    transcoding = it.obj("TranscodingInfo")?.let(::transcoding),
                )
            }
        }

        private fun transcoding(json: JsonObject) = JellyfinTranscoding(
            isVideoDirect = json.flag("IsVideoDirect"),
            isAudioDirect = json.flag("IsAudioDirect"),
            videoCodec = json.string("VideoCodec"),
            audioCodec = json.string("AudioCodec"),
            container = json.string("Container"),
            hardwareAcceleration = json.string("HardwareAccelerationType"),
            reasons = (json["TranscodeReasons"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )

        private fun JsonObject.flag(key: String): Boolean? = if (key in this) bool(key) else null
    }
}

/**
 * Jellyfin's account of a playback in Reflux's terms. Pure, so that each mode has a test without a server.
 *
 * A conversion is read from `TranscodingInfo` alone. Direct Play has no such record, so it needs all of: the
 * original was requested, the server lists this device playing that item, and no conversion is listed. Anything
 * less stays unknown. The server's `Supports...` flags and the `PlayMethod` it repeats play no part.
 */
object JellyfinDelivery {
    fun modeOf(transcoding: JellyfinTranscoding): DeliveryMode = when {
        transcoding.isVideoDirect == null || transcoding.isAudioDirect == null -> DeliveryMode.UNKNOWN
        !transcoding.isVideoDirect -> DeliveryMode.VIDEO_TRANSCODE
        !transcoding.isAudioDirect -> DeliveryMode.AUDIO_CONVERSION
        else -> DeliveryMode.REMUX
    }

    fun modeOf(originalRequested: Boolean, itemId: String, session: JellyfinSessionFacts?): DeliveryMode {
        session?.transcoding?.let { return modeOf(it) }
        val playing = session?.nowPlayingItemId?.let { sameId(it, itemId) } == true
        return if (originalRequested && playing) DeliveryMode.DIRECT_PLAY else DeliveryMode.UNKNOWN
    }

    fun evidence(
        requested: String,
        originalRequested: Boolean,
        itemId: String,
        answer: JellyfinPlaybackAnswer?,
        session: JellyfinSessionFacts?,
        authenticated: Boolean?,
    ): PlaybackEvidence {
        val delivery = modeOf(originalRequested, itemId, session)
        val transcoding = session?.transcoding
        val video = when (delivery) {
            DeliveryMode.DIRECT_PLAY, DeliveryMode.REMUX, DeliveryMode.AUDIO_CONVERSION -> StreamFate.PRESERVED
            DeliveryMode.VIDEO_TRANSCODE -> StreamFate.CONVERTED
            DeliveryMode.UNKNOWN -> StreamFate.UNKNOWN
        }
        val audio = when {
            delivery == DeliveryMode.UNKNOWN -> StreamFate.UNKNOWN
            delivery == DeliveryMode.DIRECT_PLAY || transcoding?.isAudioDirect == true -> StreamFate.PRESERVED
            else -> StreamFate.CONVERTED
        }
        val reasons = (transcoding?.reasons.orEmpty() + answer?.reasons.orEmpty()).distinct()
        val burnIn = answer?.burnInIndex
        return PlaybackEvidence(
            requested = requested,
            answered = answer?.summary(),
            delivery = delivery,
            video = video,
            audio = audio,
            subtitles = answer?.subtitles.orEmpty().map { SubtitleEvidence(it.index, it.codec, handling(it, burnIn)) },
            subtitleTriggeredConversion = delivery == DeliveryMode.VIDEO_TRANSCODE && (burnIn != null || SUBTITLE_REASON in reasons),
            reasons = reasons,
            authenticated = authenticated,
        )
    }

    /**
     * `Encode` for a track nobody selected is only what the server would do if it were; it describes a track
     * this client made no claim about, not a burn-in.
     */
    private fun handling(track: JellyfinSubtitleTrack, burnIn: Int?): SubtitleHandling = when (track.method) {
        "Embed" -> SubtitleHandling.LOCAL_RENDER
        "External", "Hls" -> SubtitleHandling.EXTERNAL
        "Encode" -> if (track.index == burnIn) SubtitleHandling.SERVER_BURN_IN else SubtitleHandling.CAPABILITY_UNKNOWN
        else -> SubtitleHandling.CAPABILITY_UNKNOWN
    }

    /** The server writes one id with dashes in one answer and without them in another. */
    private fun sameId(a: String, b: String): Boolean = a.replace("-", "").equals(b.replace("-", ""), ignoreCase = true)

    private const val SUBTITLE_REASON = "SubtitleCodecNotSupported"
}
