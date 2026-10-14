@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.reflux.android.playback

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import dev.reflux.core.playback.DeviceCapabilities
import dev.reflux.core.playback.PlaybackFailure
import dev.reflux.core.playback.PlaybackRequest
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.PlayerTrack
import dev.reflux.core.playback.TrackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import androidx.media3.common.Player as EnginePlayer

/**
 * Reflux's [Player] contract on AndroidX Media3 (ExoPlayer): MediaCodec decoding, in hardware where the
 * device has it, rendered straight to a [SurfaceView].
 *
 * The engine lives on the main thread. Commands may come from anywhere and return at once; their effect
 * shows in [state]. Subtitles are delivered as [cues] for the shell to draw, since Reflux brings its own
 * player interface rather than the engine's.
 *
 * While something is loaded the engine is also the device's media session, which is how the system's own
 * controls reach it: headset and Bluetooth buttons, and the buttons of a picture-in-picture window. They
 * act on this same engine, so there is one playback state however it is controlled. Next and previous
 * are not the engine's to decide (it holds one item) and go to [onSkip].
 */
class Media3Player(context: Context, override val capabilities: DeviceCapabilities) : Player {
    private val application = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(PlayerState())
    override val state: StateFlow<PlayerState> = mutableState.asStateFlow()

    private val mutableCues = MutableStateFlow<List<Cue>>(emptyList())

    /** The subtitle lines and images to show right now. */
    val cues: StateFlow<List<Cue>> = mutableCues.asStateFlow()

    private val mutableClearance = MutableStateFlow(0f)

    /** The bottom share of the window (0–1) that subtitle text keeps above, while the controls show there. */
    val subtitleClearance: StateFlow<Float> = mutableClearance.asStateFlow()

    override fun setSubtitleClearance(fraction: Float) {
        mutableClearance.value = fraction.coerceIn(0f, 1f)
    }

    private val mutableAspect = MutableStateFlow<Float?>(null)

    /** The shape of the picture (width over height) once known. The surface is laid out to it; the engine fills whatever it is given. */
    val videoAspect: StateFlow<Float?> = mutableAspect.asStateFlow()

    /** Called for the system's next (true) and previous (false) controls. Set by the shell. */
    @Volatile
    var onSkip: ((forward: Boolean) -> Unit)? = null

    private var session: MediaSession? = null
    private var engine: ExoPlayer? = null
    private var surface: SurfaceView? = null
    private var started = false
    private var closed = false
    private var volume = 100
    private var muted = false

    private val ticker = object : Runnable {
        override fun run() {
            publish()
            main.postDelayed(this, POSITION_INTERVAL_MS)
        }
    }

    private val listener = object : EnginePlayer.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == EnginePlayer.STATE_READY) started = true
            publish()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publish()
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onTracksChanged(tracks: Tracks) {
            // The picture's shape is in the track itself, before a single frame has been drawn.
            tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }?.let { group ->
                val format = (0 until group.length).firstOrNull(group::isTrackSelected)?.let(group::getTrackFormat)
                if (format != null && format.width > 0 && format.height > 0) {
                    mutableAspect.value = format.width * format.pixelWidthHeightRatio / format.height
                }
            }
            publish()
        }
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) = publish()
        override fun onPositionDiscontinuity(oldPosition: EnginePlayer.PositionInfo, newPosition: EnginePlayer.PositionInfo, reason: Int) = publish()

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) mutableAspect.value = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
        }

        override fun onCues(cueGroup: CueGroup) {
            mutableCues.value = cueGroup.cues
        }

        override fun onPlayerError(error: PlaybackException) {
            mutableState.update {
                it.copy(status = PlayerStatus.ERROR, failure = failureOf(error), error = "${error.errorCodeName}: ${error.message.orEmpty()}")
            }
        }
    }

    /** Shows the video on [view]; null detaches it. Safe to call before the first load. */
    fun attach(view: SurfaceView?) = onMain {
        surface = view
        engine?.setVideoSurfaceView(view)
    }

    override fun load(request: PlaybackRequest) {
        // Whoever asked sees at once that something is loading, whichever thread they are on.
        mutableState.update { PlayerState(status = PlayerStatus.LOADING, volume = it.volume, muted = it.muted, speed = it.speed) }
        onMain { open(request) }
    }

    private fun open(request: PlaybackRequest) {
        if (closed) return
        val player = engine ?: create().also { engine = it }
        started = false
        mutableCues.value = emptyList()
        mutableAspect.value = null

        val item = MediaItem.Builder()
            .setUri(request.target.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(request.title).build())
            .setSubtitleConfigurations(
                request.subtitles.mapIndexed { index, subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitle.target.uri))
                        .setId("external-$index")
                        .setMimeType(subtitleMimeType(subtitle.target.uri))
                        .setLanguage(subtitle.language)
                        .setSelectionFlags(if (subtitle.forced) C.SELECTION_FLAG_FORCED else 0)
                        .setRoleFlags(if (subtitle.hearingImpaired) C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND else 0)
                        .build()
                },
            )
            .build()
        // A source's own headers (a server's token) travel with every request for this item.
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(request.target.headers)
        val source = DefaultMediaSourceFactory(DefaultDataSource.Factory(application, http)).createMediaSource(item)
        val start = request.startPositionMs
        if (start != null && start > 0) player.setMediaSource(source, start) else player.setMediaSource(source)
        player.playWhenReady = true
        player.prepare()
    }

    override fun play() = onMain {
        val player = engine ?: return@onMain
        // Play at the end starts over, as on any player.
        if (player.playbackState == EnginePlayer.STATE_ENDED) player.seekTo(0)
        player.play()
    }

    override fun pause() = onMain { engine?.pause() }

    override fun seekTo(positionMs: Long) = onMain {
        engine?.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    override fun setSpeed(speed: Double) = onMain { engine?.setPlaybackSpeed(speed.toFloat()) }

    override fun setVolume(volume: Int) = onMain {
        this.volume = volume.coerceIn(0, 100)
        applyVolume()
    }

    override fun setMuted(muted: Boolean) = onMain {
        this.muted = muted
        applyVolume()
    }

    override fun selectAudio(trackId: String) = onMain { select(trackId, C.TRACK_TYPE_AUDIO) }

    override fun selectSubtitle(trackId: String?) = onMain {
        val player = engine ?: return@onMain
        if (trackId == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
            mutableCues.value = emptyList()
        } else {
            select(trackId, C.TRACK_TYPE_TEXT)
        }
        publish()
    }

    override fun stop() = onMain {
        engine?.stop()
        engine?.clearMediaItems()
        mutableCues.value = emptyList()
        mutableState.update { PlayerState(volume = it.volume, muted = it.muted) }
    }

    override fun close() = onMain {
        if (closed) return@onMain
        closed = true
        main.removeCallbacks(ticker)
        session?.release()
        session = null
        engine?.let {
            it.removeListener(listener)
            it.setVideoSurfaceView(null)
            it.release()
        }
        engine = null
        surface = null
        mutableCues.value = emptyList()
    }

    private fun create(): ExoPlayer = ExoPlayer.Builder(application)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        .setHandleAudioBecomingNoisy(true)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()
        .also { player ->
            player.addListener(listener)
            player.setVideoSurfaceView(surface)
            player.volume = if (muted) 0f else volume / 100f
            main.post(ticker)
            session = runCatching { MediaSession.Builder(application, SystemControls(player)).setId("reflux-${System.identityHashCode(this)}").build() }.getOrNull()
        }

    /** The engine as the system's controls see it: everything is the engine's, except moving between works. */
    private inner class SystemControls(player: ExoPlayer) : ForwardingPlayer(player) {
        private val skipping = intArrayOf(
            EnginePlayer.COMMAND_SEEK_TO_NEXT, EnginePlayer.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            EnginePlayer.COMMAND_SEEK_TO_PREVIOUS, EnginePlayer.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )

        override fun getAvailableCommands(): EnginePlayer.Commands = super.getAvailableCommands().buildUpon().addAll(*skipping).build()
        override fun isCommandAvailable(command: Int): Boolean = command in skipping || super.isCommandAvailable(command)
        override fun seekToNext() = skip(true)
        override fun seekToNextMediaItem() = skip(true)
        override fun seekToPrevious() = skip(false)
        override fun seekToPreviousMediaItem() = skip(false)

        private fun skip(forward: Boolean) {
            onSkip?.invoke(forward)
        }
    }

    private fun applyVolume() {
        engine?.volume = if (muted) 0f else volume / 100f
        mutableState.update { it.copy(volume = volume, muted = muted) }
    }

    private fun select(trackId: String, type: Int) {
        val player = engine ?: return
        val (groupIndex, trackIndex) = trackId.split(':').drop(1).mapNotNull(String::toIntOrNull).takeIf { it.size == 2 } ?: return
        val group = player.currentTracks.groups.getOrNull(groupIndex)?.takeIf { it.type == type } ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    /** Reads the engine's state into [state]. An error stays until the next load. */
    private fun publish() {
        val player = engine ?: return
        if (closed) return
        mutableState.update { previous ->
            if (previous.status == PlayerStatus.ERROR || previous.status == PlayerStatus.IDLE) return@update previous
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            val status = when (player.playbackState) {
                EnginePlayer.STATE_ENDED -> PlayerStatus.ENDED
                EnginePlayer.STATE_READY -> if (player.playWhenReady) PlayerStatus.PLAYING else PlayerStatus.PAUSED
                EnginePlayer.STATE_BUFFERING -> when {
                    !started -> PlayerStatus.LOADING
                    player.playWhenReady -> PlayerStatus.BUFFERING
                    else -> PlayerStatus.PAUSED
                }
                else -> previous.status
            }
            val tracks = tracksOf(player.currentTracks)
            previous.copy(
                status = status,
                positionMs = if (status == PlayerStatus.ENDED) duration ?: previous.positionMs else player.currentPosition.coerceAtLeast(0),
                durationMs = duration ?: previous.durationMs,
                speed = player.playbackParameters.speed.toDouble(),
                tracks = tracks.map { it.first },
                selectedAudioId = tracks.firstOrNull { it.second && it.first.type == TrackType.AUDIO }?.first?.id,
                selectedSubtitleId = tracks.firstOrNull { it.second && it.first.type == TrackType.SUBTITLE }?.first?.id,
            )
        }
    }

    /** Every audio and subtitle track with whether it is the one playing. */
    private fun tracksOf(tracks: Tracks): List<Pair<PlayerTrack, Boolean>> = tracks.groups.flatMapIndexed { groupIndex, group ->
        val type = when (group.type) {
            C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
            C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
            else -> return@flatMapIndexed emptyList()
        }
        (0 until group.length).filter { group.isTrackSupported(it) || type == TrackType.SUBTITLE }.map { trackIndex ->
            val format = group.getTrackFormat(trackIndex)
            PlayerTrack(
                id = "${if (type == TrackType.AUDIO) "a" else "s"}:$groupIndex:$trackIndex",
                type = type,
                language = format.language?.takeIf { it != "und" },
                title = format.label,
                codec = format.sampleMimeType ?: format.codecs,
                channels = format.channelCount.takeIf { it > 0 },
                default = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0,
                forced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0,
                hearingImpaired = format.roleFlags and (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or C.ROLE_FLAG_CAPTION) != 0,
                external = format.id?.contains("external-") == true,
            ) to group.isTrackSelected(trackIndex)
        }
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else main.post(action)
    }

    companion object {
        private const val POSITION_INTERVAL_MS = 500L

        /** Says what went wrong in the player's terms, from the engine's error code. */
        fun failureOf(error: PlaybackException): PlaybackFailure = when (error.errorCode) {
            in 2000..2999 -> PlaybackFailure.UNREACHABLE
            in 3000..4999 -> PlaybackFailure.UNSUPPORTED
            in 5000..5999 -> PlaybackFailure.OUTPUT
            else -> PlaybackFailure.UNKNOWN
        }

        fun subtitleMimeType(uri: String): String = when (uri.substringBefore('?').substringAfterLast('.').lowercase()) {
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            "vtt" -> MimeTypes.TEXT_VTT
            "ttml", "dfxp", "xml" -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }
}
