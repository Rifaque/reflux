package dev.reflux.ui.state

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.MediaItem
import dev.reflux.core.playback.AutoplayPolicy
import dev.reflux.core.playback.PlaybackController
import dev.reflux.core.playback.Player
import dev.reflux.core.playback.PlayerState
import dev.reflux.core.playback.PlayerStatus
import dev.reflux.core.playback.SkipSegment
import dev.reflux.core.playback.TrackPreferences
import dev.reflux.core.playback.WatchReporter
import dev.reflux.library.Library
import dev.reflux.library.LibraryEntry
import dev.reflux.library.PlaybackPlan
import dev.reflux.library.SyncingWatchReporter
import dev.reflux.library.planPlayback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/** The post-play prompt: a countdown into [entry], or a "still watching?" question when [asks] is set. */
data class UpNext(val entry: LibraryEntry, val totalMs: Long, val remainingMs: Long, val asks: Boolean)

/**
 * One visit to the player: the work being played, its neighbours, and what happens when it ends.
 *
 * The player UI renders [state] and forwards semantic actions to [handle]; engine-independent playback
 * behavior (default tracks, watch reporting, skipping) stays in the core `PlaybackController`.
 */
@Stable
class PlaybackSession internal constructor(
    private val app: AppState,
    private val player: Player,
) {
    private val scope: CoroutineScope = app.scope + SupervisorJob(app.scope.coroutineContext[Job])
    private val autoplay = AutoplayPolicy()
    private val wasFullscreen = app.platform.fullscreen
    private var controller: PlaybackController? = null
    private var reporter: SerialWatchReporter? = null
    private var countdown: Job? = null
    private var endHandled = false
    private var closing: Job? = null
    private val closed: Boolean get() = closing != null

    val state: StateFlow<PlayerState> get() = player.state

    var entry: LibraryEntry? by mutableStateOf(null)
        private set

    /** The headline shown in the player: the movie or show title. */
    var title by mutableStateOf("")
        private set

    /** The line under it: `S1 · E2 · Cat's in the Bag...` or the year. */
    var subtitle: String? by mutableStateOf(null)
        private set

    var previous: LibraryEntry? by mutableStateOf(null)
        private set

    var next: LibraryEntry? by mutableStateOf(null)
        private set

    var upNext: UpNext? by mutableStateOf(null)
        private set

    /** Set when playback failed; the player shows it and offers to close. */
    var error: String? by mutableStateOf(null)
        private set

    /** Advances on every user interaction, so the player UI can reveal its controls. */
    var interactions by mutableIntStateOf(0)
        private set

    init {
        player.setVolume(app.volume)
        player.setMuted(app.muted)
        scope.launch { player.state.collect(::onState) }
    }

    internal suspend fun load(plan: PlaybackPlan) {
        val itemId = plan.itemId
        val details = app.read {
            val entry = entry(itemId)
            Details(entry, entry?.item?.let { heading(it) } ?: (plan.request.title to null), entry?.item?.let { neighbours(it) } ?: (null to null))
        }
        controller?.stop()
        countdown?.cancel()
        upNext = null
        error = null
        endHandled = false
        entry = details.entry
        title = details.heading.first
        subtitle = details.heading.second
        previous = details.neighbours.first
        next = details.neighbours.second
        val reporter = SerialWatchReporter(
            SyncingWatchReporter(app.library, plan.version.version, plan.source, app.scope + app.background),
            app.scope,
            app.background,
        )
        this.reporter = reporter
        controller = PlaybackController(
            player = player,
            itemId = itemId,
            reporter = reporter,
            preferences = TrackPreferences(app.platform.preferredLanguages),
            onSkip = { forward -> switchTo(if (forward) next else previous) },
        ).also { it.start(scope, plan.request) }
    }

    /** Handles a semantic action while the player is open. Every action counts as "still watching". */
    fun handle(action: SemanticAction): Boolean {
        touch()
        val prompt = upNext
        when (action) {
            SemanticAction.BACK, SemanticAction.STOP -> close()
            SemanticAction.FULLSCREEN -> app.platform.requestFullscreen(!app.platform.fullscreen)
            SemanticAction.PLAY_PAUSE, SemanticAction.SELECT -> when {
                prompt == null -> controller?.handle(SemanticAction.PLAY_PAUSE)
                prompt.asks -> continueWatching()
                else -> playNext()
            }
            // Without directional focus in the player, up and down are the volume, as in every desktop player.
            SemanticAction.NAVIGATE_UP -> controller?.handle(SemanticAction.VOLUME_UP)
            SemanticAction.NAVIGATE_DOWN -> controller?.handle(SemanticAction.VOLUME_DOWN)
            else -> controller?.handle(action)
        }
        return true
    }

    /** Any user activity in the player that is not a semantic action (pointer movement, scrubbing). */
    fun touch() {
        autoplay.onInteraction()
        interactions++
    }

    fun seekTo(positionMs: Long) {
        touch()
        cancelUpNext()
        player.seekTo(positionMs.coerceAtLeast(0))
    }

    fun skip(segment: SkipSegment) = seekTo(segment.endMs)

    fun setVolume(volume: Int) {
        touch()
        player.setVolume(volume)
        if (volume > 0) player.setMuted(false)
    }

    fun setSpeed(speed: Double) {
        touch()
        player.setSpeed(speed)
    }

    fun selectAudio(trackId: String) {
        touch()
        player.selectAudio(trackId)
    }

    /** Selects a subtitle track, or turns subtitles off with null. */
    fun selectSubtitle(trackId: String?) {
        touch()
        player.selectSubtitle(trackId)
    }

    fun playNext() = switchTo(next)

    fun playPrevious() = switchTo(previous)

    /** Dismisses the post-play prompt and stays on the finished episode. */
    fun cancelUpNext() {
        countdown?.cancel()
        upNext = null
    }

    /** Answers "still watching?" with yes. */
    fun continueWatching() {
        autoplay.onStillWatching()
        playNext()
    }

    /**
     * Leaves the player: records the position, releases the engine, and returns to browsing. The player UI
     * disappears at once; the returned job completes when the position is saved and the engine released.
     */
    fun close(): Job {
        closing?.let { return it }
        countdown?.cancel()
        rememberVolume()
        controller?.stop()
        scope.cancel()
        if (app.platform.fullscreen != wasFullscreen) app.platform.requestFullscreen(wasFullscreen)
        app.playbackClosed(this)
        val pending = reporter
        return app.scope.launch {
            pending?.flush()
            app.invalidate()
            app.platform.releasePlayer(player)
        }.also { closing = it }
    }

    private fun switchTo(target: LibraryEntry?): Boolean {
        if (target == null || closed) return false
        countdown?.cancel()
        upNext = null
        scope.launch {
            val plan = app.read { planPlayback(target.item.id, app.platform.device, app.sources) }
            if (plan == null) {
                app.notify("The next episode can't be played right now. Its source is offline.")
                close()
            } else {
                reporter?.flush()
                load(plan)
            }
        }
        return true
    }

    private fun onState(state: PlayerState) {
        when (state.status) {
            PlayerStatus.ENDED -> if (!endHandled) {
                endHandled = true
                onEnded()
            }
            PlayerStatus.ERROR -> error = "This couldn't be played." + (state.error?.let { " ($it)" } ?: "")
            else -> Unit
        }
    }

    private fun onEnded() {
        val following = next
        when (val decision = autoplay.onEnded(hasNext = following != null)) {
            is AutoplayPolicy.Decision.Countdown -> {
                upNext = UpNext(following!!, decision.countdownMs, decision.countdownMs, asks = false)
                countdown = scope.launch {
                    var remaining = decision.countdownMs
                    while (remaining > 0) {
                        delay(COUNTDOWN_TICK_MS)
                        remaining -= COUNTDOWN_TICK_MS
                        upNext = upNext?.copy(remainingMs = remaining.coerceAtLeast(0))
                    }
                    playNext()
                }
            }
            AutoplayPolicy.Decision.AskStillWatching -> upNext = UpNext(following!!, 0, 0, asks = true)
            AutoplayPolicy.Decision.Stop -> close()
        }
    }

    private fun rememberVolume() {
        val current = player.state.value
        app.volume = current.volume
        app.muted = current.muted
    }

    private class Details(
        val entry: LibraryEntry?,
        val heading: Pair<String, String?>,
        val neighbours: Pair<LibraryEntry?, LibraryEntry?>,
    )

    private companion object {
        const val COUNTDOWN_TICK_MS = 250L
    }
}

/** The player headline for a work: title, and a second line for episodes and dated movies. */
internal fun Library.heading(item: MediaItem): Pair<String, String?> = when (item) {
    is Episode -> {
        val show = item(item.showId)?.title ?: item.title
        show to listOfNotNull(Formatting.episodeNumber(item), item.episodeTitle).joinToString(" · ")
    }
    else -> item.title to Formatting.year(item)?.toString()
}

/** The episodes before and after [item] in viewing order; specials are not part of the sequence. */
internal fun Library.neighbours(item: MediaItem): Pair<LibraryEntry?, LibraryEntry?> {
    val episode = item as? Episode ?: return null to null
    val episodes = showDetail(episode.showId)?.seasons.orEmpty().filter { it.season.number != 0 }.flatMap { it.episodes }
    val index = episodes.indexOfFirst { it.item.id == episode.id }
    if (index < 0) return null to null
    return episodes.getOrNull(index - 1) to episodes.getOrNull(index + 1)
}

/**
 * Moves watch reports off the UI thread while keeping their order, so a progress write can never land
 * after the final "stopped" write.
 */
internal class SerialWatchReporter(
    private val delegate: WatchReporter,
    private val scope: CoroutineScope,
    dispatcher: CoroutineDispatcher,
) : WatchReporter {
    private val serial = dispatcher.limitedParallelism(1)

    override fun progress(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        scope.launch(serial) { delegate.progress(itemId, positionMs, durationMs) }
    }

    override fun stopped(itemId: MediaId, positionMs: Long, durationMs: Long?) {
        scope.launch(serial) { delegate.stopped(itemId, positionMs, durationMs) }
    }

    /** Suspends until every report so far has been written. */
    suspend fun flush() = withContext(serial) {}
}
