package dev.reflux.core.playback

import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.StateFlow

enum class PlayerStatus { IDLE, LOADING, BUFFERING, PLAYING, PAUSED, ENDED, ERROR }

enum class TrackType { VIDEO, AUDIO, SUBTITLE }

/** A track as reported by a playback engine. [id] is engine-specific and opaque to product logic. */
data class PlayerTrack(
    val id: String,
    val type: TrackType,
    val language: String? = null,
    val title: String? = null,
    val codec: String? = null,
    val channels: Int? = null,
    val default: Boolean = false,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
    val external: Boolean = false,
)

data class PlayerState(
    val status: PlayerStatus = PlayerStatus.IDLE,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val speed: Double = 1.0,
    /** 0..100. */
    val volume: Int = 100,
    val muted: Boolean = false,
    val tracks: List<PlayerTrack> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    val error: String? = null,
) {
    val audioTracks: List<PlayerTrack> get() = tracks.filter { it.type == TrackType.AUDIO }
    val subtitleTracks: List<PlayerTrack> get() = tracks.filter { it.type == TrackType.SUBTITLE }
}

/** An external subtitle file to attach when a version is loaded. */
data class SubtitleAttachment(
    val target: PlaybackTarget,
    val language: String?,
    val forced: Boolean = false,
    val hearingImpaired: Boolean = false,
)

data class PlaybackRequest(
    val target: PlaybackTarget,
    val title: String,
    val startPositionMs: Long? = null,
    val subtitles: List<SubtitleAttachment> = emptyList(),
)

/**
 * The contract every playback engine implements (Media3 on Android, libmpv on desktop).
 *
 * Commands are asynchronous: they return immediately and their effect appears in [state].
 */
interface Player : AutoCloseable {
    val state: StateFlow<PlayerState>

    /** What this engine can play on this device. */
    val capabilities: DeviceCapabilities

    fun load(request: PlaybackRequest)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Double)
    fun setVolume(volume: Int)
    fun setMuted(muted: Boolean)
    fun selectAudio(trackId: String)

    /** Selects a subtitle track, or turns subtitles off with null. */
    fun selectSubtitle(trackId: String?)

    /** Stops playback and unloads the current media. The player can be reused. */
    fun stop()
}
