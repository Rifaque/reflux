package dev.reflux.core.playback

import dev.reflux.core.source.PlaybackTarget
import kotlinx.coroutines.flow.StateFlow

/**
 * What a player is doing. [BUFFERING] is playback that wants to run and is waiting for data or for a seek to
 * land; a paused player is [PAUSED] even while it seeks, so the status always says what Play/Pause would do.
 */
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

/** Why playback failed, in terms the player UI can explain. The engine's own wording goes in [PlayerState.error]. */
enum class PlaybackFailure {
    /** The media could not be opened: it is missing, was moved, cannot be read, or its source went away. */
    UNREACHABLE,

    /** The media was opened but is not something this engine can play. */
    UNSUPPORTED,

    /** The engine could not start video or audio output on this device. */
    OUTPUT,

    UNKNOWN,
}

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
    val chapters: List<Chapter> = emptyList(),
    /** The codecs of audio tracks the engine found and cannot decode here. They are not in [tracks]. */
    val unsupportedAudioCodecs: List<String> = emptyList(),
    /** Set with [PlayerStatus.ERROR]. */
    val failure: PlaybackFailure? = null,
    /** The engine's description of [failure], for diagnostics rather than for display. */
    val error: String? = null,
) {
    val audioTracks: List<PlayerTrack> get() = tracks.filter { it.type == TrackType.AUDIO }
    val subtitleTracks: List<PlayerTrack> get() = tracks.filter { it.type == TrackType.SUBTITLE }

    /**
     * The media has sound and none of it is playing: picture without audio, which no engine counts as a failure.
     * Reflux has no way to turn audio off, so no track in use means none could be used.
     */
    val audioUnavailable: Boolean
        get() = (status == PlayerStatus.PLAYING || status == PlayerStatus.PAUSED) && selectedAudioId == null &&
            (audioTracks.isNotEmpty() || unsupportedAudioCodecs.isNotEmpty())
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

    /**
     * Keeps subtitles above the bottom [fraction] (0–1) of the window, where the player's controls are
     * showing; 0 returns them to their usual place. An engine that cannot move its subtitles ignores it.
     */
    fun setSubtitleClearance(fraction: Float) {}

    /** Stops playback and unloads the current media. The player can be reused. */
    fun stop()
}
