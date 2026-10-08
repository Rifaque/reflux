package dev.reflux.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.reflux.core.input.InputContext
import dev.reflux.core.input.InputMapping
import dev.reflux.core.input.InputModality
import dev.reflux.core.input.InputModalityTracker
import dev.reflux.core.input.InputSignal
import dev.reflux.core.input.InputState
import dev.reflux.core.input.Key
import dev.reflux.core.input.SemanticAction
import dev.reflux.core.model.Episode
import dev.reflux.core.model.MediaId
import dev.reflux.core.model.Movie
import dev.reflux.core.model.Season
import dev.reflux.core.model.Show
import dev.reflux.core.model.SourceId
import dev.reflux.core.model.VersionId
import dev.reflux.core.source.MediaSource
import dev.reflux.library.Library
import dev.reflux.library.LibraryEntry
import dev.reflux.library.LibraryPipeline
import dev.reflux.library.PipelineActivity
import dev.reflux.library.planPlayback
import dev.reflux.library.setWatchedEverywhere
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext

/**
 * The state of the Reflux experience: where the user is, what is playing, and how they are interacting.
 *
 * It talks to the Media Core only: nothing here knows which sources exist or which engine plays. Platform
 * shells create one instance, feed it input, and render it with `RefluxApp` and `PlayerOverlay`.
 *
 * @param scope runs on the UI thread.
 * @param background where library reads and writes run, off the UI thread.
 */
@Stable
class AppState(
    val library: Library,
    private val pipeline: LibraryPipeline,
    internal val sources: (SourceId) -> MediaSource?,
    val platform: Platform,
    internal val scope: CoroutineScope,
    internal val background: CoroutineDispatcher = Dispatchers.Default,
) {
    val navigator = Navigator()

    /** Advances whenever library content or user state may have changed; screens reload when it does. */
    var revision by mutableIntStateOf(0)
        private set

    /** What is playing, if anything. Rendered above everything else by the shell. */
    var playback: PlaybackSession? by mutableStateOf(null)
        private set

    var sourcesOpen by mutableStateOf(false)

    var searchQuery by mutableStateOf("")

    /** A short, transient message ("Its source is offline"). */
    var notice: String? by mutableStateOf(null)
        private set

    /** What the background pipeline is doing, for a quiet progress indicator. */
    val activity: StateFlow<PipelineActivity> get() = pipeline.activity

    /** Volume and mute carry over from one playback session to the next. */
    internal var volume = 100
    internal var muted = false

    private var starting = false
    private var noticeJob: Job? = null
    private var watchJob: Job? = null

    /** Starts keeping the library current: an initial refresh, then change and reconnect watching. */
    fun start() {
        scope.launch { library.changes().collect { revision++ } }
        refresh()
        watchSources()
    }

    private fun watchSources() {
        watchJob?.cancel()
        watchJob = pipeline.watch(scope + background)
    }

    internal suspend fun <T> read(block: suspend Library.() -> T): T = withContext(background) { library.block() }

    /** Tells every screen that user state changed. */
    fun invalidate() {
        revision++
    }

    // Library actions ----------------------------------------------------------------------------

    fun refresh(sourceId: SourceId? = null): Job = scope.launch {
        try {
            withContext(background) { pipeline.refresh(sourceId) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            notify("Couldn't update the library. It will be retried.")
        }
        invalidate()
    }

    fun addFolder(): Job = scope.launch {
        val id = platform.addMediaFolder() ?: return@launch
        invalidate()
        watchSources()
        refresh(id).join()
    }

    fun removeSource(id: SourceId): Job = scope.launch {
        platform.removeSource(id)
        watchSources()
        invalidate()
    }

    fun toggleFavorite(entry: LibraryEntry): Job = scope.launch {
        read { setFavorite(entry.item.id, !entry.favorite) }
        invalidate()
    }

    /** Marks a work watched or unwatched here and on every source that keeps its own watch state. */
    fun setWatched(itemId: MediaId, watched: Boolean): Job = scope.launch {
        read { setWatchedEverywhere(itemId, watched, sources) }
        invalidate()
    }

    // Playback -----------------------------------------------------------------------------------

    /**
     * Plays a work: a movie or episode directly, a show from its Next Up episode (or its first).
     *
     * @param versionId a specific version instead of Best Version.
     * @param resume false starts from the beginning.
     */
    fun play(itemId: MediaId, versionId: VersionId? = null, resume: Boolean = true): Job = scope.launch {
        if (playback != null || starting) return@launch
        starting = true
        try {
            val playable = read { playableFor(itemId) } ?: return@launch notify("There is nothing to play here yet.")
            val plan = read { planPlayback(playable, platform.device, sources, versionId, resume) }
                ?: return@launch notify("This can't be played right now. Its source is offline.")
            val player = (if (platform.canPlay) platform.createPlayer() else null)
                ?: return@launch notify("No playback engine was found. Install libmpv to play media.")
            val session = PlaybackSession(this@AppState, player)
            session.load(plan)
            playback = session
        } finally {
            starting = false
        }
    }

    internal fun playbackClosed(session: PlaybackSession) {
        if (playback === session) playback = null
    }

    private fun Library.playableFor(itemId: MediaId): MediaId? = when (val item = item(itemId)) {
        is Movie, is Episode -> item.id
        is Show -> showDetail(item.id)?.let { detail ->
            detail.nextUp?.id ?: (detail.seasons.firstOrNull { it.season.number > 0 } ?: detail.seasons.firstOrNull())
                ?.episodes?.firstOrNull()?.item?.id
        }
        is Season -> showDetail(item.showId)?.seasons?.firstOrNull { it.season.id == item.id }?.let { season ->
            (season.episodes.firstOrNull { it.watchState?.completed != true } ?: season.episodes.firstOrNull())?.item?.id
        }
        null -> null
    }

    fun notify(message: String) {
        notice = message
        noticeJob?.cancel()
        noticeJob = scope.launch {
            delay(NOTICE_MS)
            notice = null
        }
    }

    // Input --------------------------------------------------------------------------------------

    private val modality = InputModalityTracker(InputModality.POINTER)

    /** How the user is interacting right now; the layout follows it (the controller morph). */
    var input: InputState by mutableStateOf(modality.state)
        private set

    /** Set while a text field has focus, so typing is never taken for shortcuts. */
    var textInputActive by mutableStateOf(false)

    /** Moves toolkit focus for a NAVIGATE_* action. Installed by the root composable. */
    internal var moveFocus: ((SemanticAction) -> Boolean)? = null
    internal var clearFocus: (() -> Unit)? = null

    /** Reports raw input evidence (pointer travel, touches, controller use) from the shell or the UI. */
    fun onSignal(signal: InputSignal) {
        val next = modality.onSignal(signal)
        if (next != input) input = next
    }

    /**
     * Handles a key from a keyboard, media keys, or a remote. Returns false when the key is not Reflux's to
     * handle, so the toolkit can process it (typing, activating the focused control).
     */
    fun onKey(key: Key): Boolean {
        onSignal(InputSignal.KeyPress(key))
        val playing = playback != null
        if (!playing && textInputActive) {
            return when (key) {
                Key.ESCAPE -> {
                    clearFocus?.invoke()
                    true
                }
                Key.ARROW_DOWN -> dispatch(SemanticAction.NAVIGATE_DOWN)
                else -> false
            }
        }
        val action = InputMapping.map(key, if (playing) InputContext.PLAYBACK else InputContext.BROWSE) ?: return false
        return dispatch(action)
    }

    /** Performs a semantic action, whichever input device expressed it. */
    fun dispatch(action: SemanticAction): Boolean {
        playback?.let { return it.handle(action) }
        return when (action) {
            SemanticAction.NAVIGATE_UP, SemanticAction.NAVIGATE_DOWN, SemanticAction.NAVIGATE_LEFT, SemanticAction.NAVIGATE_RIGHT ->
                moveFocus?.invoke(action) ?: false
            SemanticAction.BACK -> if (sourcesOpen) {
                sourcesOpen = false
                true
            } else {
                navigator.back()
            }
            SemanticAction.MENU -> {
                sourcesOpen = !sourcesOpen
                true
            }
            SemanticAction.FULLSCREEN -> {
                platform.requestFullscreen(!platform.fullscreen)
                true
            }
            // SELECT activates the focused control, which the toolkit does itself.
            else -> false
        }
    }

    private companion object {
        const val NOTICE_MS = 4_500L
    }
}

/**
 * Reads from the library off the UI thread and keeps the result current: it reloads whenever the library
 * or user state changes, keeping the previous value on screen meanwhile so nothing flickers.
 */
@Composable
fun <T> AppState.libraryState(vararg keys: Any?, block: Library.() -> T): State<T?> {
    val state = remember(*keys) { mutableStateOf<T?>(null) }
    LaunchedEffect(revision, *keys) { state.value = read { block() } }
    return state
}
