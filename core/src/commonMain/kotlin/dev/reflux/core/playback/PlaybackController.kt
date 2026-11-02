package dev.reflux.core.playback

import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.MediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Receives watch-progress updates for a playing work (implemented by the library). */
interface WatchReporter {
    fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?)
    fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?)
}

/**
 * Engine-independent playback behavior: semantic actions, default tracks, and watch reporting.
 *
 * Keeps the player UI simple: it forwards semantic actions and renders [Player.state].
 */
class PlaybackController(
    private val player: Player,
    private val itemId: MediaId,
    private val reporter: WatchReporter,
    private val preferences: TrackPreferences,
    /** Called with the place asked for when the engine has said it cannot move in this media. */
    private val onSeekRefused: (positionMs: Long) -> Unit = {},
    /** Called for NEXT / PREVIOUS, e.g. to move through episodes. Returns whether it was handled. */
    private val onSkip: (forward: Boolean) -> Boolean = { false },
) {
    private var job: Job? = null
    private var lastReportedMs = -1L
    private var tracksApplied = false
    private var finished = false
    private var begun = false
    private var directive: TrackDirective? = null

    /** A planned attachment that was not there when tracks were applied: it may be on its way. */
    private var awaitedSubtitle: Int? = null

    /** Loads [request] and starts observing the player. */
    fun start(scope: CoroutineScope, request: PlaybackRequest) {
        job?.cancel()
        tracksApplied = false
        finished = false
        begun = false
        lastReportedMs = -1L
        directive = request.tracks
        awaitedSubtitle = null
        player.load(request)
        job = scope.launch { player.state.collect(::onState) }
    }

    /** Handles a playback action. Returns false for actions the UI should handle (navigation, menus). */
    fun handle(action: SemanticAction): Boolean {
        val state = player.state.value
        when (action) {
            // Buffering is playback that wants to run: pausing must work in the middle of a seek too.
            SemanticAction.PLAY_PAUSE ->
                if (state.status == PlayerStatus.PLAYING || state.status == PlayerStatus.BUFFERING) player.pause() else player.play()
            SemanticAction.SEEK_FORWARD -> seekBy(state, SEEK_STEP_MS)
            SemanticAction.SEEK_BACKWARD -> seekBy(state, -SEEK_STEP_MS)
            SemanticAction.SPEED_UP -> player.setSpeed(nextSpeed(state.speed, up = true))
            SemanticAction.SPEED_DOWN -> player.setSpeed(nextSpeed(state.speed, up = false))
            SemanticAction.VOLUME_UP -> player.setVolume((state.volume + VOLUME_STEP).coerceAtMost(100))
            SemanticAction.VOLUME_DOWN -> player.setVolume((state.volume - VOLUME_STEP).coerceAtLeast(0))
            SemanticAction.MUTE -> player.setMuted(!state.muted)
            SemanticAction.STOP -> stop()
            SemanticAction.NEXT -> return onSkip(true)
            SemanticAction.PREVIOUS -> {
                // Like every media player: restart the current item unless already near its start.
                if (state.positionMs > RESTART_THRESHOLD_MS || !onSkip(false)) player.seekTo(0)
            }
            else -> return false
        }
        return true
    }

    /**
     * Stops playback and records the final position.
     *
     * A playback that never got going (still opening, or failed to open) has no position of its own, and
     * records nothing: the work's progress stays where it was.
     *
     * @param final false stops in order to play the work again (Try again after a failure). The position is
     *   then recorded as progress, which never completes a work: nothing about trying again is an ending.
     */
    fun stop(final: Boolean = true) {
        val state = player.state.value
        job?.cancel()
        job = null
        if (!finished) {
            finished = true
            if (begun || state.status in PLAYED) {
                if (final) reporter.stopped(itemId, state.positionMs, state.durationMs) else reporter.progress(itemId, state.positionMs, state.durationMs)
            }
        }
        player.stop()
    }

    private fun onState(state: PlayerState) {
        if (state.status in PLAYED) begun = true
        if (!tracksApplied && state.tracks.isNotEmpty() && state.status != PlayerStatus.LOADING) {
            tracksApplied = true
            applyDefaultTracks(state)
        }
        awaitedSubtitle?.let { index ->
            when {
                // The viewer chose something meanwhile: that stands.
                state.selectedSubtitleId != null -> awaitedSubtitle = null
                else -> state.subtitleTracks.firstOrNull { it.external && it.position == index }?.let {
                    awaitedSubtitle = null
                    player.selectSubtitle(it.id)
                }
            }
        }
        when (state.status) {
            PlayerStatus.PLAYING -> if (lastReportedMs < 0 || state.positionMs - lastReportedMs >= REPORT_INTERVAL_MS ||
                state.positionMs < lastReportedMs
            ) {
                report(state)
            }
            PlayerStatus.PAUSED -> if (state.positionMs != lastReportedMs) report(state)
            PlayerStatus.ENDED -> if (!finished) {
                finished = true
                reporter.stopped(itemId, state.durationMs ?: state.positionMs, state.durationMs)
            }
            else -> Unit
        }
    }

    private fun report(state: PlayerState) {
        lastReportedMs = state.positionMs
        reporter.progress(itemId, state.positionMs, state.durationMs)
    }

    /**
     * Carries out what the plan chose. Default selection remains for what was not planned, or a plan's track the
     * engine does not have, except a viewer's own: that is that track or none.
     */
    private fun applyDefaultTracks(state: PlayerState) {
        val tracks = directive
        val fallback = TrackSelector.selectAudio(state.tracks, preferences)
        val audio = when (val wanted = tracks?.audio) {
            AudioDirective.None -> null
            is AudioDirective.Track -> state.audioTracks.firstOrNull { it.position == wanted.position } ?: fallback.takeUnless { wanted.explicit }
            AudioDirective.EngineChoice, null -> fallback
        }
        when {
            audio != null -> if (audio.id != state.selectedAudioId) player.selectAudio(audio.id)
            tracks?.audio != null && tracks.audio != AudioDirective.EngineChoice -> player.selectNoAudio()
        }
        val subtitle = when (val wanted = tracks?.subtitle) {
            SubtitleDirective.Off -> null
            is SubtitleDirective.Embedded -> state.subtitleTracks.firstOrNull { !it.external && it.position == wanted.position }
                ?: TrackSelector.selectSubtitle(state.tracks, audio, preferences)
            is SubtitleDirective.Attached -> state.subtitleTracks.firstOrNull { it.external && it.position == wanted.index }
                .also { if (it == null) awaitedSubtitle = wanted.index }
            SubtitleDirective.EngineChoice, null -> TrackSelector.selectSubtitle(state.tracks, audio, preferences)
        }
        if (subtitle?.id != state.selectedSubtitleId) player.selectSubtitle(subtitle?.id)
    }

    private fun seekBy(state: PlayerState, deltaMs: Long) {
        val upper = state.durationMs ?: Long.MAX_VALUE
        val target = (state.positionMs + deltaMs).coerceIn(0, upper)
        if (state.seekable == false) onSeekRefused(target) else player.seekTo(target)
    }

    companion object {
        const val SEEK_STEP_MS: Long = 10_000
        const val REPORT_INTERVAL_MS: Long = 10_000
        const val RESTART_THRESHOLD_MS: Long = 5_000
        const val VOLUME_STEP: Int = 5
        val SPEEDS: List<Double> = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

        /** States in which the engine has a position of the playback's own. */
        private val PLAYED = setOf(PlayerStatus.PLAYING, PlayerStatus.PAUSED, PlayerStatus.ENDED)

        fun nextSpeed(current: Double, up: Boolean): Double =
            if (up) SPEEDS.firstOrNull { it > current + 1e-6 } ?: SPEEDS.last()
            else SPEEDS.lastOrNull { it < current - 1e-6 } ?: SPEEDS.first()
    }
}
