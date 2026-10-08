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
    /** Called for NEXT / PREVIOUS, e.g. to move through episodes. Returns whether it was handled. */
    private val onSkip: (forward: Boolean) -> Boolean = { false },
) {
    private var job: Job? = null
    private var lastReportedMs = -1L
    private var tracksApplied = false
    private var finished = false

    /** Loads [request] and starts observing the player. */
    fun start(scope: CoroutineScope, request: PlaybackRequest) {
        job?.cancel()
        tracksApplied = false
        finished = false
        lastReportedMs = -1L
        player.load(request)
        job = scope.launch { player.state.collect(::onState) }
    }

    /** Handles a playback action. Returns false for actions the UI should handle (navigation, menus). */
    fun handle(action: SemanticAction): Boolean {
        val state = player.state.value
        when (action) {
            SemanticAction.PLAY_PAUSE -> if (state.status == PlayerStatus.PLAYING) player.pause() else player.play()
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

    /** Stops playback and records the final position. */
    fun stop() {
        val state = player.state.value
        job?.cancel()
        job = null
        if (!finished) {
            finished = true
            reporter.stopped(itemId, state.positionMs, state.durationMs)
        }
        player.stop()
    }

    private fun onState(state: PlayerState) {
        if (!tracksApplied && state.tracks.isNotEmpty() && state.status != PlayerStatus.LOADING) {
            tracksApplied = true
            applyDefaultTracks(state)
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

    private fun applyDefaultTracks(state: PlayerState) {
        val audio = TrackSelector.selectAudio(state.tracks, preferences)
        if (audio != null && audio.id != state.selectedAudioId) player.selectAudio(audio.id)
        val subtitle = TrackSelector.selectSubtitle(state.tracks, audio, preferences)
        if (subtitle?.id != state.selectedSubtitleId) player.selectSubtitle(subtitle?.id)
    }

    private fun seekBy(state: PlayerState, deltaMs: Long) {
        val upper = state.durationMs ?: Long.MAX_VALUE
        player.seekTo((state.positionMs + deltaMs).coerceIn(0, upper))
    }

    companion object {
        const val SEEK_STEP_MS: Long = 10_000
        const val REPORT_INTERVAL_MS: Long = 10_000
        const val RESTART_THRESHOLD_MS: Long = 5_000
        const val VOLUME_STEP: Int = 5
        val SPEEDS: List<Double> = listOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

        fun nextSpeed(current: Double, up: Boolean): Double =
            if (up) SPEEDS.firstOrNull { it > current + 1e-6 } ?: SPEEDS.last()
            else SPEEDS.lastOrNull { it < current - 1e-6 } ?: SPEEDS.first()
    }
}
